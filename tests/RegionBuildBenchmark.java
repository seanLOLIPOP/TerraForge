import java.nio.file.*;
import java.lang.management.*;
import java.util.*;
public class RegionBuildBenchmark {
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("terraforge-region-benchmark-");
        var graph=TerrainGraph.template("Island mountains");
        var os=(com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
        int runs=args.length==0?1:Integer.parseInt(args[0]);
        for(int run=0;run<runs;run++){
            long start=System.nanoTime(),cpu=os.getProcessCpuTime();
            LargeWorldBuilder.exportGraph(root.resolve("World-"+run),graph,4242,62,2048);
            double elapsed=(System.nanoTime()-start)/1e9,cpuSeconds=(os.getProcessCpuTime()-cpu)/1e9;
            System.out.printf(Locale.ROOT,"REGION_BENCHMARK run=%d elapsed=%.3fs cpu=%.3fs average-cores=%.2f world=%s%n",run,elapsed,cpuSeconds,cpuSeconds/elapsed,root);
        }
    }
}
