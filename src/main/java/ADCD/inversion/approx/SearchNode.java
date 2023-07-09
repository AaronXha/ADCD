package ADCD.inversion.approx;

import ch.javasoft.bitset.LongBitSet;
import ch.javasoft.bitset.search.NTreeSearch;

import java.util.Collection;

public class SearchNode {
    //覆盖那个evidence
   public int e;
    public  LongBitSet addablePredicates;
   public  ArrayTreeSearch dcCandidates;
   public Collection<DCCandidate> invalidDCs;
   public long target;

   public String H; //没什么用

    public SearchNode(int e, LongBitSet addablePredicates, ArrayTreeSearch dcCandidates, Collection<DCCandidate> invalidDCs, long target) {
        this.e = e;
        this.addablePredicates = addablePredicates;
        this.dcCandidates = dcCandidates;
        this.invalidDCs = invalidDCs;
        this.target = target;
    }

    public SearchNode(int e, LongBitSet addablePredicates, ArrayTreeSearch dcCandidates,
                      Collection<DCCandidate> invalidDCs, long target, String status) {
        this.e = e;
        this.addablePredicates = addablePredicates;
        this.dcCandidates = dcCandidates;
        this.invalidDCs = invalidDCs;
        this.target = target;
        H = status;
    }
}
