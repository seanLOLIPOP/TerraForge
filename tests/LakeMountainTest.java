import java.util.*;
public class LakeMountainTest{
 static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
 public static void main(String[] args){
  int n=512;var lake=new TerrainGraph.Node(1,TerrainGraph.Type.LAKE,0,0);lake.values.put("random",0.0);lake.values.put("level",90.0);lake.values.put("overflow",0.0);var base=new EnvironmentMap(EnvironmentMap.filled(n*n,240));
  var result=EnvironmentNodes.apply(lake,base,null,n,1024,62,42,()->false);var repeat=EnvironmentNodes.apply(lake,base,null,n,1024,62,42,()->false);check(Arrays.equals(result.height,repeat.height),"deterministic");
  int water=0,reshaped=0;double largestBankStep=0;for(int z=1;z<n-1;z++)for(int x=1;x<n-1;x++){int i=z*n+x;if(result.height[i]<90&&result.water[i]==90)water++;if(result.height[i]>90&&result.height[i]<239)reshaped++;if(result.height[i]>90)for(int j:new int[]{i+1,i-1,i+n,i-n})if(result.height[j]>90)largestBankStep=Math.max(largestBankStep,Math.abs(result.height[i]-result.height[j]));}
  check(water>500,"lake exists");check(reshaped>10000,"mountain slopes reshaped beyond old rim");check(largestBankStep<3,"land banks have no cliff: "+largestBankStep);check(result.height[0]==240,"distant terrain preserved");check(base.height[n*n/2]==240,"input preserved");
  lake.values.put("bankSlope",.25);var gentle=EnvironmentNodes.apply(lake,base,null,n,1024,62,42,()->false);check(gentle.height[256*n+400]<result.height[256*n+400],"slope control widens transition");
  boolean cancelled=false;try{EnvironmentNodes.apply(lake,base,null,n,1024,62,42,()->true);}catch(java.util.concurrent.CancellationException e){cancelled=true;}check(cancelled,"cancellation");
  System.out.println("PASS: mountain lake reshapes "+reshaped+" bank cells, maximum land step "+largestBankStep+", repeatability, slope control, unchanged distant/input terrain and cancellation");
 }
}
