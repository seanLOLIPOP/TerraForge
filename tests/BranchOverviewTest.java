import java.util.*;
import java.nio.file.*;
public class BranchOverviewTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static double distance(RiverRibbon.Point p,RiverRibbon.Segment s){double dx=s.b().x()-s.a().x(),dz=s.b().z()-s.a().z(),t=Math.max(0,Math.min(1,((p.x()-s.a().x())*dx+(p.z()-s.a().z())*dz)/Math.max(.0001,dx*dx+dz*dz)));return Math.hypot(p.x()-s.a().x()-t*dx,p.z()-s.a().z()-t*dz);}
 public static void main(String[] args)throws Exception{
  int n=128,size=1024;float[] h=new float[n*n];for(int z=0;z<n;z++)for(int x=0;x<n;x++)h[z*n+x]=140-z*.7f;
  var node=new TerrainGraph.Node(9,TerrainGraph.Type.RIVERS,0,0);node.values.put("sources",2.0);node.values.put("mountainForks",8.0);node.values.put("branchReach",1.2);node.values.put("forkLength",128.0);node.values.put("riverSpacing",128.0);node.values.put("headwater",100.0);
  var e=EnvironmentNodes.apply(node,new EnvironmentMap(h),null,n,size,62,83,()->false);check(!e.mountainArms.isEmpty(),"fixture generates fork geometry");
  int branchSegments=e.mountainArms.stream().mapToInt(a->a.points().size()-1).sum(),mainCount=e.ribbons.segments.size()-branchSegments;int samples=0,wet=0;
  for(var arm:e.mountainArms)for(var p:arm.points()){double nearest=Double.POSITIVE_INFINITY;for(int j=0;j<mainCount;j++)nearest=Math.min(nearest,distance(p,e.ribbons.segments.get(j)));if(nearest<24)continue;int x=(int)Math.round(p.x()/8),z=(int)Math.round(p.z()/8);if(x<0||z<0||x>=n||z>=n)continue;int i=z*n+x;samples++;if(e.river[i]>.1&&e.waterY(i,62)>Math.round(e.height[i]))wet++;}
  check(samples>20,"fork points away from main channel");check(wet>samples*.65,"fork geometry missing from coarse water channels: "+wet+"/"+samples);
  System.out.println("PASS fork geometry appears in coarse overview: "+wet+"/"+samples+" samples away from primary rivers");
  if(args.length>=2){var graph=GraphEditor.readFile(Path.of(args[0]));GraphEditor.writeFile(graph,Path.of(args[1]));LargeWorldBuilder.configure(16384);LargeWorldBuilder.SEED=args.length>2?Long.parseLong(args[2]):4242;LargeWorldBuilder.SEA=args.length>3?Integer.parseInt(args[3]):62;LargeWorldBuilder.ACTIVE_GRAPH=graph;var layout=LargeWorldBuilder.layout();var world=new TerraForge.World(LargeWorldBuilder.GRID,LargeWorldBuilder.SEA,LargeWorldBuilder.SEED);world.height=layout.height;world.environment=layout;world.buildHeight=ExtendedHeight.forPeak(LargeWorldBuilder.terrainCeiling());javax.imageio.ImageIO.write(EnvironmentView.render(world,0),"png",Path.of("dist/branch-overview-actual-seed.png").toFile());long branchWet=0,branchSamples=0;for(var arm:layout.mountainArms)for(var p:arm.points()){int x=Math.min(LargeWorldBuilder.GRID-1,Math.max(0,(int)Math.round(p.x()/8))),z=Math.min(LargeWorldBuilder.GRID-1,Math.max(0,(int)Math.round(p.z()/8))),i=z*LargeWorldBuilder.GRID+x;branchSamples++;if(layout.river[i]>.1&&layout.waterY(i,LargeWorldBuilder.SEA)>Math.round(layout.height[i]))branchWet++;}System.out.println("CITY actual seed="+LargeWorldBuilder.SEED+" sea="+LargeWorldBuilder.SEA+" overview wet fork samples="+branchWet+"/"+branchSamples);check(branchWet>branchSamples*.65,"City coarse fork visibility");}
 }
}
