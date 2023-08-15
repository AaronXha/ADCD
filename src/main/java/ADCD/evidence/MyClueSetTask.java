package ADCD.evidence;

import ADCD.plishard.PliShard;
import com.koloboke.collect.map.hash.HashLongLongMap;
import com.koloboke.collect.map.hash.HashLongLongMaps;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class MyClueSetTask {
    List<HashLongLongMap> list = new ArrayList<>();
    CopyOnWriteArrayList<HashLongLongMap> copyOnWriteArrayList = new CopyOnWriteArrayList();
    ExecutorService executorService = Executors.newFixedThreadPool(4);
    CompletionService<HashLongLongMap> completionService = new ExecutorCompletionService<HashLongLongMap>(executorService);
    PliShard[] right_pliShards;
    PliShard[] left_pliShards;

    public MyClueSetTask(PliShard[] left_pliShards, PliShard[] right_pliShards) {
        this.left_pliShards = left_pliShards;
        this.right_pliShards = right_pliShards;
    }

    public HashLongLongMap buildEvidenceSet() throws ExecutionException, InterruptedException {
        HashLongLongMap result = HashLongLongMaps.newMutableMap();
        for (int i = 0; i < left_pliShards.length; i++) {
            for (int j = 0; j < right_pliShards.length; j++) {

                completionService.submit(new MyThread(i, j, left_pliShards, right_pliShards));
            }
        }
        for (int i = 0; i < left_pliShards.length * right_pliShards.length; i++) {
            list.add(completionService.take().get());
        }
        for (int i = 0; i < list.size(); i++) {
            for (var e : list.get(i).entrySet()) {
                result.addValue(e.getKey(), e.getValue(), 0);
            }
        }
        executorService.shutdown();
        return result;
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
