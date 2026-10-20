class BeachTest{
 static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
 public static void main(String[] a){
  int n=128;float[] h=EnvironmentMap.filled(n*n,63.2f);
  // a tile with no water of its own: the sea lies just beyond its west edge (shoreDistance = x + 6 blocks)
  var base=new EnvironmentMap(h);base.shoreDistance=new float[n*n];for(int z=0;z<n;z++)for(int x=0;x<n;x++)base.shoreDistance[z*n+x]=x+6;
  var node=new TerrainGraph.Node(1,TerrainGraph.Type.ORGANIC_MATERIALS,0,0);
  var plain=OrganicMaterials.apply(node,base,n,n,62,2024,0,0,()->false);
  int nearSand=0;for(int z=0;z<n;z++)for(int x=0;x<12;x++)if(plain.material[z*n+x]==2)nearSand++;
  check(nearSand==12*n,"beach reaches the tile edge when the sea is in the neighbouring tile: "+nearSand+"/"+(12*n));
  node.values.put("beachWidth",110.0);node.values.put("dunes",.6);
  var wide=OrganicMaterials.apply(node,base,n,n,62,2024,0,0,()->false);
  int wideSand=0,plainSand=0;for(int i=0;i<n*n;i++){if(wide.material[i]==2)wideSand++;if(plain.material[i]==2)plainSand++;}
  check(wideSand>plainSand*1.4,"wider beach setting widens the sand: "+plainSand+" -> "+wideSand);
  int duneMix=0;for(int z=0;z<n;z++){boolean sand=false,grass=false;for(int x=60;x<n;x++){if(wide.material[z*n+x]==2)sand=true;if(wide.material[z*n+x]==1)grass=true;}if(sand&&grass)duneMix++;}
  check(duneMix>n/4,"dune zone mixes sand into grass: "+duneMix);
  // no local water and no whole-map distance: no beach at all (original behaviour, no grid artefacts)
  var inland=OrganicMaterials.apply(new TerrainGraph.Node(2,TerrainGraph.Type.ORGANIC_MATERIALS,0,0),new EnvironmentMap(EnvironmentMap.filled(n*n,63.2f)),n,n,62,2024,0,0,()->false);
  for(int i=0;i<n*n;i++)check(inland.material[i]!=2,"no stray sand inland at cell "+i);
  System.out.println("PASS: beaches continue across tile seams, widen with Beach width ("+plainSand+" -> "+wideSand+" sand cells), blend through dunes, and inland tiles stay sand-free");
 }
}
