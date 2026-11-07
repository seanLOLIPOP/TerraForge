import java.util.*;
/** Width-scaled, arc-length smoothing of the final route, including erased-loop shortcuts. */
final class RiverBendFairing {
 static List<RiverRibbon.Point> smooth(List<RiverRibbon.Point> input,float[] terrain,int n,double block){
  if(input.size()<3||terrain==null)return input;
  var points=new ArrayList<RiverRibbon.Point>();points.add(input.get(0));double until=4;var prev=input.get(0);
  for(int i=1;i<input.size();i++){var next=input.get(i);double length=Math.hypot(next.x()-prev.x(),next.z()-prev.z());while(length>=until&&length>.000001){prev=RiverRibbon.lerp(prev,next,until/length);points.add(prev);length=Math.hypot(next.x()-prev.x(),next.z()-prev.z());until=4;}until-=length;prev=next;}
  var end=input.get(input.size()-1);if(Math.hypot(end.x()-points.get(points.size()-1).x(),end.z()-points.get(points.size()-1).z())>.001)points.add(end);else points.set(points.size()-1,end);
  if(points.size()<3)return input;var anchors=List.copyOf(points);double[] arc=new double[points.size()];for(int i=1;i<arc.length;i++)arc[i]=arc[i-1]+Math.hypot(points.get(i).x()-points.get(i-1).x(),points.get(i).z()-points.get(i-1).z());double total=arc[arc.length-1];
  for(int pass=0;pass<3;pass++){
   double[] xx=new double[points.size()],zz=new double[points.size()],caps=new double[points.size()];Arrays.fill(caps,1);
   for(int i=0;i<points.size();i++){var p=points.get(i);double sigma=Math.max(16,Math.min(56,p.radius()*3)),sum=0,x=0,z=0;int span=(int)Math.ceil(sigma*2.5/4);
    for(int j=Math.max(0,i-span);j<=Math.min(points.size()-1,i+span);j++){double w=Math.exp(-.5*Math.pow((arc[j]-arc[i])/sigma,2));sum+=w;x+=points.get(j).x()*w;z+=points.get(j).z()*w;}
    xx[i]=x/sum-anchors.get(i).x();zz[i]=z/sum-anchors.get(i).z();
   }
   double firstX=xx[0],firstZ=zz[0],lastX=xx[xx.length-1],lastZ=zz[zz.length-1];
   for(int i=1;i<points.size()-1;i++){var origin=anchors.get(i);double sigma=Math.max(16,Math.min(56,origin.radius()*3)),left=1-TerraForge.smooth(Math.min(1,arc[i]/(sigma*3))),right=1-TerraForge.smooth(Math.min(1,(total-arc[i])/(sigma*3)));
    double dx=xx[i]-firstX*left-lastX*right,dz=zz[i]-firstZ*left-lastZ*right,distance=Math.hypot(dx,dz),limit=Math.max(40,Math.min(128,origin.radius()*6));if(distance>limit){dx*=limit/distance;dz*=limit/distance;distance=limit;}
    xx[i]=dx;zz[i]=dz;double fit=1;if(distance>.001){for(int attempt=0;attempt<16&&!valid(origin,dx*fit,dz*fit,terrain,n,block);attempt++)fit*=.5;}
    caps[i]=fit;
   }
   // Blend the terrain-fit envelope to avoid independent point clamps creating new kinks.
   double[] fits=caps.clone();for(int round=0;round<3;round++){var blend=RiverMorphology.gaussian(fits,2);for(int i=1;i<fits.length-1;i++)fits[i]=Math.min(caps[i],blend[i]);}
   var next=new ArrayList<RiverRibbon.Point>();next.add(anchors.get(0));for(int i=1;i<points.size()-1;i++){var p=anchors.get(i);next.add(new RiverRibbon.Point(p.x()+xx[i]*fits[i],p.z()+zz[i]*fits[i],p.radius(),p.water(),p.discharge()));}next.add(end);points=next;
  }
  return List.copyOf(points);
 }
 static List<RiverRibbon.Point> safeFinal(List<RiverRibbon.Point> path,RiverRibbon.Network network,float[] terrain,int n,double block){
  var rounded=smooth(path,terrain,n,block);if(rounded.size()<3)return path;var space=new RiverBranchSpace(network);int owner=space.owner();double total=0;for(int i=1;i<rounded.size();i++)total+=Math.hypot(rounded.get(i).x()-rounded.get(i-1).x(),rounded.get(i).z()-rounded.get(i-1).z());double arc=0;
  for(int i=1;i<rounded.size();i++){var a=rounded.get(i-1);var b=rounded.get(i);double length=Math.hypot(b.x()-a.x(),b.z()-a.z());if(space.crosses(a,b))return path;var touch=space.contact(b.x(),b.z(),b.radius()+block*.65,owner,arc,null);if(touch!=null&&touch.clearance()<1&&(touch.owner()==owner||arc<total-Math.max(64,b.radius()*6)))return path;space.add(a,b,owner,arc);arc+=length;}return rounded;
 }
 static boolean valid(RiverRibbon.Point p,double dx,double dz,float[] terrain,int n,double block){
  double x=p.x()+dx,z=p.z()+dz;if(x<0||z<0||x>(n-1)*block||z>(n-1)*block)return false;
  double length=Math.hypot(dx,dz),base=RiverShape.height(terrain,n,p.x()/block,p.z()/block),slope=RiverCourse.terrainSlope(terrain,n,p.x()/block,p.z()/block,block);int samples=Math.max(2,(int)Math.ceil(length/Math.min(4,block)*2));
  for(int j=1;j<=samples;j++){double t=j/(double)samples,u=(p.x()+dx*t)/block,v=(p.z()+dz*t)/block;if(RiverShape.height(terrain,n,u,v)>base+Math.min(6,2+length*.12)||RiverCourse.terrainSlope(terrain,n,u,v,block)>Math.min(.4,slope+.1))return false;}return true;
 }
}
