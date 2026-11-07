import java.util.*;
public class NoiseBenchmark {
    public static void main(String[] args){
        System.setProperty("terraforge.gpu","cpu");System.setProperty("terraforge.stageCache","false");var g=new TerrainGraph();var source=g.add(TerrainGraph.Type.NOISE,0,0);var output=g.add(TerrainGraph.Type.OUTPUT,200,0);g.connect(source.id,output.id,0);float[] reference=null;
        for(boolean simd:new boolean[]{false,true}){System.setProperty("terraforge.simd",Boolean.toString(simd));for(int run=0;run<7;run++){long start=System.nanoTime();float[] result=g.evaluateForBuild(1024,16384,62,4242,output.id,()->false).output();if(reference==null)reference=result;else if(!Arrays.equals(reference,result))throw new AssertionError("noise differs");System.out.printf(Locale.ROOT,"NOISE simd=%s run=%d elapsed=%.3fms%n",simd,run,(System.nanoTime()-start)/1e6);}}
    }
}
