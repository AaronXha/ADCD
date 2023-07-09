package ADCD.evidence;

import ADCD.evidence.clue.BinaryPliClueSetBuilder;
import ADCD.evidence.clue.ClueSetBuilder;
import ADCD.evidence.clue.UnaryPliClueSetBuilder;
import ADCD.evidence.evidenceSet.EvidenceSet;
import com.koloboke.collect.map.hash.HashLongLongMap;
import com.koloboke.collect.map.hash.HashLongLongMaps;
import ADCD.plishard.PliShard;
import ADCD.predicate.PredicateBuilder;
import net.mintern.primitive.Primitive;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;


public class EvidenceSetBuilder {

    private  EvidenceSet fullEvidenceSet;

    public EvidenceSetBuilder(PredicateBuilder predicateBuilder) {
        ClueSetBuilder.configure(predicateBuilder);
        fullEvidenceSet = new EvidenceSet(predicateBuilder, ClueSetBuilder.getCorrectionMap());
    }

    public EvidenceSet buildEvidenceSet(PliShard[] pliShards) {
        if (pliShards.length != 0) {
            HashLongLongMap clueSet = buildClueSet(pliShards);
            fullEvidenceSet.build(clueSet);
        }
        return fullEvidenceSet;
    }
    public EvidenceSet buildEvidenceSet2(PliShard[] pliShards) {
        if (pliShards.length != 0) {
            HashLongLongMap clueSet = buildClueSet2(pliShards);
            fullEvidenceSet.build(clueSet);
        }
        return fullEvidenceSet;
    }
    public EvidenceSet buildEvidenceSet3(PliShard[] left_pliShards,PliShard[] right_pliShard) throws ExecutionException, InterruptedException {
        if (left_pliShards.length != 0&&right_pliShard.length!=0) {
            HashLongLongMap clueSet = buildClueSet3(left_pliShards,right_pliShard);

            fullEvidenceSet.build(clueSet);
        }
        return fullEvidenceSet;
    }

    private HashLongLongMap buildClueSet(PliShard[] pliShards) {
        int taskCount = (pliShards.length * (pliShards.length+1)) / 2;
        System.out.println("  [CLUE BUILDER] task count: " + taskCount);

        ClueSetTask rootTask = new ClueSetTask(null, pliShards, 0, taskCount);
        return rootTask.invoke();
    }
    private HashLongLongMap buildClueSet2(PliShard[] pliShards) {
        int taskCount = (pliShards.length * (pliShards.length+1)) / 2;
        System.out.println("  [CLUE BUILDER] task count: " + taskCount);

        ClueSetTask rootTask = new ClueSetTask(null, pliShards, 0, taskCount);
        return rootTask.invoke();
    }
    private HashLongLongMap buildClueSet3(PliShard[] left_pliShards,PliShard[] right_pliShards) throws ExecutionException, InterruptedException {
        MyClueSetTask myClueSetTask = new MyClueSetTask(left_pliShards,right_pliShards);
        return myClueSetTask.buildEvidenceSet();
    }

    public EvidenceSet getEvidenceSet() {
        return fullEvidenceSet;
    }

}

