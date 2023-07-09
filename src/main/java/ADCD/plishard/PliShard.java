package ADCD.plishard;

import java.util.Iterator;
import java.util.List;

public class PliShard implements Iterable<Pli> {

    public final List<Pli> plis;
    public final int beg, end;  // tuple id range [beg, end)

    public PliShard(List<Pli> plis, int beg, int end) {
        this.plis = plis;
        this.beg = beg;
        this.end = end;

        for (Pli pli : plis)
            pli.pliShard = this;
    }


    @Override
    public Iterator<Pli> iterator() {
        return plis.iterator();
    }
}
