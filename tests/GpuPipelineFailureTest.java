import java.util.*;
import java.util.concurrent.atomic.*;
public class GpuPipelineFailureTest {
    public static void main(String[] args){
        var graph=TerrainGraph.template("Island mountains");System.setProperty("terraforge.gpu","cpu");float[] expected=graph.evaluateForBuild(768,768,62,42,graph.output(),()->false).output();System.setProperty("terraforge.gpu","auto");
        long before=GpuGeneration.fusedNodes();AtomicBoolean injected=new AtomicBoolean();
        var actual=graph.evaluateForBuild(768,768,62,42,graph.output(),()->{
            if(GpuGeneration.fusedNodes()>before&&injected.compareAndSet(false,true))GpuGeneration.compute("intentional-pipeline-failure","invalid kernel text;",new double[0],new float[0][],new float[768*768],768,768,62,42,()->false);
            return false;
        });
        if(!injected.get()||!Arrays.equals(expected,actual.output())||GpuGeneration.residentBytes()!=0||!GpuGeneration.status().startsWith("CPU fallback"))throw new AssertionError("Failed to restart the graph cleanly on CPU after a resident GPU failure");
        System.out.println("PASS: shader failure with resident-only data restarts the complete evaluation on CPU, returns exact CPU output, and frees all buffers");
    }
}
