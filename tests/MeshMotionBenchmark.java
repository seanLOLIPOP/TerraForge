import java.util.*;
/** Compare old full-mesh rebuilding with retained 64-block tiles on the same camera path. */
public class MeshMotionBenchmark {
    public static void main(String[] args)throws Exception{
        var world=TerraForge.generateWorld(512,62,2024,170,"Island");
        ExtendedHeight.configure(world);var forest=new TreeLibrary.SpatialIndex(world);
        try(var assets=new MinecraftAssets(MinecraftAssets.locate())){
            long start=System.nanoTime(),faces=0,builds=0,firstNanos=0;Set<Long> retained=new HashSet<>();
            for(int camera:new int[]{192,256,192}){
                if(args[0].equals("legacy")){
                    faces+=new MinecraftMesh(world,assets,camera,256,128,forest).build().faces();builds++;
                }else{
                    for(int z=128;z<384;z+=64)for(int x=camera-128;x<camera+128;x+=64){
                        long key=(long)x<<32|(z&0xffffffffL);if(!retained.add(key))continue;
                        faces+=new MinecraftMesh(world,assets,x+32,z+32,32,forest).build().faces();builds++;
                    }
                }
                if(firstNanos==0)firstNanos=System.nanoTime()-start;
            }
            System.out.printf(Locale.ROOT,"%s first=%.3fs moving-and-return=%.3fs total=%.3fs builds=%d faces=%d%n",args[0],firstNanos/1e9,(System.nanoTime()-start-firstNanos)/1e9,(System.nanoTime()-start)/1e9,builds,faces);
        }
    }
}
