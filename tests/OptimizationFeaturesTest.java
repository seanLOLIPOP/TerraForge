import java.util.*;
public class OptimizationFeaturesTest {
    static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
    public static void main(String[] args){
        for(long seed:new long[]{0,4242,Long.MIN_VALUE,Long.MAX_VALUE})for(double z:new double[]{-19.4,0,128.72}){
            double[] x=new double[1031],out=new double[1031];for(int i=0;i<x.length;i++)x[i]=(i-500)*.037;VectorNoise.samples(x,z,seed,out);
            for(int i=0;i<x.length;i++)check(Double.doubleToLongBits(out[i])==Double.doubleToLongBits(TerraForge.sample(x[i],z,seed)),"SIMD sample "+i);
        }
        System.setProperty("terraforge.gpu","cpu");
        for(String name:new String[]{"Island mountains","Rugged mountains"}){
            var graph=TerrainGraph.template(name);System.setProperty("terraforge.simd","false");GraphStageCache.clear();var scalar=graph.evaluateForBuild(128,2048,62,4242,graph.output(),()->false);
            System.setProperty("terraforge.simd","true");GraphStageCache.clear();var vector=graph.evaluateForBuild(128,2048,62,4242,graph.output(),()->false);check(Arrays.equals(scalar.output(),vector.output()),"SIMD graph "+name);
        }
        var graph=TerrainGraph.template("Island mountains");GraphStageCache.clear();var first=graph.evaluateForBuild(128,2048,62,4242,graph.output(),()->false);long before=GraphStageCache.hits();var second=graph.evaluateForBuild(128,2048,62,4242,graph.output(),()->false);check(GraphStageCache.hits()>before&&Arrays.equals(first.output(),second.output()),"stage reuse");
        first.output()[0]=999;var third=graph.evaluateForBuild(128,2048,62,4242,graph.output(),()->false);check(Arrays.equals(second.output(),third.output()),"cache snapshot isolation");
        String key=GraphStageCache.key(graph,graph.output(),128,2048,62,4242);graph.nodes.get(1).x+=200;check(key.equals(GraphStageCache.key(graph,graph.output(),128,2048,62,4242)),"editor move keeps cache");graph.nodes.get(1).values.put("height",27.0);check(!key.equals(GraphStageCache.key(graph,graph.output(),128,2048,62,4242)),"upstream change invalidates cache");
        var climate=graph.add(TerrainGraph.Type.CLIMATE,0,0);graph.connect(graph.nodes.get(graph.output()).input[0],climate.id,0);GraphStageCache.clear();var cold=graph.evaluateForBuild(128,2048,62,4242,climate.id,()->false);var warm=graph.evaluateForBuild(128,2048,62,4242,climate.id,()->false);check(Arrays.equals(cold.output(),warm.output())&&Arrays.equals(cold.environment().height,warm.environment().height)&&Arrays.equals(cold.environment().rain,warm.environment().rain),"climate cache preserves its output field and terrain/environment");
        System.out.println("PASS: exact SIMD samples/graphs, upstream cache invalidation, position-independent keys and isolated cache snapshots");
    }
}
