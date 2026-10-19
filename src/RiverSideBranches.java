import java.util.*;
/** Short backwaters with seasonal beds that fade into gentle adjacent terrain. */
final class RiverSideBranches {
 static List<RiverRibbon.SideBranch> generate(RiverRibbon.Network network,float[] terrain,float[] spill,int n,int size,int sea,int wanted,long seed,double maximumSlope){
  if(wanted==0)return List.of();double block=size/(double)n;
  var candidates=new ArrayList<>(network.segments);Collections.shuffle(candidates,new Random(seed+7351));
  var result=new ArrayList<RiverRibbon.SideBranch>();
  for(var segment:candidates){if(result.size()>=wanted)break;var start=segment.a();double length=Math.hypot(segment.b().x()-start.x(),segment.b().z()-start.z());
   if(start.water()<sea+6||start.radius()<2.5||Math.abs(segment.b().water()-start.water())/Math.max(1,length)>.06)continue;
   double ground=RiverShape.height(terrain,n,start.x()/block,start.z()/block);if(Math.abs(ground-start.water())>6)continue;
   boolean near=false;for(var branch:result){var p=branch.points().get(0);if(Math.hypot(p.x()-start.x(),p.z()-start.z())<320)near=true;}if(near)continue;
   long local=seed+(long)start.x()*713+(long)start.z()*997;var random=new Random(local);
   double heading=Math.atan2(segment.b().z()-start.z(),segment.b().x()-start.x())+(random.nextBoolean()?1:-1)*(1.05+random.nextDouble()*.4),x=start.x(),z=start.z(),lastHeight=ground;
   int steps=12+random.nextInt(13);double initialRadius=Math.max(1.3,Math.min(2.3,start.radius()*.6));var points=new ArrayList<RiverRibbon.Point>();points.add(new RiverRibbon.Point(x,z,initialRadius,start.water(),0));
   for(int k=1;k<=steps;k++){double best=Double.POSITIVE_INFINITY,bx=0,bz=0,angle=heading,bh=0;boolean found=false;
    for(int j=-4;j<=4;j++){double a=heading+j*.12,xx=x+Math.cos(a)*8,zz=z+Math.sin(a)*8;if(xx<2*block||zz<2*block||xx>size-2*block||zz>size-2*block)continue;
     double h=RiverShape.height(terrain,n,xx/block,zz/block),slope=Math.abs(h-lastHeight)/8;int cell=NaturalRivers.clamp((int)Math.round(zz/block),n)*n+NaturalRivers.clamp((int)Math.round(xx/block),n);
     if(spill[cell]-terrain[cell]>1.5||slope>maximumSlope||RiverCourse.terrainSlope(terrain,n,xx/block,zz/block,block)>maximumSlope||h<ground-1||h>ground+10)continue;var safe=NaturalRivers.safe(xx/block,zz/block,new double[]{x/block,z/block},terrain,spill,n,true);if(Math.abs(safe[0]-xx/block)>.001||Math.abs(safe[1]-zz/block)>.001)continue;
     double target=ground+k*.22,score=Math.abs(h-target)+j*j*.035+Math.max(0,lastHeight-h)*2;
     if(score<best){best=score;bx=xx;bz=zz;angle=a;bh=h;found=true;}
    }
    if(!found)break;x=bx;z=bz;heading=angle;lastHeight=bh;points.add(new RiverRibbon.Point(x,z,initialRadius*(1-k/(double)steps*.8),start.water(),0));
   }
   if(points.size()<10)continue;var tip=points.get(points.size()-1);if(clearance(network,tip.x(),tip.z())<6)continue;
   // Rescale the taper if terrain stopped the branch early.
   var tapered=new ArrayList<RiverRibbon.Point>();for(int k=0;k<points.size();k++){var p=points.get(k);tapered.add(new RiverRibbon.Point(p.x(),p.z(),initialRadius*(1-.85*k/(points.size()-1.)),p.water(),0));}
   result.add(new RiverRibbon.SideBranch(List.copyOf(tapered),1.8));
  }
  return List.copyOf(result);
 }
 static double clearance(RiverRibbon.Network network,double x,double z){double nearest=Double.POSITIVE_INFINITY;for(var segment:network.query((int)x,(int)z)){var a=segment.a();var b=segment.b();double dx=b.x()-a.x(),dz=b.z()-a.z(),t=Math.max(0,Math.min(1,((x-a.x())*dx+(z-a.z())*dz)/Math.max(.001,dx*dx+dz*dz)));nearest=Math.min(nearest,Math.hypot(x-a.x()-dx*t,z-a.z()-dz*t)-a.radius()-(b.radius()-a.radius())*t);}return nearest;}
 static void carve(EnvironmentMap e,int n,int ox,int oz,int sea,RiverRibbon.Network network,EnvironmentMap global){
  for(var branch:network.sideBranches){var points=branch.points();var start=points.get(0);double level=global==null?start.water():RiverRibbon.surfaceAt(global,start,sea);if(level<=sea)continue;
   for(int k=1;k<points.size();k++){var a=points.get(k-1);var b=points.get(k);double dx=b.x()-a.x(),dz=b.z()-a.z(),len2=Math.max(.001,dx*dx+dz*dz),pad=Math.max(a.radius(),b.radius())+3;
    int x0=Math.max(0,(int)Math.floor(Math.min(a.x(),b.x())-pad)-ox),x1=Math.min(n-1,(int)Math.ceil(Math.max(a.x(),b.x())+pad)-ox),z0=Math.max(0,(int)Math.floor(Math.min(a.z(),b.z())-pad)-oz),z1=Math.min(n-1,(int)Math.ceil(Math.max(a.z(),b.z())+pad)-oz);
    for(int z=z0;z<=z1;z++)for(int x=x0;x<=x1;x++){int i=z*n+x;if(e.waterY(i,sea)>Math.round(e.height[i]))continue;
     double t=Math.max(0,Math.min(1,((ox+x-a.x())*dx+(oz+z-a.z())*dz)/len2)),progress=(k-1+t)/(points.size()-1),radius=a.radius()+(b.radius()-a.radius())*t,d=Math.hypot(ox+x-a.x()-dx*t,oz+z-a.z()-dz*t);if(d>radius+3)continue;
     double localLevel=branch.downslopeTip()?a.water()+(b.water()-a.water())*t+level-start.water():level;double bed=branch.downslopeTip()?localLevel-branch.depth()*Math.max(0,1-progress/.3)+Math.max(0,progress-.3):level-branch.depth()+progress*7,bank=localLevel+.7+d*.3,target=bed+(bank-bed)*Math.min(1,Math.pow(d/Math.max(.3,radius+3),2)),fade=1-TerraForge.smooth(Math.max(0,(progress-.7)/.3));
     double ground=e.height[i]+(Math.min(e.height[i],target)-e.height[i])*fade;e.height[i]=(float)ground;
     if(progress<.28&&d<=radius&&Math.round(ground)<Math.round(localLevel)){e.water[i]=(float)localLevel;e.river[i]=.65f;e.material[i]=7;e.snow[i]=0;}
     else if(d<radius+1&&progress<.9&&fade>.1){e.material[i]=(byte)(progress<.5?7:10);}
    }
   }
  }
 }
}
