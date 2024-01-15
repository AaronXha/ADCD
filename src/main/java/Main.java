import ADCD.ADCD;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint;
import de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraintSet;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.*;
import java.util.concurrent.ForkJoinPool;

@Command(name = "ADCD", version = "1.0.2", mixinStandardHelpOptions = true)
public class Main implements Runnable {

    @Option(names = {"-s"}, description = "Only consider single-column predicates")
    static boolean singleColumn = true;

    @Option(names = {"-w"}, description = "Shard length")
    static int shardLength = 350;

    @Option(names = {"-g"}, description = "Threshold of APPROXIMATION FUNCTION g1")
    static double threshold = 0.01d;

    @Option(names = {"-f"}, description = "Path of input dataset")
    static private String fp;

    @Option(names = {"-l"}, description = "Row limit of dataset")
    static int rowLimit = 300000;
    @Option(names = {"-p"}, description = "Parallelism for evidence set construction")
    static int parallelism = 4;



    @Option(names = {"-m"}, description = "0: regular;\n1: read evidences from file;\n" +
            "2: write evidences to file;\n3: build and write evidences only")
    int mode = 0;

    @Override
    public void run() {
        try {
            testADCD(1);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
//        for (int i = 0; i < 1; i++) {
//            int dataset = 4;
//            testADCD(dataset);
//            System.out.println();
//        }

        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    void testADCD(int dataset) throws IOException {
        //设定最大并行度
        if (parallelism > 0)
            System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", Integer.toString(parallelism));
        System.out.println("PARALLELISM: " + ForkJoinPool.commonPool().getParallelism());

        ADCD adcd = new ADCD(singleColumn, threshold, shardLength, mode);
        DenialConstraintSet dcs = adcd.buildApproxDCs(fp == null ? DataFp.DATA_FP[1] : fp, rowLimit);
        for(DenialConstraint dc:dcs) {
            System.out.println(dc);
        }

    }
    public void writeDC(DenialConstraintSet dcs) throws IOException {
        File file = new File("inspection.txt");
        FileWriter writer = new FileWriter(file);
        for (DenialConstraint dc:dcs){
//            boolean flag = false;
//            for(Predicate predicate:dc.getPredicateSet()){
//                if(!predicate.getOperand1().getColumn().toString().equals(predicate.getOperand2().getColumn().toString()))flag=true;
//            }
//            if(flag){
//                writer.write(dc.toString());
//                writer.write("\n");
//            }
//            boolean flag = true;
//            for(Predicate predicate:dc.getPredicateSet()){
//                if(predicate.getOperand1().getColumn().toString().equals("Zip(String)")||predicate.getOperand2().getColumn().toString().equals("Zip(String)"))flag=false;
//            }
//            if(flag){
//                writer.write(dc.toString());
//                writer.write("\n");}
            writer.write(dc.toString());
            writer.write("\n");
        }
        writer.flush();
        writer.close();
    }

    private void writeDCObject(DenialConstraintSet dcs) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("dataset//airport.dc"))) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(dcs, writer);
        } catch (IOException e) {
            System.err.println(e);
        }
    }
    DenialConstraintSet readDc() {
        DenialConstraintSet evidenceSet = null;
        try (BufferedReader reader = new BufferedReader(new FileReader("dataset//airport.dc"))) {
            Gson gson = new Gson();
            evidenceSet = gson.fromJson(reader,DenialConstraintSet.class);
        } catch (IOException e) {
            System.err.println(e);
        }
        return evidenceSet;
    }


}