class MyClueSetTask {
    List<HashLongLongMap> list = new ArrayList<>();
    CopyOnWriteArrayList<HashLongLongMap> copyOnWriteArrayList = new CopyOnWriteArrayList();
    ExecutorService executorService = Executors.newFixedThreadPool(4);
    CompletionService<HashLongLongMap> completionService = new ExecutorCompletionService<HashLongLongMap>(executorService);
    PliShard[] right_pliShards;
    PliShard[] left_pliShards;
    public MyClueSetTask(PliShard[] left_pliShards,PliShard[] right_pliShards){
        this.left_pliShards = left_pliShards;
        this.right_pliShards = right_pliShards;
    }
    public HashLongLongMap buildEvidenceSet() throws ExecutionException, InterruptedException {
        HashLongLongMap result = HashLongLongMaps.newMutableMap();
        for(int i=0;i<left_pliShards.length;i++){
            for(int j=0;j<right_pliShards.length;j++){
//                Future<HashLongLongMap> f = executorService.submit(new MyThread(i,j,left_pliShards,right_pliShards));
//                copyOnWriteArrayList.add(f.get());
                completionService.submit(new MyThread(i,j,left_pliShards,right_pliShards));
            }
        }
        for(int i=0;i<left_pliShards.length*right_pliShards.length;i++){
            list.add(completionService.take().get());
        }
        for(int i=0;i<list.size();i++){
            for(var e:list.get(i).entrySet()){
                result.addValue(e.getKey(),e.getValue(),0);
            }
        }
        executorService.shutdown();
       return  result;
    }
//    public HashLongLongMap buildEvidenceSet() throws ExecutionException, InterruptedException {
//        HashLongLongMap result = HashLongLongMaps.newMutableMap();
//        for(int i=0;i<left_pliShards.length;i++){
//            for(int j=0;j<right_pliShards.length;j++){
//                copyOnWriteArrayList.add(new BinaryPliClueSetBuilder(left_pliShards[i],right_pliShards[j]).buildClueSet());
//            }
//        }
//       // System.out.println("time"+copyOnWriteArrayList.size());
//        for(int i=0;i<copyOnWriteArrayList.size();i++){
//            //int count = 0;
//            for(var e:copyOnWriteArrayList.get(i).entrySet()){
//                result.addValue(e.getKey(),e.getValue(),0);
//                //count+=e.getValue();
//            }
//            //System.out.println(i+" "+count);
//        }
//        executorService.shutdown();
//        return  result;
//    }

}
class  MyThread implements Callable<HashLongLongMap>{
    int i;
    int j;
    PliShard[] left_pliShards;
    PliShard[] right_pliShards;
    public  MyThread(int i,int j,PliShard[] left_pliShards,PliShard[] right_pliShards){
        this.i = i;
        this.j = j;
        this.left_pliShards = left_pliShards;
        this.right_pliShards = right_pliShards;
    }

    @Override
    public HashLongLongMap call() throws Exception {
       return  new BinaryPliClueSetBuilder(left_pliShards[i],right_pliShards[j]).buildClueSet();
       //return  new UnaryPliClueSetBuilder(pliShards[i]).buildClueSet();
    }
}
class ClueSetTask extends CountedCompleter<HashLongLongMap> {

    private static int[] searchIndexes;

    private static void buildSearchIndex(int count) {
        if (searchIndexes == null || searchIndexes[searchIndexes.length - 1] < count) {
            int n = (int) Math.sqrt(2 * count + 2) + 3;
            searchIndexes = new int[n];
            for (int i = 1; i < n; i++)
                searchIndexes[i] = searchIndexes[i - 1] + i + 1;
        }
    }

    final int taskBeg, taskEnd;
    PliShard[] pliShards;

    ClueSetTask sibling;
    HashLongLongMap partialClueSet;

    public ClueSetTask(ClueSetTask parent, PliShard[] _pliShards, int _beg, int _end) {
        super(parent);
        pliShards = _pliShards;
        taskBeg = _beg;
        taskEnd = _end;
        buildSearchIndex(taskEnd);
    }

    @Override
    public void compute() {
        if (taskEnd - taskBeg >= 2) {
            int mid = (taskBeg + taskEnd) >>> 1;
            ClueSetTask left = new ClueSetTask(this, pliShards, taskBeg, mid);
            ClueSetTask right = new ClueSetTask(this, pliShards, mid, taskEnd);
            left.sibling = right;
            right.sibling = left;
            //设置这个任务的挂起任务数量为1
            this.setPendingCount(1);
            //将右边丢到线程池里面
            right.fork();
            //左边开始递归执行
            if(left!=null)left.compute();
        } else {
            if (taskEnd > taskBeg) {
                ClueSetBuilder builder = getClueSetBuilder(taskBeg);
                if(builder!=null)partialClueSet = builder.buildClueSet();
                else partialClueSet = HashLongLongMaps.newMutableMap();
            }
           //查看是否父任务全部完成  如果全部完成 那么就要触发父任务的oncompletion
            tryComplete();
        }
    }

    private ClueSetBuilder getClueSetBuilder(int taskID) {
        // taskID = i*(i+1)/2 + j
        int i = lowerBound(searchIndexes, taskID);
        int j = i - (searchIndexes[i] - taskID);
//        if(i!=j)return  new BinaryPliClueSetBuilder(pliShards[i],pliShards[j]);
//       else return  null;
      //  if(i==j)System.out.println("chun");
        return i == j ? new UnaryPliClueSetBuilder(pliShards[i]) : new BinaryPliClueSetBuilder(pliShards[i], pliShards[j]);
    }

