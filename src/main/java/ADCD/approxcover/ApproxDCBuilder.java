package ADCD.approxcover;

import ADCD.evidence.evidenceSet.Evidence;
import ADCD.evidence.evidenceSet.EvidenceSet;
import ADCD.predicate.PredicateBuilder;
import ch.javasoft.bitset.LongBitSet;
import de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint;
import de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraintSet;
import de.metanome.algorithms.dcfinder.predicates.sets.PredicateSet;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import static ADCD.CommonHelper.checkDCs;

public class ApproxDCBuilder {

    private final long tuplePairCount;
    private final long minCoverCount;   // min number of evidences that an AC should cover
    private final PredicateBuilder predicateBuilder;
    private EvidenceSet evidenceSet;
    private final ApproxCoverTraverser traverser;

    private DenialConstraintSet DCs;

    public ApproxDCBuilder(long _tuplePairCount, long _minCoverCount, PredicateBuilder builder) {
        tuplePairCount = _tuplePairCount;
        minCoverCount = _minCoverCount;
        traverser = new ApproxCoverTraverser(builder.predicateCount(), minCoverCount, builder.getMutexMap());
        predicateBuilder = builder;
    }

    public DenialConstraintSet buildApproxDCs(EvidenceSet _evidenceSet) {
        evidenceSet = _evidenceSet;
        System.out.println("  [ACS] Searching min covers...");
        long t0 = System.currentTimeMillis();

        List<HyperEdge> edges = new ArrayList<>();
        for (var evi : _evidenceSet.getEvidences())
            edges.add(new HyperEdge(evi.getBitSetPredicates(), evi.getCount()));
        List<LongBitSet> rawMinCovers = traverser.initiate(edges);
        long t1 = System.currentTimeMillis();
        System.out.println("  [ACS] Min cover time : " + (t1 - t0) + "ms");

        return DCs = buildMinDCs(rawMinCovers);
    }

    private DenialConstraintSet buildMinDCs(List<LongBitSet> rawMinCovers) {
        System.out.println("  [ACS] Min cover size: " + rawMinCovers.size());

        DenialConstraintSet dcs = new DenialConstraintSet(predicateBuilder, rawMinCovers);
        System.out.println("  [ACS] Total DC size: " + dcs.size());

        dcs.minimize();
        System.out.println(" [ACS] Min DC size : " + dcs.size());

        return dcs;
    }

    public DenialConstraintSet getDCs() {
        return DCs;
    }

}
