import java.util.*;
import java.util.concurrent.*;
public class GpuGenerationTest {
    public static void main(String[] args)throws Exception{
        for(var type:new TerrainGraph.Type[]{TerrainGraph.Type.NOISE,TerrainGraph.Type.RIDGES})for(long seed:new long[]{42,-3273817,Long.MAX_VALUE}){
            TerrainGraph graph=new TerrainGraph();var node=graph.add(type,0,0);int n=768;
            System.setProperty("terraforge.gpu","cpu");long start=System.nanoTime();float[] cpu=graph.evaluate(n,n,62,seed,node.id,()->false).output();double cpuMs=(System.nanoTime()-start)/1e6;
            System.setProperty("terraforge.gpu","auto");start=System.nanoTime();float[] gpu=graph.evaluate(n,n,62,seed,node.id,()->false).output();double gpuMs=(System.nanoTime()-start)/1e6;
            if(!GpuGeneration.status().startsWith("OpenCL"))throw new AssertionError(GpuGeneration.status());
            double max=0;int different=0;for(int i=0;i<cpu.length;i++){max=Math.max(max,Math.abs(cpu[i]-gpu[i]));if(cpu[i]!=gpu[i])different++;}
            if(max>.0002)throw new AssertionError(type+" max difference "+max);
            System.out.printf("%s seed %d CPU %.1fms GPU %.1fms maxDelta %.8f different %d · %s%n",type,seed,cpuMs,gpuMs,max,different,GpuGeneration.status());
        }
        try{var g=new TerrainGraph();var node=g.add(TerrainGraph.Type.NOISE,0,0);g.evaluate(1024,1024,62,42,node.id,()->true);throw new AssertionError("Cancellation ignored");}catch(CancellationException expected){}
        for(var type:new TerrainGraph.Type[]{TerrainGraph.Type.MOUNTAIN,TerrainGraph.Type.ISLAND,TerrainGraph.Type.REGIONAL_RELIEF,TerrainGraph.Type.TERRACE,TerrainGraph.Type.REMAP,TerrainGraph.Type.CLAMP,TerrainGraph.Type.HEIGHT_MASK,TerrainGraph.Type.SLOPE_MASK,TerrainGraph.Type.BLEND,TerrainGraph.Type.EROSION,TerrainGraph.Type.OCEAN,TerrainGraph.Type.CLIMATE,TerrainGraph.Type.TERRAIN_DETAIL}){
            for(int variant=0;variant<(type==TerrainGraph.Type.MOUNTAIN?3:1);variant++){
                var g=new TerrainGraph();var a=g.add(TerrainGraph.Type.NOISE,0,0);var node=g.add(type,0,0);g.connect(a.id,node.id,0);
                if(type==TerrainGraph.Type.BLEND){var b=g.add(TerrainGraph.Type.RIDGES,0,0);g.connect(b.id,node.id,1);}
                if(type==TerrainGraph.Type.MOUNTAIN){node.values.put("peaks",1.0);node.values.put("organic",variant==0?0.0:1.0);node.values.put("spurStyle",variant==2?1.0:0.0);node.values.put("spurs",1.5);node.values.put("coastalReach",512.0);}
                int n=512;System.setProperty("terraforge.gpu","cpu");float[] cpu=g.evaluate(n,n,62,42,node.id,()->false).output();System.setProperty("terraforge.gpu","gpu");float[] gpu=g.evaluate(n,n,62,42,node.id,()->false).output();
                if(!GpuGeneration.status().startsWith("OpenCL"))throw new AssertionError(GpuGeneration.status());
                double max=0;for(int i=0;i<cpu.length;i++){if(!Float.isFinite(gpu[i]))throw new AssertionError("nonfinite "+type);max=Math.max(max,Math.abs(cpu[i]-gpu[i]));}
                if(max>.002)throw new AssertionError(type+" variant "+variant+" max delta "+max);
                System.out.println(type+" variant "+variant+" max delta "+max);
            }
        }
        for(String preset:new String[]{"Island","Alpine ranges","Archipelago","Mesa plateaus","Rolling plains"}){
            var p=TerrainGenerator.preset(preset);System.setProperty("terraforge.gpu","cpu");var cpu=TerrainGenerator.generate(512,p.sea(),42,p.relief(),p.shape(),p.settings());System.setProperty("terraforge.gpu","auto");var gpu=TerrainGenerator.generate(512,p.sea(),42,p.relief(),p.shape(),p.settings());
            if(!GpuGeneration.status().startsWith("OpenCL"))throw new AssertionError(GpuGeneration.status());double max=0;for(int i=0;i<cpu.height.length;i++)max=Math.max(max,Math.abs(cpu.height[i]-gpu.height[i]));if(max>.002)throw new AssertionError(preset+" delta "+max);System.out.println(preset+" max delta "+max);
        }
        int n=512;var base=new EnvironmentMap(new float[n*n]);base.water=EnvironmentMap.filled(n*n,62);base.snow=EnvironmentMap.filled(n*n,.7f);base.rain=EnvironmentMap.filled(n*n,.8f);base.material=new byte[n*n];
        for(int z=0;z<n;z++)for(int x=0;x<n;x++){int i=z*n+x;base.height[i]=50+z*.9f+(float)Math.sin(x*.07)*14;if(x%100==0){base.material[i]=6;base.water[i]=90;}}
        for(var type:new TerrainGraph.Type[]{TerrainGraph.Type.ORGANIC_MATERIALS,TerrainGraph.Type.SNOW}){
            var node=new TerrainGraph.Node(1,type,0,0);if(type==TerrainGraph.Type.SNOW)MountainZones.fields(base,n,n);if(type==TerrainGraph.Type.ORGANIC_MATERIALS){node.values.put("zones",1.0);node.values.put("glaciers",1.0);node.values.put("snowline",300.0);}System.setProperty("terraforge.gpu","cpu");var cpu=type==TerrainGraph.Type.ORGANIC_MATERIALS?OrganicMaterials.apply(node,base,n,n,62,42,317,739,()->false):NaturalSnow.apply(node,base,null,n,n,62,42,317,739,()->false);
            System.setProperty("terraforge.gpu","auto");var gpu=type==TerrainGraph.Type.ORGANIC_MATERIALS?OrganicMaterials.apply(node,base,n,n,62,42,317,739,()->false):NaturalSnow.apply(node,base,null,n,n,62,42,317,739,()->false);
            if(!GpuGeneration.status().startsWith("OpenCL"))throw new AssertionError(GpuGeneration.status());
            if(type==TerrainGraph.Type.ORGANIC_MATERIALS&&!Arrays.equals(cpu.material,gpu.material)){int diff=0;for(int i=0;i<n*n;i++)if(cpu.material[i]!=gpu.material[i])diff++;throw new AssertionError("Material differences "+diff);}
            double max=0;for(int i=0;i<n*n;i++)max=Math.max(max,Math.abs(cpu.snow[i]-gpu.snow[i]));if(max>.0002)throw new AssertionError(type+" snow delta "+max);System.out.println(type+" surface fixture matches CPU");
            if(!Arrays.equals(cpu.biome,gpu.biome))throw new AssertionError("Zone biome mismatch");if(cpu.glacier!=null)for(int i=0;i<n*n;i++)if(Math.abs(cpu.glacier[i]-gpu.glacier[i])>.0002)throw new AssertionError("Zone glacier mismatch");
        }
        for(var type:new TerrainGraph.Type[]{TerrainGraph.Type.FREEZE,TerrainGraph.Type.BIOMES,TerrainGraph.Type.GLACIER,TerrainGraph.Type.RAPIDS,TerrainGraph.Type.SNOW,TerrainGraph.Type.LAKE,TerrainGraph.Type.COAST,TerrainGraph.Type.SEDIMENT,TerrainGraph.Type.WETLAND}){
            var node=new TerrainGraph.Node(1,type,0,0);if(type==TerrainGraph.Type.SNOW)node.values.put("natural",0.0);
            if(type==TerrainGraph.Type.COAST){node.values.put("flatAmount",.7);node.values.put("flatCoverage",.6);}if(type==TerrainGraph.Type.LAKE){node.values.put("overflow",0.0);node.values.put("radius",8.0);}
            System.setProperty("terraforge.gpu","cpu");var cpu=EnvironmentNodes.apply(node,base,null,n,n,62,42,()->false);System.setProperty("terraforge.gpu","gpu");var gpu=EnvironmentNodes.apply(node,base,null,n,n,62,42,()->false);
            if(!GpuGeneration.status().startsWith("OpenCL"))throw new AssertionError(GpuGeneration.status());
            if(!Arrays.equals(cpu.material,gpu.material)||!Arrays.equals(cpu.biome,gpu.biome))throw new AssertionError(type+" categories mismatch");
            for(float[][] pair:new float[][][]{ {cpu.height,gpu.height},{cpu.water,gpu.water},{cpu.snow,gpu.snow},{cpu.ice,gpu.ice},{cpu.glacier,gpu.glacier} })if(pair[0]!=null)for(int i=0;i<n*n;i++)if(Math.abs(pair[0][i]-pair[1][i])>.0002)throw new AssertionError(type+" field mismatch "+i);
            System.out.println(type+" channel fixture matches CPU");
        }
    }
}
