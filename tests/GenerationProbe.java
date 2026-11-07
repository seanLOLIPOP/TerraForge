import java.util.*;
import java.nio.*;
import java.security.*;
import java.nio.file.*;
import java.lang.management.*;
/** Run the same probe against baseline and new JARs; hashes must match. */
public class GenerationProbe {
    static String hash(EnvironmentMap environment)throws Exception{
        var digest=MessageDigest.getInstance("SHA-256");var bytes=ByteBuffer.allocate(4);
        for(float[] field:new float[][]{environment.height,environment.water,environment.snow,environment.ice,environment.glacier,environment.rain,environment.temperature}){
            digest.update((byte)(field==null?0:1));if(field!=null)for(float value:field){bytes.clear();bytes.putFloat(value);digest.update(bytes.array());}
        }
        if(environment.material!=null)digest.update(environment.material);if(environment.biome!=null)digest.update(environment.biome);
        return HexFormat.of().formatHex(digest.digest());
    }
    public static void main(String[] args)throws Exception{
        int n=args.length>1?Integer.parseInt(args[1]):512;
        var os=(com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
        long start=System.nanoTime(),cpu=os.getProcessCpuTime();EnvironmentMap result;
        if(args[0].equals("generate"))result=TerraForge.generateWorld(n,62,42,170,"Island").environment;
        else {
            TerrainGraph graph=args[0].equals("default")?TerrainGraph.template("Island mountains"):GraphEditor.readFile(Path.of(args[0]));
            result=graph.evaluate(n,n,62,42,graph.output(),()->false).environment();
        }
        double elapsed=(System.nanoTime()-start)/1e9,cpuSeconds=(os.getProcessCpuTime()-cpu)/1e9;
        System.out.printf(Locale.ROOT,"%s n=%d elapsed=%.3fs cpu=%.3fs average-cores=%.2f SHA256=%s%n",args[0],n,elapsed,cpuSeconds,cpuSeconds/elapsed,hash(result));
    }
}
