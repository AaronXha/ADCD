package DADC;

import ADCD.evidence.EvidenceSetBuilder;
import ADCD.evidence.evidenceSet.Evidence;
import ADCD.evidence.evidenceSet.EvidenceSet;
import ADCD.inversion.approx.ApproxEvidenceInverter;
import ADCD.plishard.PliShard;
import ADCD.plishard.PliShardBuilder;
import ADCD.predicate.PredicateBuilder;
import ch.javasoft.bitset.LongBitSet;
import de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint;
import de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraintSet;
import de.metanome.algorithms.dcfinder.input.Input;
import de.metanome.algorithms.dcfinder.input.RelationalInput;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class DADC {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        String origin = "dataset/airport_10000_red.csv";
        String add = "dataset/airport_10000.csv";
        String path ="dataset/airport.csv";

/*        String origin = "dataset/test_origin.csv";
        String add = "dataset/test_new.csv";
        String path="dataset/Tax.csv";*/

        DADC dadc = new DADC(true,0.01,350);
        //dadc.buildEvidence(path);
        dadc.buildEvidence(origin,add);
        //System.out.println(evi1.equals(evi2));
    }

    private final boolean noCrossColumn;
    private final double minimumSharedValue = 0.3d;
    private final double comparableThreshold = 0.1d;

    // configure of PliShardBuilder
    private final int shardLength;

    // configure of ApproxCoverSearcher
    private final double threshold;

    public DADC(boolean _noCrossColumn, double _threshold, int _len) {
        noCrossColumn = _noCrossColumn;
        threshold = _threshold;
        shardLength = _len;
    }

    public void buildEvidence(String dataFp){

        System.out.println("INPUT FILE: " + dataFp);
        System.out.println("ERROR THRESHOLD: " + threshold);

        // load input data, build predicate space
        long t00 = System.currentTimeMillis();
        Input input = new Input(new RelationalInput(dataFp), -1);
        PredicateBuilder predicateBuilder = new PredicateBuilder(noCrossColumn, minimumSharedValue, comparableThreshold);
        predicateBuilder.buildPredicateSpace(input);
        System.out.println(" [ADCD] Predicate space size: " + predicateBuilder.predicateCount());

        // build pli shards
        PliShardBuilder pliShardBuilder = new PliShardBuilder(shardLength, input.getParsedColumns());
        PliShard[] pliShards = pliShardBuilder.buildPliShards(input.getIntInput());
        long t_pre = System.currentTimeMillis() - t00;
        System.out.println(" [TIME] Pre-process time: " + t_pre + "ms");

        long t01 = System.currentTimeMillis();
        EvidenceSetBuilder evidenceSetBuilder = new EvidenceSetBuilder(predicateBuilder);
        EvidenceSet evidenceSet = evidenceSetBuilder.buildEvidenceSet(pliShards);
        long t_evi = System.currentTimeMillis() - t01;
        System.out.println(" [ADCD] evidence set size: " + evidenceSet.size());
        System.out.println(" [ADCD] evidence count: " + evidenceSet.getTotalCount());
        System.out.println(" [TIME] Build evidence time: " + t_evi + "ms");

        long t02 = System.currentTimeMillis();
        ApproxEvidenceInverter aei = new ApproxEvidenceInverter(predicateBuilder,true);

        long target = (long)Math.ceil((1 - threshold) * (input.getRowCount()) * (input.getRowCount() - 1));
        DenialConstraintSet denialConstraints = aei.buildDenialConstraints(evidenceSet, target);
        long t_aei = System.currentTimeMillis() - t02;
        System.out.println(" [TIME] AEI time: " + t_aei + "ms");

    }

    public void buildEvidence(String originFile,String newFile) throws ExecutionException, InterruptedException {

        System.out.println("INPUT ORIGIN FILE: " + originFile);
        System.out.println("INPUT NEW FILE: " + newFile);
        System.out.println("ERROR THRESHOLD: " + threshold);

        // load input data, build predicate space
        long t00 = System.currentTimeMillis();
        RelationalInput relationalInputOrigin = new RelationalInput(originFile);
        RelationalInput relationalInputNew = new RelationalInput(newFile);

        Input input = new Input(relationalInputOrigin, relationalInputNew);
        PredicateBuilder predicateBuilder = new PredicateBuilder(noCrossColumn, minimumSharedValue, comparableThreshold);
        predicateBuilder.buildPredicateSpace(input);
        System.out.println(" [ADCD] Predicate space size: " + predicateBuilder.predicateCount());

        int originRowCount = input.getOriginRowCount();
        int newRowCount = input.getNewRowCount();

        PliShardBuilder pliShardBuilder = new PliShardBuilder(shardLength, input.getParsedColumns());
        PliShard[] pliShards = pliShardBuilder.buildPliShardsDynamic(input.getIntInput(), originRowCount);

        int originShardsNumber = originRowCount / shardLength + 1;
        int newShardsNumber = newRowCount / shardLength + 1;
        PliShard[] pliOrigin = new PliShard[originShardsNumber];
        PliShard[] pliNew = new PliShard[newShardsNumber];

        System.arraycopy(pliShards, 0, pliOrigin, 0, originShardsNumber);
        System.arraycopy(pliShards, originShardsNumber , pliNew, 0, newShardsNumber);

        long t_pre = System.currentTimeMillis() - t00;
        System.out.println(" [TIME] Pre-process time: " + t_pre + "ms");

        long t01 = System.currentTimeMillis();
        EvidenceSetBuilder evidenceSetBuilderOrigin = new EvidenceSetBuilder(predicateBuilder);
        EvidenceSet evidenceSetOrigin = evidenceSetBuilderOrigin.buildEvidenceSet(pliOrigin);

        System.out.println(" [ADCD] original evidence set size: " + evidenceSetOrigin.size());
        System.out.println(" [ADCD] evidence count: " + evidenceSetOrigin.getTotalCount());
        long t_evi_origin = System.currentTimeMillis() - t01;
        System.out.println(" [TIME] Build origin evidence time: " + t_evi_origin + "ms");

        long t04 = System.currentTimeMillis();
        EvidenceSetBuilder evidenceSetBuilderNew = new EvidenceSetBuilder(predicateBuilder);
        EvidenceSet evidenceSetNew = evidenceSetBuilderNew.buildEvidenceSet(pliNew);

        System.out.println(" [ADCD] new evidence set size: " + evidenceSetNew.size());
        System.out.println(" [ADCD] evidence count: " + evidenceSetNew.getTotalCount());
        long t_evi_new = System.currentTimeMillis() - t04;
        System.out.println(" [TIME] Build new evidence time: " + t_evi_new + "ms");

        long t05 = System.currentTimeMillis();
        EvidenceSetBuilder evidenceSetBuilderCross = new EvidenceSetBuilder(predicateBuilder);
        EvidenceSet evidenceSetCross = evidenceSetBuilderCross.buildCrossEvidenceSet(pliOrigin,pliNew);

        System.out.println(" [ADCD] cross evidence set size: " + evidenceSetCross.size());
        System.out.println(" [ADCD] evidence count: " + evidenceSetCross.getTotalCount());
        long t_evi_cross = System.currentTimeMillis() - t05;
        System.out.println(" [TIME] Build cross evidence time: " + t_evi_cross + "ms");

        long t_evi = System.currentTimeMillis() - t01;

        long t02 = System.currentTimeMillis();
        ApproxEvidenceInverter aei = new ApproxEvidenceInverter(predicateBuilder,true);
        long target = (long)Math.ceil((1 - threshold) * originRowCount * (originRowCount - 1));
        DenialConstraintSet originDCSet = aei.buildDenialConstraints(evidenceSetOrigin, target);
        long t_aei = System.currentTimeMillis() - t02;
        System.out.println(" [TIME] AEI time: " + t_aei + "ms");

        EvidenceSet evidenceSet = new EvidenceSet(evidenceSetOrigin);

        for(var c: evidenceSetNew.clueToEvidence.entrySet()){
            long clue = c.getKey();
            if(evidenceSet.clueToEvidence.containsKey(clue)){
                Evidence tmp = evidenceSet.clueToEvidence.get(clue);
                tmp.count += c.getValue().count;
                evidenceSet.clueToEvidence.put(clue,tmp);
            }
            else{
                evidenceSet.clueToEvidence.put(clue,c.getValue());
            }

        }

        for(var c: evidenceSetCross.clueToEvidence.entrySet()){
            long clue = c.getKey();
            if(evidenceSet.clueToEvidence.containsKey(clue)){
                Evidence tmp = evidenceSet.clueToEvidence.get(clue);
                tmp.count+=c.getValue().count;
                evidenceSet.clueToEvidence.put(clue,tmp);
            }
            else{
                evidenceSet.clueToEvidence.put(clue,c.getValue());
            }
        }

        System.out.println(" [ADCD] evidence set size: " + evidenceSet.size());
        System.out.println(" [ADCD] evidence count: " + evidenceSet.getTotalCount());
        System.out.println(" [TIME] Build evidence time: " + t_evi + "ms");

        long t03 = System.currentTimeMillis();
        //获得尚未发生改变前的dc有哪些
        ApproxDynamicEvidence approxDynamicEvidence = new ApproxDynamicEvidence(predicateBuilder,true);
        long leastEvidenceToCover = (long) Math.ceil((1 - threshold) * input.getRowCount() * (input.getRowCount() - 1));

        ApproxEvidenceInverter approxEvidenceInverter = new ApproxEvidenceInverter(predicateBuilder,true);
        DenialConstraintSet fullDCSet = approxEvidenceInverter.buildDenialConstraints(evidenceSet, leastEvidenceToCover);



        // 获得增量后的dc
        DenialConstraintSet dcSet = approxDynamicEvidence.build(evidenceSet, originDCSet, leastEvidenceToCover);
        long t_dynamic = System.currentTimeMillis() - t03;
        System.out.println(" [TIME] Dynamic time: " + t_dynamic + "ms");

/*        for(DenialConstraint dc: dcSet){
            if(!fullDCSet.contains(dc))
                System.out.println(dc.getPredicateSet().getBitset());
        }
        System.out.println();
        for(DenialConstraint dc: fullDCSet){
            if(!dcSet.contains(dc))
                System.out.println(dc.getPredicateSet().getBitset());
        }*/
    }
}
