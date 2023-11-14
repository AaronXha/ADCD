package DADC;

import ADCD.evidence.evidenceSet.Evidence;
import ADCD.evidence.evidenceSet.EvidenceSet;
import ADCD.predicate.PredicateBuilder;
import ch.javasoft.bitset.LongBitSet;
import de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint;
import de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraintSet;

import java.util.*;

public class ExactDynamicEvidence {
    private final int nPredicates;
    public LongBitSet[] mutexMap;
    public LongBitSet fullMask;
    public List<Evidence> evidenceList;
    public Map<LongBitSet, CheckedDC> checkedDCDemo;
    public Set<LongBitSet> minValidDCDemo;
    public Set<LongBitSet> checkedValidDemo;
    public Set<LongBitSet> checkedInvalidDemo;

    public ExactDynamicEvidence(PredicateBuilder pBuilder) {
        this.nPredicates = pBuilder.predicateCount();
        this.mutexMap = pBuilder.getMutexMap();// i -> indices of predicates from the same column pair with predicate i
        this.fullMask = new LongBitSet(nPredicates);
        for (int i = 0; i < nPredicates; i++)
            fullMask.set(i);
        checkedDCDemo = new HashMap<>();
        minValidDCDemo = new HashSet<>();
        checkedValidDemo = new HashSet<>();
        checkedInvalidDemo = new HashSet<>();
    }

    public boolean checkDC(LongBitSet dc){
        if(checkedValidDemo.contains(dc))
            return true;
        else if(checkedInvalidDemo.contains(dc))
            return false;
        else{
            for(Evidence evi: evidenceList){
                if (dc.isSubSetOf(evi.getBitSetPredicates())) {
                    checkedInvalidDemo.add(dc);
                    return false;
                }
            }
            checkedValidDemo.add(dc);
            return true;
        }
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

    public DenialConstraintSet buildInsert(EvidenceSet evidenceSet,DenialConstraintSet originDCSet, DenialConstraintSet additionDCSet){
        this.evidenceList = evidenceSet.getEvidenceList();

        Set<LongBitSet> setOrigin = originDCSet.getBitSetSet();
        Set<LongBitSet> setAddition = additionDCSet.getBitSetSet();

        long t0 = System.currentTimeMillis();

        for (LongBitSet dc: setAddition){
            // addition上 X 为最小DC
            if(setOrigin.contains(dc)){
                minValidDCDemo.add(dc);
                continue;
            }
            Set<LongBitSet> realSupersetList = getRealSupersetOf(dc, setOrigin);
            Set<LongBitSet> realSubsetList = getRealSubsetOf(dc, setOrigin);

            if(!realSupersetList.isEmpty()){
                minValidDCDemo.add(dc);
            }
            else if(!realSubsetList.isEmpty()){
                minValidDCDemo.addAll(realSubsetList);
            }
        }

        System.out.println(System.currentTimeMillis() - t0);

        long t1 = System.currentTimeMillis();

        DenialConstraintSet constraints = new DenialConstraintSet();
        for (LongBitSet rawDC : minValidDCDemo)
            constraints.add(new DenialConstraint(rawDC));

        System.out.println("  [PACS] Total DC size: " + constraints.size());

        constraints.minimize();

        System.out.println("  [PACS] Min DC size : " + constraints.size());

        System.out.println(System.currentTimeMillis() - t1);

        return constraints;
    }

/*    public DenialConstraintSet buildDelete(EvidenceSet evidenceSet,DenialConstraintSet originDCSet, DenialConstraintSet additionDCSet){
        this.evidenceList = evidenceSet.getEvidenceList();

        Set<LongBitSet> setOrigin = originDCSet.getBitSetSet();
        Set<LongBitSet> setAddition = additionDCSet.getBitSetSet();

        long t0 = System.currentTimeMillis();

        for (LongBitSet dc: setAddition){
            // addition上 X 为最小DC
            if(setOrigin.contains(dc)){
                minValidDCDemo.add(dc);
                continue;
            }
            Set<LongBitSet> realSupersetList = getRealSupersetOf(dc, setOrigin);
            Set<LongBitSet> realSubsetList = getRealSubsetOf(dc, setOrigin);

            if(!realSupersetList.isEmpty()){
                minValidDCDemo.add(dc);
            }
            else if(!realSubsetList.isEmpty()){
                minValidDCDemo.addAll(realSubsetList);
            }
        }

        System.out.println(System.currentTimeMillis() - t0);

        long t1 = System.currentTimeMillis();

        DenialConstraintSet constraints = new DenialConstraintSet();
        for (LongBitSet rawDC : minValidDCDemo)
            constraints.add(new DenialConstraint(rawDC));

        System.out.println("  [PACS] Total DC size: " + constraints.size());

        constraints.minimize();

        System.out.println("  [PACS] Min DC size : " + constraints.size());

        System.out.println(System.currentTimeMillis() - t1);

        return constraints;
    }*/


}