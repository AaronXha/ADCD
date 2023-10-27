package DADC;

import ADCD.evidence.evidenceSet.Evidence;
import ADCD.evidence.evidenceSet.EvidenceSet;
import ADCD.predicate.PredicateBuilder;
import ch.javasoft.bitset.LongBitSet;
import de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint;
import de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraintSet;
import java.util.*;

public class ApproxDynamicEvidence {
    private final int nPredicates;
    public LongBitSet[] mutexMap;
    public LongBitSet fullMask;
    public EvidenceSet evidenceSetAll;

    public long limit;
    public long target;
    public Map<LongBitSet, CheckedDC> checkedDCDemo;
    public Set<LongBitSet> minValidDCDemo;
    public Set<LongBitSet> checkedValidDemo;
    public Set<LongBitSet> checkedInvalidDemo;
    public Set<LongBitSet> downwardVisitedDemo;
    public long checkTime = 0;

    public long validateTime = 0;

    public ApproxDynamicEvidence(PredicateBuilder pBuilder) {
        this.nPredicates = pBuilder.predicateCount();
        // i -> indices of predicates from the same column pair with predicate i
        this.mutexMap = pBuilder.getMutexMap();
        fullMask = new LongBitSet(nPredicates);
        for(int i = 0; i < nPredicates; i++)
            fullMask.set(i);
    }

    public CheckedDC checkDC(LongBitSet dcBitSet){
        long unhitCount = 0;
        long hitCount = 0;
        for(Evidence set: evidenceSetAll) {
            if (dcBitSet.isSubSetOf(set.getBitSetPredicates()))
                unhitCount += set.count;
            else
                hitCount += set.count;
        }
        return new CheckedDC(unhitCount, hitCount);
    }

    public CheckedDC getCheckedDC(LongBitSet dc){
        long t0 = System.currentTimeMillis();

        if(!checkedDCDemo.containsKey(dc)){
            CheckedDC checkedDC = checkDC(dc);
            checkedDCDemo.put(dc.clone(), checkedDC);
            if(checkedDC.hitCount < target)
                checkedInvalidDemo.add(dc);
        }

        validateTime += System.currentTimeMillis() - t0;
        return checkedDCDemo.get(dc);
    }

    public boolean check(LongBitSet dc){
        if(checkedValidDemo.contains(dc))
            return true;
        else if(checkedInvalidDemo.contains(dc))
            return false;
        else{
            long unhitCount = 0;
            long hitCount = 0;
            for(Evidence evi: evidenceSetAll) {
                if (dc.isSubSetOf(evi.getBitSetPredicates()))
                    unhitCount += evi.count;
                else
                    hitCount += evi.count;

                if(unhitCount > limit){
                    checkedInvalidDemo.add(dc);
                    return false;
                }
                if(hitCount >= target){
                    checkedValidDemo.add(dc);
                    return true;
                }
            }
            if(hitCount >= target){
                checkedValidDemo.add(dc);
                return true;
            }
            else {
                checkedInvalidDemo.add(dc);
                return false;
            }
        }
    }

    public boolean checkIsValid(LongBitSet dcBitSet){
        long t0 = System.currentTimeMillis();
        //是已有的最小的DC的超集，修剪
        for(LongBitSet e: minValidDCDemo)
            if(e.isSubSetOf(dcBitSet))
                return false;

        checkTime += System.currentTimeMillis() - t0;

        return true;
    }

    public boolean checkIsValid(LongBitSet dcBitSet, Set<LongBitSet> minValidDCDemo, Set<LongBitSet> realSubsetList){
        //不是realSubsetList里DC的超集
        boolean flag = false;
        for(LongBitSet e: realSubsetList)
            if(e.isSubSetOf(dcBitSet))
                flag = true;
        if (!flag)
            return flag;
        //用已有的最小DC剪枝
        for(LongBitSet e: minValidDCDemo)
            if(!e.equals(dcBitSet) && dcBitSet.isSubSetOf(e))
                return false;

        return true;
    }

