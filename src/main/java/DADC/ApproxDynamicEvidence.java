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
    public List<Evidence> evidenceList;
    public long limit;
    public long target;
    public Map<LongBitSet, CheckedDC> checkedDCDemo;
    public Set<LongBitSet> minValidDCDemo;
    public Set<LongBitSet> checkedValidDemo;
    public Set<LongBitSet> checkedInvalidDemo;
    public Set<LongBitSet> downwardVisitedDemo;
    public Set<LongBitSet> commonDCDemo;

    public ApproxDynamicEvidence(PredicateBuilder pBuilder) {
        this.nPredicates = pBuilder.predicateCount();
        this.mutexMap = pBuilder.getMutexMap();// i -> indices of predicates from the same column pair with predicate i
        this.fullMask = new LongBitSet(nPredicates);
        for(int i = 0; i < nPredicates; i++)
            fullMask.set(i);
        checkedDCDemo = new HashMap<>();
        minValidDCDemo = new HashSet<>();
        checkedValidDemo = new HashSet<>();
        checkedInvalidDemo = new HashSet<>();
        downwardVisitedDemo = new HashSet<>();
        commonDCDemo = new HashSet<>();
    }

    public boolean checkDC(LongBitSet dc){
        if(checkedValidDemo.contains(dc))
            return true;
        else if(checkedInvalidDemo.contains(dc))
            return false;
        else{
            long unhitCount = 0;
            long hitCount = 0;
            for(Evidence evi: evidenceList) {
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

    public boolean checkCanOffer(LongBitSet dc, Set<LongBitSet> validSet){
        //向下走已经经过了
        if(downwardVisitedDemo.contains(dc))
            return false;
        //是已经检查过无效的dc
        if(checkedInvalidDemo.contains(dc))
            return true;
        //是已有的最小的DC的超集，修剪
        for(LongBitSet e: commonDCDemo)
            if(e.isSubSetOf(dc))
                return false;
        for(LongBitSet e: minValidDCDemo) {
            if (e.isSubSetOf(dc)) {
                commonDCDemo.add(e);
                return false;
            }
        }
        //如果是realSuperset,直接添加
        if(validSet.contains(dc)){
            minValidDCDemo.add(dc);
            return false;
        }
        return true;
    }

    public boolean checkIsValid(LongBitSet dcBitSet, Set<LongBitSet> realSubsetList){
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

    /** 验证向下走的downward方法*/
    public void downwardTraverse(LongBitSet dcBitSet){
        if(downwardVisitedDemo.contains(dcBitSet))
            return;
        downwardVisitedDemo.add(dcBitSet);
        Queue<LongBitSet> queue = new LinkedList<>();
        queue.offer(dcBitSet);

        while(!queue.isEmpty()){
            int size = queue.size();
            List<LongBitSet> invalidDemo = new ArrayList<>();
            for(int i = 0; i < size; i++){
                LongBitSet cur = queue.poll();
                if(checkDC(cur))
                    minValidDCDemo.add(cur);
                else
                    invalidDemo.add(cur);
            }
            for (LongBitSet e: invalidDemo) {
                LongBitSet canAdd = fullMask.clone();
                for(int i = e.nextSetBit(0); i >= 0; i = e.nextSetBit(i + 1))
                    canAdd.andNot(mutexMap[i]);

                for (int j = canAdd.nextSetBit(0); j >= 0; j = canAdd.nextSetBit(j + 1)) {
                    LongBitSet bitSetTemp = e.clone();
                    bitSetTemp.set(j);
                    if(checkCanOffer(bitSetTemp, new HashSet<>())){
                        queue.offer(bitSetTemp);
                        downwardVisitedDemo.add(bitSetTemp);
                    }
                }
            }
        }
    }

    public void downwardTraverse(LongBitSet dcBitSet,  Set<LongBitSet> realSupersetList){
        Queue<LongBitSet> queue = new LinkedList<>();
        queue.offer(dcBitSet);
        downwardVisitedDemo.add(dcBitSet);

        while(!queue.isEmpty()){
            int size = queue.size();
            List<LongBitSet> invalidDemo = new ArrayList<>();
            for(int i = 0; i < size; i++){
                LongBitSet cur = queue.poll();
                if(checkDC(cur))
                    minValidDCDemo.add(cur);
                else
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
                    if(checkCanOffer(bitSetTemp, realSupersetList)){
                        queue.offer(bitSetTemp);
                        downwardVisitedDemo.add(bitSetTemp);
                    }
                }
            }
        }
    }

    public void upwardTraverse(LongBitSet dcBitSet, boolean flag){
        boolean isMinimal = flag;
        LongBitSet bitSetTemp = dcBitSet.clone();
        LongBitSet canRemove = dcBitSet.clone();
        for(int i = canRemove.nextSetBit(0); i >= 0; i = canRemove.nextSetBit(i + 1)){
            bitSetTemp.clear(i);
            boolean canUp = true;
            //用已有的最小DC剪枝
            for(LongBitSet e: minValidDCDemo)
                if(!e.equals(bitSetTemp) && bitSetTemp.isSubSetOf(e))
                    canUp = false;
            if(canUp) {
                if(!minValidDCDemo.contains(bitSetTemp)){
                    if (checkDC(bitSetTemp)) {
                        isMinimal = false;
                        upwardTraverse(bitSetTemp, true);
                    }
                } else isMinimal = false;
            }
            bitSetTemp.set(i);
        }
        if(isMinimal)
            minValidDCDemo.add(bitSetTemp);
    }

    public void upwardTraverse(LongBitSet dcBitSet, LongBitSet canRemove, Set<LongBitSet> realSubsetList, boolean flag){
        boolean isMinimal = flag;
        LongBitSet bitSetTemp = dcBitSet.clone();
        for(int i = canRemove.nextSetBit(0); i >= 0; i = canRemove.nextSetBit(i + 1)){
            bitSetTemp.clear(i);
            if(checkIsValid(bitSetTemp, realSubsetList)) {
                if(!minValidDCDemo.contains(bitSetTemp)) {
                    if (checkDC(bitSetTemp)) {
                        isMinimal = false;
                        LongBitSet canRemoveSet = canRemove.clone();
                        canRemoveSet.clear(i);
                        upwardTraverse(bitSetTemp, canRemoveSet, realSubsetList, true);
                    }
                } else isMinimal = false;
            }
            bitSetTemp.set(i);
        }
        if(isMinimal)
            minValidDCDemo.add(bitSetTemp);
    }

    public DenialConstraintSet buildDelete(EvidenceSet evidenceSet,DenialConstraintSet originDCSet, DenialConstraintSet additionDCSet, long targetNumber){
        this.evidenceList = evidenceSet.getEvidenceList();
        this.target = targetNumber;
        this.limit = evidenceSet.getTotalCount() - target;

        Set<LongBitSet> setOrigin = originDCSet.getBitSetSet();
        Set<LongBitSet> setAddition = additionDCSet.getBitSetSet();
        Set<LongBitSet> setExtra = new HashSet<>(setOrigin);

        Map<LongBitSet, Set<LongBitSet>> supersetMap = new HashMap<>();
        List<LongBitSet> upwardDCList = new ArrayList<>();
        List<LongBitSet> downwardDCList = new ArrayList<>();
        List<LongBitSet> extraDCList = new ArrayList<>(setExtra);

        long t0 = System.currentTimeMillis();

        for (LongBitSet dc: setAddition){
            // addition上 X 为最小DC
            if(setOrigin.contains(dc)){
                if(checkDC(dc))
                    upwardDCList.add(dc);
                else
                    extraDCList.add(dc);
                setExtra.remove(dc);
                continue;
            }
            Set<LongBitSet> realSupersetList = getRealSupersetOf(dc, setOrigin);
            Set<LongBitSet> realSubsetList = getRealSubsetOf(dc, setOrigin);
            realSubsetList.forEach(setExtra::remove);
            realSupersetList.forEach(setExtra::remove);

            if(!realSupersetList.isEmpty()){
                supersetMap.put(dc, realSupersetList);
                if(checkDC(dc))
                    downwardDCList.add(dc);
                else{
                    for(LongBitSet e: realSupersetList)
                        if(!extraDCList.contains(e))
                            extraDCList.add(e);
                }
            }
            else if (!realSubsetList.isEmpty()){
                for (LongBitSet e: realSubsetList)
                    if(!upwardDCList.contains(e))
                        upwardDCList.add(e);
                /*subsetMap.put(dc, realSubsetList);
                upwardDCList.add(dc);*/
            }
        }
        // 遍历origin上的addition够不着的DC
        upwardDCList.addAll(setExtra);

        //将向上向下走的DCList按谓词个数排序
        upwardDCList.sort(Comparator.comparingInt(LongBitSet::cardinality));
        downwardDCList.sort(Comparator.comparingInt(LongBitSet::cardinality));
        extraDCList.sort(Comparator.comparingInt(LongBitSet::cardinality));

        System.out.println(System.currentTimeMillis() - t0);

        long t1 = System.currentTimeMillis();
        //向上走
        for(LongBitSet dc: upwardDCList){
            upwardTraverse(dc, true);
        }
        System.out.println(System.currentTimeMillis() - t1);

        long t2 = System.currentTimeMillis();
        //向下走
        for(LongBitSet dc: downwardDCList){
            downwardTraverse(dc, supersetMap.get(dc));
        }
        System.out.println(System.currentTimeMillis() - t2);

        long t3 = System.currentTimeMillis();
        //向下走
        for(LongBitSet dc: extraDCList){
            downwardTraverse(dc);
        }
        System.out.println(System.currentTimeMillis() - t3);

        long t4 = System.currentTimeMillis();

        DenialConstraintSet constraints = new DenialConstraintSet();
        for (LongBitSet rawDC : minValidDCDemo)
            constraints.add(new DenialConstraint(rawDC));

        System.out.println("  [PACS] Total DC size: " + constraints.size());

        constraints.minimize();

        System.out.println("  [PACS] Min DC size : " + constraints.size());

        System.out.println(System.currentTimeMillis() - t4);

        return constraints;

    }


    public DenialConstraintSet buildInsert(EvidenceSet evidenceSet,DenialConstraintSet originDCSet, DenialConstraintSet additionDCSet, long targetNumber){
        this.evidenceList = evidenceSet.getEvidenceList();
        this.target = targetNumber;
        this.limit = evidenceSet.getTotalCount() - target;

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

        System.out.println(System.currentTimeMillis() - t0);

        long t1 = System.currentTimeMillis();

        //将向上走的DCList按谓词个数排序
        upwardDCList.sort(Comparator.comparingInt(LongBitSet::cardinality));
        // 从 X+P 向上走
        for(LongBitSet dc: upwardDCList){
            Set<LongBitSet> realSubsetList = subsetMap.get(dc);
            Set<LongBitSet> invalidDemo = new HashSet<>();
            boolean flag = true;
            LongBitSet canRemove = new LongBitSet(nPredicates);

            for(LongBitSet bitSet: realSubsetList){
                //在all上验证 X
                // X 在all上成立，则它为all上最小DC
                if(checkDC(bitSet)){
                    minValidDCDemo.add(bitSet);
                    flag = false;
                }
                // X在all上不成立
                else{
                    invalidDemo.add(bitSet);
                    //可以去掉的谓词
                    canRemove.or(bitSet.getXor(dc));
                }
            }

            if(!invalidDemo.isEmpty())
                // 从 X+P 向上走到 X
                upwardTraverse(dc, canRemove, realSubsetList, flag);

            for(LongBitSet e: invalidDemo)
                if(!extraDCList.contains(e))
                    extraDCList.add(e);
        }

        System.out.println(System.currentTimeMillis() - t1);

        long t2 = System.currentTimeMillis();

        //将向下走的DCList按谓词个数排序
        downwardDCList.sort(Comparator.comparingInt(LongBitSet::cardinality));
        // 从 X-P 向下走
        for(LongBitSet dc: downwardDCList){
            Set<LongBitSet> realSupersetList = supersetMap.get(dc);
            // 在all上验证 X-P
            // X-P 在all上成立，则它为all上最小DC
            if(checkDC(dc))
                minValidDCDemo.add(dc);
            // X-P 在all上不成立,从 X-P 向下走到 X
            else
                downwardTraverse(dc, realSupersetList);
        }

        System.out.println(System.currentTimeMillis() - t2);

        long t3 = System.currentTimeMillis();

        //将向下走的DCList按谓词个数排序
        extraDCList.sort(Comparator.comparingInt(LongBitSet::cardinality));
        // 从 Y 向下走
        for(LongBitSet dc: extraDCList){
            if(checkDC(dc))
                minValidDCDemo.add(dc);
                //upwardTraverse(dc, true);
            // downward
            else
                downwardTraverse(dc);
        }

        System.out.println(System.currentTimeMillis() - t3);

        long t4 = System.currentTimeMillis();

        DenialConstraintSet constraints = new DenialConstraintSet();
        for (LongBitSet rawDC : minValidDCDemo)
            constraints.add(new DenialConstraint(rawDC));

        System.out.println("  [PACS] Total DC size: " + constraints.size());

        constraints.minimize();

        System.out.println("  [PACS] Min DC size : " + constraints.size());

        System.out.println(System.currentTimeMillis() - t4);

        return constraints;
    }

}

