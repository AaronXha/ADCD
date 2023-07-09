import com.sun.source.tree.Tree;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class huawei {
    public static void main(String[] args) throws IOException {
          Scanner sc = new Scanner(System.in);
          int min = sc.nextInt();
          int max = sc.nextInt();
          int now = 0;
          Set<Integer> used = new HashSet<>();
          TreeSet<Integer> map = new TreeSet<>();
          Map<Integer,Integer> yinshe = new HashMap<>();
          for(int i=min;i<max;i++){
              map.add(i);
              yinshe.put(i,i);
          }
          int n = sc.nextInt();
          for(int i=0;i<n;i++){
              int fi = sc.nextInt();
              int se = sc.nextInt();
              if(fi==1){
                  if(se>map.size())continue;
                  while (se>0){
                      int tmp = map.first()%(max+1);
                      map.remove(map.first());
                      used.add(tmp);
                      se--;

                  }
              }
              else if(fi==2){
                  if(used.contains(se))continue;
                  int tmp = yinshe.get(se);
                  map.remove(tmp);
                  used.add(se);
              }
              else{
                  if(!used.contains(se))continue;
                  now+=max+1;
                  used.remove(se);
                  map.add(se+now);
                  yinshe.put(se,se+now);
              }
          }
          int ans = map.first()%(max+1);
          System.out.println(ans);
    }




}
