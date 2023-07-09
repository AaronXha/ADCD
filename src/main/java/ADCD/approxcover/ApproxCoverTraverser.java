package ADCD.approxcover;

import ch.javasoft.bitset.LongBitSet;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountedCompleter;
import java.util.stream.Collectors;


class ApproxCoverTraverser {

    private final long minCoverTarget;

    private List<HyperEdge> edges;

    private Collection<ApproxCoverNode> ApproxCoverNodes;
    private Collection<LongBitSet> ApproxCovers;

    ApproxCoverTraverser(int nEle, long minCover, LongBitSet[] mutexMap) {
        minCoverTarget = minCover;
        ApproxCoverNode.configure(nEle, mutexMap);
    }

    public List<LongBitSet> initiate(List<HyperEdge> _edges) {
        edges = new ArrayList<>(_edges);
        ApproxCoverNodes = walkDownFromRoot();
        return getNontrivialMinCovers();
    }

    private Collection<ApproxCoverNode> walkDownFromRoot() {
        ApproxCoverNode rootNode = new ApproxCoverNode(minCoverTarget, edges);

        /* parallel */
//        Queue<ApproxCoverNode> newCoverNodes = new ConcurrentLinkedQueue<>();
//        InitiateTask rootTask = new InitiateTask(null, rootNode, newCoverNodes);
//        rootTask.invoke();

        /* single thread */
        List<ApproxCoverNode> newCoverNodes = new ArrayList<>();
        walkDown(newCoverNodes, rootNode);

        return newCoverNodes;
    }

    private void walkDown(List<ApproxCoverNode> newCoverNodes, ApproxCoverNode nd) {
        if (nd.isApproxCover() && nd.isMinimal()) {
            nd.addTo(newCoverNodes);
            return;
        }
        if (nd.canHitUncov.isEmpty()) return;

        HyperEdge edgeF = nd.chooseAnCanHitUncovEdge();

        // not hit edgeF
        ApproxCoverNode child1 = nd.getUnhitChild(edgeF);
        if (child1 != null) {
            walkDown(newCoverNodes, child1);
        }

        // hit edgeF
        LongBitSet verticesToAdd = nd.nontrivialCand.getAnd(edgeF.vertices);
        LongBitSet child2Cand = nd.nontrivialCand.getAndNot(verticesToAdd);

        for (int addV = verticesToAdd.nextSetBit(0); addV >= 0; addV = verticesToAdd.nextSetBit(addV + 1)) {
            ApproxCoverNode child2 = nd.getHitChild(addV, child2Cand);
            if (child2 != null) {    // child2 is null if it's not minimal
                walkDown(newCoverNodes, child2);
                child2Cand.set(addV);
            }
        }
    }

//    private void walkDownBFS(List<ApproxCoverNode> newCoverNodes, ApproxCoverNode nd) {
//        Set<LongBitSet> walked = new HashSet<>();
//        Queue<ApproxCoverNode> nodes = new ArrayDeque<>();
//        nodes.add(nd);
//        walked.add(nd.vertices);
//
//        while (!nodes.isEmpty()) {
//            for (int i = nodes.size(); i > 0; --i) {
//                ApproxCoverNode currNode = nodes.poll();
//
//                if (currNode.isApproxCover() && currNode.isMinimal()) {
//                    currNode.addTo(newCoverNodes);
//                    continue;
//                }
//                if (currNode.canHitUncov.isEmpty()) continue;
//
//                HyperEdge edgeF = currNode.chooseAnCanHitUncovEdge();
//
//                // not hit edgeF
//                ApproxCoverNode child1 = currNode.getUnhitChild(edgeF);
//                if (child1 != null) {
//                    nodes.add(child1);
//                }
//
//                // hit edgeF
//                LongBitSet verticesToAdd = currNode.nontrivialCand.getAnd(edgeF.vertices);
//                LongBitSet child2Cand = currNode.nontrivialCand.getAndNot(verticesToAdd);
//
//                for (int addV = verticesToAdd.nextSetBit(0); addV >= 0; addV = verticesToAdd.nextSetBit(addV + 1)) {
//                    ApproxCoverNode child2 = currNode.getHitChild(addV, child2Cand);
//                    if (child2 != null) {    // child2 is null if it's not minimal
//                        nodes.add(child2);
//                        if (!walked.add(child2.vertices)) System.out.println("Walked!");
//                        child2Cand.set(addV);
//                    }
//                }
//            }
//        }
//    }

    public List<LongBitSet> getNontrivialMinCovers() {
        return ApproxCoverNodes.stream().map(ApproxCoverNode::getVertices).collect(Collectors.toList());
    }

}

class InitiateTask extends CountedCompleter<Void> {

    ApproxCoverNode nd;
    Queue<ApproxCoverNode> newCoverNodes;

    public InitiateTask(InitiateTask parent, ApproxCoverNode nd, Queue<ApproxCoverNode> newCoverNodes) {
        super(parent);
        this.nd = nd;
        this.newCoverNodes = newCoverNodes;
    }

    @Override
    public void compute() {
        if (nd.isApproxCover() && nd.isMinimal()) {
            nd.addTo(newCoverNodes);
            propagateCompletion();
            return;
        }

        if (nd.canHitUncov.isEmpty()) {
            propagateCompletion();
            return;
        }

        HyperEdge edgeF = nd.chooseAnCanHitUncovEdge();

        // not hit edgeF
        ApproxCoverNode child1 = nd.getUnhitChild(edgeF);
        if (child1 != null) {
            addToPendingCount(1);
            new InitiateTask(this, child1, newCoverNodes).fork();
        }

        // hit edgeF
        LongBitSet verticesToAdd = nd.nontrivialCand.getAnd(edgeF.vertices);
        LongBitSet child2Cand = nd.nontrivialCand.getAndNot(verticesToAdd);

        int i = verticesToAdd.nextSetBit(0);
        ApproxCoverNode firstChildNode = null;
        if (i >= 0) {
            for (int v = verticesToAdd.nextSetBit(i + 1); v >= 0; v = verticesToAdd.nextSetBit(v + 1)) {
                ApproxCoverNode childNode = nd.getHitChild(v, child2Cand);   // non-first child nodes
                if (childNode != null) {    // childNode is null if it's not minimal
                    addToPendingCount(1);
                    new InitiateTask(this, childNode, newCoverNodes).fork();
                    child2Cand.set(v);
                }
            }

            firstChildNode = nd.getHitChild(i, child2Cand);           // first child node
            nd = null;
            if (firstChildNode != null)
                new InitiateTask(this, firstChildNode, newCoverNodes).compute();
        }

        if (firstChildNode == null) {
            propagateCompletion();
        }
    }
}