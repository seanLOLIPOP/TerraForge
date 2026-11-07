import java.nio.file.*;
import java.util.*;
public class GraphCacheBenchmark {
    public static void main(String[] args)throws Exception{
        LargeWorldBuilder.configure(16384);LargeWorldBuilder.SEED=4242;LargeWorldBuilder.SEA=62;LargeWorldBuilder.ACTIVE_GRAPH=GraphEditor.readFile(Path.of(args[0]));GraphStageCache.clear();
        float[] reference=null;for(int run=0;run<3;run++){long start=System.nanoTime(),hits=GraphStageCache.hits();var e=LargeWorldBuilder.layout();if(reference==null)reference=e.height.clone();else if(!Arrays.equals(reference,e.height))throw new AssertionError("cached layout changed");System.out.printf(Locale.ROOT,"GRAPH_CACHE run=%d elapsed=%.3fs hits=%d%n",run,(System.nanoTime()-start)/1e9,GraphStageCache.hits()-hits);}
    }
}
