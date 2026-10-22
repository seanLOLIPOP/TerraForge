import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.IntConsumer;
import java.util.zip.*;
/**
 * A map built region by region into a world folder on disk, for maps too large to hold in memory.
 * The editor gets an overview (one cell per 8 x 8 blocks, assembled from the finished regions), and the
 * Minecraft block view gets full-resolution windows built on demand from the same shared layout.
 */
final class RegionMap {
    static final int CELL=8,WINDOW=1536;
    /** Part of the build folder key. Change only when region files must be rebuilt, not on every app release. */
    static final String BUILD_FORMAT="0.1.0";
    final Path folder;final int size,sea,buildHeight;final long seed;final TerrainGraph graph;final EnvironmentMap layout;
    private final LinkedHashMap<Long,TerraForge.World> tiles=new LinkedHashMap<>(16,.75f,true){protected boolean removeEldestEntry(Map.Entry<Long,TerraForge.World> e){return size()>9;}};
    RegionMap(Path folder,int size,long seed,int sea,TerrainGraph graph,EnvironmentMap layout,int buildHeight){this.folder=folder;this.size=size;this.seed=seed;this.sea=sea;this.graph=graph;this.layout=layout;this.buildHeight=buildHeight;}

    /** Working folder for a build: the same graph, size, seed and sea level reuse (and resume) the same folder. */
    static Path folderFor(TerrainGraph recipe,int size,long seed,int sea)throws Exception{
        var bytes=new ByteArrayOutputStream();Path tmp=Files.createTempFile("terraforge-key-",".tgraph");
        try{GraphEditor.writeFile(recipe,tmp);bytes.write(Files.readAllBytes(tmp));}finally{Files.deleteIfExists(tmp);}
        bytes.write((size+"|"+seed+"|"+sea+"|"+BUILD_FORMAT).getBytes());
        byte[] d=MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());StringBuilder h=new StringBuilder();for(int i=0;i<6;i++)h.append(String.format("%02x",d[i]));
        String root=System.getProperty("terraforge.regionBuilds",AppHome.data("region-builds").toString());
        return Path.of(root).resolve("map-"+size+"-"+h);
    }

    /** Builds (or resumes) the whole map region by region on disk and returns the editor overview world. */
    static TerraForge.World build(TerrainGraph recipe,long seed,int sea,int size,IntConsumer progress,java.util.function.BooleanSupplier cancel)throws Exception{
        Path folder=folderFor(recipe,size,seed,sea);Files.createDirectories(folder.getParent());
        synchronized(LargeWorldBuilder.class){
            LargeWorldBuilder.PROGRESS=progress;LargeWorldBuilder.OVERVIEW=true;LargeWorldBuilder.CANCEL=cancel;
            try{LargeWorldBuilder.exportGraph(folder,recipe,seed,sea,size);}
            finally{LargeWorldBuilder.PROGRESS=null;LargeWorldBuilder.OVERVIEW=false;LargeWorldBuilder.CANCEL=null;}
            var layout=LargeWorldBuilder.LAST_LAYOUT;LargeWorldBuilder.LAST_LAYOUT=null;
            int ceiling=1024;for(var n:recipe.nodes.values())if(n.type==TerrainGraph.Type.CLAMP)ceiling=(int)n.p("max");
            var map=new RegionMap(folder,size,seed,sea,recipe.copy(),layout,ExtendedHeight.forPeak(ceiling));
            return map.overview();
        }
    }

    /** One cell per 8 x 8 blocks: mean height, the dominant surface block, and water wherever a quarter of the cell is wet. */
    static void writeOverview(TerraForge.World tile,Path file)throws IOException{
        int n=tile.size,cells=n/CELL;Files.createDirectories(file.getParent());Path tmp=file.resolveSibling(file.getFileName()+".tmp");
        var e=tile.environment;
        try(var out=new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp))))){
            out.writeInt(cells);
            int[] votes=new int[64];
            for(int cz=0;cz<cells;cz++)for(int cx=0;cx<cells;cx++){
                double sum=0;int wet=0,wetIndex=-1;Arrays.fill(votes,0);
                for(int z=0;z<CELL;z++)for(int x=0;x<CELL;x++){int bx=cx*CELL+x,bz=cz*CELL+z,i=bz*n+bx;sum+=tile.height[i];
                    if(tile.waterY(bx,bz)>tile.h(bx,bz)){wet++;if(wetIndex<0||tile.waterY(bx,bz)>tile.waterY(wetIndex%n,wetIndex/n))wetIndex=i;}
                    votes[Math.max(0,Math.min(63,TerrainGenerator.surface(tile,bx,bz)))]++;}
                int center=(cz*CELL+CELL/2)*n+cx*CELL+CELL/2,pick=wet*4>=CELL*CELL&&wetIndex>=0?wetIndex:center,material=0;
                for(int k=1;k<64;k++)if(votes[k]>votes[material])material=k;
                out.writeFloat(pick==center?(float)(sum/(CELL*CELL)):tile.height[pick]);
                out.writeFloat(EnvironmentMap.at(e.water,pick,tile.sea));out.writeFloat(EnvironmentMap.at(e.snow,pick,0));out.writeFloat(EnvironmentMap.at(e.ice,pick,0));
                out.writeFloat(EnvironmentMap.at(e.glacier,pick,0));out.writeFloat(EnvironmentMap.at(e.river,pick,0));
                out.writeByte(material);out.writeByte(e.biome==null?0:e.biome[pick]);
            }
        }
        Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }

    TerraForge.World overview()throws IOException{
        int regions=size/LargeWorldBuilder.TILE,cells=LargeWorldBuilder.TILE/CELL,n=size/CELL;
        var w=new TerraForge.World(n,sea,seed);var e=w.environment;int count=n*n;
        e.water=new float[count];e.snow=new float[count];e.ice=new float[count];e.glacier=new float[count];e.river=new float[count];e.material=new byte[count];e.biome=new byte[count];
        for(int rz=0;rz<regions;rz++)for(int rx=0;rx<regions;rx++){
            Path file=folder.resolve("overview/r."+rx+"."+rz+".bin");
            try(var in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))){
                if(in.readInt()!=cells)throw new IOException("Overview does not match region size: "+file);
                for(int cz=0;cz<cells;cz++)for(int cx=0;cx<cells;cx++){int i=(rz*cells+cz)*n+rx*cells+cx;
                    w.height[i]=in.readFloat();e.water[i]=in.readFloat();e.snow[i]=in.readFloat();e.ice[i]=in.readFloat();e.glacier[i]=in.readFloat();e.river[i]=in.readFloat();e.material[i]=in.readByte();e.biome[i]=in.readByte();}
            }
        }
        w.buildHeight=buildHeight;w.graph=graph.copy();w.region=this;
        return w;
    }

    /** Full-resolution window of the map, span x span blocks around a centre, for the Minecraft block view. */
    TerraForge.World window(int centerX,int centerZ,int span){
        span=Math.min(size,span/LargeWorldBuilder.TILE*LargeWorldBuilder.TILE);
        int tile=LargeWorldBuilder.TILE,ox=Math.max(0,Math.min(size-span,(centerX-span/2)/tile*tile)),oz=Math.max(0,Math.min(size-span,(centerZ-span/2)/tile*tile));
        var w=new TerraForge.World(span,sea,seed);var e=w.environment;int count=span*span;
        e.water=new float[count];e.snow=new float[count];e.ice=new float[count];e.glacier=new float[count];e.river=new float[count];e.rain=new float[count];e.temperature=new float[count];e.material=new byte[count];e.biome=new byte[count];
        var trees=new ArrayList<TreeLibrary.Placement>();
        for(int rz=oz/tile;rz<(oz+span)/tile;rz++)for(int rx=ox/tile;rx<(ox+span)/tile;rx++){
            var t=tile(rx,rz);var te=t.environment;int bx=rx*tile-ox,bz=rz*tile-oz;
            for(int z=0;z<tile;z++){int src=z*tile,dst=(bz+z)*span+bx;
                System.arraycopy(t.height,src,w.height,dst,tile);
                copy(te.water,e.water,src,dst,tile,sea);copy(te.snow,e.snow,src,dst,tile,0);copy(te.ice,e.ice,src,dst,tile,0);copy(te.glacier,e.glacier,src,dst,tile,0);
                copy(te.river,e.river,src,dst,tile,0);copy(te.rain,e.rain,src,dst,tile,.6f);copy(te.temperature,e.temperature,src,dst,tile,.6f);
                if(te.material!=null)System.arraycopy(te.material,src,e.material,dst,tile);if(te.biome!=null)System.arraycopy(te.biome,src,e.biome,dst,tile);}
            for(var p:te.trees)trees.add(new TreeLibrary.Placement(p.tree(),p.x()+bx,p.z()+bz,p.rotation()));
        }
        e.trees=List.copyOf(trees);w.buildHeight=buildHeight;w.graph=graph;w.region=this;w.regionWindow=true;w.originX=ox;w.originZ=oz;
        return w;
    }
    static void copy(float[] from,float[] to,int src,int dst,int length,float fallback){if(from==null)Arrays.fill(to,dst,dst+length,fallback);else System.arraycopy(from,src,to,dst,length);}

    /** Rebuilds one region exactly as the exporter did, using the same shared layout. */
    TerraForge.World tile(int rx,int rz){
        long key=((long)rx<<32)|(rz&0xffffffffL);
        synchronized(tiles){var cached=tiles.get(key);if(cached!=null)return cached;}
        TerraForge.World t;
        synchronized(LargeWorldBuilder.class){
            int oldSize=LargeWorldBuilder.SIZE,oldSea=LargeWorldBuilder.SEA;long oldSeed=LargeWorldBuilder.SEED;var oldGraph=LargeWorldBuilder.ACTIVE_GRAPH;
            try{LargeWorldBuilder.configure(size);LargeWorldBuilder.SEED=seed;LargeWorldBuilder.SEA=sea;LargeWorldBuilder.ACTIVE_GRAPH=graph;t=LargeWorldBuilder.tile(layout,rx,rz,true);}
            finally{LargeWorldBuilder.configure(oldSize);LargeWorldBuilder.SEED=oldSeed;LargeWorldBuilder.SEA=oldSea;LargeWorldBuilder.ACTIVE_GRAPH=oldGraph;}
        }
        synchronized(tiles){tiles.put(key,t);}
        return t;
    }

    /** Copies the built world folder (without the editor overview) into a Minecraft saves folder. */
    void copyWorld(Path target)throws IOException{
        if(Files.exists(target))throw new IOException("That world already exists: "+target);
        try(var walk=Files.walk(folder)){
            for(Path p:(Iterable<Path>)walk::iterator){
                Path rel=folder.relativize(p);if(rel.toString().startsWith("overview")||p.getFileName().toString().endsWith(".tmp"))continue;
                Path to=target.resolve(rel.toString());
                if(Files.isDirectory(p))Files.createDirectories(to);else Files.copy(p,to);
            }
        }
    }
}
