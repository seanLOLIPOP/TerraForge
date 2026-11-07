import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
public class FullWorldBatchTest {
    public static void main(String[] args)throws Exception{
        var world=new TerraForge.World(1024,62,4242);for(int z=0;z<1024;z++)for(int x=0;x<1024;x++)world.height[z*1024+x]=80+(float)(Math.sin(x*.03)*12+Math.cos(z*.02)*8);world.environment.trees=List.of(new TreeLibrary.Placement(20,510,510,1));Path root=Files.createTempDirectory("terraforge-full-batch-");Path folder=root.resolve("World");TerraForge.writeWorld(world,folder);var forest=new TreeLibrary.SpatialIndex(world);
        for(int rz=0;rz<2;rz++)for(int rx=0;rx<2;rx++)try(var file=new RandomAccessFile(folder.resolve("region/r."+rx+"."+rz+".mca").toFile(),"r")){
            for(int index:new int[]{0,31,32,33,511,992,1023}){file.seek(index*4L);int location=file.readInt();file.seek((location>>>8)*4096L);int length=file.readInt();if(file.readByte()!=2)throw new AssertionError("compression type");byte[] compressed=new byte[length-1];file.readFully(compressed);byte[] actual;try(var in=new InflaterInputStream(new ByteArrayInputStream(compressed))){actual=in.readAllBytes();}byte[] expected=TerraForge.chunk(world,rx*32+index%32,rz*32+index/32,forest);if(!Arrays.equals(expected,actual))throw new AssertionError("global chunk offset "+rx+","+rz+" "+index);}
        }
        System.out.println("PASS: direct 1024-world batch export preserves chunk coordinates, absolute noise, and cross-region trees");
    }
}
