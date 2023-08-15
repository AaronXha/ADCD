package ADCD.evidence;

import ADCD.evidence.clue.BinaryPliClueSetBuilder;
import ADCD.evidence.clue.ClueSetBuilder;
import ADCD.evidence.clue.UnaryPliClueSetBuilder;
import ADCD.plishard.PliShard;
import com.koloboke.collect.map.hash.HashLongLongMap;
import com.koloboke.collect.map.hash.HashLongLongMaps;

import java.util.concurrent.CountedCompleter;

public class CrossClueSetTask extends CountedCompleter<HashLongLongMap> {
    final int taskBeg, taskEnd;
    PliShard[] pliShardsLeft;
    PliShard[] pliShardsRight;
    CrossClueSetTask sibling;
    HashLongLongMap partialClueSet;
    public CrossClueSetTask(CrossClueSetTask parent, PliShard[] _pliShardsLeft, PliShard[] _pliShardsRight, int _beg, int _end){
        super(parent);
        pliShardsLeft = _pliShardsLeft;
        pliShardsRight = _pliShardsRight;
        taskBeg = _beg;
        taskEnd = _end;
    }
    @Override
    public void compute() {
        if (taskEnd - taskBeg >= 2) {
            int mid = (taskBeg + taskEnd) >>> 1;
            CrossClueSetTask left = new CrossClueSetTask(this, pliShardsLeft, pliShardsRight, taskBeg, mid);
            CrossClueSetTask right = new CrossClueSetTask(this, pliShardsLeft, pliShardsRight, mid, taskEnd);
            left.sibling = right;
            right.sibling = left;
            //设置这个任务的挂起任务数量为1
            this.setPendingCount(1);
            //将右边丢到线程池里面
            right.fork();
            //左边开始递归执行
            if (left != null)
                left.compute();
        } else {
            if (taskEnd > taskBeg) {
                ClueSetBuilder builder = getClueSetBuilder(taskBeg);
                partialClueSet = builder.buildClueSet();
            }
            //查看是否父任务全部完成  如果全部完成 那么就要触发父任务的oncompletion
            tryComplete();
        }
    }

    private ClueSetBuilder getClueSetBuilder(int taskID) {
        int i = taskID % pliShardsLeft.length;    //pliLeftID
        int j = taskID / pliShardsLeft.length;    //pliRightID

        return new BinaryPliClueSetBuilder(pliShardsLeft[i], pliShardsRight[j]);
    }

    @Override
    //将结果进行合并
    public void onCompletion(CountedCompleter<?> caller) {
        if (caller != this) {
            CrossClueSetTask child = (CrossClueSetTask) caller;
            CrossClueSetTask childSibling = child.sibling;

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
