package DADC;

import ADCD.evidence.evidenceSet.Evidence;
import ADCD.evidence.evidenceSet.EvidenceSet;
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
    private final LongBitSet[] mutexMapOrigin;   // i -> indices of predicates from the same column pair with predicate i
    boolean ascending;//升序

    public ApproxDynamicEvidence(PredicateBuilder pBuilder, boolean ascending) {
        this.nPredicates = pBuilder.predicateCount();
        this.mutexMapOrigin = pBuilder.getMutexMap();
        //前缀树
        this.ascending = ascending;
        ArrayTreeSearch.N = nPredicates;
    }
    /** might have problem!*/
    public void backTrack(DenialConstraintSet extraDCSet, int count, LongBitSet canAdd, LongBitSet candidate){
        if(canAdd.cardinality() < count)
            return;
        if(count == 0){
            extraDCSet.add(new DenialConstraint(candidate.clone()));
            return;
        }
        for (int i = canAdd.nextSetBit(0); i >= 0; i = canAdd.nextSetBit(i + 1)) {
            candidate.set(i);
            LongBitSet temp = canAdd.clone();
            canAdd.andNot(mutexMapOrigin[i]);
            backTrack(extraDCSet, count - 1, canAdd.clone(), candidate.clone());
            candidate.clear(i);
            temp.clear(i);
            backTrack(extraDCSet, count, temp.clone(), candidate.clone());
        }
    }

    public DenialConstraintSet initExtraDCSet(DenialConstraintSet originDCSet){
        int predicateNumber = nPredicates;
        LongBitSet canAdd = new LongBitSet(nPredicates);
        for(int i = 0; i < nPredicates; i++)
            canAdd.set(i);
        for(DenialConstraint dc: originDCSet){
            predicateNumber = Math.min(predicateNumber, dc.getPredicateCount());
            LongBitSet dcBitSet = dc.getPredicateSet().getLongBitSet();
            for(int j = dcBitSet.nextSetBit(0); j >= 0; j = dcBitSet.nextSetBit(j + 1)){
                if(dcBitSet.get(j)){
                    canAdd.clear(j);
                }
            }
        }
        DenialConstraintSet extraDCSet = new DenialConstraintSet();

        backTrack(extraDCSet, predicateNumber, canAdd.clone(), new LongBitSet(nPredicates));

        return extraDCSet;
    }


    public CheckedDC checkDC(LongBitSet dcLongbitset, EvidenceSet evidenceSet){
        List<Evidence> unhitEvidence = new ArrayList<>();
        long unhitCount = 0;
        List<Evidence> hitEvidence = new ArrayList<>();
        long hitCount = 0;
        LongBitSet predicateUnChosen = new LongBitSet();

        for(Evidence set: evidenceSet){
            if(dcLongbitset.isSubSetOf(set.getBitSetPredicates())){
                unhitCount += set.count;
                unhitEvidence.add(set);
                for(int i = set.getBitSetPredicates().nextSetBit(0); i >= 0; i = set.getBitSetPredicates().nextSetBit(i + 1)){
                    if(!dcLongbitset.get(i)){
                        predicateUnChosen.set(i);
                    }
                }
            }
            else{
                hitCount += set.count;
                hitEvidence.add(set);
            }
        }
        return new CheckedDC(unhitEvidence, unhitCount, hitEvidence, hitCount, predicateUnChosen);
    }

    public CheckedDC checkDC(LongBitSet dcBitSet, CheckedDC checkedDC){
        List<Evidence> unhitEvidence = new ArrayList<>(checkedDC.unhitEvidences);
        long unhitCount = checkedDC.unhitCount;
        List<Evidence> hitEvidence = new ArrayList<>(checkedDC.hitEvidences);
        long hitCount = checkedDC.hitCount;
        LongBitSet predicateUnChosen = checkedDC.predicateUnchosed.clone();

        for(int i = 0; i < checkedDC.hitEvidences.size(); i++){
            Evidence set = checkedDC.hitEvidences.get(i);
            if(dcBitSet.isSubSetOf(set.getBitSetPredicates())){
                unhitEvidence.add(set);
                unhitCount += set.count;
                hitEvidence.remove(set);
                hitCount -= set.count;
                for(int j = set.getBitSetPredicates().nextSetBit(0); j >= 0; j = set.getBitSetPredicates().nextSetBit(j + 1)){
                    if(!dcBitSet.get(j)){
                        predicateUnChosen.set(j);
                    }
                }
            }
        }

        return new CheckedDC(unhitEvidence, unhitCount, hitEvidence, hitCount, predicateUnChosen);
    }

    boolean isApproxCover(LongBitSet dc, int e, long target, List<Evidence> evidences) {
        //target 表示还需要覆盖多少个evidence
        if (target <= 0) return true;
        for (; e < evidences.size(); e++) {
            //如果下面判断数为真 那么说明还没有覆盖当前这个evidence  那么这个数字就不能被加上
            if (!dc.isSubSetOf(evidences.get(e).bitset)) {
                target -= evidences.get(e).count;
                if (target <= 0) return true;
            }
        }
        return false;
    }

    void hit(SearchNode nd, List<Evidence> evidences, ArrayTreeSearch approxCovers) {
        //如果候选predicates是evi的subset 那么就不可能覆盖了  直接结束。
        if (nd.e >= evidences.size() || nd.addablePredicates.isSubSetOf(evidences.get(nd.e).bitset))
            return;

        nd.target -= evidences.get(nd.e).count;

        LongBitSet evi = evidences.get(nd.e).bitset;
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
                    //一个谓词组只能选择其他一个 如果我选了其中一个，那么我的候选predicate需要把这个谓词组给排除。
                    validDC.cand.andNot(mutexMapOrigin[i]);
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

    void walk(int e, LongBitSet addablePredicates, ArrayTreeSearch dcCandidates, long target, Stack<SearchNode> nodes, String status, List<Evidence> evidences, ArrayTreeSearch approxCovers) {
        while (e < evidences.size() && !dcCandidates.isEmpty()) {
            LongBitSet evi = evidences.get(e).bitset;
            //将无效的dc收集起来，就是从候选集里面不能覆盖当前evi的dc收集起来 然后添加新的谓词来 即这个无效dc里面的谓词都是这个evi的子集。
            Collection<DCCandidate> unhitEviDCs = dcCandidates.getAndRemoveGeneralizations(evi);

            // hit evidences[e] later
            SearchNode nd = new SearchNode(e, addablePredicates.clone(), dcCandidates, unhitEviDCs, target,status + e);
            nodes.push(nd);

            // unhit evidences[e]  所有的dc候选者都已经覆盖了当前的这个evi  那么就不存在不覆盖这个evi的可能了。
            if (unhitEviDCs.isEmpty()) return;
            //如果我不覆盖这个evid，那么我可选的谓词只能是evid里面出现过的。
            addablePredicates.and(evi);
            //如果没有了候选的谓词 那么就结束了。
            if (addablePredicates.isEmpty()) return;

            //如果不覆盖这个evi  且后面的全部覆盖 也不能达到target  那么就不必要进行了结束。
            long maxCanHit = 0L;
            for (int i = e + 1; i < evidences.size(); i++)
                if (!addablePredicates.isSubSetOf(evidences.get(i).bitset))
                    maxCanHit += evidences.get(i).count;
            if (maxCanHit < target) return;
            //为不覆盖当前evi  构建新的dccandidate的树  方便搜索。
            ArrayTreeSearch newCandidates = new ArrayTreeSearch();
            for (DCCandidate dc : unhitEviDCs) {
                LongBitSet unhitCand = dc.cand.getAnd(evi);
                if (!unhitCand.isEmpty())
                    newCandidates.add(new DCCandidate(dc.bitSet, unhitCand));
                    //如果没有了候选，说明递归结束了 那么需要直接进行判断是否是有效的dc
                else if (!approxCovers.containsSubset(dc) && isApproxCover(dc.bitSet, e + 1, target, evidences))
                    approxCovers.add(dc);
            }
            if (newCandidates.isEmpty()) return;

            e++;
            dcCandidates = newCandidates;
        }
    }



    public List<LongBitSet> inverseEvidenceSet(long target, LongBitSet predicateUnChosen, DCCandidate dcCandidate, List<Evidence> evidences){
        ArrayTreeSearch approxCovers = new ArrayTreeSearch();
        Stack<SearchNode> stack = new Stack<>();
        ArrayTreeSearch dcCandidates = new ArrayTreeSearch();
        dcCandidates.add(dcCandidate);
        //第一个参数代表 第几个evidence  第二个代表能选那些谓词 第三个是有哪些dc的候选者 第四个是还要覆盖多少  哪些evid还没有覆盖
        walk(0, predicateUnChosen, dcCandidates, target, stack,"", evidences, approxCovers);
        while (!stack.empty()){
            SearchNode nd = stack.pop();
            //遍历完所有的evidence或者没有可以加入的predicate 那么就结束了。
            if (nd.e >= evidences.size() || nd.addablePredicates.isEmpty())
                continue;
            hit(nd, evidences, approxCovers);    // hit evidences[e]
            if (nd.target > 0)
                walk(nd.e + 1, nd.addablePredicates, nd.dcCandidates, nd.target, stack, nd.H, evidences, approxCovers);
        }
        List<LongBitSet> rawDC = new ArrayList<>();
        approxCovers.forEach(dcs -> rawDC.add(dcs.bitSet));
        return rawDC;
    }

    public void addMinimalDCDemo(LongBitSet dcBitSet, Set<LongBitSet> minimalDCDemo){
        boolean canAdd = true;
        for (LongBitSet e: minimalDCDemo){
            if(e.isSubSetOf(dcBitSet) && !dcBitSet.isSubSetOf(e)){
                canAdd = false;
                break;
            }
        }
        if(!canAdd)
            return;
        minimalDCDemo.removeIf(dcBitSet::isSubSetOf);
        minimalDCDemo.add(dcBitSet);
    }

    public void downwardTraverse(LongBitSet dcBitSet, CheckedDC checkedDC, Set<LongBitSet> minValidDCDemo, long target){
        List<Evidence> evidences = new ArrayList<>(checkedDC.unhitEvidences);

        evidences.sort((o1,o2)->Long.compare(o2.count,o1.count));

        List<LongBitSet> res = inverseEvidenceSet(target - checkedDC.hitCount, checkedDC.predicateUnchosed, new DCCandidate(dcBitSet, checkedDC.predicateUnchosed.clone()), evidences);

        for(LongBitSet e: res)
            addMinimalDCDemo(e, minValidDCDemo);
    }

    public void upwardTraverse(LongBitSet dcBitSet, CheckedDC checkedDC, Map<LongBitSet, CheckedDC> checkedDCDemo, Set<LongBitSet> minValidDCDemo, long target){
        boolean isMinimal = true;
        LongBitSet bitSetTemp = dcBitSet.clone();
        for(int i = bitSetTemp.nextSetBit(0); i >= 0; i = bitSetTemp.nextSetBit(i + 1)){
            bitSetTemp.clear(i);
            /*****/
            if(!minValidDCDemo.contains(bitSetTemp)){
                if(!checkedDCDemo.containsKey(bitSetTemp)){
                    checkedDCDemo.put(bitSetTemp.clone(), checkDC(bitSetTemp, checkedDC));
                }
                CheckedDC checkedDCCandidate = checkedDCDemo.get(bitSetTemp);

                if(target <= checkedDCCandidate.hitCount){
                    isMinimal = false;
                    upwardTraverse(bitSetTemp, checkedDCCandidate, checkedDCDemo, minValidDCDemo, target);
                }
            }
            else{
                isMinimal = false;
            }
            /*****/
            bitSetTemp.set(i, true);
        }
        if(isMinimal)
            addMinimalDCDemo(bitSetTemp, minValidDCDemo);
    }

    public DenialConstraintSet build(EvidenceSet evidenceSet, DenialConstraintSet originDCSet, long target){
        Map<LongBitSet, CheckedDC> checkedDCDemo = new HashMap<>();
        Set<LongBitSet> minValidDCDemo = new HashSet<>();

        DenialConstraintSet extraDCSet = initExtraDCSet(originDCSet);

        long downTime = 0;
        long upTime = 0;

        for(DenialConstraint dc: extraDCSet){
            LongBitSet dcBitSet = dc.getPredicateSet().getLongBitSet();

            if(minValidDCDemo.contains(dcBitSet))
                continue;

            if(!checkedDCDemo.containsKey(dcBitSet))
                checkedDCDemo.put(dcBitSet.clone(), checkDC(dcBitSet, evidenceSet));

            CheckedDC checkedDC = checkedDCDemo.get(dcBitSet);

            /** downward*/
            if(target > checkedDC.hitCount){
                long t0 = System.currentTimeMillis();
                downwardTraverse(dcBitSet, checkedDC, minValidDCDemo, target);
                downTime += System.currentTimeMillis() - t0;
            }
            /** upward*/
            if(target <= checkedDC.hitCount){
                long t1 = System.currentTimeMillis();
                upwardTraverse(dcBitSet, checkedDC, checkedDCDemo, minValidDCDemo, target);
                upTime += System.currentTimeMillis() - t1;
            }

        }


        for(DenialConstraint dc: originDCSet){
            LongBitSet dcBitSet = dc.getPredicateSet().getLongBitSet();

            if(minValidDCDemo.contains(dcBitSet))
                continue;

            if(!checkedDCDemo.containsKey(dcBitSet))
                checkedDCDemo.put(dcBitSet.clone(), checkDC(dcBitSet, evidenceSet));

            CheckedDC checkedDC = checkedDCDemo.get(dcBitSet);

            /** downward*/
            if(target > checkedDC.hitCount){
                long t2 = System.currentTimeMillis();
                downwardTraverse(dcBitSet, checkedDC, minValidDCDemo, target);
                downTime += System.currentTimeMillis() - t2;
            }
            /** upward*/
            if(target <= checkedDC.hitCount){
                long t3 = System.currentTimeMillis();
                upwardTraverse(dcBitSet, checkedDC, checkedDCDemo, minValidDCDemo, target);
                upTime += System.currentTimeMillis() -t3;
            }

        }

        DenialConstraintSet constraints = new DenialConstraintSet();
        //去掉一些重复的dc
        for (LongBitSet rawDC : minValidDCDemo)
            constraints.add(new DenialConstraint(rawDC));
        System.out.println("  [PACS] Total DC size: " + constraints.size());

        constraints.minimize();

        System.out.println("  [PACS] Min DC size : " + constraints.size());

        return constraints;
    }


}