    public Set<LongBitSet> getRealSupersetOf(LongBitSet bitSet, Set<LongBitSet> bitSetSet){
        Set<LongBitSet> bitSetList = new HashSet<>();
        for(LongBitSet e: bitSetSet)
            if(bitSet.isSubSetOf(e) && !bitSet.equals(e))
                bitSetList.add(e);
        return bitSetList;
    }

    public Set<LongBitSet> getRealSubsetOf(LongBitSet bitSet, Set<LongBitSet> bitSetSet){
        Set<LongBitSet> bitSetList = new HashSet<>();
        for(LongBitSet e: bitSetSet)
            if(e.isSubSetOf(bitSet) && !e.equals(bitSet))
                bitSetList.add(e);
        return bitSetList;
    }

/*    public CheckedDC checkDC(LongBitSet dcLongbitset, EvidenceSet evidenceSet){
        List<Evidence> unhitEvidence = new ArrayList<>();
        long unhitCount = 0;
        List<Evidence> hitEvidence = new ArrayList<>();
        long hitCount = 0;
        LongBitSet predicateUnChosen = new LongBitSet();

        for(Evidence set: evidenceSet){
            if(dcLongbitset.isSubSetOf(set.getBitSetPredicates())){
                unhitCount += set.count;
                unhitEvidence.add(set);
                //predicateUnChosen = (evidence and (dc)') or predicateUnChosen
                predicateUnChosen = predicateUnChosen.getOr(LongBitSet.getOr(dcLongbitset, set.getBitSetPredicates()).getXor(dcLongbitset));
            }
            else{
                hitCount += set.count;
                hitEvidence.add(set);
            }
        }

        return new CheckedDC(unhitEvidence, unhitCount, hitEvidence, hitCount, predicateUnChosen);
    }*/


    /** 验证向下走的downward方法*/
    public void downwardTraverse(LongBitSet dcBitSet){
        Queue<LongBitSet> queue = new LinkedList<>();
        queue.offer(dcBitSet);
        downwardVisitedDemo.add(dcBitSet);

        while(!queue.isEmpty()){
            int size = queue.size();
            List<LongBitSet> invalidDemo = new ArrayList<>();
            for(int i = 0; i < size; i++){
                LongBitSet cur = queue.poll();
                CheckedDC checkedDC = getCheckedDC(cur);
                //向下走成立了
                if(target <= checkedDC.hitCount)
                    minValidDCDemo.add(cur);
                //不成立，打算继续向下走
                if(target > checkedDC.hitCount)
                    invalidDemo.add(cur);
            }
            for (LongBitSet e: invalidDemo) {
                LongBitSet canAdd = fullMask.clone();
                for(int i = e.nextSetBit(0); i >= 0; i = e.nextSetBit(i + 1))
                    canAdd.andNot(mutexMap[i]);

                for (int j = canAdd.nextSetBit(0); j >= 0; j = canAdd.nextSetBit(j + 1)) {
                    LongBitSet bitSetTemp = e.clone();
                    bitSetTemp.set(j);
                    if(!downwardVisitedDemo.contains(bitSetTemp)){
                        if(checkedInvalidDemo.contains(bitSetTemp)){
                            queue.offer(bitSetTemp);
                            downwardVisitedDemo.add(bitSetTemp);
                        }
                        //判断向下走后是否能剪枝，部分有效的dc被剪枝了
                        else if(checkIsValid(bitSetTemp)){
                            queue.offer(bitSetTemp);
                            downwardVisitedDemo.add(bitSetTemp);
                        }
                    }
                }
            }
        }
    }

