package ADCD.predicate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ch.javasoft.bitset.LongBitSet;
import de.metanome.algorithms.dcfinder.helpers.IndexProvider;
import de.metanome.algorithms.dcfinder.predicates.ColumnPair;
import de.metanome.algorithms.dcfinder.input.InputOld;
import de.metanome.algorithms.dcfinder.input.ParsedColumn;
import de.metanome.algorithms.dcfinder.predicates.Operator;
import de.metanome.algorithms.dcfinder.predicates.Predicate;
import de.metanome.algorithms.dcfinder.predicates.PredicateProvider;
import de.metanome.algorithms.dcfinder.predicates.operands.ColumnOperand;
import de.metanome.algorithms.dcfinder.predicates.sets.Closure;
import de.metanome.algorithms.dcfinder.predicates.sets.PredicateSet;

public class PredicateBuilder {

    private final boolean noCrossColumn;
    private final double comparableThreshold;
    private final double minimumSharedValue;

    private final List<Predicate> predicates;
    private final List<List<Predicate>> predicateGroups;    // [i]: predicates of i-th Column Pair

    List<List<Predicate>> numSingleColumnPredicateGroups;
    List<List<Predicate>> numCrossColumnPredicateGroups;
    List<List<Predicate>> strSingleColumnPredicateGroups;
    List<List<Predicate>> strCrossColumnPredicateGroups;

    public int count = 0;

    private final PredicateProvider predicateProvider;
    private final IndexProvider<Predicate> predicateIdProvider;
    private LongBitSet[] mutexMap;   // i -> indices of predicates from the same column pair with predicate i
    private int[] inverseMap;        // i -> index of predicate having inverse op to predicate i

    public PredicateBuilder(boolean _noCrossColumn, double _minimumSharedValue, double _comparableThreshold) {
        noCrossColumn = _noCrossColumn;
        minimumSharedValue = _minimumSharedValue;
        comparableThreshold = _comparableThreshold;
        predicates = new ArrayList<>();
        predicateGroups = new ArrayList<>();
        predicateProvider = new PredicateProvider();
        predicateIdProvider = new IndexProvider<>();
    }

    public void buildPredicateSpace(InputOld input) {
        List<ColumnPair> columnPairs = constructColumnPairs(input);
        List<Predicate> set = new ArrayList<>();

        for (ColumnPair pair : columnPairs) {
            ColumnOperand<?> o1 = new ColumnOperand<>(pair.getC1(), 0);
            addPredicates(set, o1, new ColumnOperand<>(pair.getC2(), 1), pair.isJoinable(), pair.isComparable());
        }
        predicates.addAll(set);
        predicateIdProvider.addAll(predicates);

        Predicate.configure(predicateProvider);
        Closure.configure(predicateProvider);
        PredicateSet.configure(predicateIdProvider);

        buildPredicateGroups();
        buildMutexMap();
        buildInverseMap();
    }

    public void buildMutexMap() {
        //为每个谓词初始化他的longbitset  一个谓词组里面只能选择一个
        mutexMap = new LongBitSet[predicates.size()];
        for (Predicate p1 : predicates) {
            LongBitSet mutex = new LongBitSet();
            for (Predicate p2 : predicates) {
                //if (!p1.equals(p2) && p1.getOperand1().equals(p2.getOperand1()) && p1.getOperand2().equals(p2.getOperand2()))
                if (p1.getOperand1().equals(p2.getOperand1()) && p1.getOperand2().equals(p2.getOperand2()))
                    mutex.set(predicateIdProvider.getIndex(p2));
            }
            mutexMap[predicateIdProvider.getIndex(p1)] = mutex;
        }
    }

    public void buildInverseMap() {
        //为每个谓词得到其相反谓词的编号  放在inverseMap里面
        inverseMap = new int[predicateIdProvider.size()];
        for (var r : predicateIdProvider.entrySet())
            inverseMap[r.getValue()] = predicateIdProvider.getIndex(r.getKey().getInverse());
    }

    public List<Predicate> getPredicates() {
        return predicates;
    }

    public int predicateCount() {
        return predicates.size();
    }

    public List<List<Predicate>> getPredicateGroups() {
        return predicateGroups;
    }

