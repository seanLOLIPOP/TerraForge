import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.system.MemoryUtil;
import java.nio.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Retains exact block meshes while the camera moves; only missing tiles are built. */
final class WholeMapStream implements AutoCloseable {
    static final int TILE=64;
    static final long BUDGET=288L*1024*1024;
    record Cell(int x,int z) {
        long key(){return (long)x<<32|(z&0xffffffffL);}
        double distance(double px,double pz){return Math.pow(x+TILE*.5-px,2)+Math.pow(z+TILE*.5-pz,2);}
    }
    record Result(Cell cell,MinecraftMesh.Patch patch,List<BufferedImage> images,long nanos){}
    record Tile(Cell cell,List<MinecraftPreview.Draw> draws,long bytes,long faces){}
    /** One draw per transparency class, instead of one draw for every 16-block chunk. */
    static MinecraftMesh.Patch compact(MinecraftMesh.Patch patch){
        List<MinecraftMesh.Batch> batches=new ArrayList<>();
        for(boolean transparent:new boolean[]{false,true}){
            int count=0;for(var batch:patch.batches())if(batch.transparent()==transparent)count+=batch.vertices().length;
            if(count==0)continue;
            float[] vertices=new float[count];int offset=0;
            for(var batch:patch.batches())if(batch.transparent()==transparent){System.arraycopy(batch.vertices(),0,vertices,offset,batch.vertices().length);offset+=batch.vertices().length;}
            batches.add(new MinecraftMesh.Batch(vertices,transparent));
        }
        return new MinecraftMesh.Patch(patch.x(),patch.z(),patch.side(),List.copyOf(batches),patch.faces());
    }
    final MinecraftPreview owner;
    final TerraForge.World world;
    final long budget;
    final Map<Long,Tile> tiles=new HashMap<>();
    final Set<Long> skipped=new HashSet<>();
    final AtomicReference<Result> pending=new AtomicReference<>();
    final int coverage,grid;
    long bytes,builds,hits;
    volatile boolean closed;
    int cameraCellX=Integer.MIN_VALUE,cameraCellZ,lastRadius;
    TerraForge.World regionSource;
    TreeLibrary.SpatialIndex regionForest;int sourceRX=-1,sourceRZ=-1;