    public void downwardTraverse(LongBitSet dcBitSet,  Set<LongBitSet> realSupersetList){
        Queue<LongBitSet> queue = new LinkedList<>();
        queue.offer(dcBitSet);
        downwardVisitedDemo.add(dcBitSet);

        /*//可以添加的谓词
        LongBitSet canAdd = new LongBitSet(nPredicates);
        for(LongBitSet e : realSupersetList)
            canAdd.or(e);
        canAdd.xor(dcBitSet);*/

        while(!queue.isEmpty()){
            int size = queue.size();
            List<LongBitSet> invalidDemo = new ArrayList<>();
            for(int i = 0; i < size; i++){
                LongBitSet cur = queue.poll();
                CheckedDC checkedDC = getCheckedDC(cur);
                //向下走成立了
                if(target <= checkedDC.hitCount)
                    minValidDCDemo.add(cur);
                //不成立，打算继续向下走
                if(target > checkedDC.hitCount)
                    invalidDemo.add(cur);
            }
            for(LongBitSet e: invalidDemo){
                LongBitSet canAddSet = fullMask.clone();
                for(int i = e.nextSetBit(0); i >= 0; i = e.nextSetBit(i + 1))
                    canAddSet.andNot(mutexMap[i]);
                //canAddSet.and(canAdd);

                for(int j = canAddSet.nextSetBit(0); j >= 0; j = canAddSet.nextSetBit(j + 1)){
                    LongBitSet bitSetTemp = e.clone();
                    bitSetTemp.set(j);
                    if(!downwardVisitedDemo.contains(bitSetTemp)){
                        if(checkedInvalidDemo.contains(bitSetTemp)){
                            queue.offer(bitSetTemp);
                            downwardVisitedDemo.add(bitSetTemp);
                        }
                        else if(checkIsValid(bitSetTemp)){
                            //如果是realSuperset里的dc，直接添加
                            if(realSupersetList.contains(bitSetTemp))
                                minValidDCDemo.add(bitSetTemp);
                            //判断向下走后是否能剪枝，部分有效的dc被剪枝了
                            else {
                                queue.offer(bitSetTemp);
                                downwardVisitedDemo.add(bitSetTemp);
                            }
                        }
                    }
                }
            }
        }
    }


    public void upwardTraverse(LongBitSet dcBitSet, LongBitSet canRemove, Set<LongBitSet> realSubsetList){
        boolean isMinimal = true;
        LongBitSet bitSetTemp = dcBitSet.clone();
        for(int i = canRemove.nextSetBit(0); i >= 0; i = canRemove.nextSetBit(i + 1)){
            bitSetTemp.clear(i);
            if(checkIsValid(bitSetTemp, minValidDCDemo, realSubsetList)) {
                if(!minValidDCDemo.contains(bitSetTemp)) {
                    CheckedDC checkedDC = getCheckedDC(bitSetTemp);
                    if (target <= checkedDC.hitCount) {
                        isMinimal = false;
                        LongBitSet canRemoveSet = canRemove.clone();
                        canRemoveSet.clear(i);
                        upwardTraverse(bitSetTemp, canRemoveSet, realSubsetList);
                    }
                } else isMinimal = false;
            }
            bitSetTemp.set(i);
        }
        if(isMinimal)
            minValidDCDemo.add(bitSetTemp);
    }