    public IndexProvider<Predicate> getPredicateIdProvider() {
        return predicateIdProvider;
    }

    public LongBitSet[] getMutexMap() {
        return mutexMap;
    }

    public PredicateSet getInverse(PredicateSet predicateSet) {
        LongBitSet bitset = predicateSet.getLongBitSet();
        LongBitSet inverse = new LongBitSet();
        for (int l = bitset.nextSetBit(0); l >= 0; l = bitset.nextSetBit(l + 1))
            inverse.set(inverseMap[l]);
        return new PredicateSet(inverse);
    }

    public PredicateSet getInverse(LongBitSet predicateSet) {
        LongBitSet inverse = new LongBitSet();
        for (int l = predicateSet.nextSetBit(0); l >= 0; l = predicateSet.nextSetBit(l + 1))
            inverse.set(inverseMap[l]);
        return new PredicateSet(inverse);
    }

    public LongBitSet getMutex(int index) {
        return mutexMap[index];
    }

    public LongBitSet getMutex(LongBitSet ps) {
        LongBitSet mutex = new LongBitSet();
        for (int i = ps.nextSetBit(0); i >= 0; i = ps.nextSetBit(i + 1))
            mutex.or(mutexMap[i]);
        return mutex;
    }

    public List<List<Predicate>> getPredicateGroupsNumericalSingleColumn() {
        return numSingleColumnPredicateGroups;
    }

    public List<List<Predicate>> getPredicateGroupsNumericalCrossColumn() {
        return numCrossColumnPredicateGroups;
    }

    public List<List<Predicate>> getPredicateGroupsCategoricalSingleColumn() {
        return strSingleColumnPredicateGroups;
    }

    public List<List<Predicate>> getStrCrossColumnPredicateGroups() {
        return strCrossColumnPredicateGroups;
    }

    public Predicate getPredicateByType(Collection<Predicate> predicateGroup, Operator type) {
        Predicate pwithtype = null;
        for (Predicate p : predicateGroup) {
            if (p.getOperator().equals(type)) {
                pwithtype = p;
                break;
            }
        }
        return pwithtype;
    }

    //返回可以构成谓词空间的列对
    private ArrayList<ColumnPair> constructColumnPairs(InputOld input) {
        ArrayList<ColumnPair> pairs = new ArrayList<>();
        for (int i = 0; i < input.getColumns().length; ++i) {
            ParsedColumn<?> c1 = input.getColumns()[i];
            for (int j = i; j < input.getColumns().length; ++j) {
                ParsedColumn<?> c2 = input.getColumns()[j];
                boolean joinable = isJoinable(c1, c2);
                boolean comparable = isComparable(c1, c2);
                if (joinable || comparable) {
                    pairs.add(new ColumnPair(c1, c2, true, comparable));
                }
            }
        }
        return pairs;
    }

    /**
     * Can form a predicate within {==, !=}
     */
    private boolean isJoinable(ParsedColumn<?> c1, ParsedColumn<?> c2) {
        if (noCrossColumn)
            return c1.equals(c2);

        if (!c1.getType().equals(c2.getType()))
            return false;
        double[] tmp = c1.getSharedPercentage2(c2);
        if(tmp[0]>minimumSharedValue){
          //  System.out.println(tmp[1]+" "+tmp[2]);
            return  true;
        }
        else  return  false;
        //return c1.getSharedPercentage(c2) > minimumSharedValue;
    }

    /**
     * Can form a predicate within {==, !=, <, <=, >, >=}
     */
    private boolean isComparable(ParsedColumn<?> c1, ParsedColumn<?> c2) {
        if (noCrossColumn)
            return c1.equals(c2) && (c1.getType().equals(Double.class) || c1.getType().equals(Long.class));

        if (!c1.getType().equals(c2.getType()))
            return false;

        if (c1.getType().equals(Double.class) || c1.getType().equals(Long.class)) {
            if (c1.equals(c2))
                return true;

            double avg1 = c1.getAverage();
            double avg2 = c2.getAverage();
            if(Math.min(avg1, avg2) / Math.max(avg1, avg2) > comparableThreshold){
               // System.out.println(avg1+" "+avg2);
                return  true;
            }
            else return  false;
          //  return Math.min(avg1, avg2) / Math.max(avg1, avg2) > comparableThreshold;
        }
        return false;
    }

