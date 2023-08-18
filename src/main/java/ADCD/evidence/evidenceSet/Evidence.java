package ADCD.evidence.evidenceSet;

import ch.javasoft.bitset.LongBitSet;
import de.metanome.algorithms.dcfinder.predicates.sets.PredicateSet;

public class Evidence {

    public long count;
    public long clue;
    public LongBitSet bitset;
    //PredicateSet predicates;

    public Evidence() {

    }

    public Evidence(long _satisfied, long _count, LongBitSet cardinalityMask, LongBitSet[] correctMap) {
        clue = _satisfied;
        count = _count;
        //predicates = buildEvidenceFromClue(cardinalityMask, correctMap);
        buildEvidenceFromClue(cardinalityMask, correctMap);
    }

    public Evidence(LongBitSet bitSet, long _count) {
        bitset = bitSet;
        count = _count;
    }

    public PredicateSet buildEvidenceFromClue(LongBitSet cardinalityMask, LongBitSet[] correctMap) {
        LongBitSet evidence = cardinalityMask.clone();
        //初始都是不相等，当tmp&1==1是 代表这是相等，那么xor  将01 xor 11 变成了10  前面那个是相等。
        long tmp = clue;
        int pos = 0;
        while (tmp != 0) {
            if ((tmp & 1L) != 0L) evidence.xor(correctMap[pos]);
            pos++;
            tmp >>>= 1;
        }

        bitset = evidence;
        return new PredicateSet(evidence);
    }

//    public PredicateSet getPredicates() {
//        return predicates;
//    }

    public LongBitSet getBitSetPredicates() {
        return bitset;
    }

    public long getCount() {
        return count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Evidence evidence = (Evidence) o;
        return clue == evidence.clue;
    }

    @Override
    public int hashCode() {
        return (int) (clue ^ (clue >>> 32));
    }

    @Override
    public String toString() {
        return new PredicateSet(bitset).toString();
    }
}
