import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public class GpuPipelineTest {
    static void equal(float[] expected,float[] actual,String message){if(!Arrays.equals(expected,actual)){double max=0;for(int i=0;i<actual.length;i++)max=Math.max(max,Math.abs(expected[i]-actual[i]));throw new AssertionError(message+" max delta "+max);}}
    static TerrainGraph.Result build(TerrainGraph g){return g.evaluateForBuild(768,768,62,-734123,g.output(),()->false);}
    public static void main(String[] args)throws Exception{
        System.setProperty("terraforge.gpu","auto");
        for(String name:new String[]{"Island mountains","Masked mountains","Terraced ranges"}){
            var graph=TerrainGraph.template(name);System.setProperty("terraforge.gpuPipeline","false");long up=GpuGeneration.uploadedBytes(),down=GpuGeneration.downloadedBytes();var reference=build(graph);long legacyBytes=GpuGeneration.uploadedBytes()-up+GpuGeneration.downloadedBytes()-down;
            System.setProperty("terraforge.gpuPipeline","true");up=GpuGeneration.uploadedBytes();down=GpuGeneration.downloadedBytes();long fused=GpuGeneration.fusedNodes();var actual=build(graph);long bytes=GpuGeneration.uploadedBytes()-up+GpuGeneration.downloadedBytes()-down;
            equal(reference.output(),actual.output(),name+" build");if(GpuGeneration.fusedNodes()<=fused)throw new AssertionError("No fused group: "+name);if(Integer.getInteger("terraforge.gpuBudgetMiB",256)>=64&&bytes>=legacyBytes)throw new AssertionError("Transfers did not decrease: "+bytes+" >= "+legacyBytes);
            if(actual.fields().size()!=1||actual.fields().get(graph.output())!=actual.output())throw new AssertionError("Build exposed unmaterialized intermediate fields");
            var preview=graph.evaluate(768,768,62,-734123,graph.output(),()->false);System.setProperty("terraforge.gpuPipeline","false");var expectedPreview=graph.evaluate(768,768,62,-734123,graph.output(),()->false);System.setProperty("terraforge.gpuPipeline","true");
            for(int id:expectedPreview.fields().keySet())equal(expectedPreview.fields().get(id),preview.fields().get(id),name+" preview #"+id);
            System.out.println(name+" bit-exact; transfers "+legacyBytes+" -> "+bytes);
        }
        var rounding=new TerrainGraph();var noise=rounding.add(TerrainGraph.Type.NOISE,0,0);noise.values.put("height",1024.0);noise.values.put("detail",8.0);noise.values.put("scale",17.25);noise.values.put("rough",.8);
        var remap=rounding.add(TerrainGraph.Type.REMAP,0,0);remap.values.put("scale",-2.3);remap.values.put("offset",149.0);rounding.connect(noise.id,remap.id,0);
        var clamp=rounding.add(TerrainGraph.Type.CLAMP,0,0);clamp.values.put("min",-40.0);clamp.values.put("max",120.0);rounding.connect(remap.id,clamp.id,0);
        var terrace=rounding.add(TerrainGraph.Type.TERRACE,0,0);terrace.values.put("step",7.0);rounding.connect(clamp.id,terrace.id,0);
        var mask=rounding.add(TerrainGraph.Type.HEIGHT_MASK,0,0);mask.values.put("min",-10.0);mask.values.put("max",90.0);rounding.connect(noise.id,mask.id,0);
        var constant=rounding.add(TerrainGraph.Type.CONSTANT,0,0);var combined=rounding.add(TerrainGraph.Type.BLEND,0,0);rounding.connect(terrace.id,combined.id,0);rounding.connect(constant.id,combined.id,1);rounding.connect(mask.id,combined.id,2);
        var output=rounding.add(TerrainGraph.Type.OUTPUT,0,0);rounding.connect(combined.id,output.id,0);
        System.setProperty("terraforge.gpuPipeline","false");var expectedRounding=build(rounding);System.setProperty("terraforge.gpuPipeline","true");equal(expectedRounding.output(),build(rounding).output(),"fused clamping and float rounding at every node");
        var branch=TerrainGraph.template("Island mountains");int erosion=branch.nodes.get(branch.output()).input[0];var source=branch.nodes.values().stream().filter(n->n.type==TerrainGraph.Type.NOISE).findFirst().orElseThrow();var blend=branch.add(TerrainGraph.Type.BLEND,0,0);branch.connect(erosion,blend.id,0);branch.connect(source.id,blend.id,1);branch.connect(blend.id,branch.output(),0);
        System.setProperty("terraforge.gpuPipeline","false");var expectedBranch=build(branch);System.setProperty("terraforge.gpuPipeline","true");equal(expectedBranch.output(),build(branch).output(),"shared branch remains immutable through erosion");
        var boundary=TerrainGraph.template("Island mountains");var hydro=boundary.add(TerrainGraph.Type.HYDRAULIC,0,0);hydro.values.put("iterations",1.0);boundary.connect(boundary.nodes.get(boundary.output()).input[0],hydro.id,0);boundary.connect(hydro.id,boundary.output(),0);
        System.setProperty("terraforge.gpuPipeline","false");var expectedBoundary=build(boundary);System.setProperty("terraforge.gpuPipeline","true");equal(expectedBoundary.output(),build(boundary).output(),"CPU hydraulic boundary receives current heights");
        var graph=TerrainGraph.template("Island mountains");AtomicInteger checks=new AtomicInteger();try{graph.evaluateForBuild(2048,2048,62,42,graph.output(),()->checks.incrementAndGet()>12);throw new AssertionError("Cancellation ignored");}catch(CancellationException expected){}
        if(Integer.getInteger("terraforge.gpuBudgetMiB",256)>=64){System.setProperty("terraforge.gpuPipeline","false");var full=graph.evaluateForBuild(2048,2048,62,4242,graph.output(),()->false);System.setProperty("terraforge.gpuPipeline","true");equal(full.output(),graph.evaluateForBuild(2048,2048,62,4242,graph.output(),()->false).output(),"full 2048 build across dispatch batches");}
        var pool=Executors.newFixedThreadPool(3);float[] reference=build(graph).output();try{var futures=new ArrayList<Future<TerrainGraph.Result>>();for(int i=0;i<3;i++)futures.add(pool.submit(()->build(graph)));for(var future:futures)equal(reference,future.get(30,TimeUnit.SECONDS).output(),"concurrent sessions");}finally{pool.shutdownNow();}
        if(!GpuGeneration.status().startsWith("OpenCL"))throw new AssertionError(GpuGeneration.status());
        if(GpuGeneration.residentBytes()!=0)throw new AssertionError("Resident buffers leaked: "+GpuGeneration.residentBytes());
        System.out.println("PASS: fusion, resident transfers, preview fields, branch isolation, CPU boundaries, cancellation, and concurrent evaluations");
    }
}
