class MountainZonesTest{
 static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
 public static void main(String[] a){
  int n=256;float[] h=new float[n*n];
  // cone mountain 62..700 with one steep east face
  int size=2048;for(int z=0;z<n;z++)for(int x=0;x<n;x++){double d=Math.hypot(x-128,z-128)*8;double v=720-d*.55;if(x>136&&Math.abs(z-128)<30)v-=(x-136)*8*1.6;h[z*n+x]=(float)Math.max(66,v);}
  var base=new EnvironmentMap(h);
  var off=new TerrainGraph.Node(1,TerrainGraph.Type.ORGANIC_MATERIALS,0,0);
  var plain=OrganicMaterials.apply(off,base,n,size,62,7,0,0,()->false);
  var on=new TerrainGraph.Node(2,TerrainGraph.Type.ORGANIC_MATERIALS,0,0);on.values.put("zones",1.0);on.values.put("treeline",300.0);on.values.put("snowline",600.0);
  var zoned=OrganicMaterials.apply(on,base,n,size,62,7,0,0,()->false);
  check(plain.zoneSlope==null,"zones are off by default");
  int cliffRock=0,steep=0,meadowGrass=0,meadow=0,lowGround=0,lowRock=0,biomes=0;
  for(int i=0;i<n*n;i++){double hh=h[i],sl=zoned.zoneSlope[i];int m=zoned.material[i];
   if(sl>1.3&&hh>150){steep++;if(MountainZones.rock(m))cliffRock++;}
   if(hh>360&&hh<440&&sl<.7){meadow++;if(m==1||m==10||m==16)meadowGrass++;}
   if(hh>120&&hh<220&&sl<.7){lowGround++;if(MountainZones.rock(m))lowRock++;}
   if(zoned.biome!=null&&zoned.biome[i]>=10)biomes++;}
  check(steep>200&&cliffRock>steep*.95,"steep faces are bare rock: "+cliffRock+"/"+steep);
  check(meadow>200&&meadowGrass>meadow*.4&&meadowGrass<meadow*.98,"alpine meadow mixes grass with stone: "+meadowGrass+"/"+meadow);
  check(lowGround>200&&lowRock<lowGround*.1,"forest belt below the treeline stays soil: "+lowRock+"/"+lowGround);
  check(biomes>n*n/10,"mountain biomes assigned: "+biomes);
  // aspect: snow reaches lower on north faces (-Z) than on south faces
  var snowNode=new TerrainGraph.Node(3,TerrainGraph.Type.SNOW,0,0);snowNode.values.put("natural",1.0);snowNode.values.put("line",600.0);snowNode.values.put("transition",120.0);
  var snow=NaturalSnow.apply(snowNode,zoned,null,n,size,62,7,0,0,()->false);
  double northSnow=0,southSnow=0;for(int x=100;x<128;x++)for(int r=8;r<22;r++){northSnow+=snow.snow[(128-r)*n+x];southSnow+=snow.snow[(128+r)*n+x];}
  check(northSnow>southSnow*1.3,"north faces hold more snow: north="+northSnow+" south="+southSnow);
  // tree scatter thins toward the treeline and stops above it
  var tree=new TerrainGraph.Node(4,TerrainGraph.Type.TREES,0,0);tree.values.put("group",28.0);tree.values.put("variant",1.0);tree.values.put("spacing",8.0);tree.values.put("density",1.0);tree.values.put("minY",70.0);tree.values.put("maxY",1000.0);tree.values.put("slope",3.0);tree.values.put("treeline",300.0);tree.values.put("count",4096.0);
  float[] ramp=new float[n*n];for(int z=0;z<n;z++)for(int x=0;x<n;x++)ramp[z*n+x]=(float)(150+x*.7);tree.values.put("treeline",220.0);var forest=TreeLibrary.scatter(tree,new EnvironmentMap(ramp),null,n,n,62,7,()->false);
  int below=0,above=0;for(var p:forest.trees){double th=ramp[p.z()*n+p.x()];if(th<190)below++;if(th>310)above++;}
  check(below>40&&above==0,"trees grow below the treeline and stop above it: below="+below+" above="+above);
  System.out.println("PASS: realistic zones - rock cliffs ("+cliffRock+"/"+steep+"), mixed alpine meadow, soil forest belt, mountain biomes, north-facing snow, trees stop at the treeline ("+below+" trees below, "+above+" above)");
 }
}
