package DADC;

import ADCD.evidence.evidenceSet.Evidence;
import ch.javasoft.bitset.LongBitSet;

import java.util.List;

public class Pair {
    List<Evidence> unhitEvidenceSet;
    List<Evidence> hitEvidenceSet;
    boolean flag;
    long needEvidence;
    LongBitSet predicateUnchosed;
    LongBitSet DCCandicate;

    public Pair(List<Evidence> unhitEvidenceSet, boolean flag, long needEvidence, LongBitSet predicateUnchosed, LongBitSet DCCandicate, List<Evidence> hitEvidenceSet) {
        this.predicateUnchosed = predicateUnchosed;
        this.unhitEvidenceSet = unhitEvidenceSet;
        this.flag = flag;
        this.needEvidence = needEvidence;
        this.DCCandicate = DCCandicate;
        this.hitEvidenceSet = hitEvidenceSet;

    }
}
