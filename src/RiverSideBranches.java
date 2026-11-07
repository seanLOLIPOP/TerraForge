import java.util.*;
/** Short backwaters with seasonal beds that fade into gentle adjacent terrain. */
final class RiverSideBranches {
 static List<RiverRibbon.SideBranch> generate(RiverRibbon.Network network,float[] terrain,float[] spill,int n,int size,int sea,int wanted,long seed,double maximumSlope){return generate(network,terrain,spill,n,size,sea,wanted,seed,maximumSlope,false);}
 static List<RiverRibbon.SideBranch> generate(RiverRibbon.Network network,float[] terrain,float[] spill,int n,int size,int sea,int wanted,long seed,double maximumSlope,boolean natural){
  if(wanted==0)return List.of();double block=size/(double)n;
  var candidates=new ArrayList<>(network.segments);Collections.shuffle(candidates,new Random(seed+7351));
  var result=new ArrayList<RiverRibbon.SideBranch>();var space=new RiverBranchSpace(network);
  for(var segment:candidates){if(result.size()>=wanted)break;var start=segment.a();double length=Math.hypot(segment.b().x()-start.x(),segment.b().z()-start.z());
   if(start.water()<sea+6||start.radius()<2.5||Math.abs(segment.b().water()-start.water())/Math.max(1,length)>.06)continue;
   double ground=RiverShape.height(terrain,n,start.x()/block,start.z()/block);if(Math.abs(ground-start.water())>6)continue;
   boolean near=false;for(var branch:result){var p=branch.points().get(0);if(Math.hypot(p.x()-start.x(),p.z()-start.z())<320)near=true;}if(near)continue;
   long local=seed+(long)start.x()*713+(long)start.z()*997;var random=new Random(local);
   double heading=Math.atan2(segment.b().z()-start.z(),segment.b().x()-start.x())+(random.nextBoolean()?1:-1)*(natural?.45+random.nextDouble()*.35:1.05+random.nextDouble()*.4),x=start.x(),z=start.z(),lastHeight=ground;
   int owner=space.owner();int steps=12+random.nextInt(13);double initialRadius=Math.max(1.3,Math.min(2.3,start.radius()*.6));var points=new ArrayList<RiverRibbon.Point>();points.add(new RiverRibbon.Point(x,z,initialRadius,start.water(),0));
   for(int k=1;k<=steps;k++){if(natural)heading+=(TerraForge.sample(k/12.,.31,local+191)-.5)*.12;double best=Double.POSITIVE_INFINITY,bx=0,bz=0,angle=heading,bh=0;boolean found=false;
    for(int j=-4;j<=4;j++){double a=heading+j*.12,xx=x+Math.cos(a)*8,zz=z+Math.sin(a)*8;if(xx<2*block||zz<2*block||xx>size-2*block||zz>size-2*block)continue;
     double h=RiverShape.height(terrain,n,xx/block,zz/block),slope=Math.abs(h-lastHeight)/8;int cell=NaturalRivers.clamp((int)Math.round(zz/block),n)*n+NaturalRivers.clamp((int)Math.round(xx/block),n);
     if(spill[cell]-terrain[cell]>1.5||slope>maximumSlope||RiverCourse.terrainSlope(terrain,n,xx/block,zz/block,block)>maximumSlope||(natural?h<ground-steps*8*maximumSlope||h>lastHeight+.2:h<ground-1||h>ground+10))continue;var safe=NaturalRivers.safe(xx/block,zz/block,new double[]{x/block,z/block},terrain,spill,n,true);if(Math.abs(safe[0]-xx/block)>.001||Math.abs(safe[1]-zz/block)>.001)continue;
     var candidate=new RiverRibbon.Point(xx,zz,initialRadius,start.water());var contact=space.contact(xx,zz,initialRadius,owner,k*8,start);if(k>6&&contact!=null&&contact.clearance()<4||space.crosses(points.get(points.size()-1),candidate))continue;
     double target=ground+k*(natural?-.12:.22),score=Math.abs(h-target)+j*j*.035+Math.max(0,lastHeight-h)*2;
     if(score<best){best=score;bx=xx;bz=zz;angle=a;bh=h;found=true;}
    }
    if(!found)break;x=bx;z=bz;heading=angle;lastHeight=bh;var previous=points.get(points.size()-1);var point=new RiverRibbon.Point(x,z,initialRadius*(1-k/(double)steps*.8),natural?Math.min(previous.water(),Math.floor(bh)):start.water(),0);points.add(point);space.add(previous,point,owner,k*8);
   }
   if(points.size()<10){space.retire(owner);continue;}var tip=points.get(points.size()-1);if(clearance(network,tip.x(),tip.z())<6){space.retire(owner);continue;}
   // Rescale the taper if terrain stopped the branch early.
   var tapered=new ArrayList<RiverRibbon.Point>();for(int k=0;k<points.size();k++){var p=points.get(k);tapered.add(new RiverRibbon.Point(p.x(),p.z(),initialRadius*(1-.85*k/(points.size()-1.)),p.water(),0));}
   space.wetEnd(owner,(tapered.size()-1)*8*.28);
   result.add(new RiverRibbon.SideBranch(List.copyOf(tapered),1.8,natural));
  }
  return List.copyOf(result);
 }
 static double clearance(RiverRibbon.Network network,double x,double z){double nearest=Double.POSITIVE_INFINITY;for(var segment:network.query((int)x,(int)z)){var a=segment.a();var b=segment.b();double dx=b.x()-a.x(),dz=b.z()-a.z(),t=Math.max(0,Math.min(1,((x-a.x())*dx+(z-a.z())*dz)/Math.max(.001,dx*dx+dz*dz)));nearest=Math.min(nearest,Math.hypot(x-a.x()-dx*t,z-a.z()-dz*t)-a.radius()-(b.radius()-a.radius())*t);}return nearest;}
 static void carve(EnvironmentMap e,int n,int ox,int oz,int sea,RiverRibbon.Network network,EnvironmentMap global){carveScaled(e,n,n,ox,oz,sea,network,global);}
 static void carveScaled(EnvironmentMap e,int n,int size,int ox,int oz,int sea,RiverRibbon.Network network,EnvironmentMap global){
  double block=size/(double)n;
  for(var branch:network.sideBranches){var points=branch.points();var start=points.get(0);double level=global==null?start.water():RiverRibbon.surfaceAt(global,start,sea);if(level<=sea)continue;
   for(int k=1;k<points.size();k++){var a=points.get(k-1);var b=points.get(k);double dx=b.x()-a.x(),dz=b.z()-a.z(),len2=Math.max(.001,dx*dx+dz*dz),pad=Math.max(a.radius(),b.radius())+3;
    int x0=Math.max(0,(int)Math.floor((Math.min(a.x(),b.x())-pad-ox)/block)),x1=Math.min(n-1,(int)Math.ceil((Math.max(a.x(),b.x())+pad-ox)/block)),z0=Math.max(0,(int)Math.floor((Math.min(a.z(),b.z())-pad-oz)/block)),z1=Math.min(n-1,(int)Math.ceil((Math.max(a.z(),b.z())+pad-oz)/block));
    for(int z=z0;z<=z1;z++)for(int x=x0;x<=x1;x++){int i=z*n+x;if(e.waterY(i,sea)>Math.round(e.height[i]))continue;
     double t=Math.max(0,Math.min(1,((ox+x*block-a.x())*dx+(oz+z*block-a.z())*dz)/len2)),progress=(k-1+t)/(points.size()-1),radius=Math.max(branch.wetFraction()!=.28?block*.65:0,a.radius()+(b.radius()-a.radius())*t),d=Math.hypot(ox+x*block-a.x()-dx*t,oz+z*block-a.z()-dz*t);if(d>radius+3)continue;
     double localLevel=branch.downslopeTip()?a.water()+(b.water()-a.water())*t+level-start.water():level;double bed=branch.downslopeTip()?localLevel-branch.depth()*Math.max(0,1-progress/.3)+Math.max(0,progress-.3):level-branch.depth()+progress*7,bank=localLevel+.7+d*.3,target=bed+(bank-bed)*Math.min(1,Math.pow(d/Math.max(.3,radius+3),2)),fade=1-TerraForge.smooth(Math.max(0,(progress-.7)/.3));
     if(branch.wetFraction()!=.28){fade=branch.terminalRadius()>0?1:1-TerraForge.smooth(Math.max(0,(progress-.88)/.12));double wet=branch.wetFraction();bed=progress<wet?localLevel-Math.max(branch.terminalRadius()>0?1:.6,branch.depth()*(1-progress/wet)):localLevel+.3+(progress-wet)/Math.max(.01,1-wet)*2;target=bed+(bank-bed)*Math.min(1,Math.pow(d/Math.max(.3,radius+3),2));}
     double ground=e.height[i]+(Math.min(e.height[i],target)-e.height[i])*fade;e.height[i]=(float)ground;
     if(progress<branch.wetFraction()&&d<=Math.max(radius,branch.wetFraction()!=.28?block*.65:radius)&&Math.round(ground)<Math.round(localLevel)){e.water[i]=(float)localLevel;e.river[i]=.65f;e.material[i]=7;if(e.snow!=null)e.snow[i]=0;}
     else if(d<radius+1&&progress<.9&&fade>.1){e.material[i]=(byte)(progress<.5?7:10);}
    }
   }
   RiverTerminals.lake(e,n,block,ox,oz,sea,branch);
  }
 }
}