    public DenialConstraintSet buildInsert(EvidenceSet evidenceSet,DenialConstraintSet originDCSet, DenialConstraintSet additionDCSet, long targetNumber){
        this.evidenceSetAll = evidenceSet;
        this.target = targetNumber;
        this.limit = evidenceSetAll.getTotalCount() - target;
        checkedDCDemo = new HashMap<>();
        minValidDCDemo = new HashSet<>();
        checkedInvalidDemo = new HashSet<>();
        downwardVisitedDemo = new HashSet<>();

        Set<LongBitSet> setOrigin = originDCSet.getBitSetSet();
        Set<LongBitSet> setAddition = additionDCSet.getBitSetSet();
        Set<LongBitSet> setExtra = new HashSet<>(setOrigin);

        Map<LongBitSet, Set<LongBitSet>> supersetMap = new HashMap<>();
        Map<LongBitSet, Set<LongBitSet>> subsetMap = new HashMap<>();
        List<LongBitSet> upwardDCList = new ArrayList<>();
        List<LongBitSet> downwardDCList = new ArrayList<>();
        List<LongBitSet> extraDCList = new ArrayList<>();

        long t0 = System.currentTimeMillis();

        for (LongBitSet dc: setAddition){
            // addition上 X 为最小DC
            if(setOrigin.contains(dc)){
                minValidDCDemo.add(dc);
                setExtra.remove(dc);
                continue;
            }
            Set<LongBitSet> realSupersetList = getRealSupersetOf(dc, setOrigin);
            Set<LongBitSet> realSubsetList = getRealSubsetOf(dc, setOrigin);
            realSubsetList.forEach(setExtra::remove);
            realSupersetList.forEach(setExtra::remove);

            if(!realSupersetList.isEmpty()){
                supersetMap.put(dc, realSupersetList);
                downwardDCList.add(dc);
            }
            else if (!realSubsetList.isEmpty()){
                subsetMap.put(dc, realSubsetList);
                upwardDCList.add(dc);
            }
            else
                extraDCList.add(dc);
        }
        // 遍历origin上的addition够不着的DC
        extraDCList.addAll(setExtra);
        //将向上向下走的DCList按谓词个数排序
        upwardDCList.sort(Comparator.comparingInt(LongBitSet::cardinality));
        downwardDCList.sort(Comparator.comparingInt(LongBitSet::cardinality));
        extraDCList.sort(Comparator.comparingInt(LongBitSet::cardinality));

        System.out.println(System.currentTimeMillis() - t0);

        long t1 = System.currentTimeMillis();

        // 从 X+P 向上走
        for(LongBitSet dc: upwardDCList){
            Set<LongBitSet> realSubsetList = subsetMap.get(dc);
            Set<LongBitSet> invalidDemo = new HashSet<>();
            LongBitSet canRemove = new LongBitSet(nPredicates);
            for(LongBitSet bitSet: realSubsetList){
                //在all上验证 X
                CheckedDC checkedDC = getCheckedDC(bitSet);
                // X 在all上成立，则它为all上最小DC
                if(target <= checkedDC.hitCount)
                    minValidDCDemo.add(bitSet);
                // X在all上不成立
                if(target > checkedDC.hitCount){
                    invalidDemo.add(bitSet);
                    //可以去掉的谓词
                    canRemove.or(bitSet.getXor(dc));
                }
            }
            if(!invalidDemo.isEmpty())
                // 从 X+P 向上走到 X
                upwardTraverse(dc, canRemove, realSubsetList);

        }

        System.out.println(System.currentTimeMillis() - t1);

        long t2 = System.currentTimeMillis();

        // 从 X-P 向下走
        for(LongBitSet dc: downwardDCList){
            Set<LongBitSet> realSupersetList = supersetMap.get(dc);
            // 在all上验证 X-P
            CheckedDC checkedDC = getCheckedDC(dc);
            // X-P 在all上成立，则它为all上最小DC
            if(target <= checkedDC.hitCount)
                minValidDCDemo.add(dc);
            // X-P 在all上不成立,从 X-P 向下走到 X
            if(target > checkedDC.hitCount)
                downwardTraverse(dc, realSupersetList);
        }

        System.out.println(System.currentTimeMillis() - t2);

        long t3 = System.currentTimeMillis();

        // 从 Y 向下走
        for(LongBitSet dc: extraDCList){
            CheckedDC checkedDC = getCheckedDC(dc);

            if(target <= checkedDC.hitCount)
                minValidDCDemo.add(dc);
            // downward
            if(target > checkedDC.hitCount)
                downwardTraverse(dc);
        }

        System.out.println(System.currentTimeMillis() - t3);

        System.out.println("Check Time: " + checkTime);
        System.out.println("Validate Time: " + validateTime);

        DenialConstraintSet constraints = new DenialConstraintSet();
        for (LongBitSet rawDC : minValidDCDemo)
            constraints.add(new DenialConstraint(rawDC));

        System.out.println("  [PACS] Total DC size: " + constraints.size());

        constraints.minimize();

        System.out.println("  [PACS] Min DC size : " + constraints.size());

        return constraints;
    }

}

