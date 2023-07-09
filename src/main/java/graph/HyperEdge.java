package graph;

import ch.javasoft.bitset.LongBitSet;

public class HyperEdge {
    public LongBitSet bitset;
    public long count;

    public HyperEdge(LongBitSet bitset, long count) {
        this.bitset = bitset;
        this.count = count;
    }
}
