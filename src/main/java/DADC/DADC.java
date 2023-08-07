package DADC;

import ADCD.evidence.EvidenceSetBuilder;
import ADCD.evidence.evidenceSet.Evidence;
import ADCD.evidence.evidenceSet.EvidenceSet;
import ADCD.inversion.approx.ApproxEvidenceInverter;
import ADCD.plishard.PliShard;
import ADCD.plishard.PliShardBuilder;
import ADCD.predicate.PredicateBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraintSet;
import de.metanome.algorithms.dcfinder.input.Input;
import de.metanome.algorithms.dcfinder.input.RelationalInput;

import java.io.*;
import java.util.concurrent.ExecutionException;

public class DADC {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        String origin = "dataset/airport_10000_red.csv";
        String add = "dataset/airport_10000.csv";
        String path="dataset/airport.csv";

        DADC dadc = new DADC(true,0.01,350);
        //dadc.new_produce(path,10000);
        dadc.buildEvidence(origin);
        //dadc.buildEvidence(origin,add);
    }
    private final boolean noCrossColumn;
    private final double minimumSharedValue = 0.3d;
    private final double comparableThreshold = 0.1d;

    private PliShard[] pre_pliShards;
    private PliShard[] mid_pliShards;

    // configure of PliShardBuilder
    private final int shardLength;

    // configure of ApproxCoverSearcher
    private final double threshold;

    // private String dataFp;
    private Input input;
    private PredicateBuilder predicateBuilder;
    private PliShardBuilder pliShardBuilder;
    private EvidenceSetBuilder evidenceSetBuilder;


    public DADC(boolean _noCrossColumn, double _threshold, int _len) {
        noCrossColumn = _noCrossColumn;
        threshold = _threshold;
        shardLength = _len;
        predicateBuilder = new PredicateBuilder(noCrossColumn, minimumSharedValue, comparableThreshold);
    }

    public void buildEvidence(String dataFp){

        System.out.println("INPUT FILE: " + dataFp);
        System.out.println("ERROR THRESHOLD: " + threshold);

        // load input data, build predicate space
        long t00 = System.currentTimeMillis();
        input = new Input(new RelationalInput(dataFp), -1);
        predicateBuilder.buildPredicateSpace(input);
        System.out.println(" [ADCD] Predicate space size: " + predicateBuilder.predicateCount());

        // build pli shards
        pliShardBuilder = new PliShardBuilder(shardLength, input.getParsedColumns());
        PliShard[] pliShards = pliShardBuilder.buildPliShards(input.getIntInput());
        long t_pre = System.currentTimeMillis() - t00;
        System.out.println(" [TIME] Pre-process time: " + t_pre + "ms");

        long t01 = System.currentTimeMillis();
        evidenceSetBuilder = new EvidenceSetBuilder(predicateBuilder);
        EvidenceSet evidenceSet = evidenceSetBuilder.buildEvidenceSet(pliShards);
        long t_evi = System.currentTimeMillis() - t01;
        System.out.println(" [ADCD] evidence set size: " + evidenceSet.size());
        System.out.println(" [ADCD] evidence count: " + evidenceSet.getTotalCount());
        System.out.println(" [TIME] Build evidence time: " + t_evi + "ms");

        long t02 = System.currentTimeMillis();ApproxEvidenceInverter aei = new ApproxEvidenceInverter(predicateBuilder,true);
        long target = (long)Math.ceil(1 - threshold) * (input.getRowCount())*(input.getRowCount() - 1);
        DenialConstraintSet denialConstraints = aei.buildDenialConstraints(evidenceSet, target);
        long t_aei = System.currentTimeMillis() - t02;
        System.out.println(" [TIME] AEI time: " + t_aei + "ms");
    }

    public void new_produce(String dataFp,int number) throws ExecutionException, InterruptedException {

        System.out.println("INPUT FILE: " + dataFp);
        System.out.println("ERROR THRESHOLD: " + threshold);

        // load input data, build predicate space
        long t00 = System.currentTimeMillis();
        input = new Input(new RelationalInput(dataFp), -1);
        //System.out.println(input.getRowCount());
        predicateBuilder.buildPredicateSpace(input);
        System.out.println(" [ADCD] Predicate space size: " + predicateBuilder.predicateCount());

        // build pli shards
        pliShardBuilder = new PliShardBuilder(shardLength, input.getParsedColumns());
        //PliShard[] pliShards = pliShardBuilder.buildPliShards(input.getIntInput(), 1000);
        PliShard[] pliShards = pliShardBuilder.buildPliShards(input.getIntInput());
        long t_pre = System.currentTimeMillis() - t00;
        System.out.println(" [TIME] Pre-process time: " + t_pre + "ms");

        int n = pliShards.length;
        int first_pli_number = (number + shardLength - 1)/shardLength;

        PliShard[] first = new PliShard[n - first_pli_number];
        if (n - first_pli_number >= 0) System.arraycopy(pliShards, 0, first, 0, n - first_pli_number);

        PliShard[] second = new PliShard[first_pli_number];
        System.arraycopy(pliShards, n - first_pli_number, second, 0, first_pli_number);

        long t01 = System.currentTimeMillis();
        evidenceSetBuilder = new EvidenceSetBuilder(predicateBuilder);
        EvidenceSet evidenceSet_f = evidenceSetBuilder.buildEvidenceSet(first);
        System.out.println(" [ADCD] origin evidence set size: " + evidenceSet_f.size());
        System.out.println(" [ADCD] evidence count: " + evidenceSet_f.getTotalCount());

        evidenceSetBuilder = new EvidenceSetBuilder(predicateBuilder);
        EvidenceSet evidenceSet_s = evidenceSetBuilder.buildEvidenceSet(second);
        System.out.println(" [ADCD] addition evidence set size: " + evidenceSet_s.size());
        System.out.println(" [ADCD] evidence count: " + evidenceSet_s.getTotalCount());

        evidenceSetBuilder = new EvidenceSetBuilder(predicateBuilder);
        EvidenceSet evidenceSet_t = evidenceSetBuilder.buildEvidenceSet3(first,second);
        long t_evi = System.currentTimeMillis() - t01;
        System.out.println(" [ADCD] cross evidence set size: " + evidenceSet_t.size());
        System.out.println(" [ADCD] evidence count: " + evidenceSet_t.getTotalCount());
        System.out.println(" [TIME] Build evidence time: " + t_evi + "ms");

        long t02 = System.currentTimeMillis();
        ApproxEvidenceInverter aei = new ApproxEvidenceInverter(predicateBuilder,true);
        long target = (long)Math.ceil(1 - threshold) * (input.getRowCount() - number) * (input.getRowCount() - number - 1);
        DenialConstraintSet denialConstraints = aei.buildDenialConstraints(evidenceSet_f, target);
        long t_aei = System.currentTimeMillis() - t02;
        System.out.println(" [TIME] AEI time: " + t_aei + "ms");

        for(var c:evidenceSet_s.clueToEvidence.entrySet()){
            long clue = c.getKey();
            if(evidenceSet_f.clueToEvidence.containsKey(clue)){
                Evidence tmp = evidenceSet_f.clueToEvidence.get(clue);
                tmp.count+=c.getValue().count;
                evidenceSet_f.clueToEvidence.put(clue,tmp);
            }
            else{
                evidenceSet_f.clueToEvidence.put(clue,c.getValue());
            }

        }
        for(var c:evidenceSet_t.clueToEvidence.entrySet()){
            long clue = c.getKey();
            if(evidenceSet_f.clueToEvidence.containsKey(clue)){
                Evidence tmp = evidenceSet_f.clueToEvidence.get(clue);
                tmp.count+=c.getValue().count;
                evidenceSet_f.clueToEvidence.put(clue,tmp);
            }
            else{
                evidenceSet_f.clueToEvidence.put(clue,c.getValue());
            }
        }

        System.out.println(" [ADCD] total evidence set size: " + evidenceSet_f.size());
        System.out.println(" [ADCD] evidence count: " + evidenceSet_f.getTotalCount());

        long t03 = System.currentTimeMillis();
        //获得尚未发生改变前的dc有哪些
        ApproxDynamicEvidence approxDynamicEvidence = new ApproxDynamicEvidence(predicateBuilder,true);
        long leastEvidenceToCover = (long) Math.ceil((1 - threshold) * input.getRowCount()*(input.getRowCount() - 1));
        // 获得增量后的dc
        DenialConstraintSet denialConstraints1 = approxDynamicEvidence.build(evidenceSet_f, denialConstraints, leastEvidenceToCover, predicateBuilder);
        long t_dynamic = System.currentTimeMillis() - t03;
        System.out.println("  [PACS] Min DC size : " + denialConstraints1.size());
        System.out.println(" [TIME] Dynamic time: " + t_dynamic + "ms");

    }


    public void buildEvidence(String originFile,String newFile) throws ExecutionException, InterruptedException {

        System.out.println("INPUT ORIGIN FILE: " + originFile);
        System.out.println("INPUT NEW FILE: " + newFile);
        System.out.println("ERROR THRESHOLD: " + threshold);

        Input inputOrigin = new Input(new RelationalInput(originFile),-1);
        predicateBuilder.buildPredicateSpace(inputOrigin);

        PliShardBuilder pliOriginBuilder = new PliShardBuilder(shardLength,inputOrigin.getParsedColumns());
        PliShard[] pliOrigin = pliOriginBuilder.buildPliShards(inputOrigin.getIntInput());

        EvidenceSetBuilder evidenceSetBuilderOrigin = new EvidenceSetBuilder(predicateBuilder);
        EvidenceSet evidenceSetOrigin = evidenceSetBuilderOrigin.buildEvidenceSet(pliOrigin);

        System.out.println(" [ADCD] original evidence set size: " + evidenceSetOrigin.size());
        System.out.println(" [ADCD] evidence count: " + evidenceSetOrigin.getTotalCount());

        Input inputNew = new Input(new RelationalInput(newFile), -1);
        PliShardBuilder pliNewFileBuilder = new PliShardBuilder(shardLength,inputNew.getParsedColumns());
        PliShard[] pliNew = pliNewFileBuilder.buildPliShards(inputNew.getIntInput());

        EvidenceSetBuilder evidenceSetBuilderNew = new EvidenceSetBuilder(predicateBuilder);
        EvidenceSet evidenceSetNew = evidenceSetBuilderNew.buildEvidenceSet(pliNew);

        System.out.println(" [ADCD] new evidence set size: " + evidenceSetNew.size());
        System.out.println(" [ADCD] evidence count: " + evidenceSetNew.getTotalCount());

        EvidenceSetBuilder evidenceSetBuilderCross = new EvidenceSetBuilder(predicateBuilder);
        EvidenceSet evidenceSetCross = evidenceSetBuilderCross.buildEvidenceSet3(pliOrigin,pliNew);

        System.out.println(" [ADCD] cross evidence set size: " + evidenceSetCross.size());
        System.out.println(" [ADCD] evidence count: " + evidenceSetCross.getTotalCount());

        EvidenceSet evidenceSet = evidenceSetOrigin;

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
    }
}
