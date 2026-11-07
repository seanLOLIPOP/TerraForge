import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Bounded snapshots keyed by complete upstream dependencies, never editor positions. */
final class GraphStageCache {
    private record Entry(EnvironmentMap environment,long bytes){}
    private static final LinkedHashMap<String,Entry> CACHE=new LinkedHashMap<>(16,.75f,true);
    private static long bytes,hits;
    static boolean eligible(TerrainGraph.Type type){return switch(type){case HYDRAULIC,EROSION,RIVERS,LAKE,CLIMATE->true;default->false;};}
    static String key(TerrainGraph graph,int id,int n,int size,int sea,long seed){
        String context=System.getProperty("terraforge.gpuPipeline","true")+"|"+n+"|"+size+"|"+sea+"|"+seed+"|"+(GpuGeneration.mode()+"|"+GpuGeneration.enabled())+"|"+System.getProperty("terraforge.gpuDevice","")+"|"+Boolean.toString(FastGpu.requested());
        return hash(context+dependency(graph,id,new HashMap<>()));
    }
    static String dependency(TerrainGraph graph,int id,Map<Integer,String> done){
        if(id<0)return "-";if(done.containsKey(id))return done.get(id);var node=graph.nodes.get(id);var text=new StringBuilder(node.type.name()+"#"+node.id+new TreeMap<>(node.values));for(int input:node.input)text.append('|').append(dependency(graph,input,done));String key=hash(text.toString());done.put(id,key);return key;
    }
    static String hash(String input){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static EnvironmentMap snapshot(EnvironmentMap source){var e=source.deepCopy();e.lakeMask=source.lakeMask==null?null:source.lakeMask.clone();e.riverCatchment=EnvironmentMap.copy(source.riverCatchment);e.riverNetworkCells=source.riverNetworkCells==null?null:(BitSet)source.riverNetworkCells.clone();e.zoneSlope=EnvironmentMap.copy(source.zoneSlope);e.zoneNorth=EnvironmentMap.copy(source.zoneNorth);e.zoneBowl=EnvironmentMap.copy(source.zoneBowl);e.zoneSource=source.zoneSource==source.height?e.height:null;e.moisture=EnvironmentMap.copy(source.moisture);e.riverGrad=EnvironmentMap.copy(source.riverGrad);if(source.riverFlow!=null){var f=source.riverFlow;e.riverFlow=new RiverFlow.Field(f.discharge().clone(),f.supplied(),f.terminal(),f.residual());}return e;}
    static synchronized EnvironmentMap get(String key){if(!Boolean.parseBoolean(System.getProperty("terraforge.stageCache","true")))return null;var entry=CACHE.get(key);if(entry==null)return null;hits++;return snapshot(entry.environment);}
    static synchronized void put(String key,EnvironmentMap environment){
        if(!Boolean.parseBoolean(System.getProperty("terraforge.stageCache","true")))return;
        long limit=Math.min(384L<<20,Runtime.getRuntime().maxMemory()/10),cost=weight(environment);
        if(cost>limit)return;var previous=CACHE.remove(key);if(previous!=null)bytes-=previous.bytes;
        while(bytes+cost>limit&&!CACHE.isEmpty()){var iterator=CACHE.entrySet().iterator();bytes-=iterator.next().getValue().bytes;iterator.remove();}
        CACHE.put(key,new Entry(snapshot(environment),cost));bytes+=cost;
    }
    private static long weight(EnvironmentMap e){var seen=Collections.newSetFromMap(new IdentityHashMap<float[],Boolean>());long size=0;for(float[] a:new float[][]{e.height,e.water,e.snow,e.ice,e.temperature,e.rain,e.river,e.glacier,e.zoneSlope,e.zoneNorth,e.zoneBowl,e.moisture,e.riverGrad,e.riverCatchment,e.shoreDistance})if(a!=null&&seen.add(a))size+=a.length*4L;return size+(e.lakeBank==null?0:e.lakeBank.length)+e.height.length*2L+(e.riverFlow==null?0:e.riverFlow.discharge().length*8L);}
    static synchronized long hits(){return hits;}
    static synchronized void clear(){CACHE.clear();bytes=0;hits=0;}
}
