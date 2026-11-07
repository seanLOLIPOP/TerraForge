import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Run against old and new release JARs, then compare the resulting SHA-256. */
public class GenerationCompatibilityCorpus {
    public static void main(String[] args)throws Exception{
        var digest=MessageDigest.getInstance("SHA-256");
        try(var out=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),digest))){
            for(int test=0;test<24;test++){
                long seed=new long[]{0,4242,-9,Long.MAX_VALUE}[test%4];var random=new Random(seed+test);
                var world=new TerraForge.World(32,62,seed);var e=world.environment;
                e.material=new byte[1024];e.water=new float[1024];e.snow=new float[1024];e.ice=new float[1024];e.glacier=new float[1024];
                for(int z=0;z<32;z++)for(int x=0;x<32;x++){int i=z*32+x;world.height[i]=test<4?80:test<8?1024:test<12?-60:test<16?350+z*15+x:random.nextInt(1085)-60;
                    e.material[i]=(byte)(test<21?test:random.nextInt(21));e.water[i]=test%3==0?Math.max(62,world.height[i]+4):62;e.snow[i]=random.nextFloat();e.ice[i]=random.nextFloat()*5;e.glacier[i]=random.nextFloat()*30;}
                if(test%2==0)e.trees=List.of(new TreeLibrary.Placement(20,16,16,test%4));
                ExtendedHeight.configure(world);var forest=new TreeLibrary.SpatialIndex(world);
                for(int z=0;z<2;z++)for(int x=0;x<2;x++){byte[] chunk=TerraForge.chunk(world,x,z,forest,-16384,8192);out.writeInt(chunk.length);out.write(chunk);}
            }
            for(int test=0;test<8;test++){
                int n=128;float[] height=new float[n*n];for(int z=0;z<n;z++)for(int x=0;x<n;x++)height[z*n+x]=(float)(190-z*1.15+Math.sin(x*.08)*13+Math.cos(z*.13)*7);
                var e=new EnvironmentMap(height);e.lakeMask=new byte[n*n];if(test%2==0)for(int i=60*n+60;i<60*n+67;i++)e.lakeMask[i]=1;
                var route=RiverCourse.route(height,e,n,1024,62,()->false,test%3==0,1,4242+test,test<4?Double.POSITIVE_INFINITY:.18,test<4?Double.POSITIVE_INFINITY:.3,test%2==0);
                for(int p:route.flow().parent())out.writeInt(p);for(float a:route.area())out.writeFloat(a);for(float h:route.flow().level())out.writeFloat(h);
            }
            int n=128;var e=new EnvironmentMap(EnvironmentMap.filled(n*n,90));e.water=EnvironmentMap.filled(n*n,62);e.rain=EnvironmentMap.filled(n*n,.7f);
            for(int i=0;i<128;i++)e.water[i]=100;
            for(int pass=0;pass<10;pass++){var node=new TerrainGraph.Node(pass+1,TerrainGraph.Type.TREES,0,0);node.values.put("variant",20.0);node.values.put("spacing",16.0);node.values.put("waterMargin",5.0);node.values.put("moisture",.4);node.values.put("seed",(double)pass);e=TreeLibrary.scatter(node,e,null,n,n,62,4242,()->false);}
            e.write(out);
        }
        System.out.println("GENERATION_CORPUS_SHA256 "+HexFormat.of().formatHex(digest.digest()));
    }
}
