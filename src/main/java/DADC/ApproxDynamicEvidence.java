package DADC;

import ADCD.evidence.evidenceSet.Evidence;
import ADCD.evidence.evidenceSet.EvidenceSet;
import ADCD.inversion.ArrayIndexComparator;
import ADCD.inversion.BitSetTranslator;
import ADCD.inversion.approx.ArrayTreeSearch;
import ADCD.inversion.approx.DCCandidate;
import ADCD.inversion.approx.SearchNode;
import ADCD.predicate.PredicateBuilder;
import ch.javasoft.bitset.LongBitSet;
import de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint;
import de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraintSet;
import java.util.*;

public class ApproxDynamicEvidence {
    private final int nPredicates;
    private LongBitSet[] mutexMap;   // i -> indices of predicates from the same column pair with predicate i
    private Evidence[] evidences;//将所有evidence从evidenceset里面拿出来。

    private ArrayTreeSearch approxCovers;//前缀树
//    public ArrayTreeSearch invalidDCs;
//    public ArrayTreeSearch notMinDCs;

    boolean ascending;//升序
    BitSetTranslator translator;    // re-order predicates by evidence coverage

    public ApproxDynamicEvidence(PredicateBuilder pBuilder, boolean ascending) {
        this.nPredicates = pBuilder.predicateCount();
        this.mutexMap = pBuilder.getMutexMap();
        this.approxCovers = null;
        this.ascending = ascending;
        ArrayTreeSearch.N = nPredicates;
    }

   public List<LongBitSet> buildInvalidDCs(Pair dc){
        Evidence[] evidence = new Evidence[dc.unhitEvidenceSet.size()];
        int[] counts = getCounts(dc.unhitEvidenceSet);
        for(int i=0;i< dc.unhitEvidenceSet.size();i++)evidence[i] = dc.unhitEvidenceSet.get(i);
       ArrayIndexComparator comparator = new ArrayIndexComparator(counts, ascending);
       BitSetTranslator   translator = new BitSetTranslator(comparator.createIndexArray());
       mutexMap = transformMutexMap(mutexMap,translator);
       Arrays.sort(evidence,(o1,o2)->Long.compare(o2.count,o1.count));
       ArrayTreeSearch res = inverseEvidenceSet(dc.needEvidence,dc.predicateUnchosed,new DCCandidate(dc.DCCandicate,dc.predicateUnchosed),evidence);
       List<LongBitSet> rawDC = new ArrayList<>();
       res.forEach(dcs->rawDC.add(translator.retransform(dcs.bitSet)));
        return rawDC;
   }
   public ArrayTreeSearch inverseEvidenceSet(long target,LongBitSet predicateUnchoosed,DCCandidate dcCandidate,Evidence[] evidences){
        ArrayTreeSearch approxCovers = new ArrayTreeSearch();
        Stack<SearchNode> stack = new Stack<>();
        ArrayTreeSearch dcCandidates = new ArrayTreeSearch();
        dcCandidates.add(dcCandidate);
        walk(0,predicateUnchoosed,dcCandidates,target,stack,"",evidences,approxCovers);
        while (!stack.empty()){
            SearchNode nd = stack.pop();
            //遍历完所有的evidence或者没有可以加入的predicate 那么就结束了。
            if (nd.e >= evidences.length || nd.addablePredicates.isEmpty())
                continue;
            hit(nd,evidences,approxCovers);    // hit evidences[e]
            if (nd.target > 0)
                walk(nd.e + 1, nd.addablePredicates, nd.dcCandidates, nd.target, stack, nd.H,evidences,approxCovers);
        }
        return approxCovers;
   }

