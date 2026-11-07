import java.util.*;
final class RegionOrder {
    static int[] near(int side,int centerX,int centerZ){Integer[] order=new Integer[side*side];for(int i=0;i<order.length;i++)order[i]=i;Arrays.sort(order,Comparator.comparingLong((Integer i)->{long dx=i%side-centerX,dz=i/side-centerZ;return dx*dx+dz*dz;}).thenComparingInt(i->i));return Arrays.stream(order).mapToInt(Integer::intValue).toArray();}
}
