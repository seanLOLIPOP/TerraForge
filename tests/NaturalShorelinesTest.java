import java.nio.file.*;
import java.util.*;
public class NaturalShorelinesTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  int n=128,sea=62;float[] heights=new float[n*n];
  for(int z=0;z<n;z++)for(int x=0;x<n;x++)heights[z*n+x]=62+(x-64)*.35f;
  float[] original=heights.clone();var base=new EnvironmentMap(heights);base.water=EnvironmentMap.filled(n*n,sea);
  var node=new TerrainGraph.Node(1,TerrainGraph.Type.COAST,0,0);node.values.put("naturalShore",1.0);node.values.put("width",32.0);
  var result=EnvironmentNodes.apply(node,base,null,n,n,sea,4242,()->false);
  int changed=0;for(int i=0;i<n*n;i++){check(Float.isFinite(result.height[i]),"finite heights");check(base.height[i]==original[i],"source immutable");if(heights[i]<sea)check(result.height[i]<=sea-1,"no exposed shelf");if(result.height[i]!=heights[i])changed++;}
  check(changed>100,"coast shaped");check(result.water==base.water,"water levels preserved");
  var wet=new EnvironmentMap(EnvironmentMap.filled(n*n,20));var deep=NaturalShorelines.apply(node,wet,n,n,sea,4242,()->false);check(Arrays.equals(deep.height,wet.height),"all-water maps unchanged");
  base.river=EnvironmentMap.filled(n*n,1);var river=NaturalShorelines.apply(node,base,n,n,sea,4242,()->false);check(Arrays.equals(river.height,base.height),"river channels preserved");
  if(args.length>0){
   Path source=Path.of(args[0]),target=Path.of(args[1]);var graph=GraphEditor.readFile(source);int coast=0,materials=0;
   for(var v:graph.nodes.values()){if(v.type==TerrainGraph.Type.COAST){v.values.put("naturalShore",1.0);v.values.put("variation",.65);v.values.put("width",40.0);v.values.put("shelfWidth",80.0);v.values.put("shelfDepth",6.0);coast++;}if(v.type==TerrainGraph.Type.ORGANIC_MATERIALS){v.values.put("naturalShore",1.0);v.values.put("beachVariation",.65);v.values.put("dunes",.35);materials++;}}
   check(coast>0,"City map has coast stage");GraphEditor.writeFile(graph,target);var loaded=GraphEditor.readFile(target);check(loaded.nodes.size()==graph.nodes.size(),"graph round trip");
   LargeWorldBuilder.configure(16384);LargeWorldBuilder.SEED=4242;LargeWorldBuilder.ACTIVE_GRAPH=loaded;
   var layout=LargeWorldBuilder.layout();int wetCells=0;for(int i=0;i<layout.height.length;i++){check(Float.isFinite(layout.height[i]),"City layout finite");if(layout.height[i]<layout.waterY(i,62))wetCells++;}
   check(wetCells>0,"City water remains");
   // Shared global coordinates must sample identically on either side of a region boundary.
   int rx=15,rz=15;double best=Double.POSITIVE_INFINITY;
   for(int z=64;z<LargeWorldBuilder.GRID-64;z++)for(int x=64;x<LargeWorldBuilder.GRID-64;x++){int i=z*LargeWorldBuilder.GRID+x;double h=layout.height[i],water=layout.waterY(i,62);boolean wetNeighbor=layout.height[i-1]<layout.waterY(i-1,62)||layout.height[i+1]<layout.waterY(i+1,62)||layout.height[i-LargeWorldBuilder.GRID]<layout.waterY(i-LargeWorldBuilder.GRID,62)||layout.height[i+LargeWorldBuilder.GRID]<layout.waterY(i+LargeWorldBuilder.GRID,62);double score=h-water;if(h>water+.5&&water<=63&&wetNeighbor&&score<best&&EnvironmentMap.at(layout.river,i,0)<.05){best=score;rx=Math.max(0,x/64-1);rz=z/64;}}
   var a=LargeWorldBuilder.tile(layout,rx,rz);var b=LargeWorldBuilder.tile(layout,rx+1,rz);
   for(int z=0;z<512;z++){check(Float.isFinite(a.height[z*512+511])&&Float.isFinite(b.height[z*512]),"region border finite");}
   var image=new java.awt.image.BufferedImage(1024,512,java.awt.image.BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();graphics.drawImage(EnvironmentView.render(a,0),0,0,512,512,null);graphics.drawImage(EnvironmentView.render(b,0),512,0,512,512,null);graphics.dispose();javax.imageio.ImageIO.write(image,"png",Path.of("dist/natural-shorelines-city-preview.png").toFile());
   System.out.println("City graph validated: "+coast+" coast stages, "+materials+" material stages; copy: "+target);
  }
  System.out.println("PASS natural shore profiles, water/river preservation, all-water fallback");
 }
}