    public DenialConstraintSet buildDenialConstraints(EvidenceSet evidenceSet, long target) {
        //精确dc
        if (target == 0) {
            DenialConstraintSet res = new DenialConstraintSet();
            res.add(new DenialConstraint(new LongBitSet()));
            return res;
        }
        //由于根据谓词出现的频率重新进行了排序  当需要覆盖一个evidence时候，需要一个谓词 其没有在evidence中出现过，那么这个谓词频率越低
        //符合的可能性越大
        int[] counts = getCounts(evidenceSet);
        ArrayIndexComparator comparator = new ArrayIndexComparator(counts, ascending);
        translator = new BitSetTranslator(comparator.createIndexArray());

        evidences = transformEvidenceSet(evidenceSet);
        mutexMap = transformMutexMap(mutexMap);
        //按照evidence的频率从高到低排
        Arrays.sort(evidences, (o1, o2) -> Long.compare(o2.count, o1.count));
        //执行
        inverseEvidenceSet(target);

        /* collect resulted DC */
        List<LongBitSet> rawDCs = new ArrayList<>();
        //回到原来的predicate的顺序
        approxCovers.forEach(transDC -> rawDCs.add(translator.retransform(transDC.bitSet)));
        System.out.println("  [PACS] Min cover size: " + rawDCs.size());

        DenialConstraintSet constraints = new DenialConstraintSet();
        //去掉一些重复的dc
        for (LongBitSet rawDC : rawDCs)
            constraints.add(new DenialConstraint(rawDC));
        System.out.println("  [PACS] Total DC size: " + constraints.size());

        constraints.minimize();
        constraints.minimize();
        System.out.println("  [PACS] Min DC size : " + constraints.size());
        return constraints;
    }

