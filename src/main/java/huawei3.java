import java.util.*;

public class huawei3 {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int m  = sc.nextInt();
        Map<Long,Integer> map = new HashMap<>();
        int n = sc.nextInt();
        int ans = 0;
        List[] shuzu = new ArrayList[n];
        for(int i=0;i<n;i++)shuzu[i] = new ArrayList<Integer>();
        int[][] tmp = new int[n][2];
        for(int i=0;i<n;i++){
            tmp[i][0] = sc.nextInt();
            tmp[i][1] = sc.nextInt();
        }
        for(int i=0;i<n;i++){
            for(int j=0;j<n;j++){
                if(i==j)shuzu[i].add(-1);
                else {
                    if(tmp[i][0]==tmp[j][0]&&tmp[i][1]==tmp[j][1])shuzu[i].add(-1);
                    else shuzu[i].add(Math.max(Math.abs(tmp[i][0]-tmp[j][0]),Math.abs(tmp[i][1]-tmp[j][1])));
                }
            }
        }
        int l = 0;
        int r = (int) Math.pow(10,9);
        while (l<r){
            int mid = l+r>>1;
            if(check(shuzu,mid,m))r=mid;
            else l = mid+1;
        }
        System.out.println(r);

    }
    public static boolean check(List<Integer>[] shuzu,int mid,int m){
        int n = shuzu.length;
        for(int i=0;i<n;i++){
            int count = 0;
            for(int j=0;j<n;j++){
                if(shuzu[i].get(j)-mid*2<=0)count++;
            }
            if(count>=m)return true;
        }
        return  false;
    }
}
