package ADCD.evidence;

import ADCD.evidence.clue.BinaryPliClueSetBuilder;
import ADCD.plishard.PliShard;
import com.koloboke.collect.map.hash.HashLongLongMap;

import java.util.concurrent.Callable;

public class MyThread implements Callable<HashLongLongMap> {
    int i;
    int j;
    PliShard[] left_pliShards;
    PliShard[] right_pliShards;

    public MyThread(int i, int j, PliShard[] left_pliShards, PliShard[] right_pliShards) {
        this.i = i;
        this.j = j;
        this.left_pliShards = left_pliShards;
        this.right_pliShards = right_pliShards;
    }

    @Override
    public HashLongLongMap call() throws Exception {
        return new BinaryPliClueSetBuilder(left_pliShards[i], right_pliShards[j]).buildClueSet();
        //return  new UnaryPliClueSetBuilder(pliShards[i]).buildClueSet();
    }
}
