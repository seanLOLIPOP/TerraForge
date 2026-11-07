import java.nio.file.*;
import java.util.*;

/** Sample real region output at 16384 scale without writing a million chunks. */
public class LargeMapRegionBenchmark {
    public static void main(String[] args)throws Exception{
        LargeWorldBuilder.configure(16384);LargeWorldBuilder.SEED=4242;LargeWorldBuilder.SEA=62;
        LargeWorldBuilder.ACTIVE_GRAPH=TerrainGraph.template("Island mountains");
        var global=LargeWorldBuilder.layout();LargeWorldBuilder.seaDistance(global);LargeWorldBuilder.layoutSlope(global);
        Path root=Files.createTempDirectory("terraforge-large-region-benchmark-");
        for(int run=0;run<8;run++){
            long start=System.nanoTime();var tile=LargeWorldBuilder.tile(global,15,15);long generated=System.nanoTime();
            LargeWorldBuilder.region(tile,root.resolve("r-"+run+".mca"),15,15);
            System.out.printf(Locale.ROOT,"LARGE_REGION run=%d tile=%.3fs encode-save=%.3fs total=%.3fs file=%s%n",run,(generated-start)/1e9,(System.nanoTime()-generated)/1e9,(System.nanoTime()-start)/1e9,root.resolve("r-"+run+".mca"));
        }
    }
}
