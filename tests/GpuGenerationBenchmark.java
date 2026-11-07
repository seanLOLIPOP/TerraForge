import java.util.*;
public class GpuGenerationBenchmark {
    public static void main(String[] args){
        String mode=args.length>0?args[0]:"auto";System.setProperty("terraforge.gpu",mode);
        int n=args.length>1?Integer.parseInt(args[1]):2048;
        var graph=TerrainGraph.template("Island mountains");
        for(int run=0;run<4;run++){
            long before=GpuGeneration.jobs(),start=System.nanoTime();
            var result=graph.evaluate(n,n,62,4242,graph.output(),()->false);
            double ms=(System.nanoTime()-start)/1e6;double checksum=0;for(int i=0;i<result.output().length;i+=397)checksum+=result.output()[i];
            System.out.printf(Locale.ROOT,"GPU_BENCHMARK mode=%s size=%d run=%d elapsed=%.3fms gpuJobs=%d checksum=%.6f backend=%s%n",mode,n,run,ms,GpuGeneration.jobs()-before,checksum,GpuGeneration.status());
        }
    }
}
