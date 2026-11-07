import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class CpuGenerationTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void reference(TerraForge.World w,int iterations){
        int n=w.size;for(int pass=0;pass<iterations;pass++){
            float[] delta=new float[n*n];for(int z=1;z<n-1;z++)for(int x=1;x<n-1;x++){
                int i=z*n+x,best=i;float drop=2;
                for(int j:new int[]{i-1,i+1,i-n,i+n})if(w.height[i]-w.height[j]>drop){drop=w.height[i]-w.height[j];best=j;}
                if(best!=i){float amount=(drop-2)*.22f;delta[i]-=amount;delta[best]+=amount;}
            }
            for(int i=0;i<delta.length;i++)w.height[i]+=delta[i];
        }
    }
    public static void main(String[] args)throws Exception{
        for(int size:new int[]{32,256,512}){
            var w=new TerraForge.World(size,62,42);var random=new Random(42);
            for(int i=0;i<w.height.length;i++)w.height[i]=random.nextFloat()*1000-50;
            var expected=w.copy();reference(expected,12);TerraForge.thermalErode(w,12);
            check(Arrays.equals(expected.height,w.height),"thermal erosion preserves original float results at "+size);
        }
        var seen=ConcurrentHashMap.<String>newKeySet();
        CpuWork.rows(512,512,()->false,row->{seen.add(Thread.currentThread().getName());double value=0;for(int i=0;i<3000;i++)value+=Math.sin(i+row);if(!Double.isFinite(value))throw new AssertionError();});
        check(CpuWork.threads()==1||seen.size()>1,"CPU rows use multiple workers");
        AtomicInteger active=new AtomicInteger();AtomicBoolean cancelled=new AtomicBoolean();CountDownLatch entered=new CountDownLatch(1);
        Thread owner=new Thread(()->{
            try{CpuWork.rows(8192,8192,()->false,row->{active.incrementAndGet();entered.countDown();try{java.util.concurrent.locks.LockSupport.parkNanos(2_000_000);}finally{active.decrementAndGet();}});}
            catch(CancellationException expected){cancelled.set(true);}
        });
        owner.start();check(entered.await(5,TimeUnit.SECONDS),"workers started");owner.interrupt();owner.join(5000);
        check(!owner.isAlive()&&cancelled.get()&&active.get()==0,"interrupt cancels and drains row tasks");
        boolean failed=false;try{CpuWork.rows(512,512,()->false,row->{if(row==100)throw new IllegalArgumentException("test");});}catch(IllegalArgumentException expected){failed=true;}
        check(failed,"worker exceptions propagate");
        long gb=1L<<30;
        check(LargeWorldBuilder.chooseWorkers(32,12*gb,gb,gb,0)>4,"region workers no longer capped at four");
        check(LargeWorldBuilder.chooseWorkers(32,2*gb,gb,gb,0)<=1,"low memory limits concurrency");
        check(LargeWorldBuilder.chooseWorkers(4,12*gb,gb,gb,100)<=3,"overrides obey CPU cap");
        check(LargeWorldBuilder.chooseWorkers(32,2*gb,gb,gb,100)<=1,"overrides obey memory cap");
        var regionNames=ConcurrentHashMap.<String>newKeySet();AtomicInteger rows=new AtomicInteger();
        CpuWork.regions(8,4,()->false,index->CpuWork.rows(512,512,()->false,row->{regionNames.add(Thread.currentThread().getName());rows.incrementAndGet();}));
        check(rows.get()==8*512&&regionNames.size()<=4&&regionNames.stream().allMatch(name->name.startsWith("TerraForge-region-")),"region jobs do not multiply CPU row pools");
        AtomicInteger regionActive=new AtomicInteger();boolean regionFailed=false;
        try{CpuWork.regions(12,4,()->false,index->{
            regionActive.incrementAndGet();try{if(index==1)throw new java.io.IOException("test");for(int i=0;i<50&&!Thread.currentThread().isInterrupted();i++)java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);}
            finally{regionActive.decrementAndGet();}
        });}catch(java.io.IOException expected){regionFailed=true;}
        check(regionFailed&&regionActive.get()==0,"region failure drains all workers before shared state restoration");
        System.out.println("PASS: bit-exact thermal erosion, "+seen.size()+" CPU workers, cancellation drainage, exception propagation, and CPU/memory-bounded regions");
    }
}