    private void addPredicates(Collection<Predicate> set, ColumnOperand<?> o1, ColumnOperand<?> o2, boolean joinable, boolean comparable) {
        Set<Predicate> partialPredicates = new HashSet<>();
        for (Operator op : Operator.values()) {
            if (op == Operator.EQUAL || op == Operator.UNEQUAL) {
                if (joinable && (o1.getIndex() != o2.getIndex())){

                   if(!o1.getColumn().getColumnName().equals(o2.getColumn().getColumnName())){
                       System.out.println(o1.getColumn().getColumnName()+" "+o2.getColumn().getColumnName());
                   }
                    partialPredicates.add(predicateProvider.getPredicate(op, o1, o2));
                }

            } else if (comparable) {
                //count++;
                //System.out.println("double "+o1.toString()+" "+o2.toString());
                partialPredicates.add(predicateProvider.getPredicate(op, o1, o2));
            }
        }
        //System.out.println(partialPredicates.size());
        set.addAll(partialPredicates);
        predicateGroups.add(new ArrayList<>(partialPredicates));
    }

    private void buildPredicateGroups() {
        numSingleColumnPredicateGroups = new ArrayList<>();
        numCrossColumnPredicateGroups = new ArrayList<>();
        strSingleColumnPredicateGroups = new ArrayList<>();
        strCrossColumnPredicateGroups = new ArrayList<>();

        for (List<Predicate> predicateGroup : getPredicateGroups()) {
            // numeric: comparable, all 6 predicates
            if (predicateGroup.size() == 6) {
                if (predicateGroup.iterator().next().isCrossColumn())
                    numCrossColumnPredicateGroups.add(predicateGroup);
                else
                    numSingleColumnPredicateGroups.add(predicateGroup);
            }
            // string: joinable but non-comparable, only 2 predicates
            if (predicateGroup.size() == 2) {
                if (predicateGroup.iterator().next().isCrossColumn())
                    strCrossColumnPredicateGroups.add(predicateGroup);
                else
                    strSingleColumnPredicateGroups.add(predicateGroup);
            }
        }
    }


//    //新添加的方法 用于解析indexProvider文件
//    public PredicateBuilder(File index, Input input) throws IOException {
//        predicates = new HashSet<>();
//        predicateGroups = new ArrayList<>();
//        BufferedReader br = new BufferedReader(new FileReader(index));
//        String s = null;
//        int lasti1 = -1, lastj1 = -1;
//        Set<Predicate> tempPres = new HashSet<>();
//        while ((s = br.readLine()) != null) {
//            //操作数1 符号 操作数2
//            String[] temp = s.split(" ");
//            Operator op = getoperator(temp[1]);
//            int i;
//            int i1 = 0, j1 = 0;
//            for (i = 0; i < input.getColumns().length; i++) {
//                if (temp[0].substring(3).equals((input.getColumns()[i]).toString()))
//                    i1 = i;
//                if (temp[2].substring(3).equals((input.getColumns()[i]).toString()))
//                    j1 = i;
//            }
//            ColumnOperand operand1 = new ColumnOperand(input.getColumns()[i1], Integer.parseInt(temp[0].substring(1, 2)));
//            ColumnOperand operand2 = new ColumnOperand(input.getColumns()[j1], Integer.parseInt(temp[2].substring(1, 2)));
//            Predicate res = new Predicate(op, operand1, operand2);
//            PredicateSet.getIndex(res);
//            predicates.add(res);
//            if ((lasti1 == -1 && lastj1 == -1) || (lasti1 == i1 && lastj1 == j1)) {
//                tempPres.add(res);
//            } else {
//                this.predicateGroups.add(new ArrayList<>(tempPres));
//                tempPres = new HashSet<>();
//                tempPres.add(res);
//            }
//            lasti1 = i1;
//            lastj1 = j1;
//        }
//        this.predicateGroups.add(new ArrayList<>(tempPres));
//        dividePredicateGroupsByType();
//    }
    public List<Predicate> getPredicate(){return predicates;}
}
