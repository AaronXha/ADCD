package ADCD.plishard;

import com.koloboke.collect.set.hash.HashIntSet;
import com.koloboke.collect.set.hash.HashIntSets;
import de.metanome.algorithms.dcfinder.input.ParsedColumn;
import net.mintern.primitive.Primitive;

import java.util.*;
import java.util.stream.IntStream;


public class PliShardBuilder {

    private final int shardLength;
    private final boolean[] isNum;//得到不是number的个数

    public PliShardBuilder(int _shardLength, List<ParsedColumn<?>> pColumns) {
        shardLength = _shardLength;
        int colCount = pColumns.size();

        isNum = new boolean[colCount];
        for (int col = 0; col < colCount; col++)
            isNum[col] = pColumns.get(col).getType() != String.class;
    }

    // build the Pli of a given column within tuple id range [beg, end)
    private Pli buildPli(boolean isNum, int[] colValues, int beg, int end) {

        HashIntSet keySet = HashIntSets.newMutableSet();
        for (int row = beg; row < end; ++row)
            keySet.add(colValues[row]);

        int[] keys = keySet.toIntArray();
        if (isNum)
            Primitive.sort(keys, (a, b) -> Integer.compare(b, a), false);

        Map<Integer, Integer> keyToClusterID = new HashMap<>(); // int (key) -> cluster id
        for (int clusterID = 0; clusterID < keys.length; clusterID++)
            keyToClusterID.put(keys[clusterID], clusterID);

        List<Cluster> clusters = new ArrayList<>();             // put tuple indexes in clusters
        for (int i = 0; i < keys.length; i++)
            clusters.add(new Cluster());
        for (int row = beg; row < end; ++row)
            clusters.get(keyToClusterID.get(colValues[row])).add(row);

        return new Pli(clusters, keys, keyToClusterID);
    }

    private Pli buildPli(boolean isNum, int[] colValues, int beg, int end, int start) {

        HashIntSet keySet = HashIntSets.newMutableSet();
        for (int row = beg; row < end; ++row)
            keySet.add(colValues[row]);

        int[] keys = keySet.toIntArray();
        if (isNum)
            Primitive.sort(keys, (a, b) -> Integer.compare(b, a), false);

        Map<Integer, Integer> keyToClusterID = new HashMap<>(); // int (key) -> cluster id
        for (int clusterID = 0; clusterID < keys.length; clusterID++)
            keyToClusterID.put(keys[clusterID], clusterID);

        List<Cluster> clusters = new ArrayList<>();             // put tuple indexes in clusters
        for (int i = 0; i < keys.length; i++)
            clusters.add(new Cluster());
        for (int row = beg+start; row < end+start; ++row)
            clusters.get(keyToClusterID.get(colValues[row-start])).add(row);

        return new Pli(clusters, keys, keyToClusterID);
    }

    public PliShard[] buildPliShards(int[][] intInput) {
        if (intInput == null || intInput.length == 0 || intInput[0].length == 0)
            return new PliShard[0];

        int rowBeg = 0, rowEnd = intInput[0].length;
        int nShards = (rowEnd - rowBeg - 1) / shardLength + 1;
        PliShard[] pliShards = new PliShard[nShards];

        for(int i = 0; i < nShards; i++) {
            int shardBeg = rowBeg + i * shardLength, shardEnd = Math.min(rowEnd, shardBeg + shardLength);
            List<Pli> plis = new ArrayList<>();
            for (int col = 0; col < intInput.length; col++)
                plis.add(buildPli(isNum[col], intInput[col], shardBeg, shardEnd));
            pliShards[i] = new PliShard(plis, shardBeg, shardEnd);
        }

//        IntStream.range(0, nShards).forEach(i -> {
//            int shardBeg = rowBeg + i * shardLength, shardEnd = Math.min(rowEnd, shardBeg + shardLength);
//            List<Pli> plis = new ArrayList<>();
//            for (int col = 0; col < intInput.length; col++)
//                plis.add(buildPli(isNum[col], intInput[col], shardBeg, shardEnd));
//            pliShards[i] = new PliShard(plis, shardBeg, shardEnd);
//        });

        return pliShards;
    }

