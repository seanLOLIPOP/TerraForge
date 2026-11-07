import java.nio.file.*;
import java.util.*;
public class GpuGraphIntegrationTest {
    static void equal(float[] a,float[] b,String label){if(a==null||b==null){if(a!=b)throw new AssertionError(label+" missing channel");return;}if(!Arrays.equals(a,b))throw new AssertionError(label+" differs from the pre-pipeline GPU result");}
    public static void main(String[] args)throws Exception{
        var graph=GraphEditor.readFile(Path.of(args.length>0?args[0]:"examples/Island-mountains.tgraph"));int n=args.length>1?Integer.parseInt(args[1]):512;
        System.setProperty("terraforge.gpu","auto");System.setProperty("terraforge.gpuPipeline","false");var old=graph.evaluateForBuild(n,16384,62,4242,graph.output(),()->false);
        System.setProperty("terraforge.gpuPipeline","true");var next=graph.evaluateForBuild(n,16384,62,4242,graph.output(),()->false);
        equal(old.output(),next.output(),"height");var a=old.environment();var b=next.environment();
        equal(a.water,b.water,"water");equal(a.snow,b.snow,"snow");equal(a.ice,b.ice,"ice");equal(a.temperature,b.temperature,"temperature");equal(a.rain,b.rain,"rain");equal(a.river,b.river,"river");equal(a.glacier,b.glacier,"glacier");
        if(!Arrays.equals(a.material,b.material)||!Arrays.equals(a.biome,b.biome)||!a.trees.equals(b.trees))throw new AssertionError("Categories or object placements changed");
        if(GpuGeneration.residentBytes()!=0||!GpuGeneration.status().startsWith("OpenCL"))throw new AssertionError("Pipeline failed or leaked buffers: "+GpuGeneration.status());
        System.out.println("PASS: supplied map graph matches pre-pipeline GPU heights, environmental channels, surface categories, and objects at "+n+" cells");
    }
}
