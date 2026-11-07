import java.io.*;
import java.util.zip.*;

/** Bounded scratch storage owned by an encoder thread, never shared with returned bytes. */
final class ChunkScratch {
    private static final ThreadLocal<ChunkScratch> LOCAL=ThreadLocal.withInitial(ChunkScratch::new);
    final int[] blocks=new int[4096];final Deflater deflater=new Deflater(1);
    ByteArrayOutputStream nbt=new ByteArrayOutputStream(16384),compressed=new ByteArrayOutputStream(4096);
    static ChunkScratch get(){return LOCAL.get();}
    ByteArrayOutputStream nbt(){if(nbt.size()>4*1024*1024)nbt=new ByteArrayOutputStream(16384);nbt.reset();return nbt;}
    byte[] compress(byte[] input,int level)throws IOException{
        if(compressed.size()>4*1024*1024)compressed=new ByteArrayOutputStream(4096);compressed.reset();deflater.reset();deflater.setLevel(level);
        var output=new DeflaterOutputStream(compressed,deflater,8192);output.write(input);output.finish();return compressed.toByteArray();
    }
}
