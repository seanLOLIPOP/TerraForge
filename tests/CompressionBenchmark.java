import java.util.*;
public class CompressionBenchmark {
    public static void main(String[] args)throws Exception{
        var world=new TerraForge.World(512,62,4242);for(int z=0;z<512;z++)for(int x=0;x<512;x++)world.height[z*512+x]=250+(float)(Math.sin(x*.04)*100+Math.cos(z*.03)*75);ExtendedHeight.configure(world);var forest=new TreeLibrary.SpatialIndex(world);
        for(int level:new int[]{0,1,3}){System.setProperty("terraforge.compression",Integer.toString(level));for(int i=0;i<64;i++)RegionBatchWriter.encode(world,forest,i,0,0);}
        for(int run=0;run<3;run++)for(int j=0;j<3;j++){int level=new int[]{0,1,3}[(j+run)%3];System.setProperty("terraforge.compression",Integer.toString(level));long start=System.nanoTime(),bytes=0;for(int i=0;i<64;i++)bytes+=RegionBatchWriter.encode(world,forest,i,0,0).length;System.out.printf(Locale.ROOT,"COMPRESSION level=%d run=%d elapsed=%.3fs bytes=%d%n",level,run,(System.nanoTime()-start)/1e9,bytes);}
    }
}