    void inverseEvidenceSet(long target) {
        System.out.println("  [PACS] Inverting evidences...");

        approxCovers = new ArrayTreeSearch();
        LongBitSet fullMask = new LongBitSet(nPredicates);
        //都变成1
        for (int i = 0; i < nPredicates; i++)
            fullMask.set(i);
        //存放需要被覆盖的evidence
        Stack<SearchNode> nodes = new Stack<>();    // manual stack, where evidences[node.e] needs to be hit
        //以前缀树的方式构成dcCandidates
        ArrayTreeSearch dcCandidates = new ArrayTreeSearch();
        //初始化  没有任何一个已经被选的 以及所有的谓词都可以被选择。
        dcCandidates.add(new DCCandidate(new LongBitSet(), fullMask.clone()));
        //第一个参数代表 第几个evidence  第二个代表能选那些谓词 第三个是有哪些dc的候选者 第四个是还要覆盖多少  哪些evid还没有覆盖
        walk(0, fullMask, dcCandidates, target, nodes, "");


        while (!nodes.isEmpty()) {
            SearchNode nd = nodes.pop();
            //遍历完所有的evidence或者没有可以加入的predicate 那么就结束了。
            if (nd.e >= evidences.length || nd.addablePredicates.isEmpty())
                continue;
            hit(nd);    // hit evidences[e]
            if (nd.target > 0)
                walk(nd.e + 1, nd.addablePredicates, nd.dcCandidates, nd.target, nodes, nd.H);
        }
    }
    //第一个参数代表 第几个evidence  第二个代表能选那些谓词 第三个是有哪些dc的候选者 第四个是还要覆盖多少  哪些evid需要覆盖
    void walk(int e, LongBitSet addablePredicates, ArrayTreeSearch dcCandidates, long target, Stack<SearchNode> nodes, String status) {
        while (e < evidences.length && !dcCandidates.isEmpty()) {
            LongBitSet evi = evidences[e].bitset;
            //将无效的dc收集起来，就是从候选集里面不能覆盖当前evi的dc收集起来 然后添加新的谓词来 即这个无效dc里面的谓词都是这个evi的子集。
            Collection<DCCandidate> unhitEviDCs = dcCandidates.getAndRemoveGeneralizations(evi);

            // hit evidences[e] later
            SearchNode nd = new SearchNode(e, addablePredicates.clone(), dcCandidates, unhitEviDCs, target,status+e);
            nodes.push(nd);

            // unhit evidences[e]  所有的dc候选者都已经覆盖了当前的这个evi  那么就不存在不覆盖这个evi的可能了。
            if (unhitEviDCs.isEmpty()) return;
            //如果我不覆盖这个evid，那么我可选的谓词只能是evid里面出现过的。
            addablePredicates.and(evi);
            //如果没有了候选的谓词 那么就结束了。
            if (addablePredicates.isEmpty()) return;

            //如果不覆盖这个evi  且后面的全部覆盖 也不能达到target  那么就不必要进行了结束。
            long maxCanHit = 0L;
            for (int i = e + 1; i < evidences.length; i++)
                if (!addablePredicates.isSubSetOf(evidences[i].bitset))
                    maxCanHit += evidences[i].count;
            if (maxCanHit < target) return;
            //为不覆盖当前evi  构建新的dccandidate的树  方便搜索。
            ArrayTreeSearch newCandidates = new ArrayTreeSearch();
            for (DCCandidate dc : unhitEviDCs) {
                LongBitSet unhitCand = dc.cand.getAnd(evi);
                if (!unhitCand.isEmpty())
                    newCandidates.add(new DCCandidate(dc.bitSet, unhitCand));
                    //如果没有了候选，说明递归结束了 那么需要直接进行判断是否是有效的dc
                else if (!approxCovers.containsSubset(dc) && isApproxCover(dc.bitSet, e + 1, target))
                    approxCovers.add(dc);
            }
            if (newCandidates.isEmpty()) return;

            e++;
            dcCandidates = newCandidates;
        }
    }
    void walk(int e, LongBitSet addablePredicates, ArrayTreeSearch dcCandidates, long target, Stack<SearchNode> nodes, String status,Evidence[] evidences,ArrayTreeSearch approxCovers) {
        while (e < evidences.length && !dcCandidates.isEmpty()) {
            LongBitSet evi = evidences[e].bitset;
            //将无效的dc收集起来，就是从候选集里面不能覆盖当前evi的dc收集起来 然后添加新的谓词来 即这个无效dc里面的谓词都是这个evi的子集。
            Collection<DCCandidate> unhitEviDCs = dcCandidates.getAndRemoveGeneralizations(evi);

            // hit evidences[e] later
            SearchNode nd = new SearchNode(e, addablePredicates.clone(), dcCandidates, unhitEviDCs, target,status+e);
            nodes.push(nd);

            // unhit evidences[e]  所有的dc候选者都已经覆盖了当前的这个evi  那么就不存在不覆盖这个evi的可能了。
            if (unhitEviDCs.isEmpty()) return;
            //如果我不覆盖这个evid，那么我可选的谓词只能是evid里面出现过的。
            addablePredicates.and(evi);
            //如果没有了候选的谓词 那么就结束了。
            if (addablePredicates.isEmpty()) return;

            //如果不覆盖这个evi  且后面的全部覆盖 也不能达到target  那么就不必要进行了结束。
            long maxCanHit = 0L;
            for (int i = e + 1; i < evidences.length; i++)
                if (!addablePredicates.isSubSetOf(evidences[i].bitset))
                    maxCanHit += evidences[i].count;
            if (maxCanHit < target) return;
            //为不覆盖当前evi  构建新的dccandidate的树  方便搜索。
            ArrayTreeSearch newCandidates = new ArrayTreeSearch();
            for (DCCandidate dc : unhitEviDCs) {
                LongBitSet unhitCand = dc.cand.getAnd(evi);
                if (!unhitCand.isEmpty())
                    newCandidates.add(new DCCandidate(dc.bitSet, unhitCand));
                    //如果没有了候选，说明递归结束了 那么需要直接进行判断是否是有效的dc
                else if (!approxCovers.containsSubset(dc) && isApproxCover(dc.bitSet, e + 1, target,evidences))
                    approxCovers.add(dc);
            }
            if (newCandidates.isEmpty()) return;

            e++;
            dcCandidates = newCandidates;
        }
    }
    void hit(SearchNode nd) {
        //如果候选predicates是evi的subset 那么就不可能覆盖了  直接结束。
        if (nd.e >= evidences.length || nd.addablePredicates.isSubSetOf(evidences[nd.e].bitset))
            return;

        nd.target -= evidences[nd.e].count;

        LongBitSet evi = evidences[nd.e].bitset;
        ArrayTreeSearch dcCandidates = nd.dcCandidates;

        //如果小于0  那么这个候选dc全部加入到结果中去。
        if (nd.target <= 0) {
            dcCandidates.forEach(dc -> approxCovers.add(dc));
            for (DCCandidate invalidDC : nd.invalidDCs) {
                //如果我需要覆盖了当前的  我可选的是addpredicate和notevi的交集
                LongBitSet canAdd = invalidDC.cand.getAndNot(evi);
                for (int i = canAdd.nextSetBit(0); i >= 0; i = canAdd.nextSetBit(i + 1)) {
                    DCCandidate validDC = new DCCandidate(invalidDC.bitSet.clone());
                    validDC.bitSet.set(i);
                    if (!approxCovers.containsSubset(validDC))
                        approxCovers.add(validDC);
                }
            }
        } else {
            for (DCCandidate invalidDC : nd.invalidDCs) {
                LongBitSet canAdd = invalidDC.cand.getAndNot(evi);
                for (int i = canAdd.nextSetBit(0); i >= 0; i = canAdd.nextSetBit(i + 1)) {
                    DCCandidate validDC = invalidDC.clone();
                    validDC.bitSet.set(i);
                    //一个谓词组只能选择其他一个 如果我选了其中一个，那么我的候选predicat需要把这个谓词组给排除。
                    validDC.cand.andNot(mutexMap[i]);
                    if (!dcCandidates.containsSubset(validDC) && !approxCovers.containsSubset(validDC)) {
                        if (!validDC.cand.isEmpty())
                            dcCandidates.add(validDC);
                            //如果候选为空了 那么就需要进行判断 如果满足要求 那么就加入到最后的结果里面
                        else if (isApproxCover(validDC.bitSet, nd.e + 1, nd.target))
                            approxCovers.add(validDC);
                    }
                }
            }
        }
    }
    void hit(SearchNode nd,Evidence[] evidences,ArrayTreeSearch approxCovers) {
        //如果候选predicates是evi的subset 那么就不可能覆盖了  直接结束。
        if (nd.e >= evidences.length || nd.addablePredicates.isSubSetOf(evidences[nd.e].bitset))
            return;

        nd.target -= evidences[nd.e].count;

        LongBitSet evi = evidences[nd.e].bitset;
        ArrayTreeSearch dcCandidates = nd.dcCandidates;

        //如果小于0  那么这个候选dc全部加入到结果中去。
        if (nd.target <= 0) {
            dcCandidates.forEach(dc -> approxCovers.add(dc));
            for (DCCandidate invalidDC : nd.invalidDCs) {
                //如果我需要覆盖了当前的  我可选的是addpredicate和notevi的交集
                LongBitSet canAdd = invalidDC.cand.getAndNot(evi);
                for (int i = canAdd.nextSetBit(0); i >= 0; i = canAdd.nextSetBit(i + 1)) {
                    DCCandidate validDC = new DCCandidate(invalidDC.bitSet.clone());
                    validDC.bitSet.set(i);
                    if (!approxCovers.containsSubset(validDC))
                        approxCovers.add(validDC);
                }
            }
        } else {
            for (DCCandidate invalidDC : nd.invalidDCs) {
                LongBitSet canAdd = invalidDC.cand.getAndNot(evi);
                for (int i = canAdd.nextSetBit(0); i >= 0; i = canAdd.nextSetBit(i + 1)) {
                    DCCandidate validDC = invalidDC.clone();
                    validDC.bitSet.set(i);
                    //一个谓词组只能选择其他一个 如果我选了其中一个，那么我的候选predicat需要把这个谓词组给排除。
                    validDC.cand.andNot(mutexMap[i]);
                    if (!dcCandidates.containsSubset(validDC) && !approxCovers.containsSubset(validDC)) {
                        if (!validDC.cand.isEmpty())
                            dcCandidates.add(validDC);
                            //如果候选为空了 那么就需要进行判断 如果满足要求 那么就加入到最后的结果里面
                        else if (isApproxCover(validDC.bitSet, nd.e + 1, nd.target,evidences))
                            approxCovers.add(validDC);
                    }
                }
            }
        }
    }
    boolean isApproxCover(LongBitSet dc, int e, long target) {
        //target 表示还需要覆盖多少个evidence
        if (target <= 0) return true;
        for (; e < evidences.length; e++) {
            //如果下面判断数为真 那么说明还没有覆盖当前这个evidence  那么这个数字就不能被加上
            if (!dc.isSubSetOf(evidences[e].bitset)) {
                target -= evidences[e].count;
                if (target <= 0) return true;
            }
        }
        return false;
    }
    boolean isApproxCover(LongBitSet dc, int e, long target,Evidence[] evidences) {
        //target 表示还需要覆盖多少个evidence
        if (target <= 0) return true;
        for (; e < evidences.length; e++) {
            //如果下面判断数为真 那么说明还没有覆盖当前这个evidence  那么这个数字就不能被加上
            if (!dc.isSubSetOf(evidences[e].bitset)) {
                target -= evidences[e].count;
                if (target <= 0) return true;
            }
        }
        return false;
    }


