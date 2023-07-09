package ADCD.inversion;

import ch.javasoft.bitset.LongBitSet;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BitSetTranslator {

    private Integer[] indexes;

    public BitSetTranslator(Integer[] indexes) {
        this.indexes = indexes;
    }
    //由于predicate重新进行了排序，那么之前的索引的出现了问题，需要生成新的longbitset
    public LongBitSet retransform(LongBitSet bitset) {
        LongBitSet valid = new LongBitSet();
        // TODO: check trivial
        for (int i = bitset.nextSetBit(0); i >= 0; i = bitset.nextSetBit(i + 1)) {
            valid.set(indexes[i]);
        }
        return valid;
    }

    public int retransform(int i) {
        return indexes[i];
    }

    public int transform(int e) {
        for (int i : indexes) {
            if (e == indexes[i]) return i;
        }
        return -1;
    }

    public LongBitSet transform(LongBitSet bitset) {
        LongBitSet bitset2 = new LongBitSet();
        for (Integer i : indexes) {
            if (bitset.get(indexes[i])) {
                bitset2.set(i);
            }
        }
        return bitset2;
    }

    public List<LongBitSet> transform(Collection<LongBitSet> bitsets) {
        return bitsets.stream().map(this::transform).collect(Collectors.toList());
    }

    public List<LongBitSet> retransform(Set<LongBitSet> bitsets) {
        return bitsets.stream().map(this::retransform).collect(Collectors.toList());
    }
}
