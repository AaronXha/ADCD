package DADC;

import ADCD.evidence.evidenceSet.Evidence;
import ADCD.inversion.ArrayIndexComparator;
import ADCD.inversion.BitSetTranslator;
import ADCD.inversion.approx.ArrayTreeSearch;
import ADCD.inversion.approx.DCCandidate;
import ADCD.inversion.approx.SearchNode;
import ch.javasoft.bitset.LongBitSet;

import java.util.*;

public class AEI {

    private final int nPredicates;
    public LongBitSet[] mutexMap;   // i -> indices of predicates from the same column pair with predicate i
    public List<Evidence> evidenceUncover;
    public List<LongBitSet> minValidDCDemo;
    public boolean ascending;//升序
    public long target;
    public LongBitSet dc;
    public LongBitSet canAdd;
    BitSetTranslator translator;    // re-order predicates by evidence coverage

    public AEI(LongBitSet dc, List<Evidence> evidenceList, Set<LongBitSet> minValidDCDemo, LongBitSet[] mutexMap, long target, boolean ascending, int nPredicates){
        this.nPredicates = nPredicates;
        this.mutexMap = mutexMap;
        this.target = target;
        this.ascending = ascending;
        ArrayTreeSearch.N = nPredicates;
        this.dc = dc;
        this.minValidDCDemo = new ArrayList<>(minValidDCDemo);
        //this.minValidDCDemo.sort(Comparator.comparingInt(LongBitSet::cardinality));
        this.evidenceUncover = new ArrayList<>();
        this.canAdd = new LongBitSet();
        for(Evidence evi: evidenceList){
            LongBitSet e = evi.getBitSetPredicates();
            if(dc.isSubSetOf(e)){
                canAdd.or(LongBitSet.getXor(dc, e));
                /*for (int i = e.nextSetBit(0); i >= 0; i = e.nextSetBit(i + 1))    if (!dc.get(i))    predicateUnchosen.set(i);*/
                evidenceUncover.add(evi);
            }
            else
                this.target -= evi.count;
        }
        for(int i = dc.nextSetBit(0); i >= 0; i = dc.nextSetBit(i + 1))
            canAdd.andNot(mutexMap[i]);
        /*for(LongBitSet e: this.minValidDCDemo) {
            if (e.cardinality() == 1)
                canAdd.andNot(e);
            else
                break;
        }*/
    }

    private int[] getCounts(List<Evidence> evidenceList) {
        //统计每个谓词在evidenceSet中出现了多少次
        int[] counts = new int[nPredicates];
        for (Evidence e : evidenceList)
            for (int i = e.bitset.nextSetBit(0); i >= 0; i = e.bitset.nextSetBit(i + 1))
                counts[i]++;
        return counts;
    }

    public LongBitSet[] transformMutexMap(LongBitSet[] mutexMap) {
        LongBitSet[] transMutexMap = new LongBitSet[mutexMap.length];
        for (int i = 0; i < mutexMap.length; i++) {
            transMutexMap[translator.transform(i)] = translator.transform(mutexMap[i]);
        }
        return transMutexMap;
    }

    public List<Evidence> transformEvidenceList(List<Evidence> evidenceList) {
        List<Evidence> evidenceListNew = new ArrayList<>();
        for(Evidence e: evidenceList)
            evidenceListNew.add(new Evidence(translator.transform(e.bitset), e.count));
        return evidenceListNew;
    }

    public boolean isApproxCover(LongBitSet dc, int e, long target) {
        //target 表示还需要覆盖多少个evidence
        if (target <= 0) return true;
        for (; e < evidenceUncover.size(); e++) {
            //如果下面判断数为真 那么说明还没有覆盖当前这个evidence  那么这个数字就不能被加上
            if (!dc.isSubSetOf(evidenceUncover.get(e).bitset)) {
                target -= evidenceUncover.get(e).count;
                if (target <= 0) return true;
            }
        }
        return false;
    }

    public Set<LongBitSet> build(){
        int[] counts = getCounts(evidenceUncover);
        ArrayIndexComparator comparator = new ArrayIndexComparator(counts, ascending);
        translator = new BitSetTranslator(comparator.createIndexArray());
        evidenceUncover = transformEvidenceList(evidenceUncover);
        mutexMap = transformMutexMap(mutexMap);
        //按照evidence的频率从高到低排
        evidenceUncover.sort((o1, o2) -> Long.compare(o2.count, o1.count));

        //执行
        //System.out.println("  [PACS] Inverting evidences...");
        ArrayTreeSearch approxCovers = new ArrayTreeSearch();
        dc = translator.transform(dc);
        canAdd = translator.transform(canAdd);

        //存放需要被覆盖的evidence
        Stack<SearchNode> nodes = new Stack<>();    // manual stack, where evidences[node.e] needs to be hit
        //以前缀树的方式构成dcCandidates
        ArrayTreeSearch dcCandidates = new ArrayTreeSearch();

        //初始化
        dcCandidates.add(new DCCandidate(dc.clone(), canAdd.clone()));

        //第一个参数代表 第几个evidence  第二个代表能选那些谓词 第三个是有哪些dc的候选者 第四个是还要覆盖多少 哪些evi还没有覆盖
        walk(0, canAdd, dcCandidates, target, nodes, approxCovers, "");

        while (!nodes.isEmpty()) {
            SearchNode nd = nodes.pop();
            //遍历完所有的evidence或者没有可以加入的predicate 那么就结束了。
            if (nd.e >= evidenceUncover.size() || nd.addablePredicates.isEmpty())
                continue;
            hit(nd, approxCovers);    // hit evidences[e]
            if (nd.target > 0)
                walk(nd.e + 1, nd.addablePredicates, nd.dcCandidates, nd.target, nodes, approxCovers, nd.H);
        }

        /* collect resulted DC */
        Set<LongBitSet> rawDCs = new HashSet<>();
        //回到原来的predicate的顺序
        approxCovers.forEach(transDC -> rawDCs.add(translator.retransform(transDC.bitSet)));
        //System.out.println("  [PACS] Total DC size: " + rawDCs.size());

        return rawDCs;
    }

