import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.opengl.GL;
import java.util.*;

/** Real OpenGL integration: camera motion must keep existing meshes and upload only new tiles. */
public class WholeMapStreamingTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void settle(WholeMapStream stream,double x,double z,int radius)throws Exception{
        long deadline=System.nanoTime()+30_000_000_000L;
        do{
            stream.update(x,z,radius);
            Throwable error=stream.owner.failure.getAndSet(null);if(error!=null)throw new AssertionError(error);
            if(stream.settled())return;
            Thread.sleep(2);
        }while(System.nanoTime()<deadline);
        throw new AssertionError("Streaming timed out");
    }
    public static void main(String[] args)throws Exception{
        for(int size:new int[]{32,256,1024,16384})for(double camera:new double[]{0,size/2.,size-1}){
            var plan=WholeMapStream.plan(size,512,camera,camera);
            check(plan.stream().allMatch(c->c.x()>=0&&c.z()>=0&&c.x()<size&&c.z()<size),"tiles stay in map");
            check(plan.getFirst().distance(camera,camera)<=plan.getLast().distance(camera,camera),"nearest tiles first");
        }
        check(WholeMapStream.plan(16384,512,8192,8192).size()==256,"1024 block target span");
        check(WholeMapStream.plan(16384,768,8192,8192).size()==576,"1536 block maximum span");
        check(glfwInit(),"GLFW init");glfwDefaultWindowHints();glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(64,64,"stream test",0,0);check(window!=0,"OpenGL context");glfwMakeContextCurrent(window);GL.createCapabilities();
        var owner=new MinecraftPreview();owner.wholeMap=true;owner.world=new TerraForge.World(1024,62,42);
        Arrays.fill(owner.world.height,100);owner.forest=new TreeLibrary.SpatialIndex(owner.world);
        try(var assets=new MinecraftAssets(MinecraftAssets.locate())){
            owner.assets=assets;var stream=new WholeMapStream(owner);
            try{
                long start=System.nanoTime();settle(stream,256,256,128);
                check(stream.tiles.size()==16&&stream.builds==16,"initial detail tiles");
                var first=new HashMap<>(stream.tiles);long before=stream.builds;
                settle(stream,320,256,128);
                check(stream.builds-before==4,"moving 64 blocks loads one strip, not a whole new mesh");
                for(var entry:first.entrySet())check(stream.tiles.get(entry.getKey())==entry.getValue(),"existing GPU meshes retained");
                before=stream.builds;settle(stream,256,256,128);check(stream.builds==before,"backtracking reuses cached tiles");
                check(stream.tiles.values().stream().allMatch(t->t.draws().size()<=2),"tile geometry merged into at most two draws");
                check(stream.bytes<=WholeMapStream.BUDGET,"GPU byte budget");check(glGetError()==GL_NO_ERROR,"uploads have no OpenGL errors");
                settle(stream,896,896,128);check(!stream.tiles.containsKey(first.keySet().iterator().next()),"distant tiles evicted");
                check(stream.bytes<=WholeMapStream.BUDGET,"budget after teleport");
                System.out.printf(Locale.ROOT,"PASS: 16 initial tiles, 4 new tiles on movement, 0 on backtrack, retained GPU buffers, bounded cache, compact draws; %.3f seconds%n",(System.nanoTime()-start)/1e9);
            }finally{owner.worker.shutdownNow();owner.worker.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS);stream.close();glDeleteTextures(owner.texture);}
            regionCase(assets);
            budgetCase(assets);
        }finally{glfwDestroyWindow(window);GL.setCapabilities(null);glfwTerminate();}
    }
    static void budgetCase(MinecraftAssets assets)throws Exception{
        var owner=new MinecraftPreview();owner.wholeMap=true;owner.assets=assets;
        owner.world=new TerraForge.World(1024,62,42);Arrays.fill(owner.world.height,100);
        owner.forest=new TreeLibrary.SpatialIndex(owner.world);
        long budget=1024*1024;var stream=new WholeMapStream(owner,budget);
        try{
            settle(stream,256,256,128);
            check(stream.tiles.size()==1&&stream.bytes<=budget,"nearby detail fits enforced byte budget");
            long old=stream.tiles.keySet().iterator().next();settle(stream,384,256,128);
            check(stream.tiles.size()==1&&stream.bytes<=budget&&!stream.tiles.containsKey(old),"nearer detail displaces old detail at budget");
            java.nio.ByteBuffer coverage=org.lwjgl.system.MemoryUtil.memAlloc(stream.grid*stream.grid);
            int alignment=glGetInteger(GL_PACK_ALIGNMENT);glPixelStorei(GL_PACK_ALIGNMENT,1);
            try{
                glBindTexture(GL_TEXTURE_2D,stream.coverage);glGetTexImage(GL_TEXTURE_2D,0,GL_RED_INTEGER,GL_UNSIGNED_BYTE,coverage);
                int marked=0;for(int i=0;i<coverage.capacity();i++)if(coverage.get(i)!=0)marked++;
                check(marked==stream.tiles.size(),"overview coverage cleared on eviction");
            }finally{glPixelStorei(GL_PACK_ALIGNMENT,alignment);org.lwjgl.system.MemoryUtil.memFree(coverage);}
            check(glGetError()==GL_NO_ERROR,"budget eviction GL state");
            System.out.println("PASS: enforced mesh byte limit, nearest detail retained, and overview coverage restored on eviction");
        }finally{owner.worker.shutdownNow();owner.worker.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS);stream.close();glDeleteTextures(owner.texture);}
    }
    @SuppressWarnings("unchecked") static void regionCase(MinecraftAssets assets)throws Exception{
        // Supply already-generated region tiles: this test isolates streaming from terrain generation.
        var map=new RegionMap(java.nio.file.Path.of("unused-stream-test"),1024,42,62,null,null,384);
        var field=RegionMap.class.getDeclaredField("tiles");field.setAccessible(true);
        var cache=(Map<Long,TerraForge.World>)field.get(map);
        for(int rz=0;rz<2;rz++)for(int rx=0;rx<2;rx++){
            var tile=new TerraForge.World(512,62,42);Arrays.fill(tile.height,80+rx+rz);
            cache.put((long)rx<<32|(rz&0xffffffffL),tile);
        }
        var owner=new MinecraftPreview();owner.wholeMap=true;owner.assets=assets;
        owner.world=new TerraForge.World(128,62,42);owner.world.region=map;Arrays.fill(owner.world.height,80);
        owner.forest=new TreeLibrary.SpatialIndex(owner.world);var stream=new WholeMapStream(owner);
        try{
            settle(stream,512,512,64);check(stream.tiles.size()==4,"four region-edge detail tiles");
            for(var tile:stream.tiles.values()){
                for(var draw:tile.draws()){
                    var vertices=org.lwjgl.system.MemoryUtil.memAllocFloat(draw.count()*10);
                    try{
                        glBindBuffer(GL_ARRAY_BUFFER,draw.buffer());glGetBufferSubData(GL_ARRAY_BUFFER,0,vertices);
                        for(int i=0;i<vertices.capacity();i+=10){
                            check(vertices.get(i)>=tile.cell().x()&&vertices.get(i)<=tile.cell().x()+64,"region X translated once");
                            check(vertices.get(i+2)>=tile.cell().z()&&vertices.get(i+2)<=tile.cell().z()+64,"region Z translated once");
                        }
                    }finally{org.lwjgl.system.MemoryUtil.memFree(vertices);}
                }
            }
            check(glGetError()==GL_NO_ERROR,"region uploads have no OpenGL errors");
            System.out.println("PASS: region-built overview streams four full-resolution border tiles with correct absolute block coordinates");
        }finally{owner.worker.shutdownNow();owner.worker.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS);stream.close();glDeleteTextures(owner.texture);}
    }
}
