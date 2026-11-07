import java.util.*;
public class FastGpuAlgorithmsTest {
    static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
    public static void main(String[] args){
        System.setProperty("terraforge.gpu","gpu");System.setProperty("terraforge.fastGpu","true");int n=512;float[] seeds=new float[n*n];Arrays.fill(seeds,-1);seeds[256*n+256]=256*n+256;
        float[] distance=FastGpu.distance(seeds,n,()->false);check(distance!=null,"GPU distance available");for(int z=0;z<n;z++)for(int x=0;x<n;x++)check(Math.abs(distance[z*n+x]-Math.hypot(x-256,z-256))<.0001,"single seed distance");
        Arrays.fill(seeds,-1);distance=FastGpu.distance(seeds,n,()->false);for(float d:distance)check(d==Float.POSITIVE_INFINITY,"dry map distance");
        float[] height=new float[n*n];for(int z=0;z<n;z++)for(int x=0;x<n;x++)height[z*n+x]=100+(float)(Math.sin(x*.07)*15+Math.cos(z*.05)*15);float[] original=height.clone();var node=new TerrainGraph.Node(1,TerrainGraph.Type.HYDRAULIC,0,0);
        float[] eroded=FastGpu.erosion(height,n,16384,62,node,4242,()->false);check(eroded!=null&&!Arrays.equals(eroded,height)&&Arrays.equals(height,original),"erosion changes a copy");for(int i=0;i<eroded.length;i++)check(Float.isFinite(eroded[i])&&eroded[i]>=-60&&eroded[i]<=1024&&eroded[i]>=height[i]-node.p("maxDepth")-.001,"erosion bounds");
        var world=new TerraForge.World(512,62,4242);Arrays.fill(world.height,350);GpuRockLayers.prepare(world,512,1024,()->false);check(world.gpuStrata!=null,"GPU rock batch");for(int depth=0;depth<64;depth++)for(int col=0;col<n*n;col+=503)check((int)world.gpuStrata[depth*n*n+col]==ExportBlocks.strata(col%n+512,349-depth,col/n+1024,4242),"GPU rock values");
        check(GpuGeneration.residentBytes()==0,"GPU buffers released");System.out.println("PASS: GPU distance, dry map, bounded copied erosion, rock batches, and cleanup: "+GpuGeneration.status());
    }
}
