import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.*;

public class RegionBatchWriteTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void reference(TerraForge.World world,Path file,int rx,int rz)throws Exception{
        var forest=new TreeLibrary.SpatialIndex(world);
        try(var output=new RandomAccessFile(file.toFile(),"rw")){
            output.setLength(8192);int sector=2;
            for(int index=0;index<1024;index++){
                byte[] data=TerraForge.chunk(world,index%32,index/32,forest,rx*512,rz*512);
                var bytes=new ByteArrayOutputStream();var compressor=new Deflater(1);
                try(var compressed=new DeflaterOutputStream(bytes,compressor)){compressed.write(data);}finally{compressor.end();}
                byte[] encoded=bytes.toByteArray();int sectors=(encoded.length+5+4095)/4096;
                output.seek(index*4L);output.writeInt((sector<<8)|sectors);
                output.seek(4096+index*4L);output.writeInt((int)(System.currentTimeMillis()/1000));
                output.seek(sector*4096L);output.writeInt(encoded.length+1);output.writeByte(2);output.write(encoded);
                sector+=sectors;output.setLength(sector*4096L);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("terraforge-batch-write-");
        var world=new TerraForge.World(512,62,42);
        for(int z=0;z<512;z++)for(int x=0;x<512;x++)world.height[z*512+x]=80+(float)(Math.sin(x*.03)*8+Math.cos(z*.02)*6);
        for(int z=128;z<192;z++)for(int x=128;x<192;x++)world.height[z*512+x]=1000;
        world.height[128*512+128]=1024;world.environment.water=EnvironmentMap.filled(512*512,62);
        world.environment.water[20*512+20]=100;world.environment.snow=new float[512*512];
        for(int z=128;z<192;z++)for(int x=128;x<192;x++)world.environment.snow[z*512+x]=.5f;
        world.environment.trees=List.of(new TreeLibrary.Placement(20,400,400,1));ExtendedHeight.configure(world);
        Path old=root.resolve("reference.mca"),next=root.resolve("batch.mca");
        long start=System.nanoTime();reference(world,old,2,3);double oldSeconds=(System.nanoTime()-start)/1e9;
        start=System.nanoTime();CpuWork.region(()->RegionBatchWriter.write(world,next,2,3,()->false));double newSeconds=(System.nanoTime()-start)/1e9;
        byte[] expected=Files.readAllBytes(old),actual=Files.readAllBytes(next);
        Arrays.fill(expected,4096,8192,(byte)0);Arrays.fill(actual,4096,8192,(byte)0);
        check(Arrays.equals(expected,actual),"all 1024 compressed chunks, offsets, and padding match the old writer");
        check(!Files.exists(root.resolve("batch.mca.tmp")),"successful region has no partial file");
        Path cancelled=root.resolve("cancelled.mca");AtomicInteger checks=new AtomicInteger();boolean stopped=false;
        try{RegionBatchWriter.write(world,cancelled,0,0,()->checks.incrementAndGet()>80);}catch(CancellationException expectedCancellation){stopped=true;}
        check(stopped&&!Files.exists(cancelled)&&!Files.exists(root.resolve("cancelled.mca.tmp")),"cancelled batch drains and removes partial output");
        var workers=ConcurrentHashMap.<String>newKeySet();
        CpuWork.region(()->CpuWork.batch(64,()->false,index->{workers.add(Thread.currentThread().getName());java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);}));
        check(CpuWork.threads()==1||workers.size()>1,"chunk batches parallelize inside region workers");
        System.out.printf(Locale.ROOT,"PASS: byte-identical region including Y1024, snow, water, trees and world offsets; cancellation cleanup; %d batch CPU workers; serial %.3fs / batch %.3fs%n",workers.size(),oldSeconds,newSeconds);
    }
}
