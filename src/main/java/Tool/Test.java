package Tool;

import com.koloboke.collect.map.hash.HashIntIntMap;
import com.koloboke.collect.map.hash.HashIntIntMaps;
import com.koloboke.collect.map.hash.HashLongLongMap;
import com.koloboke.collect.map.hash.HashLongLongMaps;

import java.util.concurrent.*;

public class Test {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CopyOnWriteArrayList<Integer> list = new CopyOnWriteArrayList<>();
        CompletionService<Integer> completionService = new ExecutorCompletionService<Integer>(
                executorService);
        for (int i = 0; i < 10; i++) {
            completionService.submit(new MyThread(i));

        }
        for(int i=0;i<10;i++){
           list.add(completionService.take().get());
        }


    }
}
class MyThread implements Callable<Integer> {
    int i;
    public MyThread(int i){
        this.i = i;
    }

    @Override
    public Integer call() throws Exception {
        int count =0;
        while (count<5){
            System.out.println(i);
            count++;
            Thread.sleep(1000);
        }
        return i;
    }
}
