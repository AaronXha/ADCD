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
        String origin = "dataset/test_origin.csv";
        String add = "dataset/test_new.csv";
        String path ="dataset/test.csv";

/*        String origin = "dataset/atom_origin.csv";
        String add = "dataset/atom_new.csv";
        String path="dataset/atom.csv";*/

        DADC dadc = new DADC(true,0.01,350);
        //dadc.build(path);
        //dadc.buildAdd(origin,add);
        dadc.buildAdd(path, 0.2);

        //dadc.buildDelete(path,0.4);
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


    public void buildAdd(String originFile,String newFile) throws ExecutionException, InterruptedException {

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

        long t_evi_addition = t_evi_new + t_evi_cross;

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

        /** 获得cross和new加起来的addition上的dc */
        System.out.println(" [ADCD] additional evidence set size: " + evidenceSetAddition.size());
        System.out.println(" [ADCD] additional evidence count: " + evidenceSetAddition.getTotalCount());
        System.out.println(" [TIME] Build additional evidence time: " + t_evi_addition + "ms");
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

        /** 获得增量后的dc*/
        DenialConstraintSet dcSetNew = approxDynamicEvidence.buildInsert(evidenceSetAll, originDCSet, additionDCSet, leastEvidenceToCover);

        long t_dynamic = System.currentTimeMillis() - t03;
        System.out.println(" [TIME] Dynamic time: " + t_dynamic + "ms");

        long t07 = System.currentTimeMillis();
        ApproxEvidenceInverter approxEvidenceInverter = new ApproxEvidenceInverter(predicateBuilder,true);
        DenialConstraintSet fullDCSet = approxEvidenceInverter.buildDenialConstraints(evidenceSetAll, leastEvidenceToCover);
        long t_aei_full = System.currentTimeMillis() - t07;
        System.out.println(" [TIME] full AEI time: " + t_aei_full + "ms");

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

        Map<LongBitSet, CheckedDC> checkedDemo1 = new HashMap<>();
        for(DenialConstraint dc: dcSetNew){
            if(!fullDCSet.contains(dc)) {
                LongBitSet dcBitSet = dc.getPredicateSet().getBitset();
                checkedDemo1.put(dcBitSet, checkDC(dcBitSet, evidenceSetAll));
                List<Integer> list = new ArrayList<>();
                for (int i = dcBitSet.nextSetBit(0); i >= 0; i = dcBitSet.nextSetBit(i + 1))
                    list.add(i);
                System.out.println(list);
                if(checkedDemo1.get(dcBitSet).hitCount < leastEvidenceToCover)
                    System.out.println("false");
            }
        }
        System.out.println();
        Map<LongBitSet, CheckedDC> checkedDemo2 = new HashMap<>();
        for(DenialConstraint dc: fullDCSet){
            if(!dcSetNew.contains(dc)){
                LongBitSet dcBitSet = dc.getPredicateSet().getBitset();
                checkedDemo2.put(dcBitSet, checkDC(dcBitSet, evidenceSetAll));
                List<Integer> list = new ArrayList<>();
                for (int i = dcBitSet.nextSetBit(0); i >= 0; i = dcBitSet.nextSetBit(i + 1))
                    list.add(i);
                System.out.println(list);
                if(checkedDemo2.get(dcBitSet).hitCount < leastEvidenceToCover)
                    System.out.println("false");
            }
        }
        System.out.println();

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
        dcSet.minimize();
        System.out.println("  [PACS] Min DC size : " + dcSet.size());
        long t_aei = System.currentTimeMillis() - t02;
        System.out.println(" [TIME] AEI time: " + t_aei + "ms");

    }

    public void buildAdd(String dataFp, double scale) throws ExecutionException, InterruptedException {

        System.out.println("INPUT FILE: " + dataFp);
        System.out.println("ERROR THRESHOLD: " + threshold);

        // load input data, build predicate space
        long t00 = System.currentTimeMillis();
        Input input = new Input(new RelationalInput(dataFp), -1);
        PredicateBuilder predicateBuilder = new PredicateBuilder(noCrossColumn, minimumSharedValue, comparableThreshold);
        predicateBuilder.buildPredicateSpace(input);
        System.out.println(" [ADCD] Predicate space size: " + predicateBuilder.predicateCount());

        long rowCount = input.getRowCount();
        int originRowCount = (int)(rowCount * (1 - scale));
        int newRowCount = (int)(rowCount * scale);

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

        long t_evi_addition = t_evi_new + t_evi_cross;

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

        /** 获得cross和new加起来的addition上的dc */
        System.out.println(" [ADCD] additional evidence set size: " + evidenceSetAddition.size());
        System.out.println(" [ADCD] additional evidence count: " + evidenceSetAddition.getTotalCount());
        System.out.println(" [TIME] Build additional evidence time: " + t_evi_addition + "ms");

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

        /** 获得增量后的dc*/
        DenialConstraintSet dcSetNew = approxDynamicEvidence.buildInsertEasy(evidenceSetAll, originDCSet, additionDCSet, leastEvidenceToCover);

        long t_dynamic = System.currentTimeMillis() - t03;
        System.out.println(" [TIME] Dynamic time: " + t_dynamic + "ms");

        ApproxEvidenceInverter approxEvidenceInverter = new ApproxEvidenceInverter(predicateBuilder,true);
        DenialConstraintSet fullDCSet = approxEvidenceInverter.buildDenialConstraints(evidenceSetAll, leastEvidenceToCover);
        fullDCSet.minimize();
        System.out.println("  [PACS] Min DC size : " + fullDCSet.size());

        List<LongBitSet> setOrigin = new ArrayList<>(originDCSet.getBitSetSet());
        List<LongBitSet> setAddition = new ArrayList<>(additionDCSet.getBitSetSet());
        List<LongBitSet> setAll = new ArrayList<>(fullDCSet.getBitSetSet());
        List<LongBitSet> setDynamic = new ArrayList<>(dcSetNew.getBitSetSet());

        setOrigin.sort(Comparator.comparingInt(LongBitSet::cardinality));
        setAddition.sort(Comparator.comparingInt(LongBitSet::cardinality));
        setAll.sort(Comparator.comparingInt(LongBitSet::cardinality));
        setDynamic.sort(Comparator.comparingInt(LongBitSet::cardinality));

        /*for(LongBitSet dc: setOrigin){
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
        }
        System.out.println();*/

        /*Map<LongBitSet, CheckedDC> checkedDemo1 = new HashMap<>();
        for(DenialConstraint dc: dcSetNew){
            if(!fullDCSet.contains(dc)) {
                LongBitSet dcBitSet = dc.getPredicateSet().getBitset();
                checkedDemo1.put(dcBitSet, checkDC(dcBitSet, evidenceSetAll));
                List<Integer> list = new ArrayList<>();
                for (int i = dcBitSet.nextSetBit(0); i >= 0; i = dcBitSet.nextSetBit(i + 1))
                    list.add(i);
                System.out.println(list);
                if(checkedDemo1.get(dcBitSet).hitCount < leastEvidenceToCover)
                    System.out.println("false");
            }
        }
        System.out.println();
        Map<LongBitSet, CheckedDC> checkedDemo2 = new HashMap<>();
        for(DenialConstraint dc: fullDCSet){
            if(!dcSetNew.contains(dc)){
                LongBitSet dcBitSet = dc.getPredicateSet().getBitset();
                checkedDemo2.put(dcBitSet, checkDC(dcBitSet, evidenceSetAll));
                List<Integer> list = new ArrayList<>();
                for (int i = dcBitSet.nextSetBit(0); i >= 0; i = dcBitSet.nextSetBit(i + 1))
                    list.add(i);
                System.out.println(list);
                if(checkedDemo2.get(dcBitSet).hitCount < leastEvidenceToCover)
                    System.out.println("false");
            }
        }*/
        System.out.println();
    }

    public void buildDelete(String dataFp, double scale) throws ExecutionException, InterruptedException {

        System.out.println("INPUT FILE: " + dataFp);
        System.out.println("ERROR THRESHOLD: " + threshold);

        // load input data, build predicate space
        long t00 = System.currentTimeMillis();
        Input input = new Input(new RelationalInput(dataFp), -1);
        PredicateBuilder predicateBuilder = new PredicateBuilder(noCrossColumn, minimumSharedValue, comparableThreshold);
        predicateBuilder.buildPredicateSpace(input);
        System.out.println(" [ADCD] Predicate space size: " + predicateBuilder.predicateCount());

        long rowCount = input.getRowCount();
        int remainRowCount = (int)(rowCount * (1 - scale));
        int deleteRowCount = (int)(rowCount * scale);

        PliShardBuilder pliShardBuilder = new PliShardBuilder(shardLength, input.getParsedColumns());
        PliShard[] pliShards = pliShardBuilder.buildPliShardsDynamic(input.getIntInput(), remainRowCount);

        int originShardsNumber = remainRowCount / shardLength + 1;
        int newShardsNumber = deleteRowCount / shardLength + 1;
        PliShard[] pliOrigin = new PliShard[originShardsNumber];
        PliShard[] pliDelete = new PliShard[newShardsNumber];

        System.arraycopy(pliShards, 0, pliOrigin, 0, originShardsNumber);
        System.arraycopy(pliShards, originShardsNumber , pliDelete, 0, newShardsNumber);

        long t_pre = System.currentTimeMillis() - t00;
        System.out.println(" [TIME] Pre-process time: " + t_pre + "ms");

        long t01 = System.currentTimeMillis();
        EvidenceSetBuilder evidenceSetBuilderOrigin = new EvidenceSetBuilder(predicateBuilder);
        EvidenceSet evidenceSetOrigin = evidenceSetBuilderOrigin.buildEvidenceSet(pliShards);

        System.out.println(" [ADCD] original evidence set size: " + evidenceSetOrigin.size());
        System.out.println(" [ADCD] original evidence count: " + evidenceSetOrigin.getTotalCount());
        long t_evi_origin = System.currentTimeMillis() - t01;
        System.out.println(" [TIME] Build origin evidence time: " + t_evi_origin + "ms");

        long t04 = System.currentTimeMillis();
        EvidenceSetBuilder evidenceSetBuilderDelete = new EvidenceSetBuilder(predicateBuilder);
        EvidenceSet evidenceSetDelete = evidenceSetBuilderDelete.buildEvidenceSet(pliDelete);

        System.out.println(" [ADCD] delete evidence set size: " + evidenceSetDelete.size());
        System.out.println(" [ADCD] delete evidence count: " + evidenceSetDelete.getTotalCount());
        long t_evi_new = System.currentTimeMillis() - t04;
        System.out.println(" [TIME] Build delete evidence time: " + t_evi_new + "ms");

        long t05 = System.currentTimeMillis();
        EvidenceSetBuilder evidenceSetBuilderCross = new EvidenceSetBuilder(predicateBuilder);
        EvidenceSet evidenceSetCross = evidenceSetBuilderCross.buildCrossEvidenceSet(pliOrigin,pliDelete);

        System.out.println(" [ADCD] cross evidence set size: " + evidenceSetCross.size());
        System.out.println(" [ADCD] cross evidence count: " + evidenceSetCross.getTotalCount());
        long t_evi_cross = System.currentTimeMillis() - t05;
        System.out.println(" [TIME] Build cross evidence time: " + t_evi_cross + "ms");

        long t_evi_addition = t_evi_new + t_evi_cross;

        /** 获得origin上的的dc*/
        long t02 = System.currentTimeMillis();
        ApproxEvidenceInverter aei_origin = new ApproxEvidenceInverter(predicateBuilder,true);
        long target_origin = (long)Math.ceil((1 - threshold) * rowCount * (rowCount - 1));
        DenialConstraintSet originDCSet = aei_origin.buildDenialConstraints(evidenceSetOrigin, target_origin);
        long t_aei_origin = System.currentTimeMillis() - t02;
        System.out.println(" [TIME] origin AEI time: " + t_aei_origin + "ms");

        EvidenceSet evidenceSetAddition = new EvidenceSet(evidenceSetCross);
        EvidenceSet evidenceSetRemain = new EvidenceSet(evidenceSetOrigin);

        for(var c: evidenceSetDelete.clueToEvidence.entrySet()){
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
            if(evidenceSetRemain.clueToEvidence.containsKey(clue)){
                Evidence tmp = evidenceSetRemain.clueToEvidence.get(clue);
                tmp.count -= c.getValue().count;
                if(tmp.count > 0)
                    evidenceSetRemain.clueToEvidence.put(clue,tmp);
                else
                    evidenceSetRemain.clueToEvidence.remove(clue);
            }
        }

        /** 获得cross和delete加起来的addition上的dc */
        System.out.println(" [ADCD] additional evidence set size: " + evidenceSetAddition.size());
        System.out.println(" [ADCD] additional evidence count: " + evidenceSetAddition.getTotalCount());
        System.out.println(" [TIME] Build additional evidence time: " + t_evi_addition + "ms");

        long t06 = System.currentTimeMillis();
        ApproxEvidenceInverter aei_addition = new ApproxEvidenceInverter(predicateBuilder,true);
        long target_addition = (long)Math.ceil((1 - threshold) * evidenceSetAddition.getTotalCount());
        DenialConstraintSet additionDCSet = aei_addition.buildDenialConstraints(evidenceSetAddition, target_addition);
        long t_aei_addition = System.currentTimeMillis() - t06;
        System.out.println(" [TIME] addition AEI time: " + t_aei_addition + "ms");

        /** 进行动态发现 */
        long t03 = System.currentTimeMillis();
        ApproxDynamicEvidence approxDynamicEvidence = new ApproxDynamicEvidence(predicateBuilder);
        long leastEvidenceToCover = (long) Math.ceil((1 - threshold) * remainRowCount * (remainRowCount - 1));

        /** 获得减量后的dc*/
        DenialConstraintSet dcSetNew = approxDynamicEvidence.buildDelete(evidenceSetRemain, originDCSet, additionDCSet, leastEvidenceToCover);

        long t_dynamic = System.currentTimeMillis() - t03;
        System.out.println(" [TIME] Dynamic time: " + t_dynamic + "ms");

        ApproxEvidenceInverter approxEvidenceInverter = new ApproxEvidenceInverter(predicateBuilder,true);
        DenialConstraintSet fullDCSet = approxEvidenceInverter.buildDenialConstraints(evidenceSetRemain, leastEvidenceToCover);
        fullDCSet.minimize();
        System.out.println("  [PACS] Min DC size : " + fullDCSet.size());

        List<LongBitSet> setOrigin = new ArrayList<>(originDCSet.getBitSetSet());
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
        }
        System.out.println();

        Map<LongBitSet, CheckedDC> checkedDemo1 = new HashMap<>();
        for(DenialConstraint dc: dcSetNew){
            if(!fullDCSet.contains(dc)) {
                LongBitSet dcBitSet = dc.getPredicateSet().getBitset();
                checkedDemo1.put(dcBitSet, checkDC(dcBitSet, evidenceSetRemain));
                List<Integer> list = new ArrayList<>();
                for (int i = dcBitSet.nextSetBit(0); i >= 0; i = dcBitSet.nextSetBit(i + 1))
                    list.add(i);
                System.out.println(list);
                if(checkedDemo1.get(dcBitSet).hitCount < leastEvidenceToCover){
                    System.out.println("false");
                    if(checkDC(dcBitSet, evidenceSetRemain, leastEvidenceToCover))
                        System.out.println("yes");
                }
            }
        }
        System.out.println();
        Map<LongBitSet, CheckedDC> checkedDemo2 = new HashMap<>();
        for(DenialConstraint dc: fullDCSet){
            if(!dcSetNew.contains(dc)){
                LongBitSet dcBitSet = dc.getPredicateSet().getBitset();
                checkedDemo2.put(dcBitSet, checkDC(dcBitSet, evidenceSetRemain));
                List<Integer> list = new ArrayList<>();
                for (int i = dcBitSet.nextSetBit(0); i >= 0; i = dcBitSet.nextSetBit(i + 1))
                    list.add(i);
                System.out.println(list);
                if(checkedDemo2.get(dcBitSet).hitCount < leastEvidenceToCover)
                    System.out.println("false");
            }
        }
        System.out.println();
    }

    public boolean checkDC(LongBitSet dc, EvidenceSet evidenceList, long target){
            long limit = evidenceList.getTotalCount() - target;
            long unhitCount = 0;
            long hitCount = 0;
            for(Evidence evi: evidenceList) {
                if (dc.isSubSetOf(evi.getBitSetPredicates()))
                    unhitCount += evi.count;
                else
                    hitCount += evi.count;

                if(unhitCount > limit){
                    return false;
                }
                if(hitCount >= target){
                    return true;
                }
            }
        return hitCount >= target;
    }
}
