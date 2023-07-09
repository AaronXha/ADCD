package ADCD.inversion;

import java.util.Arrays;
import java.util.Comparator;

public class ArrayIndexComparator implements Comparator<Integer> {

    public enum Order {
        ASCENDING, DESCENDING
    }

    private final Order order;
    public final int[] array;

    public ArrayIndexComparator(int[] counts, Order order) {
        this.order = order;
        this.array = counts;
    }

    public ArrayIndexComparator(int[] counts, boolean ascending) {
        this.order = ascending ? Order.ASCENDING : Order.DESCENDING;
        this.array = counts;
    }

    public Integer[] createIndexArray(boolean sort) {
        Integer[] indexes = new Integer[array.length];
        for (int i = 0; i < array.length; i++) {
            indexes[i] = i;
        }
        if(sort) Arrays.sort(indexes, this);
        return indexes;
    }
   //将谓词空间出现的次数从小到大排序。
    public Integer[] createIndexArray() {
        Integer[] indexes = new Integer[array.length];
        for (int i = 0; i < array.length; i++) {
            indexes[i] = i;
        }
        Arrays.sort(indexes, this);
        return indexes;
    }
    //进行排序
    @Override
    public int compare(Integer index1, Integer index2) {
        switch (order) {
            case ASCENDING:
                return Integer.compare(array[index1], array[index2]);
            case DESCENDING:
                return Integer.compare(array[index2], array[index1]);
        }

        return 0;
    }
}