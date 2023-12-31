package ADCD.inversion.approx;

import ch.javasoft.bitset.LongBitSet;

import java.util.*;
import java.util.function.Consumer;

public class ArrayTreeSearch {

    public  static int N;

    // TODO: add subtree count
    ArrayTreeSearch[] subtrees = new ArrayTreeSearch[N];
    DCCandidate dc;

    public ArrayTreeSearch() {

    }

    public boolean add(DCCandidate addDC) {
        LongBitSet bitSet = addDC.bitSet;
        ArrayTreeSearch treeNode = this;

        for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
            //很像字典树trie
            ArrayTreeSearch[] currSubtrees = treeNode.subtrees;
            treeNode = currSubtrees[i] == null ? (currSubtrees[i] = new ArrayTreeSearch()) : currSubtrees[i];
        }

        //if (dc != null) System.err.println("DC exists!");  遍历到了最后一个 把dc放进去
        treeNode.dc = addDC;
        return true;
    }

    public List<DCCandidate> getAndRemoveGeneralizations(LongBitSet superset) {
        List<DCCandidate> removed = new ArrayList<>();
        getAndRemoveGeneralizations(superset, 0, removed);
        return removed;
    }

    private boolean getAndRemoveGeneralizations(LongBitSet superset, int next, List<DCCandidate> removed) {
        if (dc != null) {
            removed.add(dc);
            dc = null;
        }

        int nextBit = superset.nextSetBit(next);
        while (nextBit >= 0) {
            ArrayTreeSearch subTree = subtrees[nextBit];
            if (subTree != null)
                if (subTree.getAndRemoveGeneralizations(superset, nextBit + 1, removed))
                    subtrees[nextBit] = null;
            nextBit = superset.nextSetBit(nextBit + 1);
        }
        return noSubtree();
    }

    public boolean isEmpty() {
        return dc == null && noSubtree();
    }

    private boolean noSubtree() {
        for (ArrayTreeSearch subtree : subtrees)
            if (subtree != null) return false;
        return true;
    }

    public boolean containsSubset(DCCandidate add) {
        return getSubset(add, 0) != null;
    }

    public DCCandidate getSubset(DCCandidate add) {
        return getSubset(add, 0);
    }

    private DCCandidate getSubset(DCCandidate add, int next) {
        if (dc != null) return dc;

        int nextBit = add.bitSet.nextSetBit(next);
        while (nextBit >= 0) {
            ArrayTreeSearch subTree = subtrees[nextBit];
            if (subTree != null) {
                DCCandidate res = subTree.getSubset(add, nextBit + 1);
                if (res != null) return res;
            }
            nextBit = add.bitSet.nextSetBit(nextBit + 1);
        }

        return null;
    }

    public void forEach(Consumer<DCCandidate> consumer) {
        //将所有的结果收集起来。
        if (dc != null) consumer.accept(dc);
        for (ArrayTreeSearch subtree : subtrees) {
            if (subtree != null)
                subtree.forEach(consumer);
        }
    }

}
