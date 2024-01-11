package ADCD.evidence;

import ADCD.evidence.clue.ClueSetBuilder;
import ADCD.evidence.evidenceSet.EvidenceSet;
import ADCD.plishard.PliShard;
import ADCD.predicate.PredicateBuilder;
import com.koloboke.collect.map.hash.HashLongLongMap;

import java.util.concurrent.ExecutionException;


public class EvidenceSetBuilder {

    private EvidenceSet fullEvidenceSet;

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

    public EvidenceSet buildCrossEvidenceSet(PliShard[] left_pliShards,PliShard[] right_pliShard) {
        if (left_pliShards.length != 0 && right_pliShard.length != 0) {
            HashLongLongMap clueSet = buildCrossSet(left_pliShards, right_pliShard);

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

    private HashLongLongMap buildCrossClueSet(PliShard[] left_pliShards,PliShard[] right_pliShards) throws ExecutionException, InterruptedException {
        MyClueSetTask myClueSetTask = new MyClueSetTask(left_pliShards, right_pliShards);
        return myClueSetTask.buildEvidenceSet();
    }

    private HashLongLongMap buildCrossSet(PliShard[] left_pliShards, PliShard[] right_pliShards){
        int taskCount = left_pliShards.length * right_pliShards.length;
        System.out.println("  [CLUE BUILDER] task count: " + taskCount);

        CrossClueSetTask rootTask = new CrossClueSetTask(null, left_pliShards, right_pliShards, 0, taskCount);
        return rootTask.invoke();
    }

    public EvidenceSet getEvidenceSet() {
        return fullEvidenceSet;
    }

}