    public void walk(int e, LongBitSet addablePredicates, ArrayTreeSearch dcCandidates, long target, Stack<SearchNode> nodes, ArrayTreeSearch approxCovers, String status) {
        while (e < evidenceUncover.size() && !dcCandidates.isEmpty()) {
            LongBitSet evi = evidenceUncover.get(e).bitset;
            //将无效的dc收集起来，就是从候选集里面不能覆盖当前evi的dc收集起来 然后添加新的谓词来 即这个无效dc里面的谓词都是这个evi的子集。
            Collection<DCCandidate> unhitEviDCs = dcCandidates.getAndRemoveGeneralizations(evi);

            // hit evidences[e] later
            SearchNode nd = new SearchNode(e, addablePredicates.clone(), dcCandidates, unhitEviDCs, target,status + e);
            nodes.push(nd);

            // unhit evidences[e]  所有的dc候选者都已经覆盖了当前的这个evi  那么就不存在不覆盖这个evi的可能了。
            if (unhitEviDCs.isEmpty()) return;
            //如果我不覆盖这个evi，那么我可选的谓词只能是evi里面出现过的。
            addablePredicates.and(evi);
            //如果没有了候选的谓词 那么就结束了。
            if (addablePredicates.isEmpty()) return;

            //如果不覆盖这个evi  且后面的全部覆盖 也不能达到target  那么就不必要进行了结束。
            long maxCanHit = 0L;
            for (int i = e + 1; i < evidenceUncover.size(); i++)
                if (!addablePredicates.isSubSetOf(evidenceUncover.get(i).bitset))
                    maxCanHit += evidenceUncover.get(i).count;
            if (maxCanHit < target) return;
            //为不覆盖当前evi  构建新的dcCandidate的树  方便搜索。
            ArrayTreeSearch newCandidates = new ArrayTreeSearch();
            for (DCCandidate dc : unhitEviDCs) {
                LongBitSet unhitCand = dc.cand.getAnd(evi);
                if (!unhitCand.isEmpty()){
                    //if(checkCanAdd(dc.bitSet.clone()))
                        newCandidates.add(new DCCandidate(dc.bitSet, unhitCand));
                }
                    //如果没有了候选，说明递归结束了 那么需要直接进行判断是否是有效的dc
                else if (!approxCovers.containsSubset(dc) && isApproxCover(dc.bitSet, e + 1, target))
                    approxCovers.add(dc);
            }

            if (newCandidates.isEmpty()) return;

            e++;
            dcCandidates = newCandidates;
        }
    }

    public boolean checkCanAdd(LongBitSet dc){
        dc = translator.retransform(dc);
        for(LongBitSet e: minValidDCDemo) {
            if (e.isSubSetOf(dc)) {
                return false;
            }
        }
        return true;
    }


    public void hit(SearchNode nd, ArrayTreeSearch approxCovers) {
        //如果候选predicates是evi的subset 那么就不可能覆盖了  直接结束。
        if (nd.e >= evidenceUncover.size() || nd.addablePredicates.isSubSetOf(evidenceUncover.get(nd.e).bitset))
            return;

        nd.target -= evidenceUncover.get(nd.e).count;

        LongBitSet evi = evidenceUncover.get(nd.e).bitset;
        ArrayTreeSearch dcCandidates = nd.dcCandidates;

        //如果小于0  那么这个候选dc全部加入到结果中去。
        if (nd.target <= 0) {
            dcCandidates.forEach(approxCovers::add);
            for (DCCandidate invalidDC : nd.invalidDCs) {
                //如果我需要覆盖了当前的  我可选的是addPredicate和not evi的交集
                LongBitSet canAdd = invalidDC.cand.getAndNot(evi);
                for (int i = canAdd.nextSetBit(0); i >= 0; i = canAdd.nextSetBit(i + 1)) {
                    DCCandidate validDC = invalidDC.clone();
                    validDC.bitSet.set(i);
                    if (!approxCovers.containsSubset(validDC)){
                        if(checkCanAdd(validDC.bitSet.clone()))
                            approxCovers.add(validDC);
                    }
                }
            }
        } else {
            for (DCCandidate invalidDC : nd.invalidDCs) {
                LongBitSet canAdd = invalidDC.cand.getAndNot(evi);
                for (int i = canAdd.nextSetBit(0); i >= 0; i = canAdd.nextSetBit(i + 1)) {
                    DCCandidate validDC = invalidDC.clone();
                    validDC.bitSet.set(i);
                    //一个谓词组只能选择其他一个 如果我选了其中一个，那么我的候选predicate需要把这个谓词组给排除。
                    validDC.cand.andNot(mutexMap[i]);
                    if (!dcCandidates.containsSubset(validDC) && !approxCovers.containsSubset(validDC)) {
                        if (!validDC.cand.isEmpty()){
                            if(checkCanAdd(validDC.bitSet.clone()))
                                dcCandidates.add(validDC);
                        }
                            //如果候选为空了 那么就需要进行判断 如果满足要求 那么就加入到最后的结果里面
                        else if (isApproxCover(validDC.bitSet, nd.e + 1, nd.target))
                            approxCovers.add(validDC);
                    }
                }
            }
        }
    }

}
