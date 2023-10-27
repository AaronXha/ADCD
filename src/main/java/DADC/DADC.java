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

import java.util.*;
import java.util.concurrent.ExecutionException;

public class DADC {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
/*        String origin = "dataset/airport_10000_red.csv";
        String add = "dataset/airport_10000.csv";
        String path ="dataset/airport.csv";*/

        String origin = "dataset/test_origin.csv";
        String add = "dataset/test_new.csv";
        String path="dataset/Tax.csv";

        DADC dadc = new DADC(true,0.01,350);
        //dadc.build(path);
        dadc.build(origin,add);
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

    public void build(String dataFp){

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
        DenialConstraintSet dcSet = aei.buildDenialConstraints(evidenceSet, target);
        long t_aei = System.currentTimeMillis() - t02;
        System.out.println(" [TIME] AEI time: " + t_aei + "ms");

    }

    public void build(String originFile,String newFile) throws ExecutionException, InterruptedException {

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
        System.out.println(" [ADCD] original evidence count: " + evidenceSetOrigin.getTotalCount());
        long t_evi_origin = System.currentTimeMillis() - t01;
        System.out.println(" [TIME] Build origin evidence time: " + t_evi_origin + "ms");

        long t04 = System.currentTimeMillis();
        EvidenceSetBuilder evidenceSetBuilderNew = new EvidenceSetBuilder(predicateBuilder);
        EvidenceSet evidenceSetNew = evidenceSetBuilderNew.buildEvidenceSet(pliNew);

        System.out.println(" [ADCD] new evidence set size: " + evidenceSetNew.size());
        System.out.println(" [ADCD] new evidence count: " + evidenceSetNew.getTotalCount());
        long t_evi_new = System.currentTimeMillis() - t04;
        System.out.println(" [TIME] Build new evidence time: " + t_evi_new + "ms");

        long t05 = System.currentTimeMillis();
        EvidenceSetBuilder evidenceSetBuilderCross = new EvidenceSetBuilder(predicateBuilder);
        EvidenceSet evidenceSetCross = evidenceSetBuilderCross.buildCrossEvidenceSet(pliOrigin,pliNew);

        System.out.println(" [ADCD] cross evidence set size: " + evidenceSetCross.size());
        System.out.println(" [ADCD] cross evidence count: " + evidenceSetCross.getTotalCount());
        long t_evi_cross = System.currentTimeMillis() - t05;
        System.out.println(" [TIME] Build cross evidence time: " + t_evi_cross + "ms");

        long t_evi = System.currentTimeMillis() - t01;

        /** 获得origin上的的dc*/
        long t02 = System.currentTimeMillis();
        ApproxEvidenceInverter aei_origin = new ApproxEvidenceInverter(predicateBuilder,true);
        long target_origin = (long)Math.ceil((1 - threshold) * originRowCount * (originRowCount - 1));
        DenialConstraintSet originDCSet = aei_origin.buildDenialConstraints(evidenceSetOrigin, target_origin);
        long t_aei_origin = System.currentTimeMillis() - t02;
        System.out.println(" [TIME] origin AEI time: " + t_aei_origin + "ms");

        EvidenceSet evidenceSetAddition = new EvidenceSet(evidenceSetCross);
        EvidenceSet evidenceSetAll = new EvidenceSet(evidenceSetOrigin);

        for(var c: evidenceSetNew.clueToEvidence.entrySet()){
            long clue = c.getKey();
            if(evidenceSetAddition.clueToEvidence.containsKey(clue)){
                Evidence tmp = evidenceSetAddition.clueToEvidence.get(clue);
                tmp.count += c.getValue().count;
                evidenceSetAddition.clueToEvidence.put(clue,tmp);
            }
            else{
                evidenceSetAddition.clueToEvidence.put(clue,c.getValue());
            }
        }

        for(var c: evidenceSetAddition.clueToEvidence.entrySet()){
            long clue = c.getKey();
            if(evidenceSetAll.clueToEvidence.containsKey(clue)){
                Evidence tmp = evidenceSetAll.clueToEvidence.get(clue);
                tmp.count += c.getValue().count;
                evidenceSetAll.clueToEvidence.put(clue,tmp);
            }
            else{
                evidenceSetAll.clueToEvidence.put(clue,c.getValue());
            }
        }

        System.out.println(" [ADCD] total evidence set size: " + evidenceSetAll.size());
        System.out.println(" [ADCD] total evidence count: " + evidenceSetAll.getTotalCount());
        System.out.println(" [TIME] Build total evidence time: " + t_evi + "ms");


        /** 获得cross和new加起来的addition上的dc */
        System.out.println(" [ADCD] additional evidence set size: " + evidenceSetAddition.size());
        System.out.println(" [ADCD] additional evidence count: " + evidenceSetAddition.getTotalCount());
        long t06 = System.currentTimeMillis();
        ApproxEvidenceInverter aei_addition = new ApproxEvidenceInverter(predicateBuilder,true);
        long target_addition = (long)Math.ceil((1 - threshold) * evidenceSetAddition.getTotalCount());
        DenialConstraintSet additionDCSet = aei_addition.buildDenialConstraints(evidenceSetAddition, target_addition);
        long t_aei_addition = System.currentTimeMillis() - t06;
        System.out.println(" [TIME] addition AEI time: " + t_aei_addition + "ms");

        /** 进行动态发现 */
        long t03 = System.currentTimeMillis();
        ApproxDynamicEvidence approxDynamicEvidence = new ApproxDynamicEvidence(predicateBuilder);
        long leastEvidenceToCover = (long) Math.ceil((1 - threshold) * input.getRowCount() * (input.getRowCount() - 1));

/*        ApproxEvidenceInverter approxEvidenceInverter = new ApproxEvidenceInverter(predicateBuilder,true);
        DenialConstraintSet fullDCSet = approxEvidenceInverter.buildDenialConstraints(evidenceSetAll, leastEvidenceToCover);*/

        /** 获得增量后的dc*/
        DenialConstraintSet dcSetNew = approxDynamicEvidence.buildInsert(evidenceSetAll, originDCSet, additionDCSet, leastEvidenceToCover);

        long t_dynamic = System.currentTimeMillis() - t03;
        System.out.println(" [TIME] Dynamic time: " + t_dynamic + "ms");

/*        List<LongBitSet> setOrigin = new ArrayList<>(originDCSet.getBitSetSet());
        List<LongBitSet> setAddition = new ArrayList<>(additionDCSet.getBitSetSet());
        List<LongBitSet> setAll = new ArrayList<>(fullDCSet.getBitSetSet());
        List<LongBitSet> setDynamic = new ArrayList<>(dcSetNew.getBitSetSet());

        setOrigin.sort(Comparator.comparingInt(LongBitSet::cardinality));
        setAddition.sort(Comparator.comparingInt(LongBitSet::cardinality));
        setAll.sort(Comparator.comparingInt(LongBitSet::cardinality));
        setDynamic.sort(Comparator.comparingInt(LongBitSet::cardinality));

        for(LongBitSet dc: setOrigin){
            //System.out.println(dc);
            List<Integer> list = new ArrayList<>();
            for (int i = dc.nextSetBit(0); i >= 0; i = dc.nextSetBit(i + 1))
                list.add(i);
            System.out.println(list);
        }
        System.out.println();
        for(LongBitSet dc: setAddition){
            //System.out.println(dc);
            List<Integer> list = new ArrayList<>();
            for (int i = dc.nextSetBit(0); i >= 0; i = dc.nextSetBit(i + 1))
                list.add(i);
            System.out.println(list);
        }
        System.out.println();
        for(LongBitSet dc: setAll){
            //System.out.println(dc);
            List<Integer> list = new ArrayList<>();
            for (int i = dc.nextSetBit(0); i >= 0; i = dc.nextSetBit(i + 1))
                list.add(i);
            System.out.println(list);
        }
        System.out.println();
        for(LongBitSet dc: setDynamic){
            //System.out.println(dc);
            List<Integer> list = new ArrayList<>();
            for (int i = dc.nextSetBit(0); i >= 0; i = dc.nextSetBit(i + 1))
                list.add(i);
            System.out.println(list);
        }*/

/*        Map<LongBitSet, CheckedDC> checkedDemo1 = new HashMap<>();
        for(DenialConstraint dc: dcSetNew){
            if(!fullDCSet.contains(dc)) {
                LongBitSet dcBitSet = dc.getPredicateSet().getBitset();
                checkedDemo1.put(dcBitSet, checkDC(dcBitSet, evidenceSetAll));
                System.out.println(dcBitSet);
            }
        }
        System.out.println();
        Map<LongBitSet, CheckedDC> checkedDemo2 = new HashMap<>();
        for(DenialConstraint dc: fullDCSet){
            if(!dcSetNew.contains(dc)){
                LongBitSet dcBitSet = dc.getPredicateSet().getBitset();
                checkedDemo2.put(dcBitSet, checkDC(dcBitSet, evidenceSetAll));
                System.out.println(dc.getPredicateSet().getBitset());
            }
        }
        System.out.println();*/

    }

    public CheckedDC checkDC(LongBitSet dcLongbitset, EvidenceSet evidenceSet){
        long unhitCount = 0;
        long hitCount = 0;

        for(Evidence set: evidenceSet){
            if(dcLongbitset.isSubSetOf(set.getBitSetPredicates()))
                unhitCount += set.count;
            else
                hitCount += set.count;

        }
        return new CheckedDC(unhitCount,hitCount);
    }
}
