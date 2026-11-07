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
        if(!GpuGeneration.fusing())return null;
        var nodes=new LinkedHashMap<Integer,TerrainGraph.Node>();
        if(!collect(graph,target,nodes)||nodes.size()<2)return null;
        // A value used outside this group must stay independently cached, rather than be recomputed.
        for(var consumer:graph.nodes.values())if(!nodes.containsKey(consumer.id))for(int input:consumer.input)if(input!=target&&nodes.containsKey(input))return null;
        float[] output=new float[n*n];
        var parameters=new ArrayList<Double>();var code=new StringBuilder();
        for(var node:nodes.values()){
            String body=GpuNodes.operation(node,node.input.length>2&&node.input[2]>=0,false);
            for(var entry:node.values.entrySet()){body=body.replace("P("+entry.getKey()+")","params["+parameters.size()+"]");parameters.add(entry.getValue());}
            code.append("float field").append(node.id).append(";{double a=").append(input(node,0)).append(",b=").append(input(node,1)).append(",c=").append(input(node,2)).append(",value=0;").append(body);
            code.append("field").append(node.id).append("=(float)clamp(value,").append(node.type.mask?"0.0,1.0":"-60.0,1024.0").append(");}\n");
        }
        code.append("value=field").append(target).append(';');
        boolean result=GpuGeneration.compute("fused:"+code,code.toString(),parameters.stream().mapToDouble(Double::doubleValue).toArray(),new float[0][],output,n,size,sea,seed,stop);
        if(result)GpuGeneration.recordFused(nodes.size());return result?output:null;
    }
    private static String input(TerrainGraph.Node node,int slot){return slot<node.input.length&&node.input[slot]>=0?"(double)field"+node.input[slot]:"0.0";}
}
