import java.util.*;
import java.util.function.BooleanSupplier;
/** Terrain-guided tributaries grown upstream from confluences, with tapered headwaters. */
final class MountainRiverForks {
 record Fork(double x,double z,double armX,double armZ,double elevation,double separation,int generation){}
 record Arm(List<RiverRibbon.Point> points,double depth,int generation){}
 record Source(RiverRibbon.Point point,double angle,int group){}
 static final class Tree {List<RiverRibbon.Point> points;Tree left,right;boolean joined,upstream;int owner;Tree(List<RiverRibbon.Point> points,boolean joined){this.points=points;this.joined=joined;}}
 static final class Builder {
  final float[] terrain,spill;final int n,size,sea;final double block,preferred,hillside;final long seed;final RiverRibbon.Network base;final BooleanSupplier stop;final Random random;final RiverBranchSpace space;final double reach;boolean upstream;
  Builder(float[] terrain,float[] spill,int n,int size,int sea,double preferred,long seed,RiverRibbon.Network base,BooleanSupplier stop){this(terrain,spill,n,size,sea,preferred,preferred,seed,base,stop);}
  Builder(float[] terrain,float[] spill,int n,int size,int sea,double preferred,double hillside,long seed,RiverRibbon.Network base,BooleanSupplier stop){this(terrain,spill,n,size,sea,preferred,hillside,seed,base,stop,null,0);}
  Builder(float[] terrain,float[] spill,int n,int size,int sea,double preferred,double hillside,long seed,RiverRibbon.Network base,BooleanSupplier stop,RiverBranchSpace space,double reach){this.space=space;this.reach=reach;this.hillside=hillside;this.terrain=terrain;this.spill=spill;this.n=n;this.size=size;this.sea=sea;this.preferred=preferred;this.seed=seed;this.base=base;this.stop=stop;block=size/(double)n;random=new Random(seed);}
  Tree grow(RiverRibbon.Point start,double heading,double length,double radius,int remaining){
   int owner=space==null?0:space.owner();EnvironmentNodes.cancel(stop);var points=new ArrayList<RiverRibbon.Point>();points.add(new RiverRibbon.Point(start.x(),start.z(),radius,start.water(),start.discharge()));double x=start.x(),z=start.z(),ground=RiverShape.height(terrain,n,x/block,z/block),uphill=0;boolean joined=false;
   double reachLength=space!=null&&remaining>1?Math.min(length,112+random.nextDouble()*208):length;
   for(int step=0;step<(int)Math.ceil(reachLength/8);step++){double best=Double.POSITIVE_INFINITY,bx=0,bz=0,bh=0,ba=heading;
    for(int k=-4;k<=4;k++){double bend=space==null?0:(TerraForge.sample(step*8/Math.max(96,Math.min(640,length*.6)),.37,seed+owner*131L)-.5)*.10;double angle=heading+bend+k*.04,xx=x+Math.cos(angle)*8,zz=z+Math.sin(angle)*8;if(xx<2*block||zz<2*block||xx>=size-2*block||zz>=size-2*block)continue;double h=RiverShape.height(terrain,n,xx/block,zz/block),slope=(upstream?h-ground:ground-h)/8;
     int cell=NaturalRivers.clamp((int)Math.round(zz/block),n)*n+NaturalRivers.clamp((int)Math.round(xx/block),n);if(h<sea+2||spill[cell]-terrain[cell]>1.5||slope>preferred||RiverCourse.terrainSlope(terrain,n,xx/block,zz/block,block)>hillside||slope<-.025||uphill+Math.max(0,upstream?ground-h:h-ground)>1.5)continue;
     var contact=space==null?null:space.contact(xx,zz,radius,owner,step*8,start);if(contact!=null&&contact.clearance()<(upstream?Math.max(16,block*2):5)&&!(!upstream&&step>8&&contact.wet()&&contact.owner()!=owner&&contact.clearance()<0&&Math.abs(contact.point().water()-Math.floor(h))<=2))continue;
     if(space!=null){var candidate=new RiverRibbon.Point(xx,zz,radius,Math.floor(h));if(contact!=null&&!upstream&&step>8&&contact.wet()&&contact.owner()!=owner&&contact.clearance()<0&&Math.abs(contact.point().water()-Math.floor(h))<=2)candidate=contact.point();if(space.crosses(points.get(points.size()-1),candidate))continue;}
     var safe=NaturalRivers.safe(xx/block,zz/block,new double[]{x/block,z/block},terrain,spill,n,true);if(Math.abs(safe[0]-xx/block)>.001||Math.abs(safe[1]-zz/block)>.001)continue;
     double ahead=0;for(double distance:new double[]{24,48,72}){double sx=xx+Math.cos(angle)*distance,sz=zz+Math.sin(angle)*distance;double future=RiverCourse.terrainSlope(terrain,n,sx/block,sz/block,block);ahead+=Math.max(0,future-hillside)*distance*.35;}
     double score=ahead+Math.max(0,slope-preferred)*30+Math.max(0,-slope)*80+Math.abs(slope-.07)*.8+k*k*.015+TerraForge.sample(xx/90,zz/90,seed+713)*.12;
     if(score<best){best=score;bx=xx;bz=zz;bh=h;ba=angle;}
    }
    if(!Double.isFinite(best))break;uphill+=Math.max(0,upstream?ground-bh:bh-ground);x=bx;z=bz;ground=bh;heading=ba;var previous=points.get(points.size()-1);double water=Math.max(sea,(upstream?Math.max(previous.water(),Math.floor(ground)):Math.min(previous.water(),Math.floor(ground))));double width=Math.max(space==null?1.1:1.5,radius*(space==null?.9+.15*TerraForge.sample(x/60,z/60,seed+91):.7+.6*TerraForge.sample(step*8/144.,.71,seed+owner*317L)));if(upstream)width=Math.max(Math.min(.35,radius),radius*(1-.28*Math.min(1,step*8/reachLength)));var point=new RiverRibbon.Point(x,z,width,water,previous.discharge());if(space!=null){var touch=space.contact(x,z,width,owner,step*8,start);if(touch!=null&&!upstream&&step>8&&touch.wet()&&touch.owner()!=owner&&touch.clearance()<0&&Math.abs(touch.point().water()-water)<=2){point=new RiverRibbon.Point(touch.point().x(),touch.point().z(),width,touch.point().water(),previous.discharge());joined=true;}if(space.crosses(previous,point)){joined=false;break;}space.add(previous,point,owner,step*8);}points.add(point);if(joined)break;
    if(space==null&&step>3&&RiverSideBranches.clearance(base,x,z)<-.2){joined=true;break;}
   }
   if(points.size()<4){if(space!=null)space.retire(owner);return null;}var tree=new Tree(List.copyOf(points),joined);tree.owner=owner;tree.upstream=upstream;if(remaining<=1||joined)return finish(tree);
   var end=points.get(points.size()-1);var before=points.get(points.size()-2);double direction=Math.atan2(end.z()-before.z(),end.x()-before.x());
   if(upstream){
    // Continue the dominant valley; add a shorter, narrower headwater on one side.
    // Growing away from the outlet must never reconnect: that would create a drainage cycle.
    double share=.62+random.nextDouble()*.18,side=random.nextBoolean()?1:-1;
    var primary=new RiverRibbon.Point(end.x(),end.z(),end.radius(),end.water(),end.discharge()*share);
    var secondary=new RiverRibbon.Point(end.x(),end.z(),end.radius(),end.water(),end.discharge()*(1-share));
    tree.left=grow(primary,direction-side*(.08+random.nextDouble()*.12),Math.max(32,length*(.55+random.nextDouble()*.22)),Math.max(.25,end.radius()*Math.sqrt(share)),remaining-1);
    if(random.nextDouble()<.72)tree.right=grow(secondary,direction+side*(.5+random.nextDouble()*.35),Math.max(24,length*(.3+random.nextDouble()*.25)),Math.max(.25,end.radius()*Math.sqrt(1-share)),remaining-1);
   }else{
   double left=(space==null?.18:.48)+random.nextDouble()*(space==null?.18:.42),right=(space==null?.18:.48)+random.nextDouble()*(space==null?.18:.42);
   var childStart=new RiverRibbon.Point(end.x(),end.z(),end.radius(),end.water(),end.discharge()*.5);tree.left=grow(childStart,direction-left,Math.max(space==null?24:48,length*(space==null?.52+random.nextDouble()*.18:.65+random.nextDouble()*1.1)),Math.max(space==null?1.1:2.4,Math.min(5.5,radius*(space==null?.73:1.02+random.nextDouble()*.22))),remaining-1);
   tree.right=grow(childStart,direction+right,Math.max(space==null?24:48,length*(space==null?.52+random.nextDouble()*.18:.65+random.nextDouble()*1.1)),Math.max(space==null?1.1:2.4,Math.min(5.5,radius*(space==null?.73:1.02+random.nextDouble()*.22))),remaining-1);
   }
   if(tree.left!=null&&tree.right!=null){var a=last(tree.left.points);var b=last(tree.right.points);if(Math.hypot(a.x()-b.x(),a.z()-b.z())<8){retire(tree.right);tree.right=null;}}
   return finish(tree);
  }
  Tree finish(Tree tree){if(space!=null&&tree.left==null&&tree.right==null&&!tree.joined)space.wetEnd(tree.owner,Math.max(2,Math.floor((tree.points.size()-1)*.8))*8);return tree;}
  void retire(Tree tree){if(tree==null||space==null)return;space.retire(tree.owner);retire(tree.left);retire(tree.right);}
 }
 static RiverRibbon.Point last(List<RiverRibbon.Point> p){return p.get(p.size()-1);}
 static void generate(TerrainGraph.Node node,EnvironmentMap e,float[] original,EnvironmentNodes.Drainage drainage,int n,int size,int sea,long seed,double nominalRadius,BooleanSupplier stop){
  int wanted=(int)node.p("mountainForks");if(wanted==0||e.ribbons==null||e.ribbons.segments.isEmpty())return;double block=size/(double)n;var base=e.ribbons;var candidates=new ArrayList<Source>();
  var space=new RiverBranchSpace(base);var groups=new LinkedHashMap<Integer,List<Source>>();int group=0;RiverRibbon.Point previous=null;
  for(int k=0;k<base.segments.size();k++){var segment=base.segments.get(k);var p=segment.a();if(previous!=null&&Math.hypot(previous.x()-p.x(),previous.z()-p.z())>.1)group++;previous=segment.b();if(k%3!=0||p.water()<sea+5||RiverCourse.terrainSlope(original,n,p.x()/block,p.z()/block,block)>(node.p("watershed")>.5?.28:node.p("maxSlope"))||Math.abs(RiverShape.height(original,n,p.x()/block,p.z()/block)-p.water())>5)continue;groups.computeIfAbsent(group,key->new ArrayList<>()).add(new Source(p,Math.atan2(segment.b().z()-p.z(),segment.b().x()-p.x()),group));}
  var random=new Random(seed+76421);for(var list:groups.values()){Collections.shuffle(list,random);if(node.p("watershed")>.5)list.sort(Comparator.comparingDouble((Source source)->source.point().water()).reversed());}
  for(int round=0;;round++){boolean added=false;for(var list:groups.values())if(round<list.size()){candidates.add(list.get(round));added=true;}if(!added)break;}
  var covered=new HashSet<Integer>();var roots=new ArrayList<RiverRibbon.Point>();var forks=new ArrayList<Fork>();var arms=new ArrayList<Arm>();var tails=new ArrayList<RiverRibbon.SideBranch>();var segments=new ArrayList<>(base.segments);
  for(int pass=0;pass<3;pass++)for(var source:candidates){if(pass==2&&node.p("westBranches")<=0)break;int extra=pass==2?(int)Math.round(wanted*node.p("westBranches")*.5):0;if(roots.size()>=wanted+extra)break;if(pass==2&&source.point().x()>=size*.5)continue;if(pass==0&&covered.contains(source.group()))continue;EnvironmentNodes.cancel(stop);var p=source.point();boolean near=false;for(var root:roots)if(Math.hypot(root.x()-p.x(),root.z()-p.z())<Math.max(node.p("riverSpacing")*.65,node.p("forkLength")*1.8)*(pass==2?.45:1))near=true;if(near)continue;
   long local=seed+(long)p.x()*713+(long)p.z()*997;var builder=new Builder(original,drainage.level(),n,size,sea,node.p("maxSlope"),node.p("watershed")>.5?.28:node.p("maxSlope"),local,base,stop,space,node.p("branchReach"));double side=(builder.random.nextBoolean()?1:-1)*(space==null?.22+builder.random.nextDouble()*.15:.45+builder.random.nextDouble()*.35),radius=space==null?Math.max(1.3,Math.min(2.4,p.radius()*.65)):Math.max(2.8,Math.min(4,p.radius())),length=node.p("forkLength")*(space==null?.75+builder.random.nextDouble()*.4:.45+Math.pow(builder.random.nextDouble(),2)*node.p("branchReach")*3.8);
   builder.upstream=true;var tree=builder.grow(p,source.angle()+Math.PI+side,length,radius,(int)node.p("forkDepth"));if(tree==null)continue;if(space==null&&node.p("forkDepth")>1&&(tree.left==null||tree.right==null)){builder.retire(tree);continue;}var end=last(tree.points);if(!tree.joined&&RiverSideBranches.clearance(base,end.x(),end.z())<3){builder.retire(tree);continue;}
   covered.add(source.group());roots.add(p);forks.add(new Fork(p.x(),p.z(),end.x(),end.z(),p.water(),RiverSideBranches.clearance(base,end.x(),end.z()),0));emit(tree,0,segments,arms,tails,forks);
  }
  var network=new RiverRibbon.Network(segments,base.cutoffs,base.splitDensity,base.sideBranches);var validTails=new ArrayList<RiverRibbon.SideBranch>(base.sideBranches);for(var tail:tails){var tip=last(tail.points());if(RiverSideBranches.clearance(network,tip.x(),tip.z())>=5)validTails.add(tail);}
  e.ribbons=network.options(base.splitDensity,validTails);e.forkRiverGroups=covered.size();e.forkEligibleGroups=groups.size();e.forkRoots=roots.size();e.forkTipCount=validTails.size()-base.sideBranches.size();e.mountainArms=List.copyOf(arms);e.mountainForks=List.copyOf(forks);
  System.out.println("SHORT_RIVER_FORKS: riverGroups="+covered.size()+"/"+groups.size()+" roots="+roots.size()+" junctions="+forks.size()+" shortArms="+arms.size()+" taperTips="+e.forkTipCount);
 }
 static void emit(Tree tree,int generation,List<RiverRibbon.Segment> segments,List<Arm> arms,List<RiverRibbon.SideBranch> tails,List<Fork> forks){
  var points=tree.points;boolean leaf=tree.left==null&&tree.right==null;int end=leaf&&!tree.joined?Math.max(2,(int)Math.floor((points.size()-1)*.80)):points.size()-1;var ordered=new ArrayList<>(points.subList(0,end+1));if(tree.upstream)Collections.reverse(ordered);var wet=List.copyOf(ordered);double depth=Math.max(1.1,1.6-generation*.2);arms.add(new Arm(wet,depth,generation));
  for(int i=1;i<wet.size();i++)segments.add(new RiverRibbon.Segment(wet.get(i-1),wet.get(i),depth,.25,0));
  if(end<points.size()-1){var tail=new ArrayList<RiverRibbon.Point>();tail.add(points.get(end));for(int i=end+1;i<points.size();i++){var a=points.get(i-1);var b=points.get(i);int count=Math.max(1,(int)Math.ceil(Math.hypot(b.x()-a.x(),b.z()-a.z())/2));for(int j=1;j<=count;j++)tail.add(RiverRibbon.lerp(a,b,j/(double)count));}var taper=new ArrayList<RiverRibbon.Point>();for(int i=0;i<tail.size();i++){var p=tail.get(i);taper.add(new RiverRibbon.Point(p.x(),p.z(),Math.max(.2,p.radius()*(1-.85*i/(tail.size()-1.))),p.water(),p.discharge()));}tails.add(new RiverRibbon.SideBranch(List.copyOf(taper),1.5,true));}
  if(tree.left!=null&&tree.right!=null){var start=last(points);var a=wetEnd(tree.left);var b=wetEnd(tree.right);forks.add(new Fork(start.x(),start.z(),a.x(),a.z(),start.water(),Math.hypot(a.x()-b.x(),a.z()-b.z()),generation+1));}
  if(tree.left!=null)emit(tree.left,generation+1,segments,arms,tails,forks);if(tree.right!=null)emit(tree.right,generation+1,segments,arms,tails,forks);
 }
 static RiverRibbon.Point wetEnd(Tree tree){int i=tree.left==null&&tree.right==null&&!tree.joined?Math.max(2,(int)Math.floor((tree.points.size()-1)*.80)):tree.points.size()-1;return tree.points.get(i);}
 static void carveArms(EnvironmentMap e,int n,int sea){
  for(var arm:e.mountainArms)for(int k=1;k<arm.points.size();k++){var a=arm.points.get(k-1);var b=arm.points.get(k);double dx=b.x()-a.x(),dz=b.z()-a.z(),len2=Math.max(.001,dx*dx+dz*dz),pad=Math.max(a.radius(),b.radius())+1;
   for(int z=Math.max(0,(int)Math.floor(Math.min(a.z(),b.z())-pad));z<=Math.min(n-1,(int)Math.ceil(Math.max(a.z(),b.z())+pad));z++)for(int x=Math.max(0,(int)Math.floor(Math.min(a.x(),b.x())-pad));x<=Math.min(n-1,(int)Math.ceil(Math.max(a.x(),b.x())+pad));x++){int i=z*n+x;if(e.waterY(i,sea)>Math.round(e.height[i]))continue;double t=Math.max(0,Math.min(1,((x-a.x())*dx+(z-a.z())*dz)/len2)),r=a.radius()+(b.radius()-a.radius())*t,d=Math.hypot(x-a.x()-dx*t,z-a.z()-dz*t);if(d>r)continue;int water=Math.max(sea,(int)Math.round(a.water()+(b.water()-a.water())*t));e.height[i]=(float)(water-Math.max(1,arm.depth*(1-d/r)));e.water[i]=water;e.river[i]=.8f;e.material[i]=7;if(e.snow!=null)e.snow[i]=0;
   }
  }
 }
}
