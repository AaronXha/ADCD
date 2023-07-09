package aei;

import ch.javasoft.bitset.LongBitSet;
import graph.HyperEdge;

import java.util.*;

public class ApproxEvidenceInverter {

    private final int nPredicates;
    private HyperEdge[] evidences;

    private LongBitSetTrie approxCovers;    // for subset searching
    private PredicateOrganizer organizer;   // re-order predicates by evidence coverage to accelerate trie

    public ApproxEvidenceInverter(int nVertex) {
        this.nPredicates = nVertex;
        this.approxCovers = null;
        LongBitSetTrie.N = nPredicates;
    }

    public List<LongBitSet> buildHittingSets(List<HyperEdge> evidenceSet, long target) {
        if (target == 0) {
            List<LongBitSet> res = new ArrayList<>();
            res.add(new LongBitSet());
            return res;
        }

        // re-order predicates by evidence coverage to accelerate trie
        organizer = new PredicateOrganizer(nPredicates, evidenceSet);
        evidences = organizer.transformEvidenceSet();

        // inverse
        Arrays.sort(evidences, (o1, o2) -> Long.compare(o2.count, o1.count));
        inverseEvidenceSet(target);

        // collect results
        List<LongBitSet> hittingSets = new ArrayList<>();
        approxCovers.forEach(trans -> hittingSets.add(organizer.retransform(trans.bitSet)));
        System.out.println("  [AEI] Min cover size: " + hittingSets.size());
        return hittingSets;
    }

    void inverseEvidenceSet(long target) {
        System.out.println("  [AEI] Inverting evidences...");

        approxCovers = new LongBitSetTrie();
        LongBitSet fullMask = new LongBitSet(nPredicates);
        for (int i = 0; i < nPredicates; i++)
            fullMask.set(i);

        Stack<SearchNode> nodes = new Stack<>();    // manual stack, where evidences[node.e] needs to be hit
        LongBitSetTrie dcCandidates = new LongBitSetTrie();
        dcCandidates.add(new DCCandidate(new LongBitSet(), fullMask.clone()));

        walk(0, fullMask, dcCandidates, target, nodes, "");

        while (!nodes.isEmpty()) {
            SearchNode nd = nodes.pop();
            if (nd.e >= evidences.length || nd.addablePredicates.isEmpty())
                continue;
            hit(nd);    // hit evidences[e]
            if (nd.target > 0)
                walk(nd.e + 1, nd.addablePredicates, nd.dcCandidates, nd.target, nodes, nd.H);
        }
    }

    void walk(int e, LongBitSet addablePredicates, LongBitSetTrie dcCandidates, long target, Stack<SearchNode> nodes, String status) {
        while (e < evidences.length && !dcCandidates.isEmpty()) {
            LongBitSet evi = evidences[e].bitset;
            Collection<DCCandidate> unhitEviDCs = dcCandidates.getAndRemoveGeneralizations(evi);

            // hit evidences[e] later
            SearchNode nd = new SearchNode(e, addablePredicates.clone(), dcCandidates, unhitEviDCs, target, status + e);
            nodes.push(nd);

            // unhit evidences[e]
            if (unhitEviDCs.isEmpty()) return;

            addablePredicates.and(evi);
            if (addablePredicates.isEmpty()) return;

            long maxCanHit = 0L;
            for (int i = e + 1; i < evidences.length; i++)
                if (!addablePredicates.isSubSetOf(evidences[i].bitset))
                    maxCanHit += evidences[i].count;
            if (maxCanHit < target) return;

            LongBitSetTrie newCandidates = new LongBitSetTrie();
            for (DCCandidate dc : unhitEviDCs) {
                LongBitSet unhitCand = dc.cand.getAnd(evi);
                if (!unhitCand.isEmpty())
                    newCandidates.add(new DCCandidate(dc.bitSet, unhitCand));
                else if (!approxCovers.containsSubset(dc) && isApproxCover(dc.bitSet, e + 1, target))
                    approxCovers.add(dc);
            }
            if (newCandidates.isEmpty()) return;

            e++;
            dcCandidates = newCandidates;
        }
    }

    void hit(SearchNode nd) {
        if (nd.e >= evidences.length || nd.addablePredicates.isSubSetOf(evidences[nd.e].bitset))
            return;

        nd.target -= evidences[nd.e].count;

        LongBitSet evi = evidences[nd.e].bitset;
        LongBitSetTrie dcCandidates = nd.dcCandidates;

        if (nd.target <= 0) {
            dcCandidates.forEach(dc -> approxCovers.add(dc));
            for (DCCandidate invalidDC : nd.invalidDCs) {
                LongBitSet canAdd = invalidDC.cand.getAndNot(evi);
                for (int i = canAdd.nextSetBit(0); i >= 0; i = canAdd.nextSetBit(i + 1)) {
                    DCCandidate validDC = new DCCandidate(invalidDC.bitSet.clone());
                    validDC.bitSet.set(i);
                    if (!approxCovers.containsSubset(validDC))
                        approxCovers.add(validDC);
                }
            }
        } else {
            for (DCCandidate invalidDC : nd.invalidDCs) {
                LongBitSet canAdd = invalidDC.cand.getAndNot(evi);
                for (int i = canAdd.nextSetBit(0); i >= 0; i = canAdd.nextSetBit(i + 1)) {
                    DCCandidate validDC = invalidDC.clone();
                    validDC.bitSet.set(i);
                    if (!dcCandidates.containsSubset(validDC) && !approxCovers.containsSubset(validDC)) {
                        if (!validDC.cand.isEmpty())
                            dcCandidates.add(validDC);
                        else if (isApproxCover(validDC.bitSet, nd.e + 1, nd.target))
                            approxCovers.add(validDC);
                    }
                }
            }
        }
    }

    boolean isApproxCover(LongBitSet dc, int e, long target) {
        if (target <= 0) return true;
        for (; e < evidences.length; e++) {
            if (!dc.isSubSetOf(evidences[e].bitset)) {
                target -= evidences[e].count;
                if (target <= 0) return true;
            }
        }
        return false;
    }


}
