package ADCD.evidence;

import ADCD.evidence.clue.BinaryPliClueSetBuilder;
import ADCD.evidence.clue.ClueSetBuilder;
import ADCD.evidence.evidenceSet.EvidenceSet;
import ADCD.plishard.PliShard;
import ADCD.predicate.PredicateBuilder;
import com.koloboke.collect.map.hash.HashLongLongMap;
import com.koloboke.collect.map.hash.HashLongLongMaps;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;


public class EvidenceSetBuilder {

    private  EvidenceSet fullEvidenceSet;

    public EvidenceSetBuilder(PredicateBuilder predicateBuilder) {
        ClueSetBuilder.configure(predicateBuilder);
        fullEvidenceSet = new EvidenceSet(predicateBuilder, ClueSetBuilder.getCorrectionMap());
    }

    public EvidenceSet buildEvidenceSet(PliShard[] pliShards) {
        if (pliShards.length != 0) {
            HashLongLongMap clueSet = buildClueSet(pliShards);
            fullEvidenceSet.build(clueSet);
        }
        return fullEvidenceSet;
    }

    public EvidenceSet buildEvidenceSet3(PliShard[] left_pliShards,PliShard[] right_pliShard) {
        if (left_pliShards.length != 0 && right_pliShard.length != 0) {
            HashLongLongMap clueSet = buildCrossClueSet(left_pliShards, right_pliShard);

            fullEvidenceSet.build(clueSet);
        }
        return fullEvidenceSet;
    }

    private HashLongLongMap buildClueSet(PliShard[] pliShards) {
        int taskCount = (pliShards.length * (pliShards.length + 1)) / 2;
        System.out.println("  [CLUE BUILDER] task count: " + taskCount);

        ClueSetTask rootTask = new ClueSetTask(null, pliShards, 0, taskCount);
        return rootTask.invoke();
    }

    private HashLongLongMap buildClueSet3(PliShard[] left_pliShards,PliShard[] right_pliShards) throws ExecutionException, InterruptedException {
        MyClueSetTask myClueSetTask = new MyClueSetTask(left_pliShards, right_pliShards);
        return myClueSetTask.buildEvidenceSet();
    }

    private HashLongLongMap buildCrossClueSet(PliShard[] left_pliShards,PliShard[] right_pliShards){
        List<HashLongLongMap> clueSetList = new ArrayList<>();
        HashLongLongMap result = HashLongLongMaps.newMutableMap();

        for (int i = 0; i < left_pliShards.length; i++) {
            for (int j = 0; j < right_pliShards.length; j++) {
/*                System.out.println(i);
                System.out.println(j);
                System.out.println();*/
                ClueSetBuilder clueSetBuilder = new BinaryPliClueSetBuilder(left_pliShards[i], right_pliShards[j]);
                clueSetList.add(clueSetBuilder.buildClueSet());
                //System.out.println(clueSetBuilder.buildClueSet().entrySet());
            }
        }

        for (HashLongLongMap hashLongLongMap : clueSetList) {
            for (var e : hashLongLongMap.entrySet()) {
                result.addValue(e.getKey(), e.getValue(), 0);
            }
        }

        return result;
    }

    public EvidenceSet getEvidenceSet() {
        return fullEvidenceSet;
    }

}



