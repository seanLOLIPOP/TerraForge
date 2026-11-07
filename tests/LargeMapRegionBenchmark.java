import java.nio.file.*;
import java.util.*;

/** Sample real region output at 16384 scale without writing a million chunks. */
public class LargeMapRegionBenchmark {
    public static void main(String[] args)throws Exception{
        LargeWorldBuilder.configure(16384);LargeWorldBuilder.SEED=4242;LargeWorldBuilder.SEA=62;
        LargeWorldBuilder.ACTIVE_GRAPH=args.length>0?GraphEditor.readFile(Path.of(args[0])):TerrainGraph.template("Island mountains");
        int rx=args.length>1?Integer.parseInt(args[1]):15,rz=args.length>2?Integer.parseInt(args[2]):15,runs=args.length>3?Integer.parseInt(args[3]):8;
        long layoutStart=System.nanoTime();
        var global=LargeWorldBuilder.layout();LargeWorldBuilder.seaDistance(global);LargeWorldBuilder.layoutSlope(global);
        System.out.printf(Locale.ROOT,"LAYOUT elapsed=%.3fs%n",(System.nanoTime()-layoutStart)/1e9);
        Path root=Files.createTempDirectory("terraforge-large-region-benchmark-");
        for(int run=0;run<runs;run++){
            long start=System.nanoTime();var tile=LargeWorldBuilder.tile(global,rx,rz);long generated=System.nanoTime();
            LargeWorldBuilder.region(tile,root.resolve("r-"+run+".mca"),rx,rz);
            System.out.printf(Locale.ROOT,"LARGE_REGION run=%d tile=%.3fs encode-save=%.3fs total=%.3fs file=%s%n",run,(generated-start)/1e9,(System.nanoTime()-generated)/1e9,(System.nanoTime()-start)/1e9,root.resolve("r-"+run+".mca"));
        }
    }
}
