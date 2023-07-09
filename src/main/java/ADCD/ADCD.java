package ADCD;

import ADCD.approxcover.ApproxDCBuilder;
import DADC.ApproxDynamicEvidence;
import aei.*;
import graph.*;
import ADCD.evidence.EvidenceSetBuilder;
import ADCD.evidence.evidenceSet.Evidence;
import ADCD.evidence.evidenceSet.EvidenceSet;
import ADCD.inversion.approx.ApproxEvidenceInverter;
import ADCD.predicate.PredicateBuilder;
import ch.javasoft.bitset.LongBitSet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint;
import de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraintSet;
import de.metanome.algorithms.dcfinder.input.Input;
import de.metanome.algorithms.dcfinder.input.RelationalInput;
import ADCD.plishard.PliShard;
import ADCD.plishard.PliShardBuilder;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ADCD {

    // configures of PredicateBuilder
    private final boolean noCrossColumn;
    private final double minimumSharedValue = 0.3d;
    private final double comparableThreshold = 0.1d;

    // configure of PliShardBuilder
    private final int shardLength;

    // configure of ApproxCoverSearcher
    private final double threshold;

    int sum = 0;
    private final int mode;

    private String dataFp;
    private Input input;
    private PredicateBuilder predicateBuilder;
    private PliShardBuilder pliShardBuilder;
    private EvidenceSetBuilder evidenceSetBuilder;
    private ApproxDCBuilder coverSearcher;

    public ADCD(boolean _noCrossColumn, double _threshold, int _len, int _mode) {
        noCrossColumn = _noCrossColumn;
        threshold = _threshold;
        shardLength = _len;
        mode = _mode;
        predicateBuilder = new PredicateBuilder(noCrossColumn, minimumSharedValue, comparableThreshold);
    }
    public PliShard[] readPliShard(String dataFp){
        PliShard[] pliShards = null;
        try (BufferedReader reader = new BufferedReader(new FileReader(getPliFp(dataFp)))) {
            Gson gson = new Gson();
            pliShards = gson.fromJson(reader, PliShard[].class);
        } catch (IOException e) {
            System.err.println(e);
        }
        return pliShards;
    }
    public String getPliFp(String dataFp){
        return dataFp+".pli";
    }
    public  void writePliShard(PliShard pliShards,String dataFp){
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(getPliFp(dataFp)))) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(pliShards, writer);
        } catch (IOException e) {
            System.err.println(e);
        }
    }

    public DenialConstraintSet buildApproxDCs(String _dataFp, int sizeLimit) {
        dataFp = _dataFp;
        System.out.println("INPUT FILE: " + dataFp);
        System.out.println("ERROR THRESHOLD: " + threshold);

        // load input data, build predicate space
        long t00 = System.currentTimeMillis();
        input = new Input(new RelationalInput(dataFp), sizeLimit);
        System.out.println(input.getRowCount());
        predicateBuilder.buildPredicateSpace(input);
        System.out.println(" [ADCD] Predicate space size: " + predicateBuilder.predicateCount());

        // build pli shards
        pliShardBuilder = new PliShardBuilder(shardLength, input.getParsedColumns());
        PliShard[] pliShards = pliShardBuilder.buildPliShards(input.getIntInput());
        //PliShard[] pliShards1 = readPliShard(dataFp);
       // writePliShard(pliShards[0],dataFp);
        long t_pre = System.currentTimeMillis() - t00;
        System.out.println("[ADCD] Pre-process time: " + t_pre + "ms");

        // build evidence set
        if (mode == 1) System.out.println(" [ADCD] Read evidence set from " + getEviFp());
        long t10 = System.currentTimeMillis();
        evidenceSetBuilder = new EvidenceSetBuilder(predicateBuilder);
        //证据集
        EvidenceSet evidenceSet = mode == 1 ? readEvidenceSet() : evidenceSetBuilder.buildEvidenceSet(pliShards);
//        for(var entry:evidenceSet.clueToEvidence.entrySet()){
//            System.out.println(entry.getKey()+" "+entry.getValue().count);
//        }
  //      System.out.println(evidenceSet.clueToEvidence.containsKey(1));
        long t_evi = System.currentTimeMillis() - t10;
        System.out.println(" [ADCD] evidence set size: " + evidenceSet.size());
        System.out.println(" [ADCD] evidence count: " + evidenceSet.getTotalCount());
        System.out.println("[ADCD] Evidence time: " + t_evi + "ms");



        // write evidence set to file
        if (mode >= 2) writeEvidenceSet(evidenceSet);
        if (mode == 3) return null;

        // approx evidence inversion
        long t20 = System.currentTimeMillis();
        long rowCount = input.getRowCount(), tuplePairCount = (rowCount - 1) * rowCount;
        long target = (long)Math.ceil(threshold*tuplePairCount);
        long leastEvidenceToCover = (long) Math.ceil((1 - threshold) * tuplePairCount);
        System.out.println(" [ADCD] Violate at most " + (tuplePairCount - leastEvidenceToCover) + " tuple pairs");

        ApproxEvidenceInverter evidenceInverter = new ApproxEvidenceInverter(predicateBuilder, true);
       // ApproxDynamicEvidence approxDynamicEvidence = new ApproxDynamicEvidence(predicateBuilder,true);
       DenialConstraintSet dcs = evidenceInverter.buildDenialConstraints(evidenceSet, leastEvidenceToCover);
      // DenialConstraintSet dcs = approxDynamicEvidence.buildDenialConstraints(evidenceSet,leastEvidenceToCover);

        long t_aei = System.currentTimeMillis() - t20;
        System.out.println("[ADCD] AEI time: " + t_aei + "ms");

        System.out.println("[ADCD] Total computing time: " + (t_pre + t_evi + t_aei) + " ms\n");
        long t_dync = System.currentTimeMillis();
       // System.out.println(approxDynamicEvidence.buildDynamicDc(evidenceSet,dcs
        //        ,target).size());
        System.out.println("Dynamic time "+(System.currentTimeMillis()-t_dync));
        return dcs;
    }

    EvidenceSet readEvidenceSet() {
        EvidenceSet evidenceSet = null;
        try (BufferedReader reader = new BufferedReader(new FileReader(getEviFp()))) {
            Gson gson = new Gson();
            evidenceSet = gson.fromJson(reader, EvidenceSet.class);
        } catch (IOException e) {
            System.err.println(e);
        }
        return evidenceSet;
    }

    private void writeEvidenceSet(EvidenceSet evidenceSet) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(getEviFp()))) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(evidenceSet, writer);
        } catch (IOException e) {
            System.err.println(e);
        }
    }

    String getEviFp() {
        return dataFp + ".evi";
    }

  private void writePli(PliShard[] pliShards,String dataFp) throws IOException {
      ByteArrayOutputStream buff = new ByteArrayOutputStream();
      ObjectOutputStream out = new ObjectOutputStream(buff);
      out.writeObject(pliShards);
  }

    public PredicateBuilder getPredicateBuilder() {
        return predicateBuilder;
    }

}