    Evidence[] transformEvidenceSet(EvidenceSet evidenceSet) {
        Evidence[] evidenceArray = new Evidence[evidenceSet.size()];
        int n = 0;
        for (Evidence e : evidenceSet) {
            evidenceArray[n] = new Evidence(translator.transform(e.bitset), e.count);
            n++;
        }
        return evidenceArray;
    }

    LongBitSet[] transformMutexMap(LongBitSet[] mutexMap) {
        LongBitSet[] transMutexMap = new LongBitSet[mutexMap.length];
        for (int i = 0; i < mutexMap.length; i++) {
            transMutexMap[translator.transform(i)] = translator.transform(mutexMap[i]);
        }
        return transMutexMap;
    }
    LongBitSet[] transformMutexMap(LongBitSet[] mutexMap,BitSetTranslator translator) {
        LongBitSet[] transMutexMap = new LongBitSet[mutexMap.length];
        for (int i = 0; i < mutexMap.length; i++) {
            transMutexMap[translator.transform(i)] = translator.transform(mutexMap[i]);
        }
        return transMutexMap;
    }

    private int[] getCounts(EvidenceSet evidenceSet) {
        //统计每个谓词在evidenceset中出现了多少此
        int[] counts = new int[nPredicates];
        for (Evidence evidence : evidenceSet) {
            LongBitSet bitset = evidence.getBitSetPredicates();
            for (int i = bitset.nextSetBit(0); i >= 0; i = bitset.nextSetBit(i + 1)) {
                counts[i]++;
            }
        }
        return counts;
    }
    private  int[] getCounts(List<Evidence> evidences){
        int[] counts = new int[nPredicates];
        for(Evidence e:evidences){
            LongBitSet bitset = e.getBitSetPredicates();
            for (int i = bitset.nextSetBit(0); i >= 0; i = bitset.nextSetBit(i + 1)) {
                counts[i]++;
            }
        }
        return counts;
    }
    public DenialConstraintSet buildDynamicDc(
            EvidenceSet evidenceSet,
            DenialConstraintSet denialConstraintSet,
            long target,
            PredicateBuilder predicateBuilder){
        DenialConstraintSet invalidDC = new DenialConstraintSet();
        DenialConstraintSet notMinDC = new DenialConstraintSet();
        ArrayTreeSearch tmp = new ArrayTreeSearch();
        List<Evidence> evidences = new ArrayList<>();
        List<LongBitSet> ans = new ArrayList<>();
        //额外加入的dc
        DenialConstraintSet extraDC = initialDCs(predicateBuilder);
        for(Evidence e:evidenceSet){
            evidences.add(e);
        }
        for(DenialConstraint dc:extraDC){
            Pair checkedDC = check2(dc,evidenceSet,target);
            if (!checkedDC.flag) {
                invalidDC.add(dc);
                //调用aei的方法
                List<LongBitSet> valid = buildInvalidDCs(checkedDC);
                ans.addAll(valid);
            }
            else {
                boolean flag = true;
                for(int i=checkedDC.DCCandicate.nextSetBit(0);i>=0;i = checkedDC.DCCandicate.nextSetBit(i+1)){
                    checkedDC.DCCandicate.clear(i);
                    if(!up(checkedDC.needEvidence,checkedDC.hitEvidenceSet,dc.getPredicateSet().getBitset(),tmp))flag=false;
                    checkedDC.DCCandicate.set(i,true);
                }
                if(flag)tmp.add(new DCCandidate(checkedDC.DCCandicate,new LongBitSet()));
                tmp.forEach(dcs->ans.add(dcs.bitSet));
            }

        }

        for(DenialConstraint dc:denialConstraintSet){
            Pair checkedDC = check2(dc,evidenceSet,target);

            if(!checkedDC.flag){
                invalidDC.add(dc);
               // List<LongBitSet> validDC = buildInvalidDCs(checkedDC);

            }
            else {
                notMinDC.add(dc);
                boolean flag = true;
                for(int i=checkedDC.DCCandicate.nextSetBit(0);i>=0;i = checkedDC.DCCandicate.nextSetBit(i+1)){
                    checkedDC.DCCandicate.clear(i);
                    if(!up(checkedDC.needEvidence,checkedDC.hitEvidenceSet,dc.getPredicateSet().getBitset(),tmp))flag=false;
                    checkedDC.DCCandicate.set(i,true);
                }
                if(flag)tmp.add(new DCCandidate(checkedDC.DCCandicate,new LongBitSet()));

            }
        }
        return new DenialConstraintSet(predicateBuilder,ans);
    }

