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
    public Set<LongBitSet> upwardVisitedDemo;
    public Set<LongBitSet> downwardVisitedDemo;
    public Set<LongBitSet> commonDCDemo;
    public int[] countsIndex;
    public Set<LongBitSet> maxInvalidDCDemo;


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
        upwardVisitedDemo = new HashSet<>();
        downwardVisitedDemo = new HashSet<>();
        commonDCDemo = new HashSet<>();
        maxInvalidDCDemo = new HashSet<>();
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
        //为空
        if(dcBitSet.isEmpty())
            return false;
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

    public int[] getCountsIndex(List<Evidence> evidenceSet) {
        //统计每个谓词在evidenceSet中出现了多少次
        int[] counts = new int[nPredicates];
        for (Evidence evidence : evidenceSet) {
            LongBitSet bitset = evidence.getBitSetPredicates();
            for (int i = bitset.nextSetBit(0); i >= 0; i = bitset.nextSetBit(i + 1)) {
                counts[i]++;
            }
        }

        Integer[] indices = new Integer[nPredicates];
        for (int i = 0; i < nPredicates; i++) {
            indices[i] = i;
        }

        Arrays.sort(indices, Comparator.comparingInt(i -> counts[i]));

        int[] sortedIndices = new int[nPredicates];
        for (int i = 0; i < nPredicates; i++) {
            sortedIndices[i] = indices[i];
        }

        return sortedIndices;
    }

    List<Integer> getAddList(LongBitSet dcBitSet){
        LongBitSet canAdd = fullMask.clone();
        for(int i = dcBitSet.nextSetBit(0); i >= 0; i = dcBitSet.nextSetBit(i + 1))
            canAdd.andNot(mutexMap[i]);
        List<Integer> addList = new ArrayList<>();
        for(int i = 0; i < nPredicates; i++){
            if(canAdd.get(countsIndex[i])){
                addList.add(countsIndex[i]);
            }
        }
        return addList;
    }

    /** 向下走的aei方法*/
    public void downwardAEI(LongBitSet dcBitSet){
        if(downwardVisitedDemo.contains(dcBitSet))
            return;
        downwardVisitedDemo.add(dcBitSet);

        AEI aei = new AEI(dcBitSet, evidenceList, mutexMap, target, true, nPredicates);
        minValidDCDemo.addAll(aei.build());
    }


    /** 向下走的downward方法(DFS)*/
    public void downwardDFS(LongBitSet dcBitSet, List<Integer> addList){
        if(downwardVisitedDemo.contains(dcBitSet))
            return;
        downwardVisitedDemo.add(dcBitSet);
        //System.out.println(dcBitSet);

        for(LongBitSet e: maxInvalidDCDemo)
            if(dcBitSet.isSubSetOf(e))
                return;

        for(Integer j : addList){
            LongBitSet bitSetTemp = dcBitSet.clone();
            bitSetTemp.set(j);

            if(checkCanOffer(bitSetTemp, new HashSet<>())){
                if(checkDC(bitSetTemp)){
                    maxInvalidDCDemo.add(dcBitSet);
                    upwardTraverse(bitSetTemp.clone(), dcBitSet.clone(),true);
                }
                else{
                    List<Integer> canAddList = new ArrayList<>(addList);
                    for(int e = mutexMap[j].nextSetBit(0); e >= 0; e = mutexMap[j].nextSetBit(e + 1))
                        if(canAddList.contains(e))
                            canAddList.remove((Integer) e);
                    downwardDFS(bitSetTemp, canAddList);
                }
            }
        }
        if(addList.isEmpty())
            maxInvalidDCDemo.add(dcBitSet);
    }


    /** 验证向下走的downward方法(BFS)*/
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

    public void downwardTraverse(LongBitSet dcBitSet, Set<LongBitSet> realSupersetList){
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


    /** 验证向上走的upward方法*/
    public void upwardTraverse(LongBitSet dcBitSet, LongBitSet canRemove, boolean flag){
        boolean isMinimal = flag;
        upwardVisitedDemo.add(dcBitSet);
        LongBitSet bitSetTemp = dcBitSet.clone();
        //LongBitSet canRemove = dcBitSet.clone();
        if(bitSetTemp.cardinality() > 1){
            for(int i = canRemove.nextSetBit(0); i >= 0; i = canRemove.nextSetBit(i + 1)){
                bitSetTemp.clear(i);
                if (checkDC(bitSetTemp)) {
                    isMinimal = false;
                    if(!upwardVisitedDemo.contains(bitSetTemp))
                        upwardTraverse(bitSetTemp, bitSetTemp.clone(), true);
                }
                bitSetTemp.set(i);
            }
        }
        if(isMinimal){
            minValidDCDemo.add(bitSetTemp);
        }
    }

    public void upwardTraverse(LongBitSet dcBitSet, LongBitSet canRemove, Set<LongBitSet> realSubsetList, boolean flag){
        if(upwardVisitedDemo.contains(dcBitSet))
            return;
        boolean isMinimal = flag;
        upwardVisitedDemo.add(dcBitSet);
        LongBitSet bitSetTemp = dcBitSet.clone();
        if(bitSetTemp.cardinality() > 1){
            for(int i = canRemove.nextSetBit(0); i >= 0; i = canRemove.nextSetBit(i + 1)){
                bitSetTemp.clear(i);
                if(checkIsValid(bitSetTemp, realSubsetList)) {
                    if(!minValidDCDemo.contains(bitSetTemp)) {
                        if(checkDC(bitSetTemp)) {
                            isMinimal = false;
                            LongBitSet canRemoveSet = canRemove.clone();
                            canRemoveSet.clear(i);
                            if(!upwardVisitedDemo.contains(bitSetTemp))
                                upwardTraverse(bitSetTemp, canRemoveSet, realSubsetList,true);
                        }
                    } else isMinimal = false;
                }
                bitSetTemp.set(i);
            }
        }
        if(isMinimal)
            minValidDCDemo.add(bitSetTemp);
    }

    public DenialConstraintSet buildDelete(EvidenceSet evidenceSet, DenialConstraintSet originDCSet, DenialConstraintSet additionDCSet, long targetNumber){
        this.evidenceList = evidenceSet.getEvidenceList();
        this.target = targetNumber;
        this.limit = evidenceSet.getTotalCount() - target;
        this.countsIndex = getCountsIndex(evidenceList);

        Set<LongBitSet> setOrigin = originDCSet.getBitSetSet();
        Set<LongBitSet> setAddition = additionDCSet.getBitSetSet();
        Set<LongBitSet> setExtra = new HashSet<>(setOrigin);

        List<LongBitSet> upwardDCList = new ArrayList<>();
        List<LongBitSet> downwardDCList = new ArrayList<>();

        long t0 = System.currentTimeMillis();

        for (LongBitSet dc: setAddition){
            // addition上 X 为最小DC
            if(setOrigin.contains(dc)){
                if(checkDC(dc))
                    upwardDCList.add(dc);
                else
                    downwardDCList.add(dc);
                setExtra.remove(dc);
                continue;
            }
            Set<LongBitSet> realSupersetList = getRealSupersetOf(dc, setOrigin);
            Set<LongBitSet> realSubsetList = getRealSubsetOf(dc, setOrigin);
            // addition上 X-P 为最小DC
            if(!realSupersetList.isEmpty()){
                realSupersetList.forEach(setExtra::remove);
                if(!downwardDCList.contains(dc))
                    downwardDCList.add(dc);
                /*for(LongBitSet e: realSupersetList){
                    if(checkDC(e)){
                        if(!upwardDCList.contains(e))
                            upwardDCList.add(e);
                    }
                    else{
                        if(!downwardDCList.contains(e))
                            downwardDCList.add(e);
                    }
                }*/
            }
            // addition上 X+P 为最小DC
            else if (!realSubsetList.isEmpty()){
                realSubsetList.forEach(setExtra::remove);
                for (LongBitSet e: realSubsetList)
                    if(!upwardDCList.contains(e)){
                        upwardDCList.add(e);
                    }
            }
        }

        // 遍历origin上的addition够不着的DC
        for(LongBitSet dc: setExtra){
            if(checkDC(dc))
                if(!upwardDCList.contains(dc))
                    upwardDCList.add(dc);
            else{
                if(!downwardDCList.contains(dc))
                    downwardDCList.add(dc);
            }
        }

        /* 还有remain上成立但origin和addition上都不成立的找不出来*/

        //将向上向下走的DCList按谓词个数排序
        upwardDCList.sort(Comparator.comparingInt(LongBitSet::cardinality));
        downwardDCList.sort(Comparator.comparingInt(LongBitSet::cardinality));

        System.out.println(System.currentTimeMillis() - t0);

        long t1 = System.currentTimeMillis();
        //向上走
        for(LongBitSet dc: upwardDCList){
            upwardTraverse(dc, dc.clone(),true);
        }
        System.out.println(System.currentTimeMillis() - t1);

        long t2 = System.currentTimeMillis();
        //向下走
        for(LongBitSet dc: downwardDCList){
            downwardTraverse(dc);
            //downwardDFS(dc, getAddList(dc));
        }
        System.out.println(System.currentTimeMillis() - t2);

        long t3 = System.currentTimeMillis();

        DenialConstraintSet constraints = new DenialConstraintSet();
        for (LongBitSet rawDC : minValidDCDemo)
            constraints.add(new DenialConstraint(rawDC));

        System.out.println("  [PACS] Total DC size: " + constraints.size());

        constraints.minimize();

        System.out.println("  [PACS] Min DC size : " + constraints.size());

        System.out.println(System.currentTimeMillis() - t3);

        return constraints;

    }


    public DenialConstraintSet buildInsert(EvidenceSet evidenceSet, DenialConstraintSet originDCSet, DenialConstraintSet additionDCSet, long targetNumber){
        this.evidenceList = evidenceSet.getEvidenceList();
        this.target = targetNumber;
        this.limit = evidenceSet.getTotalCount() - target;
        this.countsIndex = getCountsIndex(evidenceList);

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

            if(!realSupersetList.isEmpty()){
                realSupersetList.forEach(setExtra::remove);
                supersetMap.put(dc, realSupersetList);
                downwardDCList.add(dc);
            }
            else if (!realSubsetList.isEmpty()){
                realSubsetList.forEach(setExtra::remove);
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
            // downward
            else
                //downwardTraverse(dc);
                //downwardDFS(dc, getAddList(dc));
                downwardAEI(dc);
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