    public PliShard[] buildPliShardsDynamic(int[][] intInput, int demarc) {
        if (intInput == null || intInput.length == 0 || intInput[0].length == 0)
            return new PliShard[0];

        int rowBeg = 0, rowEnd = intInput[0].length;
        int originShardsNumber = demarc / shardLength + 1;
        int newShardsNumber = (rowEnd - demarc) / shardLength + 1;
        PliShard[] pliShards = new PliShard[originShardsNumber + newShardsNumber];

        for(int i = 0; i < originShardsNumber; i++){
            int shardBeg = rowBeg + i * shardLength, shardEnd = Math.min(demarc, shardBeg + shardLength);
            List<Pli> plis = new ArrayList<>();

            for (int col = 0; col < intInput.length; col++)
                plis.add(buildPli(isNum[col], intInput[col], shardBeg, shardEnd));
            pliShards[i] = new PliShard(plis, shardBeg, shardEnd);
        }

        for(int j = 0; j < newShardsNumber; j++){
            int shardBeg = demarc + j * shardLength, shardEnd = Math.min(rowEnd, shardBeg + shardLength);
            List<Pli> plis = new ArrayList<>();

            for (int col = 0; col < intInput.length; col++)
                plis.add(buildPli(isNum[col], intInput[col], shardBeg, shardEnd));
            pliShards[j + originShardsNumber] = new PliShard(plis, shardBeg, shardEnd);
        }
        return pliShards;
    }

    public PliShard[] buildPliShards(int[][] intInput,int number) {
        if (intInput == null || intInput.length == 0 || intInput[0].length == 0)
            return new PliShard[0];

        int rowBeg = 0, rowEnd = intInput[0].length;
        int nShards2 = (number + shardLength - 1) / shardLength;
        int nShards = (rowEnd - number - 1) / shardLength + 1;
        PliShard[] pliShards = new PliShard[nShards + nShards2];
        for(int i = 0; i < nShards; i++){
            int shardBeg = rowBeg + i * shardLength, shardEnd = Math.min(rowEnd - number,shardBeg + shardLength);
            List<Pli> plis = new ArrayList<>();
            for (int col = 0; col < intInput.length; col++)
                plis.add(buildPli(isNum[col], intInput[col], shardBeg, shardEnd));
            pliShards[i] = new PliShard(plis, shardBeg, shardEnd);
        }
        int count = 0;
        for(int i = 0; i < nShards2; i++) {
            int shardBeg = rowEnd - number + i * shardLength, shardEnd = Math.min(rowEnd, shardBeg + shardLength);
            count += shardEnd - shardBeg;
            List<Pli> plis = new ArrayList<>();
            for (int col = 0; col < intInput.length; col++)
                plis.add(buildPli(isNum[col], intInput[col], shardBeg, shardEnd));
            pliShards[i + nShards] = new PliShard(plis, shardBeg, shardEnd);
        }

//        IntStream.range(0, nShards).forEach(i -> {
//            int shardBeg = rowBeg + i * shardLength, shardEnd = Math.min(rowEnd, shardBeg + shardLength);
//            List<Pli> plis = new ArrayList<>();
//            for (int col = 0; col < intInput.length; col++)
//                plis.add(buildPli(isNum[col], intInput[col], shardBeg, shardEnd));
//            pliShards[i] = new PliShard(plis, shardBeg, shardEnd);
//        });
      // System.out.println("一共多少 "+count);
        return pliShards;
    }

    public PliShard[] buildPliShards(int[][] intInput, int start, int end) {
        if (intInput == null || intInput.length == 0 || intInput[0].length == 0)
            return new PliShard[0];

        int rowBeg = 0, rowEnd = intInput[0].length;
        int nShards = (rowEnd - rowBeg - 1) / shardLength + 1;
        PliShard[] pliShards = new PliShard[nShards];

        for(int i = 0; i < nShards; i++) {
            int shardBeg = rowBeg + i * shardLength, shardEnd = Math.min(rowEnd, shardBeg + shardLength);
            List<Pli> plis = new ArrayList<>();
            for (int col = 0; col < intInput.length; col++)
                plis.add(buildPli(isNum[col], intInput[col], shardBeg, shardEnd,start + end));
            pliShards[i] = new PliShard(plis, shardBeg + start + end, shardEnd + start + end);
        }
        return pliShards;
    }

}
