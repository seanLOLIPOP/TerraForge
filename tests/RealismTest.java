import java.nio.charset.StandardCharsets;
class RealismTest{
 static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
 public static void main(String[] a)throws Exception{
  // 1. water erosion carves valleys, deposits sediment, keeps the sea untouched and is repeatable
  int n=256,size=4096;float[] h=new float[n*n];
  for(int z=0;z<n;z++)for(int x=0;x<n;x++){double d=Math.hypot(x-128,z-128)/128;h[z*n+x]=(float)(d>.95?40:62+560*Math.pow(1-d,1.6)*(.85+.15*TerraForge.sample(x/9.,z/9.,5)));}
  var ero=new TerrainGraph.Node(1,TerrainGraph.Type.HYDRAULIC,0,0);
  float[] out=HydraulicErosion.apply(h,n,size,62,ero,7,()->false),again=HydraulicErosion.apply(h,n,size,62,ero,7,()->false);
  check(java.util.Arrays.equals(out,again),"erosion is repeatable");
  double cut=0,fill=0,maxCut=0;for(int i=0;i<n*n;i++){double d=out[i]-h[i];if(h[i]<=62)check(out[i]<=62.5,"deltas stay below sea level: "+out[i]);if(d<0){cut-=d;maxCut=Math.max(maxCut,-d);}else fill+=d;}
  check(maxCut>8,"valleys are cut: deepest "+maxCut);
  check(fill>cut*.02,"eroded material is deposited downstream: cut="+Math.round(cut)+" fill="+Math.round(fill));
  // 2. rain shadow: lee side of a ridge is drier than the windward side
  float[] ridge=new float[n*n];for(int z=0;z<n;z++)for(int x=0;x<n;x++)ridge[z*n+x]=(float)(80+500*Math.exp(-Math.pow((x-128)/14.0,2)));
  var climate=new TerrainGraph.Node(2,TerrainGraph.Type.CLIMATE,0,0);climate.values.put("shadow",.8);climate.values.put("wind",0.0);climate.values.put("variation",0.0);
  var c=EnvironmentNodes.apply(climate,new EnvironmentMap(ridge),null,n,size,62,3,()->false);
  double windward=0,lee=0;for(int z=40;z<200;z++){windward+=c.rain[z*n+110];lee+=c.rain[z*n+160];}
  check(windward>lee*1.3,"wind from the west rains on the windward slope: windward="+windward+" lee="+lee);
  climate.values.put("shadow",0.0);var plain=EnvironmentNodes.apply(climate,new EnvironmentMap(ridge),null,n,size,62,3,()->false);
  check(Math.abs(plain.rain[128*n+160]-plain.rain[128*n+60])<.05,"rain shadow is off by default");
  // 3. ground cover in exported chunks: meadow flowers and grass, nothing under water or snow
  var w=new TerraForge.World(32,62,71);java.util.Arrays.fill(w.height,120);w.environment.material=new byte[1024];java.util.Arrays.fill(w.environment.material,(byte)1);
  w.environment.biome=new byte[1024];java.util.Arrays.fill(w.environment.biome,MountainZones.MEADOW);
  String chunk=new String(TerraForge.chunk(w,0,0),StandardCharsets.ISO_8859_1);
  check(chunk.contains("minecraft:short_grass"),"meadow grass exported");
  boolean flower=false;for(String f:new String[]{"dandelion","poppy","cornflower","azure_bluet","oxeye_daisy","allium"})flower|=chunk.contains("minecraft:"+f);
  check(flower,"meadow flowers exported");
  w.environment.snow=new float[1024];java.util.Arrays.fill(w.environment.snow,.5f);
  String snowy=new String(TerraForge.chunk(w,0,0),StandardCharsets.ISO_8859_1);
  check(!snowy.contains("minecraft:short_grass"),"no plants poke through snow");
  TerraForge.groundCover=false;String bare=new String(TerraForge.chunk(w,1,1),StandardCharsets.ISO_8859_1);TerraForge.groundCover=true;
  check(!bare.contains("minecraft:short_grass"),"ground cover can be switched off");
  // 4. two-block plants always come as matching lower and upper halves
  for(int x=0;x<400;x++)for(int z=0;z<400;z++){int[] d=GroundCover.at(x,z,x,z,100,1,MountainZones.MEADOW,.6,9,new GroundCover.Ground(){public int height(int lx,int lz){return 100;}public int material(int lx,int lz){return 1;}});
   if(d[1]!=0)check(d[1]==d[0]+1&&GroundCover.state(d[0]).properties().get("half").equals("lower"),"double plant halves");}
  System.out.println("PASS: water erosion cuts valleys (deepest "+Math.round(maxCut)+" blocks) and deposits downstream, rain shadow dries the lee, ground cover exports meadow grass and flowers but not through snow, double plants have both halves");
 }
}
