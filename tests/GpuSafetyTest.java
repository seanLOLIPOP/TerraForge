import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
public class GpuSafetyTest {
    public static void main(String[] args)throws Exception{
        System.setProperty("terraforge.gpu","gpu");var node=new TerrainGraph.Node(1,TerrainGraph.Type.NOISE,0,0);
        AtomicInteger polls=new AtomicInteger();
        try{GpuGeneration.generate(node,2048,62,42,new float[2048*2048],()->polls.incrementAndGet()>3);throw new AssertionError("source cancellation ignored");}catch(CancellationException expected){}
        if(!GpuGeneration.status().startsWith("OpenCL"))throw new AssertionError(GpuGeneration.status());
        float[] h=new float[512*512];Random random=new Random(42);for(int i=0;i<h.length;i++)h[i]=random.nextFloat()*500;float[] before=h.clone();polls.set(0);
        try{GpuGeneration.thermal(h,512,20,()->polls.incrementAndGet()>3);throw new AssertionError("erosion cancellation ignored");}catch(CancellationException expected){}
        if(!Arrays.equals(h,before))throw new AssertionError("cancelled GPU erosion changed caller data");
        var graph=TerrainGraph.template("Island mountains");System.setProperty("terraforge.gpu","cpu");float[] reference=graph.evaluate(512,512,62,42,graph.output(),()->false).output();System.setProperty("terraforge.gpu","auto");
        var pool=Executors.newFixedThreadPool(3);
        try{var jobs=new ArrayList<Future<float[]>>();for(int k=0;k<3;k++)jobs.add(pool.submit(()->graph.evaluate(512,512,62,42,graph.output(),()->false).output()));for(var job:jobs){float[] actual=job.get(30,TimeUnit.SECONDS);for(int i=0;i<actual.length;i++)if(Math.abs(actual[i]-reference[i])>.0002)throw new AssertionError("concurrent output mismatch");}}
        finally{pool.shutdownNow();}
        float[] out=new float[512*512];if(GpuGeneration.compute("invalid-kernel","this is an invalid kernel;",new double[0],new float[0][],out,512,512,62,42,()->false))throw new AssertionError("invalid shader accepted");
        float[] recovered=graph.evaluate(512,512,62,42,graph.output(),()->false).output();if(!Arrays.equals(reference,recovered))throw new AssertionError("CPU fallback after compiler failure changed output");
        System.out.println("PASS: in-flight cancellation, unchanged erosion input on cancellation, concurrent callers, subsequent reuse, and compiler-failure CPU fallback");
    }
}
