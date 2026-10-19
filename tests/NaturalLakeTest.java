import java.nio.file.*;import java.util.*;import javax.imageio.ImageIO;
class NaturalLakeTest{
 public static void main(String[] args)throws Exception{
  var graph=new TerrainGraph();var lake=graph.add(TerrainGraph.Type.LAKE,0,0);lake.values.put("random",0.0);lake.values.put("natural",1.0);
  var flat=new EnvironmentMap(EnvironmentMap.filled(256*256,110));lake.values.put("level",100.0);lake.values.put("overflow",0.0);
  var a=EnvironmentNodes.apply(lake,flat,null,256,256,62,42,()->false);var b=EnvironmentNodes.apply(lake,flat,null,256,256,62,42,()->false);
  if(!Arrays.equals(a.height,b.height))throw new AssertionError("determinism");
  int count=0;for(int i=0;i<a.height.length;i++){if(!Float.isFinite(a.height[i]))throw new AssertionError("finite");if(a.height[i]<100&&a.water[i]==100)count++;}
  if(count<100)throw new AssertionError("lake missing");
  double min=999,max=0;for(int k=0;k<64;k++){double angle=k*Math.PI/32,last=0;for(double r=0;r<60;r+=.25){int x=(int)Math.round(127.5+Math.cos(angle)*r),z=(int)Math.round(127.5+Math.sin(angle)*r);if(a.height[z*256+x]<100&&a.water[z*256+x]==100)last=r;}min=Math.min(min,last);max=Math.max(max,last);}
  if(max-min<6)throw new AssertionError("shoreline too circular");
  System.out.println("PASS: deterministic lake, irregular shoreline; radial range "+min+"–"+max+", "+count+" water cells");
 }
}
