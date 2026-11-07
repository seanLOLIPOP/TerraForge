import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
public class ValleyLakeTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static EnvironmentMap apply(TerrainGraph.Node node,EnvironmentMap base,int n,String mode){try(var scope=new GpuGeneration.ModeScope(mode)){return EnvironmentNodes.apply(node,base,null,n,n,62,4242,()->false);}}
 static BufferedImage render(EnvironmentMap e,int n){var w=new TerraForge.World(n,62,4242);w.height=e.height;w.environment=e;return EnvironmentView.render(w,0);}
 public static void main(String[] args)throws Exception{
  int n=512;float[] heights=new float[n*n];
  for(int z=0;z<n;z++)for(int x=0;x<n;x++){double valley=112+Math.abs(x-256)*.13+Math.abs(z-256)*.015;heights[z*n+x]=(float)(x>=315&&x<=330?180:valley);}
  var base=new EnvironmentMap(heights);base.rain=EnvironmentMap.filled(n*n,.8f);float[] original=heights.clone();
  var node=new TerrainGraph.Node(9,TerrainGraph.Type.LAKE,0,0);node.values.put("random",0.0);node.values.put("realistic",1.0);node.values.put("level",110.0);node.values.put("radius",25.0);node.values.put("overflow",0.0);
  var before=apply(node,base,n,"cpu");node.values.put("valleyFollow",.9);node.values.put("bankVariety",.85);node.values.put("ridgeCut",28.0);node.values.put("bankReach",64.0);
  var after=apply(node,base,n,"cpu");check(Arrays.equals(original,base.height),"source unchanged");
  int deposits=0;Set<Byte> materials=new HashSet<>();for(int i=0;i<n*n;i++){check(Float.isFinite(after.height[i]),"finite terrain");if(after.lakeBank[i]!=0){deposits++;materials.add(after.lakeBank[i]);}if(after.lakeMask[i]!=0)check(after.water[i]==110,"flat water");}
  check(deposits>100&&materials.size()>1,"varied shore deposits");
  for(int z=190;z<320;z++)for(int x=315;x<=330;x++){int i=z*n+x;check(after.height[i]==180,"ridge preserved");check(after.lakeMask[i]==0,"no water across ridge");}
  check(RealisticLakes.terrainDistance(node,.9,108,110,12)<RealisticLakes.terrainDistance(node,.9,140,110,12),"low valleys favored");
  var gpu=apply(node,base,n,"gpu");double delta=0;for(int i=0;i<n*n;i++){delta=Math.max(delta,Math.abs(after.height[i]-gpu.height[i]));check(after.lakeMask[i]==gpu.lakeMask[i],"CPU/GPU footprint");check(after.lakeBank[i]==gpu.lakeBank[i],"CPU/GPU shore materials");}check(delta<.001,"CPU/GPU profile: "+delta);
  var organic=new TerrainGraph.Node(10,TerrainGraph.Type.ORGANIC_MATERIALS,0,0);var classified=OrganicMaterials.apply(organic,after,n,n,62,4242,0,0,()->false);
  for(int i=0;i<n*n;i++)if(after.lakeBank[i]!=0&&after.waterY(i,62)<=Math.round(after.height[i]))check(classified.material[i]==after.lakeBank[i],"deposits survive final materials");
  var frozen=after.deepCopy();java.util.Arrays.fill(frozen.material,(byte)6);RealisticLakes.finishMaterials(frozen,62,()->false);for(int i=0;i<n*n;i++)check(frozen.material[i]==6,"glacier material priority");
  var snapshot=after.deepCopy();snapshot.lakeBank[0]=7;check(after.lakeBank[0]!=7,"deposit copy isolated");GraphStageCache.clear();GraphStageCache.put("lake-test",after);var cached=GraphStageCache.get("lake-test");check(Arrays.equals(cached.lakeBank,after.lakeBank),"cached deposits retained");cached.lakeBank[0]=7;check(GraphStageCache.get("lake-test").lakeBank[0]!=7,"cached deposits isolated");
  var comparison=new BufferedImage(1024,512,BufferedImage.TYPE_INT_RGB);var g=comparison.createGraphics();g.drawImage(render(before,n),0,0,null);g.drawImage(render(after,n),512,0,null);g.dispose();ImageIO.write(comparison,"png",Path.of("dist/terrain-lake-comparison.png").toFile());
  if(args.length==2){var graph=GraphEditor.readFile(Path.of(args[0]));int lakes=0;for(var v:graph.nodes.values())if(v.type==TerrainGraph.Type.LAKE){v.values.put("natural",1.0);v.values.put("realistic",1.0);v.values.put("terrainFit",0.0);v.values.put("valleyFollow",.85);v.values.put("bankVariety",.85);v.values.put("ridgeCut",40.0);v.values.put("bankReach",80.0);lakes++;}GraphEditor.writeFile(graph,Path.of(args[1]));LargeWorldBuilder.configure(16384);LargeWorldBuilder.SEED=4242;LargeWorldBuilder.ACTIVE_GRAPH=GraphEditor.readFile(Path.of(args[1]));var layout=LargeWorldBuilder.layout();check(layout.lakeBank!=null,"City bank channel exists");int lakeCells=0,bankCells=0,chosen=-1;for(int i=0;i<layout.height.length;i++){check(Float.isFinite(layout.height[i]),"City heights finite");if(layout.lakeMask!=null&&layout.lakeMask[i]!=0)lakeCells++;if(layout.lakeBank[i]!=0){bankCells++;if(chosen<0)chosen=i;}}check(lakes==5&&lakeCells>0&&bankCells>0,"City lakes and deposits retained");int rx=(chosen%LargeWorldBuilder.GRID)/64,rz=(chosen/LargeWorldBuilder.GRID)/64;var tile=LargeWorldBuilder.tile(layout,rx,rz);check(tile.environment.lakeBank!=null,"region deposits sampled");for(int z=0;z<512;z++)for(int x=0;x<512;x++){int i=z*512+x;check(tile.environment.lakeBank[i]==LargeWorldBuilder.category(layout.lakeBank,rx*512+x,rz*512+z),"global deposit coordinates");}System.out.println("CITY lakes="+lakes+" lakeCells="+lakeCells+" bankCells="+bankCells+" sampled region="+rx+","+rz);}
  System.out.println("PASS terrain-following lakes, ridge preservation, varied banks, material retention, cache isolation; GPU delta="+delta);
 }
}
