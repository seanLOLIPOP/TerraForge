import java.util.*;
public class GpuFallbackTest {
    public static void main(String[] args)throws Exception{
        var graph=TerrainGraph.template("Island mountains");System.setProperty("terraforge.gpu","cpu");float[] expected=graph.evaluate(512,512,62,42,graph.output(),()->false).output();
        System.setProperty("terraforge.gpu","auto");float[] actual=graph.evaluate(512,512,62,42,graph.output(),()->false).output();
        if(GpuGeneration.jobs()!=0||!Arrays.equals(expected,actual)||!GpuGeneration.status().startsWith("CPU fallback"))throw new AssertionError("Fallback failed: "+GpuGeneration.status());
        var autoFolder=RegionMap.folderFor(graph,2048,42,62);System.setProperty("terraforge.gpu","cpu");var cpuFolder=RegionMap.folderFor(graph,2048,42,62);if(autoFolder.equals(cpuFolder))throw new AssertionError("CPU and GPU build caches must be distinct");
        System.out.println("PASS: unavailable GPU/runtime falls back to identical CPU output; "+GpuGeneration.status());
    }
}
