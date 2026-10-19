import java.util.*;import java.io.*;
public class CoastalFlatTest{
 static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
 public static void main(String[] args)throws Exception{
  int n=512;float[] h=new float[n*n];for(int z=0;z<n;z++)for(int x=0;x<n;x++)h[z*n+x]=x<128?45:80+(x-128)*.15f;var base=new EnvironmentMap(h);var graph=new TerrainGraph();var coast=graph.add(TerrainGraph.Type.COAST,0,0);
  var original=EnvironmentNodes.apply(coast,base,null,n,n,62,42,()->false);coast.values.put("flatAmount",1.0);coast.values.put("flatCoverage",.4);coast.values.put("flatScale",64.0);
  var a=EnvironmentNodes.apply(coast,base,null,n,n,62,42,()->false);var b=EnvironmentNodes.apply(coast,base,null,n,n,62,42,()->false);check(Arrays.equals(a.height,b.height),"repeatability");
  int lowered=0,untouched=0;double maxStep=0;for(int z=1;z<n;z++){int i=z*n+148;if(original.height[i]-a.height[i]>3)lowered++;if(original.height[i]==a.height[i])untouched++;maxStep=Math.max(maxStep,Math.abs(a.height[i]-a.height[i-n]));}check(lowered>20&&untouched>20,"random separated patches: "+lowered+" lowered "+untouched+" unchanged");check(maxStep<4,"soft patch boundaries "+maxStep);
  for(int z=0;z<n;z++){check(a.height[z*n+20]==h[z*n+20],"ocean unchanged");check(a.height[z*n+400]==h[z*n+400],"inland unchanged");}
  coast.values.put("flatSeed",1.0);var reroll=EnvironmentNodes.apply(coast,base,null,n,n,62,42,()->false);check(!Arrays.equals(a.height,reroll.height),"reroll");
  var bytes=new ByteArrayOutputStream();graph.write(new DataOutputStream(bytes));var saved=TerrainGraph.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));check(saved.nodes.get(coast.id).p("flatSeed")==1,"saved controls");
  coast.values.put("flatCoverage",0.0);check(Arrays.equals(original.height,EnvironmentNodes.apply(coast,base,null,n,n,62,42,()->false).height),"zero coverage");
  coast.values.put("flatCoverage",1.0);coast.values.put("flatSeed",0.0);coast.values.put("flatReach",48.0);
  var broad=EnvironmentNodes.apply(coast,base,null,n,n,62,42,()->false);
  check(broad.height[256*n+168]<67,"flat core extends inland through selected reach");
  check(broad.height[256*n+200]<h[256*n+200]-3,"surrounding hills lowered beyond core reach");
  check(broad.height[256*n+400]==h[256*n+400],"transition returns to original terrain");
  double lowest=999,highest=-999;for(int z=40;z<470;z+=10)for(int x=145;x<174;x++){lowest=Math.min(lowest,broad.height[z*n+x]);highest=Math.max(highest,broad.height[z*n+x]);}
  check(highest-lowest<.6,"flat core has little height variation: "+(highest-lowest));
  coast.values.put("flatAmount",.5);var moderate=EnvironmentNodes.apply(coast,base,null,n,n,62,42,()->false);
  coast.values.put("flatAmount",.51);var increment=EnvironmentNodes.apply(coast,base,null,n,n,62,42,()->false);
  int probe=256*n+168;double fullDrop=original.height[probe]-broad.height[probe];
  check(Math.abs((original.height[probe]-moderate.height[probe])/fullDrop-.75)<.001,"stronger mid strength");
  check(Math.abs(moderate.height[probe]-increment.height[probe])<fullDrop*.011,"fine increments");
  float[] mountain=h.clone();for(int z=0;z<n;z++)for(int x=128;x<n;x++)mountain[z*n+x]=180;
  coast.values.put("flatAmount",1.0);coast.values.put("flatCoverage",.4);var mountainBase=new EnvironmentMap(mountain);
  var mountainCoast=EnvironmentNodes.apply(coast,mountainBase,null,n,n,62,42,()->false);double maxLandStep=0;
  for(int z=1;z<n;z++)for(int x=145;x<n-1;x++){int i=z*n+x;maxLandStep=Math.max(maxLandStep,Math.max(Math.abs(mountainCoast.height[i]-mountainCoast.height[i+1]),Math.abs(mountainCoast.height[i]-mountainCoast.height[i-n])));}
  check(maxLandStep<1,"mountain patch side and inland edges slope smoothly: "+maxLandStep);
  System.out.println("PASS: irregular partial coast patches, soft edges, repeatability, reroll, persistence, zero coverage and unchanged ocean/inland; "+lowered+" flattened and "+untouched+" untouched coastline samples");
 }
}
