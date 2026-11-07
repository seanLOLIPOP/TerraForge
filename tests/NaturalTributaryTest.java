import java.util.*;
/** The extra network must be a converging drainage tree, not a split/rejoin web. */
public class NaturalTributaryTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static String key(RiverRibbon.Point p){return Double.toHexString(p.x())+":"+Double.toHexString(p.z());}
 static String root(Map<String,String> parents,String k){parents.putIfAbsent(k,k);String p=parents.get(k);if(!p.equals(k))parents.put(k,p=root(parents,p));return p;}
 public static void main(String[] args)throws Exception{
  for(long seed:new long[]{83,4242,74839261}){
   int n=128,size=1024;float[] h=new float[n*n];for(int z=0;z<n;z++)for(int x=0;x<n;x++)h[z*n+x]=140-z*.7f;
   var node=new TerrainGraph.Node(9,TerrainGraph.Type.RIVERS,0,0);node.values.put("sources",2.0);node.values.put("mountainForks",8.0);node.values.put("branchReach",1.2);node.values.put("forkLength",128.0);node.values.put("riverSpacing",128.0);node.values.put("headwater",100.0);
   var e=EnvironmentNodes.apply(node,new EnvironmentMap(h),null,n,size,62,seed,()->false);
   check(e.mountainArms.size()>e.forkRoots,"tributaries retain branching");
   var parents=new HashMap<String,String>();var segments=new ArrayList<RiverRibbon.Segment>();
   for(var arm:e.mountainArms)for(int i=1;i<arm.points().size();i++){
    var a=arm.points().get(i-1);var b=arm.points().get(i);
    check(a.water()>=b.water(),"water flows downhill toward confluence");
    check(a.radius()<=b.radius()+1e-9,"channels widen toward confluence");
    String u=root(parents,key(a)),v=root(parents,key(b));check(!u.equals(v),"no split/rejoin drainage cycles");parents.put(u,v);segments.add(new RiverRibbon.Segment(a,b,arm.depth()));
   }
   check(RiverCrossingAudit.count(new RiverRibbon.Network(segments))==0,"no accidental tributary crossings");
   if(seed==83){node.values.put("westBranches",2.0);var extra=EnvironmentNodes.apply(node,new EnvironmentMap(h),null,n,size,62,seed,()->false);check(extra.forkRoots>e.forkRoots,"left density adds tributaries");long right=e.mountainForks.stream().filter(f->f.generation()==0&&f.x()>=size*.5).count(),rightExtra=extra.mountainForks.stream().filter(f->f.generation()==0&&f.x()>=size*.5).count();check(right==rightExtra,"left density does not add right-side roots");}
  }
  if(args.length>0){
   var graph=GraphEditor.readFile(java.nio.file.Path.of(args[0]));if(args.length>1){for(var node:graph.nodes.values())if(node.type==TerrainGraph.Type.RIVERS)node.values.put("westBranches",2.0);GraphEditor.writeFile(graph,java.nio.file.Path.of(args[1]));}LargeWorldBuilder.configure(16384);LargeWorldBuilder.SEED=74839261;LargeWorldBuilder.SEA=63;LargeWorldBuilder.ACTIVE_GRAPH=graph;var e=LargeWorldBuilder.layout();
   check(RiverCrossingAudit.count(e.ribbons)==0,"actual map has no accidental channel crossings");
   var parents=new HashMap<String,String>();for(var arm:e.mountainArms)for(int i=1;i<arm.points().size();i++){var a=arm.points().get(i-1);var b=arm.points().get(i);check(a.water()>=b.water(),"actual map downhill tributary");String u=root(parents,key(a)),v=root(parents,key(b));check(!u.equals(v),"actual map tributary cycle");parents.put(u,v);}
   var world=new TerraForge.World(LargeWorldBuilder.GRID,63,74839261);world.height=e.height;world.environment=e;world.buildHeight=ExtendedHeight.forPeak(LargeWorldBuilder.terrainCeiling());javax.imageio.ImageIO.write(EnvironmentView.render(world,0),"png",java.nio.file.Path.of("dist/natural-tributaries-city.png").toFile());
   var p=e.mountainForks.stream().filter(f->f.x()%512>128&&f.x()%512<384&&f.z()%512>128&&f.z()%512<384).findFirst().orElse(e.mountainForks.get(0));var tile=LargeWorldBuilder.tile(e,(int)p.x()/512,(int)p.z()/512);javax.imageio.ImageIO.write(EnvironmentView.render(tile,0),"png",java.nio.file.Path.of("dist/natural-tributaries-detail.png").toFile());
   System.out.println("CITY: "+e.forkRoots+" tributary roots, "+e.mountainArms.size()+" arms; left roots="+e.mountainForks.stream().filter(f->f.generation()==0&&f.x()<8192).count()+"; no crossings/cycles; full and detailed renders saved");
  }
  System.out.println("PASS branching drainage trees, downstream water/width profiles, no cycles or crossings across three seeds");
 }
}
