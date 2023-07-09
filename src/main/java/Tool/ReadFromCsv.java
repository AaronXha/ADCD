package Tool;

import com.csvreader.CsvReader;
import com.csvreader.CsvWriter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ReadFromCsv {
    public static void main(String[] args) throws IOException {
        cutFromCsv("dataset/airport.csv",10000);

    }
    public static void cutFromCsv(String path,int limit) throws IOException {
        CsvReader csvReader = new CsvReader(path,',', StandardCharsets.UTF_8);
        String[] paths = path.split("\\.");
        CsvWriter csvWriter = new CsvWriter(paths[0]+"_"+limit+"."+paths[1],',',StandardCharsets.UTF_8);
        CsvWriter csvWriter2 = new CsvWriter(paths[0]+"_"+limit+"_red"+"" +
                "."+paths[1],',',StandardCharsets.UTF_8);
        int nline = 0;
        int count = 0;
        try {
            while (csvReader.readRecord()){
                count++;
                String[] line = csvReader.getValues();
                if(nline==0) {
                    csvWriter.writeRecord(line);
                    csvWriter2.writeRecord(line);
                    nline++;
                    //System.out.println(nline);
                }
                else if(nline<=limit){
                    csvWriter.writeRecord(line);
                    nline++;

                }
                else {
                    csvWriter2.writeRecord(line);
                }
            }
            csvReader.close();
            csvWriter.close();
            csvWriter2.close();
        }
        catch (Exception e){
            e.printStackTrace();
        }
        System.out.println(count);
    }
}