    static List<Cell> plan(int size,int radius,double x,double z){
        int span=Math.min(size,Math.max(TILE,radius*2));
        int ox=Math.max(0,Math.min(size-span,(int)Math.floor((x-span*.5)/TILE)*TILE));
        int oz=Math.max(0,Math.min(size-span,(int)Math.floor((z-span*.5)/TILE)*TILE));
        List<Cell> cells=new ArrayList<>();
        for(int zz=oz/TILE*TILE;zz<oz+span;zz+=TILE)
            for(int xx=ox/TILE*TILE;xx<ox+span;xx+=TILE)cells.add(new Cell(xx,zz));
        cells.sort(Comparator.comparingDouble(c->c.distance(x,z)));
        return cells;
    }
    WholeMapStream(MinecraftPreview owner){this(owner,BUDGET);}
    WholeMapStream(MinecraftPreview owner,long budget){
        this.owner=owner;this.budget=budget;world=owner.world;grid=(owner.fullSize()+TILE-1)/TILE;
        coverage=glGenTextures();glBindTexture(GL_TEXTURE_2D,coverage);
        ByteBuffer zero=MemoryUtil.memCalloc(grid*grid);
        int alignment=glGetInteger(GL_UNPACK_ALIGNMENT);glPixelStorei(GL_UNPACK_ALIGNMENT,1);
        try{glTexImage2D(GL_TEXTURE_2D,0,GL_R8UI,grid,grid,0,GL_RED_INTEGER,GL_UNSIGNED_BYTE,zero);}
        finally{MemoryUtil.memFree(zero);glPixelStorei(GL_UNPACK_ALIGNMENT,alignment);}
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
    }
    void mark(Cell cell,boolean ready){
        ByteBuffer value=MemoryUtil.memAlloc(1);value.put((byte)(ready?1:0)).flip();
        glActiveTexture(GL_TEXTURE4);glBindTexture(GL_TEXTURE_2D,coverage);
        int alignment=glGetInteger(GL_UNPACK_ALIGNMENT);glPixelStorei(GL_UNPACK_ALIGNMENT,1);
        try{glTexSubImage2D(GL_TEXTURE_2D,0,cell.x()/TILE,cell.z()/TILE,1,1,GL_RED_INTEGER,GL_UNSIGNED_BYTE,value);}
        finally{MemoryUtil.memFree(value);glPixelStorei(GL_UNPACK_ALIGNMENT,alignment);glActiveTexture(GL_TEXTURE0);}
    }
    void remove(long key){
        Tile tile=tiles.remove(key);if(tile==null)return;
        for(var draw:tile.draws()){glDeleteVertexArrays(draw.vao());glDeleteBuffers(draw.buffer());}
        bytes-=tile.bytes();mark(tile.cell(),false);
    }
    void update(double x,double z,int radius){
        if(closed)return;
        int cx=(int)Math.floor(x/TILE),cz=(int)Math.floor(z/TILE);
        if(cx!=cameraCellX||cz!=cameraCellZ||radius!=lastRadius){
            cameraCellX=cx;cameraCellZ=cz;lastRadius=radius;skipped.clear();
        }
        List<Cell> desired=plan(owner.fullSize(),radius,x,z);
        Set<Long> wanted=new HashSet<>();for(var cell:desired)wanted.add(cell.key());
        Set<Long> retained=new HashSet<>();for(var cell:plan(owner.fullSize(),radius+TILE*2,x,z))retained.add(cell.key());
        for(long key:List.copyOf(tiles.keySet()))if(!retained.contains(key))remove(key);
        Result result=pending.getAndSet(null);
        if(result!=null&&wanted.contains(result.cell().key())){
            long needed=result.patch().batches().stream().mapToLong(b->b.vertices().length*4L).sum();
            while(bytes+needed>budget&&!tiles.isEmpty()){
                Tile farthest=Collections.max(tiles.values(),Comparator.comparingDouble(t->t.cell().distance(x,z)));
                if(farthest.cell().distance(x,z)<=result.cell().distance(x,z))break;
                remove(farthest.cell().key());
            }
            if(bytes+needed<=budget){
                owner.uploadTextures(result.images());List<MinecraftPreview.Draw> draws=new ArrayList<>();
                for(var batch:result.patch().batches()){
                    int vao=glGenVertexArrays(),buffer=glGenBuffers();glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,buffer);
                    FloatBuffer vertices=MemoryUtil.memAllocFloat(batch.vertices().length);
                    try{vertices.put(batch.vertices()).flip();glBufferData(GL_ARRAY_BUFFER,vertices,GL_STATIC_DRAW);}
                    finally{MemoryUtil.memFree(vertices);}
                    for(int i=0;i<3;i++){glEnableVertexAttribArray(i);glVertexAttribPointer(i,i==2?4:3,GL_FLOAT,false,40,i*12L);}
                    draws.add(new MinecraftPreview.Draw(vao,buffer,batch.vertices().length/10,batch.transparent()));
                }
                tiles.put(result.cell().key(),new Tile(result.cell(),List.copyOf(draws),needed,result.patch().faces()));
                bytes+=needed;builds++;mark(result.cell(),true);
            }else skipped.add(result.cell().key());
        }
        if(!owner.building.get()){
            Cell next=null;for(var cell:desired){if(tiles.containsKey(cell.key())){hits++;continue;}if(!skipped.contains(cell.key())){next=cell;break;}}
            if(next!=null&&owner.building.compareAndSet(false,true)){
                Cell cell=next;owner.worker.submit(()->{
                    try{
                        long start=System.nanoTime();var source=world;var forest=owner.forest;int ox=0,oz=0;
                        if(world.region!=null&&!world.regionWindow){
                            int rx=cell.x()/512,rz=cell.z()/512;
                            if(regionSource==null||sourceRX!=rx||sourceRZ!=rz){
                                regionSource=world.region.tile(rx,rz);sourceRX=rx;sourceRZ=rz;regionForest=new TreeLibrary.SpatialIndex(regionSource);
                            }
                            source=regionSource;forest=regionForest;ox=rx*512;oz=rz*512;
                        }
                        var mesh=new MinecraftMesh(source,owner.assets,cell.x()-ox+TILE/2,cell.z()-oz+TILE/2,TILE/2,forest,ox+source.originX,oz+source.originZ);
                        var patch=compact(mesh.build());
                        if(ox!=0||oz!=0)for(var batch:patch.batches())for(int i=0;i<batch.vertices().length;i+=10){batch.vertices()[i]+=ox;batch.vertices()[i+2]+=oz;}
                        if(!closed)pending.set(new Result(cell,patch,List.copyOf(owner.assets.images),System.nanoTime()-start));
                    }catch(java.util.concurrent.CancellationException ignored){}
                    catch(Throwable error){if(!closed)owner.failure.set(error);}
                    finally{owner.building.set(false);}
                });
            }
        }
        owner.message=tiles.size()+" detail tiles · "+(bytes/(1024*1024))+" MB · ";
    }
    List<MinecraftPreview.Draw> draws(){List<MinecraftPreview.Draw> result=new ArrayList<>();for(var tile:tiles.values())result.addAll(tile.draws());return result;}
    boolean settled(){return !owner.building.get()&&pending.get()==null;}
    public void close(){closed=true;pending.set(null);for(long key:List.copyOf(tiles.keySet()))remove(key);glDeleteTextures(coverage);}
}
