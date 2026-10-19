import java.util.*;
/** Shapes a connected drainage path inside its valley; no independent random/sine turns. */
final class RiverMorphology {
 static List<RiverRibbon.Point> shape(List<RiverRibbon.Point> path,float[] terrain,int n,double block,long seed){
  if(path.size()<3||terrain==null)return path;
  List<RiverRibbon.Point> points=new ArrayList<>();points.add(path.get(0));double until=8;var previous=path.get(0);
  for(int k=1;k<path.size();k++){var next=path.get(k);double length=Math.hypot(next.x()-previous.x(),next.z()-previous.z());while(length>=until&&length>.001){previous=RiverRibbon.lerp(previous,next,until/length);points.add(previous);length=Math.hypot(next.x()-previous.x(),next.z()-previous.z());until=8;}until-=length;previous=next;}
  var end=path.get(path.size()-1);if(Math.hypot(end.x()-points.get(points.size()-1).x(),end.z()-points.get(points.size()-1).z())>.01)points.add(end);
  points=broadCurves(points,terrain,n,block);
  var anchor=List.copyOf(points);
  for(int pass=0;pass<12;pass++){var next=new ArrayList<RiverRibbon.Point>();next.add(points.get(0));for(int i=1;i<points.size()-1;i++){var a=points.get(i-1);var b=points.get(i);var c=points.get(i+1);double dx=c.x()-a.x(),dz=c.z()-a.z(),len=Math.max(.001,Math.hypot(dx,dz)),nx=-dz/len,nz=dx/len;
    double gradient=Math.max(0,(a.water()-c.water())/len),m=RiverCourse.maturity(b.water(),LargeWorldBuilder.SEA,gradient,i/(double)points.size());
    var broadA=points.get(Math.max(0,i-4));var broadC=points.get(Math.min(points.size()-1,i+4));double ax=b.x()-broadA.x(),az=b.z()-broadA.z(),bx=broadC.x()-b.x(),bz=broadC.z()-b.z(),turn=Math.atan2(ax*bz-az*bx,ax*bx+az*bz);
    // Curvature-driven bank retreat grows bends only where the valley allows it.
    double erodibility=TerraForge.sample(b.x()/180,b.z()/180,seed+9021),migration=-Math.max(-.15,Math.min(.15,turn*.5))*m*(.3+.7*erodibility);
    if(Double.isFinite(b.discharge()))migration*=Math.max(.5,Math.min(1.5,Math.sqrt(b.discharge()/RiverFlow.REFERENCE_DISCHARGE)));
    double xx=b.x()+(a.x()+c.x()-2*b.x())*.12+nx*migration,zz=b.z()+(a.z()+c.z()-2*b.z())*.12+nz*migration;
    var origin=anchor.get(i);double corridor=Math.max(10,Math.min(36,b.radius()*4));double drift=Math.hypot(xx-origin.x(),zz-origin.z());if(drift>corridor){xx=origin.x()+(xx-origin.x())*corridor/drift;zz=origin.z()+(zz-origin.z())*corridor/drift;}
    // Never smooth across a ridge or through an enclosed basin masked by the routing surface.
    double base=RiverShape.height(terrain,n,origin.x()/block,origin.z()/block);boolean safe=true;
    int samples=Math.max(2,(int)Math.ceil(Math.hypot(xx-b.x(),zz-b.z())/block*4));
    for(int j=1;j<=samples;j++){double t=j/(double)samples,ground=RiverShape.height(terrain,n,(b.x()+(xx-b.x())*t)/block,(b.z()+(zz-b.z())*t)/block);if(ground>Math.min(base+1.5,b.water()+2)||RiverCourse.terrainSlope(terrain,n,(b.x()+(xx-b.x())*t)/block,(b.z()+(zz-b.z())*t)/block,block)>Math.min(.18,RiverCourse.terrainSlope(terrain,n,origin.x()/block,origin.z()/block,block)+.01)){safe=false;break;}}
    if(!safe){xx=b.x();zz=b.z();}
    next.add(new RiverRibbon.Point(xx,zz,b.radius(),b.water(),b.discharge()));
   }next.add(end);points=next;}
  return points;
 }
 static List<RiverRibbon.Point> broadCurves(List<RiverRibbon.Point> input,float[] terrain,int n,double block){
  var anchor=List.copyOf(input);var points=input;
  for(int pass=0;pass<5;pass++){var next=new ArrayList<RiverRibbon.Point>();next.add(points.get(0));
   for(int i=1;i<points.size()-1;i++){var b=points.get(i);var origin=anchor.get(i);int before=Math.max(0,i-8),after=Math.min(points.size()-1,i+8);var a=points.get(before);var c=points.get(after);double gradient=Math.max(0,(a.water()-c.water())/Math.max(8,Math.hypot(c.x()-a.x(),c.z()-a.z()))),m=RiverCourse.maturity(b.water(),LargeWorldBuilder.SEA,gradient,i/(double)points.size()),window=64+256*m,sigma=window*.5;
    int span=Math.max(2,(int)Math.ceil(window/8));double sum=0,xx=0,zz=0;for(int j=Math.max(0,i-span);j<=Math.min(points.size()-1,i+span);j++){double weight=Math.exp(-Math.pow((j-i)*8/sigma,2)*.5);var p=points.get(j);sum+=weight;xx+=p.x()*weight;zz+=p.z()*weight;}xx/=sum;zz/=sum;
    double fade=TerraForge.smooth(Math.min(1,Math.min(i,points.size()-1-i)/(double)span));xx=b.x()+(xx-b.x())*fade;zz=b.z()+(zz-b.z())*fade;
    double corridor=Math.max(16,window*.8),drift=Math.hypot(xx-origin.x(),zz-origin.z());if(drift>corridor){xx=origin.x()+(xx-origin.x())*corridor/drift;zz=origin.z()+(zz-origin.z())*corridor/drift;}
    double base=RiverShape.height(terrain,n,origin.x()/block,origin.z()/block);boolean safe=false;
    for(int attempt=0;attempt<8;attempt++){safe=true;int samples=Math.max(2,(int)Math.ceil(Math.hypot(xx-b.x(),zz-b.z())/block*4));for(int j=1;j<=samples;j++){double t=j/(double)samples,h=RiverShape.height(terrain,n,(b.x()+(xx-b.x())*t)/block,(b.z()+(zz-b.z())*t)/block);if(h>Math.min(base+1.5,b.water()+2)||RiverCourse.terrainSlope(terrain,n,(b.x()+(xx-b.x())*t)/block,(b.z()+(zz-b.z())*t)/block,block)>Math.min(.18,RiverCourse.terrainSlope(terrain,n,origin.x()/block,origin.z()/block,block)+.01)){safe=false;break;}}if(safe)break;xx=(xx+b.x())*.5;zz=(zz+b.z())*.5;}
    if(!safe){xx=b.x();zz=b.z();}next.add(new RiverRibbon.Point(xx,zz,b.radius(),b.water(),b.discharge()));
   }next.add(points.get(points.size()-1));points=next;
  }
  return points;
 }

}
