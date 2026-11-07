import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
public class RegionReuseTest {
    static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("terraforge-region-reuse-");System.setProperty("terraforge.regionCache",root.resolve("cache").toString());var world=new TerraForge.World(512,62,4242);Arrays.fill(world.height,80);String key=RegionReuse.key(world,0,0);Path first=root.resolve("first.mca");RegionBatchWriter.write(world,first,0,0,()->false);RegionReuse.remember(key,first);Path reused=root.resolve("reused.mca");check(RegionReuse.restore(key,reused)&&Arrays.equals(Files.readAllBytes(first),Files.readAllBytes(reused)),"exact region reuse");
        Files.delete(root.resolve("cache").resolve(key+".mca"));check(RegionReuse.restore(key,root.resolve("from-original.mca")),"original-region references survive payload eviction");
        try(var file=new java.io.RandomAccessFile(first.toFile(),"rw")){file.seek(9000);file.writeByte(123);}check(!RegionReuse.restore(key,root.resolve("corrupt.mca")),"modified original region is not reused");
        world.height[100]+=1;check(!key.equals(RegionReuse.key(world,0,0)),"height edit changes region key");world.height[100]-=1;world.environment.rain=EnvironmentMap.filled(512*512,.7f);check(!key.equals(RegionReuse.key(world,0,0)),"ground cover inputs included");world.environment.rain=null;
        check(!key.equals(RegionReuse.key(world,1,0)),"world coordinates included");System.setProperty("terraforge.fastGpu","true");check(!key.equals(RegionReuse.key(world,0,0)),"algorithm mode included");System.clearProperty("terraforge.fastGpu");
        AtomicInteger allowed=new AtomicInteger(1),active=new AtomicInteger(),maximum=new AtomicInteger();CpuWork.regions(12,4,allowed::get,()->false,i->{int count=active.incrementAndGet();maximum.accumulateAndGet(count,Math::max);try{if(i==0)allowed.set(3);Thread.sleep(30);}finally{active.decrementAndGet();}});check(maximum.get()>1&&maximum.get()<=3,"adaptive admission respects changing cap");
        try(var mode=new GpuGeneration.ModeScope("gpu");var fast=FastGpu.scope(true);var compression=new RegionBatchWriter.LevelScope(3)){CpuWork.regions(6,2,()->false,i->check(GpuGeneration.mode().equals("gpu")&&FastGpu.requested()&&RegionBatchWriter.compressionLevel()==3,"worker configuration captured"));}
        check(!FastGpu.requested()&&RegionBatchWriter.compressionLevel()==1,"worker configuration restored");
        System.out.println("PASS: identical encoded-region reuse, block-input invalidation, coordinates/mode keys and adaptive worker limits");
    }
}
