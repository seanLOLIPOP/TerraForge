import java.nio.file.*;
import java.util.*;
public class RockLayersBenchmark {
    public static void main(String[] args)throws Exception{
        System.setProperty("terraforge.gpu","gpu");var world=new TerraForge.World(512,62,4242);Arrays.fill(world.height,350);world.environment.material=new byte[512*512];Arrays.fill(world.environment.material,(byte)3);ExtendedHeight.configure(world);Path root=Files.createTempDirectory("terraforge-rock-benchmark-");byte[] reference=null;
        for(int run=0;run<4;run++)for(boolean gpu:new boolean[]{false,true}){
            System.setProperty("terraforge.fastGpu",Boolean.toString(gpu));world.gpuStrata=null;long start=System.nanoTime();if(gpu)GpuRockLayers.prepare(world,0,0,()->false);Path file=root.resolve(run+"-"+gpu+".mca");RegionBatchWriter.write(world,file,0,0,()->false);double elapsed=(System.nanoTime()-start)/1e9;byte[] bytes=Files.readAllBytes(file);Arrays.fill(bytes,4096,8192,(byte)0);if(reference==null)reference=bytes;else if(!Arrays.equals(reference,bytes))throw new AssertionError("rock export differs");System.out.printf(Locale.ROOT,"ROCK_EXPORT run=%d gpu=%s total=%.3fs%n",run,gpu,elapsed);
        }
    }
}
