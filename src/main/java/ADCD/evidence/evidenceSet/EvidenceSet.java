package ADCD.evidence.evidenceSet;

import ch.javasoft.bitset.LongBitSet;
import com.koloboke.collect.map.hash.*;
import de.metanome.algorithms.dcfinder.predicates.Operator;
import de.metanome.algorithms.dcfinder.predicates.Predicate;
import ADCD.predicate.PredicateBuilder;
import de.metanome.algorithms.dcfinder.predicates.sets.PredicateSet;

import java.util.*;
import java.util.stream.Collectors;

/**
 * record evidences and their counts
 */
public class EvidenceSet implements Iterable<Evidence> {
    //原始的cl0
    private LongBitSet cardinalityMask;
    //当clue中的某个位子是1，代表需要修改，通过xor修改原始的cl0，得到正确的结果。
    private LongBitSet[] correctMap;
    //最后的结果
    public HashMap<Long, Evidence> clueToEvidence = new HashMap<>(); // TODO: compare HashMap and HashLongObjMap
    //private Map<Long, Evidence> clueToEvidence = HashLongObjMaps.newMutableMap();

    public EvidenceSet(){}

    public EvidenceSet(PredicateBuilder pBuilder, LongBitSet[] _correctMap) {
        cardinalityMask = buildCardinalityMask(pBuilder);
        correctMap = _correctMap;
    }

    public EvidenceSet(EvidenceSet _evidenceSet){
        cardinalityMask = _evidenceSet.cardinalityMask.clone();
        correctMap = _evidenceSet.correctMap.clone();
        clueToEvidence.putAll(_evidenceSet.clueToEvidence);
    }

    public void build(HashLongLongMap clueSet) {
        for (var entry : clueSet.entrySet()) {
            //clue是满足的谓词 value是这个clue的数量。
            long clue = entry.getKey();
            Evidence evi = new Evidence(clue, entry.getValue(), cardinalityMask, correctMap);
            clueToEvidence.put(clue, evi);
          //  evidenceSet.addValue(evi, count, 0L);
        }

    }

    public int size() {
        return clueToEvidence.size();
    }

    public long getTotalCount() {
        return clueToEvidence.values().stream().mapToLong(e -> e.count).reduce(0L, Long::sum);
    }

    public Collection<Evidence> getEvidences() {
        return clueToEvidence.values();
    }

    public Collection<LongBitSet> getRawEvidences() {
        return clueToEvidence.values().stream().map(e->e.bitset).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        clueToEvidence.forEach((k, v) -> sb.append(k.toString() + "\t" + v + "\n"));
        return sb.toString();
    }

    //初始化cl0
    private LongBitSet buildCardinalityMask(PredicateBuilder predicateBuilder) {
        PredicateSet cardinalityPredicateBitset = new PredicateSet();

        for (Collection<Predicate> predicateGroup : predicateBuilder.getPredicateGroupsCategoricalSingleColumn()) {
            Predicate neq = predicateBuilder.getPredicateByType(predicateGroup, Operator.UNEQUAL);
            cardinalityPredicateBitset.add(neq);
        }

        for (Collection<Predicate> predicateGroup : predicateBuilder.getStrCrossColumnPredicateGroups()) {
            Predicate neq = predicateBuilder.getPredicateByType(predicateGroup, Operator.UNEQUAL);
            cardinalityPredicateBitset.add(neq);
        }

        for (Collection<Predicate> predicateGroup : predicateBuilder.getPredicateGroupsNumericalSingleColumn()) {
            Predicate neq = predicateBuilder.getPredicateByType(predicateGroup, Operator.UNEQUAL);
            Predicate lt = predicateBuilder.getPredicateByType(predicateGroup, Operator.LESS);
            Predicate lte = predicateBuilder.getPredicateByType(predicateGroup, Operator.LESS_EQUAL);
            cardinalityPredicateBitset.add(neq);
            cardinalityPredicateBitset.add(lt);
            cardinalityPredicateBitset.add(lte);
        }

        for (Collection<Predicate> predicateGroup : predicateBuilder.getPredicateGroupsNumericalCrossColumn()) {
            Predicate neq = predicateBuilder.getPredicateByType(predicateGroup, Operator.UNEQUAL);
            Predicate lt = predicateBuilder.getPredicateByType(predicateGroup, Operator.LESS);
            Predicate lte = predicateBuilder.getPredicateByType(predicateGroup, Operator.LESS_EQUAL);
            cardinalityPredicateBitset.add(neq);
            cardinalityPredicateBitset.add(lt);
            cardinalityPredicateBitset.add(lte);
        }

        return cardinalityPredicateBitset.getBitset();
    }

    @Override
    public Iterator<Evidence> iterator() {
        return clueToEvidence.values().iterator();
    }

    public Evidence[] toArray() {
        return clueToEvidence.values().toArray(new Evidence[0]);
    }
}
