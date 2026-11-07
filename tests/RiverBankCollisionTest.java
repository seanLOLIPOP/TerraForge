import java.util.*;
public class RiverBankCollisionTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static RiverRibbon.Point p(double x,double z){return new RiverRibbon.Point(x,z,3,90);}
 static double length(List<RiverRibbon.Point> ps){double d=0;for(int i=1;i<ps.size();i++)d+=Math.hypot(ps.get(i).x()-ps.get(i-1).x(),ps.get(i).z()-ps.get(i-1).z());return d;}
 public static void main(String[] args)throws Exception{
  var boundary=new RiverBranchSpace(new RiverRibbon.Network(List.of(new RiverRibbon.Segment(p(46,10),p(46,40),3))));var neighbor=boundary.contact(66,20,3,boundary.owner(),80,null);check(neighbor!=null&&neighbor.clearance()<16,"bank clearance searches adjacent spatial buckets");
  var unrelated=new RiverBranchSpace(new RiverRibbon.Network(List.of(new RiverRibbon.Segment(p(60,10),p(60,100),3))));check(unrelated.contact(80,40,3,unrelated.owner(),16,p(80,20))!=null,"junction exemption cannot hide an unrelated nearby river");
  var main=new RiverRibbon.Network(List.of(new RiverRibbon.Segment(p(20,60),p(240,60),3)));
  var incoming=List.of(p(100,40),p(100,50),p(100,58));var joined=RiverJunctions.fit(incoming,main);check(joined.get(joined.size()-1).z()==60,"early bank-only collision becomes one endpoint merge");
  var high=List.of(new RiverRibbon.Point(100,40,3,100),new RiverRibbon.Point(100,50,3,100),new RiverRibbon.Point(100,58,3,100));var stopped=RiverJunctions.fit(high,main);check(stopped.get(stopped.size()-1).z()<=52,"incompatible water levels stop before touching banks");
  var loop=List.of(p(20,20),p(120,20),p(120,100),p(20,100),p(20,25),p(0,25));var clean=RiverJunctions.fit(loop,new RiverRibbon.Network(List.of()));check(clean.get(clean.size()-1).equals(loop.get(loop.size()-1)),"near-self loop retains outlet");check(length(clean)<length(loop)*.5,"near-self loop erased without requiring an X intersection");
  var nearby=List.of(p(100,30),p(100,45),p(100,49));var coarse=RiverJunctions.fit(nearby,main,5.2);check(coarse.get(coarse.size()-1).z()==60,"coarse-cell width prevents apparent map collisions");
  var separate=RiverJunctions.fit(List.of(p(20,20),p(240,20)),main);check(separate.size()==2&&separate.get(1).z()==20,"separated channels retained");
  if(args.length>0){
   LargeWorldBuilder.configure(16384);LargeWorldBuilder.SEED=74839261;LargeWorldBuilder.SEA=63;LargeWorldBuilder.ACTIVE_GRAPH=GraphEditor.readFile(java.nio.file.Path.of(args[0]));var e=LargeWorldBuilder.layout();
   int arms=e.mountainArms.stream().mapToInt(a->a.points().size()-1).sum(),mainCount=e.ribbons.segments.size()-arms;
   try(var out=java.nio.file.Files.newBufferedWriter(java.nio.file.Path.of("dist/river-collision-geometry.csv"))){for(int i=0;i<e.ribbons.segments.size();i++){var q=e.ribbons.segments.get(i);out.write((i<mainCount?"main":"fork")+","+q.a().x()+","+q.a().z()+","+q.b().x()+","+q.b().z()+"\n");}for(var branch:e.ribbons.sideBranches)for(int i=1;i<branch.points().size();i++){var a=branch.points().get(i-1);var b=branch.points().get(i);out.write("tail,"+a.x()+","+a.z()+","+b.x()+","+b.z()+"\n");}}
   var world=new TerraForge.World(2048,63,74839261);world.height=e.height;world.environment=e;world.buildHeight=ExtendedHeight.forPeak(LargeWorldBuilder.terrainCeiling());javax.imageio.ImageIO.write(EnvironmentView.render(world,0),"png",java.nio.file.Path.of("dist/river-bank-map.png").toFile());
  }
  System.out.println("PASS bank-only merging, early contacts, incompatible levels, near-self loop removal, coarse footprint and separate channels");
 }
}
