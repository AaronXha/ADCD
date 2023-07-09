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
//        dadc.pre_EvidenceSet("dataset/airport_100_red.csv",-1);
//        dadc.mid_EvidenceSet("dataset/airport_100.csv",-1);
//        System.out.println("==============");
//        //dadc.last_EvidenceSet2(origin,add);
//        dadc.last_EvidenceSet();
 //       dadc.new_produce(path,10000);
    // dadc.builderEvidence(origin,add);
        dadc.new_produce(path,1000);
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

    int sum = 0;

   // private String dataFp;
    private Input input;
    private PredicateBuilder predicateBuilder;
    private PliShardBuilder pliShardBuilder;
    private EvidenceSetBuilder evidenceSetBuilder;
    private EvidenceSet pre_EvidenceSet;
    private EvidenceSet mid_EvidenceSet;
    private EvidenceSet next_EvidenceSet;

    public DADC(boolean _noCrossColumn, double _threshold, int _len) {
        noCrossColumn = _noCrossColumn;
        threshold = _threshold;
        shardLength = _len;
        predicateBuilder = new PredicateBuilder(noCrossColumn, minimumSharedValue, comparableThreshold);
    }

    public void new_produce(String dataFp,int number) throws ExecutionException, InterruptedException {

        System.out.println("INPUT FILE: " + dataFp);
        System.out.println("ERROR THRESHOLD: " + threshold);

        // load input data, build predicate space
        long t00 = System.currentTimeMillis();
        input = new Input(new RelationalInput(dataFp), -1);
        System.out.println(input.getRowCount());
        predicateBuilder.buildPredicateSpace(input);
        System.out.println(" [ADCD] Predicate space size: " + predicateBuilder.predicateCount());

        // build pli shards
        pliShardBuilder = new PliShardBuilder(shardLength, input.getParsedColumns());
        PliShard[] pliShards = pliShardBuilder.buildPliShards(input.getIntInput(),1000);
        long t_pre = System.currentTimeMillis() - t00;
        System.out.println("[ADCD] Pre-process time: " + t_pre + "ms");
        int n = pliShards.length;
        int first_pli_number = (number+shardLength-1)/shardLength;
        PliShard[] first = new PliShard[n-first_pli_number];
       // writePliShard(first,dataFp);
       // PliShard[] pliShards1 = readPliShard(dataFp);
        for(int i=0;i<n-first_pli_number;i++)first[i] = pliShards[i];
        PliShard[] second = new PliShard[first_pli_number];
        for(int i=0;i<first_pli_number;i++)second[i] = pliShards[i+n-first_pli_number];
        evidenceSetBuilder = new EvidenceSetBuilder(predicateBuilder);
        EvidenceSet evidenceSet_f = evidenceSetBuilder.buildEvidenceSet(first);
//        System.out.println(" [ADCD] evidence set size: " + evidenceSet_f.size());
//        System.out.println(" [ADCD] evidence count: " + evidenceSet_f.getTotalCount());
        evidenceSetBuilder = new EvidenceSetBuilder(predicateBuilder);
        long pre_time = System.currentTimeMillis();
        EvidenceSet evidenceSet_s = evidenceSetBuilder.buildEvidenceSet(second);

        ApproxEvidenceInverter aei = new ApproxEvidenceInverter(predicateBuilder,true);
        long target = (long)Math.ceil(1-threshold)*(input.getRowCount()-1000)*(input.getRowCount()-1001);
        DenialConstraintSet denialConstraints = aei.buildDenialConstraints(evidenceSet_f, target);

        evidenceSetBuilder = new EvidenceSetBuilder(predicateBuilder);
        EvidenceSet evidenceSet_t = evidenceSetBuilder.buildEvidenceSet3(first,second);

//        System.out.println("================");
//        System.out.println(" [ADCD] evidence set size: " + evidenceSet_t.size());
//        System.out.println(" [ADCD] evidence count: " + evidenceSet_t.getTotalCount());


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


        System.out.println(" [ADCD] evidence set size: " + evidenceSet_f.size());
        System.out.println(" [ADCD] evidence count: " + evidenceSet_f.getTotalCount());

       //获得尚未发生改变前的dc有哪些
       ApproxDynamicEvidence approxDynamicEvidence = new ApproxDynamicEvidence(predicateBuilder,true);
        long leastEvidenceToCover = (long) Math.ceil((1 - threshold) * input.getRowCount()*(input.getRowCount()-1));
        // 获得增量后的dc
        DenialConstraintSet denialConstraints1 = approxDynamicEvidence.buildDynamicDc(evidenceSet_f, denialConstraints, leastEvidenceToCover,predicateBuilder);
        System.out.println(denialConstraints1.size());


    }



    public void builderEvidence(String origin,String newFile) throws ExecutionException, InterruptedException {
        Input inputOrigin = new Input(new RelationalInput(origin),-1);
        predicateBuilder.buildPredicateSpace(inputOrigin);

        PliShardBuilder pliOriginBulider = new PliShardBuilder(shardLength,inputOrigin.getParsedColumns());
        PliShard[] pliOrigin = pliOriginBulider.buildPliShards(inputOrigin.getIntInput());

        System.out.println(inputOrigin.getRowCount());

        EvidenceSetBuilder evidenceSetBuilderorigin = new EvidenceSetBuilder(predicateBuilder);
        EvidenceSet evidenceSetorigin = evidenceSetBuilderorigin.buildEvidenceSet(pliOrigin);

        System.out.println(" [ADCD] evidence set size: " + evidenceSetorigin.size());
        System.out.println(" [ADCD] evidence count: " + evidenceSetorigin.getTotalCount());

        Input inputNewFile = new Input(new RelationalInput(newFile));
        PliShardBuilder pliNewFileBuilder = new PliShardBuilder(shardLength,inputNewFile.getParsedColumns());
        PliShard[] pliNewFile = pliNewFileBuilder.buildPliShards(inputNewFile.getIntInput(),1000900000,10000000);
        EvidenceSetBuilder evidenceSetBuilderNew = new EvidenceSetBuilder(predicateBuilder);
        EvidenceSet evidenceSetNew = evidenceSetBuilderNew.buildEvidenceSet(pliNewFile);

//  System.out.println(inputNewFile.getRowCount());
        EvidenceSetBuilder evidenceSetBuilderlast = new EvidenceSetBuilder(predicateBuilder);
        EvidenceSet evidenceSetlast = evidenceSetBuilderlast.buildEvidenceSet3(pliNewFile,pliOrigin);


        for(var c:evidenceSetNew.clueToEvidence.entrySet()){
            long clue = c.getKey();
            if(evidenceSetorigin.clueToEvidence.containsKey(clue)){
                Evidence tmp = evidenceSetorigin.clueToEvidence.get(clue);
                tmp.count+=c.getValue().count;
                evidenceSetorigin.clueToEvidence.put(clue,tmp);
            }
            else{
                evidenceSetorigin.clueToEvidence.put(clue,c.getValue());
            }

        }

        for(var c:evidenceSetlast.clueToEvidence.entrySet()){
            long clue = c.getKey();
            if(evidenceSetorigin.clueToEvidence.containsKey(clue)){
                Evidence tmp = evidenceSetorigin.clueToEvidence.get(clue);
                tmp.count+=c.getValue().count;
                evidenceSetorigin.clueToEvidence.put(clue,tmp);
            }
            else{
                evidenceSetorigin.clueToEvidence.put(clue,c.getValue());
            }

        }

        System.out.println(" [ADCD] evidence set size: " + evidenceSetNew.size());
        System.out.println(" [ADCD] evidence count: " + evidenceSetNew.getTotalCount());

        System.out.println(" [ADCD] evidence set size: " + evidenceSetlast.size());
        System.out.println(" [ADCD] evidence count: " + evidenceSetlast.getTotalCount());

        System.out.println(" [ADCD] evidence set size: " + evidenceSetorigin.size());
        System.out.println(" [ADCD] evidence count: " + evidenceSetorigin.getTotalCount());





    }
}
