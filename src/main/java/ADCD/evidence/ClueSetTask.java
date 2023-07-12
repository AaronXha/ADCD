package ADCD.evidence;

import ADCD.evidence.clue.BinaryPliClueSetBuilder;
import ADCD.evidence.clue.ClueSetBuilder;
import ADCD.evidence.clue.UnaryPliClueSetBuilder;
import ADCD.plishard.PliShard;
import com.koloboke.collect.map.hash.HashLongLongMap;
import com.koloboke.collect.map.hash.HashLongLongMaps;

import java.util.concurrent.CountedCompleter;

public class ClueSetTask extends CountedCompleter<HashLongLongMap> {
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
            if (left != null)
                left.compute();
        } else {
            if (taskEnd > taskBeg) {
                ClueSetBuilder builder = getClueSetBuilder(taskBeg);
                if (builder != null)
                    partialClueSet = builder.buildClueSet();
                else partialClueSet = HashLongLongMaps.newMutableMap();
            }
            //查看是否父任务全部完成  如果全部完成 那么就要触发父任务的oncompletion
            tryComplete();
        }
    }

    private ClueSetBuilder getClueSetBuilder(int taskID) {
        // taskID = i * (i + 1) / 2 + j
        int i = lowerBound(searchIndexes, taskID);
        int j = i - (searchIndexes[i] - taskID);

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