    // return the index of the first num that's >= target, or nums.length if no such num
    private int lowerBound(int[] nums, int target) {
        int l = 0, r = nums.length;
        while (l < r) {
            int m = l + ((r - l) >>> 1);
            if (nums[m] >= target) r = m;
            else l = m + 1;
        }
        return l;
    }

    @Override
    //将结果进行合并
    public void onCompletion(CountedCompleter<?> caller) {
        if (caller != this) {
            ClueSetTask child = (ClueSetTask) caller;
            ClueSetTask childSibling = child.sibling;

            partialClueSet = child.partialClueSet;
            if (childSibling != null && childSibling.partialClueSet != null) {
                for (var e : childSibling.partialClueSet.entrySet())
                    partialClueSet.addValue(e.getKey(), e.getValue(), 0L);
            }
        }
    }

    @Override
    public HashLongLongMap getRawResult() {
        return partialClueSet == null ? HashLongLongMaps.newMutableMap() : partialClueSet;
    }
}
class ClueSetTask2 extends CountedCompleter<HashLongLongMap> {

    private static int[] searchIndexes;

    private static void buildSearchIndex(int count) {
        if (searchIndexes == null || searchIndexes[searchIndexes.length - 1] < count) {
            int n = (int) Math.sqrt(2 * count + 2) + 3;
            searchIndexes = new int[n];
            for (int i = 1; i < n; i++)
                searchIndexes[i] = searchIndexes[i - 1] + i + 1;
        }
    }

    final int taskBeg, taskEnd;
    PliShard[] pliShards;

    ClueSetTask2 sibling;
    HashLongLongMap partialClueSet;

    public ClueSetTask2(ClueSetTask2 parent, PliShard[] _pliShards, int _beg, int _end) {
        super(parent);
        pliShards = _pliShards;
        taskBeg = _beg;
        taskEnd = _end;
        buildSearchIndex(taskEnd);
    }

    @Override
    public void compute() {
        if (taskEnd - taskBeg >= 2) {
            int mid = (taskBeg + taskEnd) >>> 1;
            ClueSetTask2 left = new ClueSetTask2(this, pliShards, taskBeg, mid);
            ClueSetTask2 right = new ClueSetTask2(this, pliShards, mid, taskEnd);
            left.sibling = right;
            right.sibling = left;
            //设置这个任务的挂起任务数量为1
            this.setPendingCount(1);
            //将右边丢到线程池里面
            right.fork();
            //左边开始递归执行
            if(left!=null)left.compute();
        } else {
            if (taskEnd > taskBeg) {
                ClueSetBuilder builder = getClueSetBuilder(taskBeg);
                if(builder!=null)partialClueSet = builder.buildClueSet();
                else partialClueSet = HashLongLongMaps.newMutableMap();
            }
            //查看是否父任务全部完成  如果全部完成 那么就要触发父任务的oncompletion
            tryComplete();
        }
    }

    private ClueSetBuilder getClueSetBuilder(int taskID) {
        // taskID = i*(i+1)/2 + j
        int i = lowerBound(searchIndexes, taskID);
        int j = i - (searchIndexes[i] - taskID);
        if(i!=j)return  new BinaryPliClueSetBuilder(pliShards[i],pliShards[j]);
       else return  null;
//        return i == j ? new UnaryPliClueSetBuilder(pliShards[i]) : new BinaryPliClueSetBuilder(pliShards[i], pliShards[j]);
    }

    // return the index of the first num that's >= target, or nums.length if no such num
    private int lowerBound(int[] nums, int target) {
        int l = 0, r = nums.length;
        while (l < r) {
            int m = l + ((r - l) >>> 1);
            if (nums[m] >= target) r = m;
            else l = m + 1;
        }
        return l;
    }

    @Override
    //将结果进行合并
    public void onCompletion(CountedCompleter<?> caller) {
        if (caller != this) {
            ClueSetTask child = (ClueSetTask) caller;
            ClueSetTask childSibling = child.sibling;

            partialClueSet = child.partialClueSet;
            if (childSibling != null && childSibling.partialClueSet != null) {
                for (var e : childSibling.partialClueSet.entrySet())
                    partialClueSet.addValue(e.getKey(), e.getValue(), 0L);
            }
        }
    }

    @Override
    public HashLongLongMap getRawResult() {
        return partialClueSet == null ? HashLongLongMaps.newMutableMap() : partialClueSet;
    }
}



