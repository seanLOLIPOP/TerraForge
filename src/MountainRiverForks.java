import java.util.*;
import java.util.function.BooleanSupplier;
/** Short terrain-guided mountain rills with recursive, irregular forks and tapered leaf ends. */
final class MountainRiverForks {
 record Fork(double x,double z,double armX,double armZ,double elevation,double separation,int generation){}
 record Arm(List<RiverRibbon.Point> points,double depth,int generation){}
 record Source(RiverRibbon.Point point,double angle,int group){}
 static final class Tree {List<RiverRibbon.Point> points;Tree left,right;boolean joined;Tree(List<RiverRibbon.Point> points,boolean joined){this.points=points;this.joined=joined;}}
 static final class Builder {
  final float[] terrain,spill;final int n,size,sea;final double block,preferred;final long seed;final RiverRibbon.Network base;final BooleanSupplier stop;final Random random;
  Builder(float[] terrain,float[] spill,int n,int size,int sea,double preferred,long seed,RiverRibbon.Network base,BooleanSupplier stop){this.terrain=terrain;this.spill=spill;this.n=n;this.size=size;this.sea=sea;this.preferred=preferred;this.seed=seed;this.base=base;this.stop=stop;block=size/(double)n;random=new Random(seed);}
  Tree grow(RiverRibbon.Point start,double heading,double length,double radius,int remaining){
   EnvironmentNodes.cancel(stop);var points=new ArrayList<RiverRibbon.Point>();points.add(new RiverRibbon.Point(start.x(),start.z(),radius,start.water(),start.discharge()));double x=start.x(),z=start.z(),ground=RiverShape.height(terrain,n,x/block,z/block),uphill=0;boolean joined=false;
   for(int step=0;step<(int)Math.ceil(length/8);step++){double best=Double.POSITIVE_INFINITY,bx=0,bz=0,bh=0,ba=heading;
    for(int k=-4;k<=4;k++){double angle=heading+k*.025,xx=x+Math.cos(angle)*8,zz=z+Math.sin(angle)*8;if(xx<2*block||zz<2*block||xx>=size-2*block||zz>=size-2*block)continue;double h=RiverShape.height(terrain,n,xx/block,zz/block),slope=(ground-h)/8;
     int cell=NaturalRivers.clamp((int)Math.round(zz/block),n)*n+NaturalRivers.clamp((int)Math.round(xx/block),n);if(h<sea+2||spill[cell]-terrain[cell]>1.5||slope>preferred||RiverCourse.terrainSlope(terrain,n,xx/block,zz/block,block)>preferred||slope<-.025||uphill+Math.max(0,h-ground)>1.5)continue;
     var safe=NaturalRivers.safe(xx/block,zz/block,new double[]{x/block,z/block},terrain,spill,n,true);if(Math.abs(safe[0]-xx/block)>.001||Math.abs(safe[1]-zz/block)>.001)continue;
     double score=Math.max(0,slope-preferred)*30+Math.max(0,-slope)*80+Math.abs(slope-.07)*.8+k*k*.015+TerraForge.sample(xx/90,zz/90,seed+713)*.12;
     if(score<best){best=score;bx=xx;bz=zz;bh=h;ba=angle;}
    }
    if(!Double.isFinite(best))break;uphill+=Math.max(0,bh-ground);x=bx;z=bz;ground=bh;heading=ba;var previous=points.get(points.size()-1);double water=Math.max(sea,Math.min(previous.water(),Math.floor(ground)));double width=Math.max(1.1,radius*(.9+.15*TerraForge.sample(x/60,z/60,seed+91)));points.add(new RiverRibbon.Point(x,z,width,water,previous.discharge()));
    if(step>3&&RiverSideBranches.clearance(base,x,z)<-.2){joined=true;break;}
   }
   if(points.size()<4)return null;var tree=new Tree(List.copyOf(points),joined);if(remaining<=1||joined)return tree;
   var end=points.get(points.size()-1);var before=points.get(points.size()-2);double direction=Math.atan2(end.z()-before.z(),end.x()-before.x()),left=.18+random.nextDouble()*.18,right=.18+random.nextDouble()*.18;
   var childStart=new RiverRibbon.Point(end.x(),end.z(),end.radius(),end.water(),end.discharge()*.5);tree.left=grow(childStart,direction-left,Math.max(24,length*(.52+random.nextDouble()*.18)),Math.max(1.1,radius*.73),remaining-1);
   tree.right=grow(childStart,direction+right,Math.max(24,length*(.52+random.nextDouble()*.18)),Math.max(1.1,radius*.73),remaining-1);
   if(tree.left!=null&&tree.right!=null){var a=last(tree.left.points);var b=last(tree.right.points);if(Math.hypot(a.x()-b.x(),a.z()-b.z())<8)tree.right=null;}
   return tree;
  }
 }
 static RiverRibbon.Point last(List<RiverRibbon.Point> p){return p.get(p.size()-1);}
 static void generate(TerrainGraph.Node node,EnvironmentMap e,float[] original,EnvironmentNodes.Drainage drainage,int n,int size,int sea,long seed,double nominalRadius,BooleanSupplier stop){
  int wanted=(int)node.p("mountainForks");if(wanted==0||e.ribbons==null||e.ribbons.segments.isEmpty())return;double block=size/(double)n;var base=e.ribbons;var candidates=new ArrayList<Source>();
  var groups=new LinkedHashMap<Integer,List<Source>>();int group=0;RiverRibbon.Point previous=null;
  for(int k=0;k<base.segments.size();k++){var segment=base.segments.get(k);var p=segment.a();if(previous!=null&&Math.hypot(previous.x()-p.x(),previous.z()-p.z())>.1)group++;previous=segment.b();if(k%3!=0||p.water()<sea+5||RiverCourse.terrainSlope(original,n,p.x()/block,p.z()/block,block)>node.p("maxSlope")||Math.abs(RiverShape.height(original,n,p.x()/block,p.z()/block)-p.water())>5)continue;groups.computeIfAbsent(group,key->new ArrayList<>()).add(new Source(p,Math.atan2(segment.b().z()-p.z(),segment.b().x()-p.x()),group));}
  var random=new Random(seed+76421);for(var list:groups.values())Collections.shuffle(list,random);
  for(int round=0;;round++){boolean added=false;for(var list:groups.values())if(round<list.size()){candidates.add(list.get(round));added=true;}if(!added)break;}
  var covered=new HashSet<Integer>();var roots=new ArrayList<RiverRibbon.Point>();var forks=new ArrayList<Fork>();var arms=new ArrayList<Arm>();var tails=new ArrayList<RiverRibbon.SideBranch>();var segments=new ArrayList<>(base.segments);
  for(int pass=0;pass<2;pass++)for(var source:candidates){if(roots.size()>=wanted)break;if(pass==0&&covered.contains(source.group()))continue;EnvironmentNodes.cancel(stop);var p=source.point();boolean near=false;for(var root:roots)if(Math.hypot(root.x()-p.x(),root.z()-p.z())<Math.max(node.p("riverSpacing")*.65,node.p("forkLength")*1.8))near=true;if(near)continue;
   long local=seed+(long)p.x()*713+(long)p.z()*997;var builder=new Builder(original,drainage.level(),n,size,sea,node.p("maxSlope"),local,base,stop);double side=(builder.random.nextBoolean()?1:-1)*(.22+builder.random.nextDouble()*.15),radius=Math.max(1.3,Math.min(2.4,p.radius()*.65)),length=node.p("forkLength")*(.75+builder.random.nextDouble()*.4);
   var tree=builder.grow(p,source.angle()+side,length,radius,(int)node.p("forkDepth"));if(tree==null||node.p("forkDepth")>1&&(tree.left==null||tree.right==null))continue;var end=last(tree.points);if(RiverSideBranches.clearance(base,end.x(),end.z())<3)continue;
   covered.add(source.group());roots.add(p);forks.add(new Fork(p.x(),p.z(),end.x(),end.z(),p.water(),RiverSideBranches.clearance(base,end.x(),end.z()),0));emit(tree,0,segments,arms,tails,forks);
  }
  var network=new RiverRibbon.Network(segments,base.cutoffs,base.splitDensity,base.sideBranches);var validTails=new ArrayList<RiverRibbon.SideBranch>(base.sideBranches);for(var tail:tails){var tip=last(tail.points());if(RiverSideBranches.clearance(network,tip.x(),tip.z())>=5)validTails.add(tail);}
  e.ribbons=network.options(base.splitDensity,validTails);e.forkRiverGroups=covered.size();e.forkEligibleGroups=groups.size();e.forkRoots=roots.size();e.forkTipCount=validTails.size()-base.sideBranches.size();e.mountainArms=List.copyOf(arms);e.mountainForks=List.copyOf(forks);
  System.out.println("SHORT_RIVER_FORKS: riverGroups="+covered.size()+"/"+groups.size()+" roots="+roots.size()+" junctions="+forks.size()+" shortArms="+arms.size()+" taperTips="+e.forkTipCount);
 }
 static void emit(Tree tree,int generation,List<RiverRibbon.Segment> segments,List<Arm> arms,List<RiverRibbon.SideBranch> tails,List<Fork> forks){
  var points=tree.points;boolean leaf=tree.left==null&&tree.right==null;int end=leaf&&!tree.joined?Math.max(2,(int)Math.floor((points.size()-1)*.55)):points.size()-1;var wet=List.copyOf(points.subList(0,end+1));double depth=Math.max(1.1,1.6-generation*.2);arms.add(new Arm(wet,depth,generation));
  for(int i=1;i<wet.size();i++)segments.add(new RiverRibbon.Segment(wet.get(i-1),wet.get(i),depth,.25,0));
  if(end<points.size()-1){var tail=new ArrayList<RiverRibbon.Point>();tail.add(points.get(end));for(int i=end+1;i<points.size();i++){var a=points.get(i-1);var b=points.get(i);int count=Math.max(1,(int)Math.ceil(Math.hypot(b.x()-a.x(),b.z()-a.z())/2));for(int j=1;j<=count;j++)tail.add(RiverRibbon.lerp(a,b,j/(double)count));}var taper=new ArrayList<RiverRibbon.Point>();for(int i=0;i<tail.size();i++){var p=tail.get(i);taper.add(new RiverRibbon.Point(p.x(),p.z(),Math.max(.2,p.radius()*(1-.85*i/(tail.size()-1.))),p.water(),p.discharge()));}tails.add(new RiverRibbon.SideBranch(List.copyOf(taper),1.5,true));}
  if(tree.left!=null&&tree.right!=null){var start=last(points);var a=wetEnd(tree.left);var b=wetEnd(tree.right);forks.add(new Fork(start.x(),start.z(),a.x(),a.z(),start.water(),Math.hypot(a.x()-b.x(),a.z()-b.z()),generation+1));}
  if(tree.left!=null)emit(tree.left,generation+1,segments,arms,tails,forks);if(tree.right!=null)emit(tree.right,generation+1,segments,arms,tails,forks);
 }
 static RiverRibbon.Point wetEnd(Tree tree){int i=tree.left==null&&tree.right==null&&!tree.joined?Math.max(2,(int)Math.floor((tree.points.size()-1)*.55)):tree.points.size()-1;return tree.points.get(i);}
 static void carveArms(EnvironmentMap e,int n,int sea){
  for(var arm:e.mountainArms)for(int k=1;k<arm.points.size();k++){var a=arm.points.get(k-1);var b=arm.points.get(k);double dx=b.x()-a.x(),dz=b.z()-a.z(),len2=Math.max(.001,dx*dx+dz*dz),pad=Math.max(a.radius(),b.radius())+1;
   for(int z=Math.max(0,(int)Math.floor(Math.min(a.z(),b.z())-pad));z<=Math.min(n-1,(int)Math.ceil(Math.max(a.z(),b.z())+pad));z++)for(int x=Math.max(0,(int)Math.floor(Math.min(a.x(),b.x())-pad));x<=Math.min(n-1,(int)Math.ceil(Math.max(a.x(),b.x())+pad));x++){int i=z*n+x;if(e.waterY(i,sea)>Math.round(e.height[i]))continue;double t=Math.max(0,Math.min(1,((x-a.x())*dx+(z-a.z())*dz)/len2)),r=a.radius()+(b.radius()-a.radius())*t,d=Math.hypot(x-a.x()-dx*t,z-a.z()-dz*t);if(d>r)continue;int water=Math.max(sea,(int)Math.round(a.water()+(b.water()-a.water())*t));e.height[i]=(float)(water-Math.max(1,arm.depth*(1-d/r)));e.water[i]=water;e.river[i]=.8f;e.material[i]=7;if(e.snow!=null)e.snow[i]=0;
   }
  }
 }
}
