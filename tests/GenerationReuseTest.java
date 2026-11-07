import java.util.*;
import java.util.concurrent.*;

public class GenerationReuseTest {
    static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
    public static void main(String[] args)throws Exception{
        var random=new Random(7137);
        var queue=new RiverQueue();var reference=new PriorityQueue<RiverCourse.Cell>(Comparator.comparingDouble(RiverCourse.Cell::cost).thenComparingInt(RiverCourse.Cell::index));
        for(int i=0;i<100000;i++){
            if(!reference.isEmpty()&&random.nextInt(3)==0){check(queue.remove()==reference.remove().index(),"routing heap order");continue;}
            int cell=random.nextInt(10000);float cost=i%10==0?0:random.nextInt(2000)/7f;
            queue.add(cell,cost);reference.add(new RiverCourse.Cell(cell,cost));
        }
        while(!reference.isEmpty())check(queue.remove()==reference.remove().index(),"routing heap drain");
        check(queue.isEmpty(),"routing heap empty");
        for(int test=0;test<350;test++){
            int h=test<21?-60:test<42?72:random.nextInt(1085)-60,mat=test%21;
            int water=random.nextBoolean()?62:h+random.nextInt(20),snow=random.nextInt(9),ice=random.nextInt(6),glacier=random.nextInt(31);
            int x=random.nextInt(65536)-32768,z=random.nextInt(65536)-32768;long seed=random.nextLong();int soil=ExportBlocks.soilDepth(x,z,seed);
            var column=new ExportBlocks.Column(h,mat,water,snow,ice,glacier,soil,x,z,seed);
            for(int y=-64;y<=1087;y++)check(column.block(y)==ExportBlocks.block(h,mat,water,snow,ice,glacier,y,soil,x,z,seed),"column differs at material="+mat+" height="+h+" y="+y);
        }
        var base=new EnvironmentMap(EnvironmentMap.filled(512*512,80));base.water=EnvironmentMap.filled(512*512,62);base.water[127]=90;
        float[] first=TreeLibrary.waterDistance(base,512,62,()->false);
        var shared=base.share(base.height);check(TreeLibrary.waterDistance(shared,512,62,()->false)==first,"tree passes reuse distance");
        shared.water[127]=62;shared.height[200]=50;
        float[] next=TreeLibrary.waterDistance(shared,512,62,()->false);
        check(next!=first&&Arrays.equals(next,RiverBanks.waterDistance(shared,512,62,()->false)),"in-place water/height edits invalidate cache");
        check(Arrays.equals(TreeLibrary.waterDistance(shared,512,100,()->false),RiverBanks.waterDistance(shared,512,100,()->false)),"sea-level handling");
        var names=ConcurrentHashMap.<String>newKeySet();
        CpuWork.regions(2,1,()->false,index->CpuWork.rows(512,512,()->false,row->{names.add(Thread.currentThread().getName());double sum=0;for(int i=0;i<1000;i++)sum+=Math.sin(row+i);if(!Double.isFinite(sum))throw new AssertionError();}));
        check(CpuWork.threads()==1||names.size()>1,"single-region terrain work uses multiple CPU workers");
        System.out.println("PASS: exact column blocks across all materials, heights, seeds and water/snow/ice; tree cache invalidation; single-region CPU scheduling");
    }
}
