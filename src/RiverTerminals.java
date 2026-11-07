import java.util.*;
final class RiverTerminals {
 static RiverRibbon.Network configure(TerrainGraph.Node node,RiverRibbon.Network network,float[] terrain,int n,int size,int sea,long seed){
  if(node.p("naturalEndings")<.5)return network;
  double block=size/(double)n;var branches=new ArrayList<RiverRibbon.SideBranch>();var ponds=new ArrayList<RiverRibbon.Point>();
  for(var branch:network.sideBranches){var p=branch.points().get(branch.points().size()-1);var random=new Random(seed+node.id*719L+(long)p.x()*137+(long)p.z()*173);
   double radius=node.p("endLakeRadius")*(.7+random.nextDouble()*.6),ground=RiverShape.height(terrain,n,p.x()/block,p.z()/block);
   boolean lake=random.nextDouble()<node.p("lakeEndShare")&&p.water()>sea+2&&Math.abs(ground-p.water())<=6&&RiverCourse.terrainSlope(terrain,n,p.x()/block,p.z()/block,block)<.3&&RiverSideBranches.clearance(network,p.x(),p.z())>radius+5&&p.x()>radius+16&&p.z()>radius+16&&p.x()<size-radius-16&&p.z()<size-radius-16;
   if(lake)for(var other:network.sideBranches){if(other==branch)continue;for(var q:other.points())if(Math.hypot(q.x()-p.x(),q.z()-p.z())<radius+q.radius()+4){lake=false;break;}}
   for(var pond:ponds)if(Math.hypot(pond.x()-p.x(),pond.z()-p.z())<radius+node.p("endLakeRadius")+20)lake=false;
   if(lake)ponds.add(p);
   branches.add(new RiverRibbon.SideBranch(branch.points(),branch.depth(),branch.downslopeTip(),lake?1:.72+random.nextDouble()*.14,lake?radius:0));
  }
  return network.options(network.splitDensity,branches);
 }
 static void lake(EnvironmentMap e,int n,double block,int ox,int oz,int sea,RiverRibbon.SideBranch branch){
  if(branch.terminalRadius()<=0)return;var tip=branch.points().get(branch.points().size()-1);double radius=branch.terminalRadius(),water=Math.floor(tip.water());
  if(water<=sea)return;if(e.lakeMask==null)e.lakeMask=new byte[n*n];
  int x0=Math.max(0,(int)Math.floor((tip.x()-radius-4-ox)/block)),x1=Math.min(n-1,(int)Math.ceil((tip.x()+radius+4-ox)/block));
  int z0=Math.max(0,(int)Math.floor((tip.z()-radius-4-oz)/block)),z1=Math.min(n-1,(int)Math.ceil((tip.z()+radius+4-oz)/block));
  for(int z=z0;z<=z1;z++)for(int x=x0;x<=x1;x++){
   int i=z*n+x;double dx=ox+x*block-tip.x(),dz=oz+z*block-tip.z(),angle=Math.atan2(dz,dx),rim=radius*(1+.09*Math.sin(angle*3+tip.x()*.01)+.05*Math.sin(angle*5+tip.z()*.01)),d=Math.hypot(dx,dz)/rim;
   if(d<1){e.height[i]=(float)Math.min(e.height[i],water-1-3*Math.pow(1-d*d,1.2));e.water[i]=(float)water;e.river[i]=.2f;e.lakeMask[i]=1;e.material[i]=(byte)(d>.7?2:5);if(e.snow!=null)e.snow[i]=0;}
  }
 }
}
