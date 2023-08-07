package DADC;

import ADCD.evidence.evidenceSet.Evidence;
import ch.javasoft.bitset.LongBitSet;

import java.util.List;

public class CheckedDC {
    List<Evidence> unhitEvidences;
    long unhitCount;
    List<Evidence> hitEvidences;
    long hitCount;
    LongBitSet predicateUnchosed;


    public CheckedDC(List<Evidence> unhitEvidences,long unhitCount, List<Evidence> hitEvidences, long hitCount, LongBitSet predicateUnchosed) {
        this.unhitEvidences = unhitEvidences;
        this.unhitCount = unhitCount;
        this.hitEvidences = hitEvidences;
        this.hitCount = hitCount;
        this.predicateUnchosed = predicateUnchosed;
    }
}
