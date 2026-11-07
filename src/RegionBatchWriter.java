import java.io.*;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.zip.*;

/** Encode bounded batches on the shared CPU pool, then commit each region in chunk order. */
final class RegionBatchWriter {
    static final int CHUNKS_PER_BATCH=64;
    static byte[] encode(TerraForge.World world,TreeLibrary.SpatialIndex forest,int index,int originX,int originZ)throws IOException{
        byte[] data=TerraForge.chunk(world,index%32,index/32,forest,originX,originZ);
        var bytes=new ByteArrayOutputStream();var compressor=new Deflater(1);
        try(var output=new DeflaterOutputStream(bytes,compressor)){output.write(data);}
        finally{compressor.end();}
        return bytes.toByteArray();
    }
    static void write(TerraForge.World world,Path file,int rx,int rz,BooleanSupplier stop)throws IOException{
        Path temporary=file.resolveSibling(file.getFileName()+".tmp");
        var forest=new TreeLibrary.SpatialIndex(world);Thread owner=Thread.currentThread();boolean complete=false;
        try{
            ByteBuffer header=ByteBuffer.allocate(8192);byte[] padding=new byte[4096];
            try(var output=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary),1<<20))){
                output.write(header.array());int sector=2;
                for(int first=0;first<1024;first+=CHUNKS_PER_BATCH){
                    if(owner.isInterrupted()||stop.getAsBoolean())throw new CancellationException();
                    int start=first,count=Math.min(CHUNKS_PER_BATCH,1024-first);byte[][] compressed=new byte[count][];
                    try{CpuWork.batch(count,stop,index->{
                        try{compressed[index]=encode(world,forest,start+index,rx*512,rz*512);}
                        catch(IOException error){throw new UncheckedIOException(error);}
                    });}catch(UncheckedIOException error){throw error.getCause();}
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
        }finally{if(!complete)Files.deleteIfExists(temporary);}
    }
}
