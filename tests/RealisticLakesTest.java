import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
public class RealisticLakesTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static EnvironmentMap apply(TerrainGraph.Node node,EnvironmentMap base,int n,String mode){try(var scope=new GpuGeneration.ModeScope(mode)){return EnvironmentNodes.apply(node,base,null,n,n,62,4242,()->false);}}
 static BufferedImage render(EnvironmentMap e,int n){var w=new TerraForge.World(n,62,4242);w.height=e.height;w.environment=e;return EnvironmentView.render(w,0);}
 public static void main(String[] args)throws Exception{
  int n=512;var base=new EnvironmentMap(EnvironmentMap.filled(n*n,114));var original=base.height.clone();
  var node=new TerrainGraph.Node(7,TerrainGraph.Type.LAKE,0,0);node.values.put("random",0.0);node.values.put("level",110.0);node.values.put("radius",25.0);node.values.put("depth",24.0);node.values.put("overflow",0.0);
  var old=apply(node,base,n,"cpu");node.values.put("realistic",1.0);node.values.put("bays",.9);node.values.put("shoreDetail",.85);node.values.put("direction",35.0);
  var cpu=apply(node,base,n,"cpu");check(Arrays.equals(base.height,original),"input unchanged");
  int changed=0,wet=0;for(int i=0;i<n*n;i++){check(Float.isFinite(cpu.height[i]),"finite basin");if(cpu.lakeMask[i]!=old.lakeMask[i])changed++;if(cpu.lakeMask[i]!=0){wet++;check(cpu.water[i]==110,"flat lake surface");check(cpu.height[i]<110,"lake bed underwater");check(cpu.height[i]>=86,"depth bounded");}}
  check(changed>1000,"outline differs from smooth legacy lake");check(wet>1000,"lake remains substantial");
  var pocketBase=new EnvironmentMap(EnvironmentMap.filled(16,100));pocketBase.water=EnvironmentMap.filled(16,70.5f);pocketBase.material=new byte[16];pocketBase.material[15]=7;
  var pockets=pocketBase.deepCopy();pockets.height[0]=pockets.height[1]=pockets.height[15]=60;pockets.water[0]=pockets.water[1]=pockets.water[15]=90;
  float[] distance=EnvironmentMap.filled(16,Float.POSITIVE_INFINITY);distance[0]=distance[1]=distance[15]=0;
  RealisticLakes.connect(pockets,pocketBase,distance,4,45,0,0,()->false);
  check(distance[0]==0&&distance[1]==0&&Float.isInfinite(distance[15]),"isolated new puddle removed");
  check(pockets.height[15]==100&&pockets.water[15]==70.5f&&pockets.material[15]==7,"original channels restored without sea-level assumptions");
  var rough=node.copy();rough.values.put("bays",0.0);rough.values.put("shoreDetail",0.0);var smooth=apply(rough,base,n,"cpu");int bays=0;for(int i=0;i<n*n;i++)if(cpu.lakeMask[i]!=smooth.lakeMask[i])bays++;check(bays>100,"coves and indentations affect outline");
  var gpu=apply(node,base,n,"gpu");double delta=0;for(int i=0;i<n*n;i++){delta=Math.max(delta,Math.abs(cpu.height[i]-gpu.height[i]));check(cpu.lakeMask[i]==gpu.lakeMask[i],"CPU/GPU footprint agrees");check(cpu.water[i]==gpu.water[i],"CPU/GPU water agrees");}check(delta<.001,"CPU/GPU profile matches: "+delta);
  var comparison=new BufferedImage(1024,512,BufferedImage.TYPE_INT_RGB);var g=comparison.createGraphics();g.drawImage(render(old,n),0,0,null);g.drawImage(render(cpu,n),512,0,null);g.dispose();ImageIO.write(comparison,"png",Path.of("dist/realistic-lake-comparison.png").toFile());
  if(args.length==2){var graph=GraphEditor.readFile(Path.of(args[0]));int lakes=0;for(var v:graph.nodes.values())if(v.type==TerrainGraph.Type.LAKE){v.values.put("realistic",1.0);v.values.put("natural",1.0);v.values.put("bays",.9);v.values.put("shoreDetail",.85);v.values.put("elongation",1.6);v.values.put("direction",(double)(v.id*35%180));lakes++;}check(lakes>0,"City map lake nodes found");GraphEditor.writeFile(graph,Path.of(args[1]));LargeWorldBuilder.configure(16384);LargeWorldBuilder.SEED=4242;LargeWorldBuilder.ACTIVE_GRAPH=GraphEditor.readFile(Path.of(args[1]));var layout=LargeWorldBuilder.layout();int lakeCells=0;for(int i=0;i<layout.height.length;i++){check(Float.isFinite(layout.height[i]),"City layout finite");if(layout.lakeMask!=null&&layout.lakeMask[i]!=0)lakeCells++;}check(lakeCells>0,"City lakes remain");System.out.println("CITY realistic lakes="+lakes+" layout lake cells="+lakeCells);}
  System.out.println("PASS realistic lake outline, adjustable bays, bounded depths, flat water, immutable input; GPU delta="+delta+" status="+GpuGeneration.status());
 }
}
