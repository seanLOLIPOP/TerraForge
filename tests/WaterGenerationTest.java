public class WaterGenerationTest{
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
 public static void main(String[] args){
  int n=128;float[] h=new float[n*n];for(int z=0;z<n;z++)for(int x=0;x<n;x++)h[z*n+x]=x<24?45:100+40*(float)Math.sin(x*.09)*(float)Math.sin(z*.07);
  var base=new EnvironmentMap(h);var river=new TerrainGraph.Node(1,TerrainGraph.Type.RIVERS,0,0);river.values.put("sources",12.0);var out=EnvironmentNodes.apply(river,base,null,n,n,62,2024,()->false);
  for(int i=0;i<h.length;i++)if(out.river[i]>0&&out.water[i]>62)check(out.water[i]-out.height[i]<=river.p("depth")+1.001,"no perched water in drainage depressions");
  var coastBase=new EnvironmentMap(new float[n*n]);coastBase.water=EnvironmentMap.filled(n*n,62);coastBase.river=new float[n*n];for(int z=0;z<n;z++)for(int x=0;x<n;x++){int i=z*n+x;coastBase.height[i]=x<24?45:120;if(x>=24&&z==64){coastBase.water[i]=125;coastBase.river[i]=1;}}
  var coast=new TerrainGraph.Node(2,TerrainGraph.Type.COAST,0,0);coast.values.put("flatAmount",1.0);coast.values.put("flatCoverage",1.0);var flat=EnvironmentNodes.apply(coast,coastBase,null,n,n,62,2024,()->false);
  int count=0;for(int i=0;i<h.length;i++)if(flat.height[i]<coastBase.height[i]&&flat.river[i]>0){count++;check(flat.water[i]-flat.height[i]<=5.001,"river water lowers with flattened ground");}check(count>100,"coast modifies river ground");check(coastBase.water[64*n+40]==125,"upstream water unchanged");
  System.out.println("PASS: river drainage depressions and coastal lowering do not create deep elevated water columns");
 }
}
