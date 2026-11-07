import java.util.*;
import java.util.function.BooleanSupplier;

/** Closed scalar subgraphs become one kernel. Explicit float boundaries preserve node rounding. */
final class GpuFusion {
    static boolean supported(TerrainGraph.Node node){
        return switch(node.type){
            case NOISE,RIDGES,CONSTANT,ISLAND,TERRACE,REMAP,CLAMP,BLEND,HEIGHT_MASK,REGIONAL_RELIEF->true;
            case OUTPUT->Arrays.stream(node.input).skip(1).allMatch(id->id<0);
            default->false;
        };
    }
    static boolean collect(TerrainGraph graph,int id,LinkedHashMap<Integer,TerrainGraph.Node> nodes){
        if(nodes.containsKey(id))return true;
        var node=graph.nodes.get(id);if(node==null||!supported(node))return false;
        for(int input:node.input)if(input>=0&&!collect(graph,input,nodes))return false;
        nodes.put(id,node);return true;
    }
    static float[] evaluate(TerrainGraph graph,int target,int n,int size,int sea,long seed,BooleanSupplier stop){
        return evaluate(graph,target,n,size,sea,seed,stop,null);
    }
    private static boolean fusible(TerrainGraph.Node node){return supported(node)&&!(node.type==TerrainGraph.Type.REMAP&&node.p("water")>.5);}
    static boolean collectPartial(TerrainGraph graph,int id,LinkedHashMap<Integer,TerrainGraph.Node> nodes,LinkedHashSet<Integer> boundaries){
        if(nodes.containsKey(id)||boundaries.contains(id))return true;var node=graph.nodes.get(id);
        if(!fusible(node)){boundaries.add(id);return boundaries.size()<=3;}
        // Water remapping changes environmental channels as well as height.

        for(int input:node.input)if(input>=0&&!collectPartial(graph,input,nodes,boundaries))return false;nodes.put(id,node);return true;
    }
    static EnvironmentMap environment(TerrainGraph graph,int id,Map<Integer,EnvironmentMap> environments,float[] result){
        int cursor=id;while(cursor>=0){var node=graph.nodes.get(cursor);if(!fusible(node)){var base=environments.get(cursor);return base==null?new EnvironmentMap(result):base.share(result);}cursor=node.input.length>0?node.input[0]:-1;}return new EnvironmentMap(result);
    }
    static float[] evaluate(TerrainGraph graph,int target,int n,int size,int sea,long seed,BooleanSupplier stop,java.util.function.IntFunction<float[]> resolve){
        if(!GpuGeneration.fusing())return null;
        if(!fusible(graph.nodes.get(target)))return null;
        var nodes=new LinkedHashMap<Integer,TerrainGraph.Node>();
        var boundaries=new LinkedHashSet<Integer>();
        if(!collectPartial(graph,target,nodes,boundaries)||nodes.size()<2||!boundaries.isEmpty()&&resolve==null)return null;
        // A value used outside this group must stay independently cached, rather than be recomputed.
        for(var consumer:graph.nodes.values())if(!nodes.containsKey(consumer.id))for(int input:consumer.input)if(input!=target&&nodes.containsKey(input))return null;
        float[] output=new float[n*n];
        var external=new HashMap<Integer,Integer>();float[][] inputs=new float[boundaries.size()][];int index=0;for(int boundary:boundaries){external.put(boundary,index);inputs[index++]=resolve.apply(boundary);}
        var parameters=new ArrayList<Double>();var code=new StringBuilder();
        for(var node:nodes.values()){
            String body=GpuNodes.operation(node,node.input.length>2&&node.input[2]>=0,false);
            for(var entry:node.values.entrySet()){body=body.replace("P("+entry.getKey()+")","params["+parameters.size()+"]");parameters.add(entry.getValue());}
            code.append("float field").append(node.id).append(";{double a=").append(input(node,0,external)).append(",b=").append(input(node,1,external)).append(",c=").append(input(node,2,external)).append(",value=0;").append(body);
            code.append("field").append(node.id).append("=(float)clamp(value,").append(node.type.mask?"0.0,1.0":"-60.0,1024.0").append(");}\n");
        }
        code.append("value=field").append(target).append(';');
        boolean result=GpuGeneration.compute("fused:"+code,code.toString(),parameters.stream().mapToDouble(Double::doubleValue).toArray(),inputs,output,n,size,sea,seed,stop);
        if(result)GpuGeneration.recordFused(nodes.size());return result?output:null;
    }
    private static String input(TerrainGraph.Node node,int slot,Map<Integer,Integer> external){if(slot>=node.input.length||node.input[slot]<0)return "0.0";int id=node.input[slot];return external.containsKey(id)?new String[]{"A[i]","B[i]","C[i]"}[external.get(id)]:"(double)field"+id;}
}