    public Pair check2(DenialConstraint dc,EvidenceSet evidenceSet,long target){
        LongBitSet dcLongbitset = dc.getPredicateSet().getBitset();
        List<Evidence> unhitEvidence = new ArrayList<>();
        List<Evidence> hitEvidence = new ArrayList<>();
        LongBitSet predicateUnChoosed = new LongBitSet();
        int count = 0;
        int choosenumber = 0;
        for(Evidence set:evidenceSet){
            if(dcLongbitset.isSubSetOf(set.getBitSetPredicates())){
                count+=set.count;
                unhitEvidence.add(set);
                for(int i=set.getBitSetPredicates().nextSetBit(0);i>=0;i = set.getBitSetPredicates().nextSetBit(i+1)){
                    if(!dcLongbitset.get(i)){
                        predicateUnChoosed.set(i);
                        choosenumber++;
                    }
                }
            } else hitEvidence.add(set);
        }

     //  System.out.println(choosenumber);
        Pair res = new Pair(unhitEvidence,count<=target,count-target,predicateUnChoosed,dc.getPredicateSet().getLongBitSet(),hitEvidence);
        return res;
    }
    //往上走 当去掉任意一个谓词都不行的时候结束
    public boolean up(long target,List<Evidence> evidences,LongBitSet dcs,ArrayTreeSearch approxCovers){
        if(target>0)return true;
        target+=untitEvidenceSet(evidences,dcs);
        if(target>0)return true;
        boolean flag = true;
        for(int i=dcs.nextSetBit(0);i>=0;i=dcs.nextSetBit(i+1)){
            dcs.clear(i);
            if(!up(target,evidences,dcs,approxCovers))flag=false;
            dcs.set(i);
        }
        if(flag)approxCovers.add(new DCCandidate(dcs,new LongBitSet()));
        return false;
    }
    //未覆盖的evidence set的数量
    public  long untitEvidenceSet(List<Evidence> evidences,LongBitSet dcs){
        long number = 0;
        for(Evidence evidence:evidences){
            if(dcs.isSubSetOf(evidence.getBitSetPredicates()))number+=evidence.count;
        }
        return  number;
    }

    public DenialConstraintSet initialDCs(PredicateBuilder predicateBuilder) {
        int predicatenumber = predicateBuilder.getPredicate().size();
        List<LongBitSet> res = new ArrayList<>();
        for(int i=0;i<predicatenumber;i++) {
            res.add(new LongBitSet(i));
        }
        return new DenialConstraintSet(predicateBuilder, res);
    }
}

class Pair{
    List<Evidence> unhitEvidenceSet;
    List<Evidence> hitEvidenceSet;
    boolean flag;
    long needEvidence;
    LongBitSet predicateUnchosed;
    LongBitSet DCCandicate;
    public Pair(List<Evidence> unhitEvidenceSet,boolean flag,long needEvidence,LongBitSet predicateUnchosed,LongBitSet DCCandicate,List<Evidence> hitEvidenceSet){
        this.predicateUnchosed = predicateUnchosed;
        this.unhitEvidenceSet = unhitEvidenceSet;
        this.flag = flag;
        this.needEvidence = needEvidence;
        this.DCCandicate = DCCandicate;
        this.hitEvidenceSet = hitEvidenceSet;

    }
}
