import java.io.*;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.CancellationException;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.zip.*;

/** Encode bounded batches on the shared CPU pool, then commit each region in chunk order. */
final class RegionBatchWriter {
    static final int CHUNKS_PER_BATCH=64;
    private static final ThreadLocal<Integer> LEVEL=new ThreadLocal<>();
    static int compressionLevel(){return LEVEL.get()!=null?LEVEL.get():Math.max(0,Math.min(9,Integer.getInteger("terraforge.compression",1)));}
    static final class LevelScope implements AutoCloseable {final Integer previous=LEVEL.get();LevelScope(int level){LEVEL.set(level);}public void close(){if(previous==null)LEVEL.remove();else LEVEL.set(previous);}}
    static byte[] encode(TerraForge.World world,TreeLibrary.SpatialIndex forest,int index,int originX,int originZ)throws IOException{
        return encode(world,forest,index,originX,originZ,compressionLevel());
    }
    private static byte[] encode(TerraForge.World world,TreeLibrary.SpatialIndex forest,int index,int originX,int originZ,int level)throws IOException{
        boolean fullWorld=world.size>512;byte[] data=TerraForge.chunk(world,index%32+(fullWorld?originX/16:0),index/32+(fullWorld?originZ/16:0),forest,fullWorld?0:originX,fullWorld?0:originZ);
        return ChunkScratch.get().compress(data,level);
    }
    private static byte[][] batch(TerraForge.World world,TreeLibrary.SpatialIndex forest,int first,int rx,int rz,int level,BooleanSupplier stop){int count=Math.min(CHUNKS_PER_BATCH,1024-first);byte[][] bytes=new byte[count][];CpuWork.batch(count,stop,index->{try{bytes[index]=encode(world,forest,first+index,rx*512,rz*512,level);}catch(IOException error){throw new UncheckedIOException(error);}});return bytes;}
    private static byte[][] await(Future<byte[][]> future)throws IOException{try{return future.get();}catch(InterruptedException error){Thread.currentThread().interrupt();throw new CancellationException();}catch(ExecutionException error){if(error.getCause() instanceof UncheckedIOException io)throw io.getCause();if(error.getCause() instanceof RuntimeException runtime)throw runtime;throw new IOException(error.getCause());}}
    static void write(TerraForge.World world,Path file,int rx,int rz,BooleanSupplier stop)throws IOException{
        Path temporary=file.resolveSibling(file.getFileName()+".tmp");
        var forest=new TreeLibrary.SpatialIndex(world);Thread owner=Thread.currentThread();boolean complete=false;
        int level=compressionLevel();
        var producer=Executors.newSingleThreadExecutor(r->{var thread=new Thread(r,"TerraForge-chunk-prefetch");thread.setDaemon(true);return thread;});
        try{
            ByteBuffer header=ByteBuffer.allocate(8192);byte[] padding=new byte[4096];
            try(var output=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary),1<<20))){
                output.write(header.array());int sector=2;
                Future<byte[][]> pending=producer.submit(()->batch(world,forest,0,rx,rz,level,stop));
                for(int first=0;first<1024;first+=CHUNKS_PER_BATCH){
                    if(owner.isInterrupted()||stop.getAsBoolean())throw new CancellationException();
                    int start=first,count=Math.min(CHUNKS_PER_BATCH,1024-first);byte[][] compressed=await(pending);
                    int next=first+CHUNKS_PER_BATCH;if(next<1024)pending=producer.submit(()->batch(world,forest,next,rx,rz,level,stop));
                    for(int index=0;index<count;index++){
                        if(owner.isInterrupted()||stop.getAsBoolean())throw new CancellationException();
                        byte[] data=compressed[index];int chunk=start+index,sectors=(data.length+5+4095)/4096;
                        if(sectors>255)throw new IOException("Chunk is too large for a region file");
                        header.putInt(chunk*4,(sector<<8)|sectors);
                        header.putInt(4096+chunk*4,(int)(System.currentTimeMillis()/1000));
                        output.writeInt(data.length+1);output.writeByte(2);output.write(data);
                        output.write(padding,0,sectors*4096-data.length-5);
                        sector+=sectors;compressed[index]=null;
                    }
                }
            }
            try(var channel=FileChannel.open(temporary,StandardOpenOption.WRITE)){while(header.hasRemaining())channel.write(header);}
            Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);complete=true;
        }finally{producer.shutdownNow();boolean interrupted=false;while(!producer.isTerminated())try{producer.awaitTermination(1,TimeUnit.SECONDS);}catch(InterruptedException error){interrupted=true;}if(interrupted)Thread.currentThread().interrupt();if(!complete)Files.deleteIfExists(temporary);}
    }
}
