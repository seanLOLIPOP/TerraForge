import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Reuse encoded output only when every block-affecting input is identical. */
final class RegionReuse {
    private static int writes;
    private record Reference(Path file,long size,String digest){}
    private static String digest(Path file)throws IOException{
        try{var digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];long offset=0;try(var in=Files.newInputStream(file)){for(int count;(count=in.read(buffer))>=0;){int first=(int)Math.max(0,4096-offset),last=(int)Math.min(count,8192-offset);if(first<last)Arrays.fill(buffer,first,last,(byte)0);digest.update(buffer,0,count);offset+=count;}}return HexFormat.of().formatHex(digest.digest());}catch(NoSuchAlgorithmException error){throw new AssertionError(error);}
    }
    private static Reference reference(Path file){try(var in=new DataInputStream(Files.newInputStream(file))){if(!in.readUTF().equals("TFREGIONREF1"))return null;return new Reference(Path.of(in.readUTF()),in.readLong(),in.readUTF());}catch(IOException|RuntimeException unavailable){return null;}}
    static Path root(){return Path.of(System.getProperty("terraforge.regionCache",AppHome.data("encoded-regions").toString()));}
    static String key(TerraForge.World w,int rx,int rz)throws IOException{
        try{
            var hash=MessageDigest.getInstance("SHA-256");var buffer=ByteBuffer.allocate(65536);
            hash.update(("v1|"+GpuGeneration.mode()+"|"+System.getProperty("terraforge.gpuDevice","")+"|"+w.settings+"|"+w.size+"|"+w.seed+"|"+w.sea+"|"+w.buildHeight+"|"+rx+"|"+rz+"|"+TerraForge.groundCover+"|"+RegionBatchWriter.compressionLevel()+"|"+Boolean.toString(FastGpu.requested())).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var e=w.environment;for(float[] a:new float[][]{w.height,e.water,e.snow,e.ice,e.temperature,e.rain,e.river,e.glacier}){
                hash.update((byte)(a==null?0:1));if(a==null)continue;for(float value:a){if(buffer.remaining()<4){hash.update(buffer.array(),0,buffer.position());buffer.clear();}buffer.putInt(Float.floatToRawIntBits(value));}hash.update(buffer.array(),0,buffer.position());buffer.clear();}
            for(byte[] a:new byte[][]{w.material,e.material,e.biome}){hash.update((byte)(a==null?0:1));if(a!=null)hash.update(a);}
            try(var out=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),hash))){TreeLibrary.writePlacements(out,e.trees);}
            return HexFormat.of().formatHex(hash.digest());
        }catch(NoSuchAlgorithmException ex){throw new AssertionError(ex);}
    }
    static boolean restore(String key,Path file)throws IOException{
        if(!Boolean.parseBoolean(System.getProperty("terraforge.reuseRegions","true")))return false;Path cached=root().resolve(key+".mca"),tmp=file.resolveSibling(file.getFileName()+".reuse.tmp");
        try{var ref=reference(root().resolve(key+".ref"));Path source=Files.isRegularFile(cached)?cached:ref==null?null:ref.file;if(source==null||!Files.isRegularFile(source)||Files.size(source)<8192||Files.size(source)%4096!=0)return false;Files.copy(source,tmp,StandardCopyOption.REPLACE_EXISTING);if(ref!=null&&(Files.size(tmp)!=ref.size||!digest(tmp).equals(ref.digest))){Files.deleteIfExists(tmp);return false;}Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);return true;}
        catch(IOException unavailable){Files.deleteIfExists(tmp);return false;}
    }
    static synchronized void remember(String key,Path file){
        if(!Boolean.parseBoolean(System.getProperty("terraforge.reuseRegions","true")))return;
        try{Path root=root();Files.createDirectories(root);Path pointer=Files.createTempFile(root,"reference-",".tmp");try{try(var out=new DataOutputStream(Files.newOutputStream(pointer))){out.writeUTF("TFREGIONREF1");out.writeUTF(file.toAbsolutePath().normalize().toString());out.writeLong(Files.size(file));out.writeUTF(digest(file));}Files.move(pointer,root.resolve(key+".ref"),StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(pointer);}
            Path target=root.resolve(key+".mca");if(!Files.exists(target)){Path tmp=Files.createTempFile(root,"region-",".tmp");try{Files.copy(file,tmp,StandardCopyOption.REPLACE_EXISTING);Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(tmp);}}
            long limit=Math.max(64L<<20,Long.getLong("terraforge.regionCacheMiB",1024)*1024*1024);try(var stream=Files.list(root)){var files=stream.filter(p->p.getFileName().toString().matches("[a-f0-9]{64}\\.mca")).sorted(Comparator.comparingLong(p->{try{return Files.getLastModifiedTime(p).toMillis();}catch(IOException e){return 0;}})).toList();long total=0;for(var p:files)total+=Files.size(p);for(var p:files){if(total<=limit)break;long length=Files.size(p);if(Files.deleteIfExists(p))total-=length;}}
            if(++writes%256==0)try(var stream=Files.list(root)){var refs=stream.filter(p->p.getFileName().toString().matches("[a-f0-9]{64}\\.ref")).sorted(Comparator.comparingLong(p->{try{return Files.getLastModifiedTime(p).toMillis();}catch(IOException e){return 0;}})).toList();for(int i=0;i<refs.size()-8192;i++)Files.deleteIfExists(refs.get(i));}
        }catch(IOException cacheFailure){System.err.println("Region cache unavailable: "+cacheFailure.getMessage());}
    }
}
