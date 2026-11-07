import java.util.*;
import java.lang.management.*;
import java.nio.file.*;
public class GenerationBenchmark {
    public static void main(String[] args)throws Exception{
        int n=Integer.parseInt(args[0]);
        TerrainGraph graph=args.length>1?GraphEditor.readFile(Path.of(args[1])):TerrainGraph.template("Island mountains");
        var os=(com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
        for(int run=0;run<4;run++){
            long start=System.nanoTime(),cpu=os.getProcessCpuTime();
            var result=graph.evaluate(n,n,62,42,graph.output(),()->false);
            double elapsed=(System.nanoTime()-start)/1e9,cpuSeconds=(os.getProcessCpuTime()-cpu)/1e9;
            System.out.printf(Locale.ROOT,"run=%d n=%d elapsed=%.3fs cpu=%.3fs average-cores=%.2f hash=%s%n",run,n,elapsed,cpuSeconds,cpuSeconds/elapsed,GenerationProbe.hash(result.environment()));
        }
    }
}
