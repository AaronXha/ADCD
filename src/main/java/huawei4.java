import java.util.*;

public class huawei4 {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int min = sc.nextInt();
        int max = sc.nextInt();
        int n = sc.nextInt();
        List<Integer> unuse = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for(int i=min;i<=max;i++)unuse.add(i);
        for(int i=0;i<n;i++){
            int fi = sc.nextInt();
            int se = sc.nextInt();
            if(fi==1){
                if(unuse.size()<se)continue;
                while (se>0){
                    int tmp = unuse.get(0);
                    unuse.remove(0);
                    used.add(tmp);
                    se--;
                }
            }
            else if(fi==2){
                if(used.contains(se))continue;
                int tmp = 0;
                for(int j=0;j<unuse.size();j++){
                    if(unuse.get(j)==se){
                        tmp = j;
                        break;
                    }
                }
                used.add(se);
                unuse.remove(tmp);
            }
            else {
                if(!used.contains(se))continue;
                used.remove(se);
                unuse.add(se);
            }
        }
        System.out.println(unuse.get(0));

    }
}
