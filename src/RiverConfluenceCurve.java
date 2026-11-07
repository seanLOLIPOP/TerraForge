import java.util.*;
/** Replace a sideways endpoint snap with a terrain-checked downstream-facing approach. */
final class RiverConfluenceCurve {
 static List<RiverRibbon.Point> smooth(List<RiverRibbon.Point> path,RiverRibbon.Network network,float[] terrain,int n,double block){
  if(path.size()<4||network.segments.isEmpty()||terrain==null)return path;
  for(double advance:new double[]{1,.5,1.5})for(double handle:new double[]{.45,.3,.6}){var rounded=attempt(path,network,terrain,n,block,advance,handle);if(rounded!=path)return rounded;}return path;
 }
 private static List<RiverRibbon.Point> attempt(List<RiverRibbon.Point> path,RiverRibbon.Network network,float[] terrain,int n,double block,double advanceScale,double handleScale){
  var end=path.get(path.size()-1);int found=-1;double parameter=0,best=.05;
  for(int i=0;i<network.segments.size();i++){var s=network.segments.get(i);double dx=s.b().x()-s.a().x(),dz=s.b().z()-s.a().z(),t=Math.max(0,Math.min(1,((end.x()-s.a().x())*dx+(end.z()-s.a().z())*dz)/Math.max(.000001,dx*dx+dz*dz))),d=Math.hypot(end.x()-s.a().x()-t*dx,end.z()-s.a().z()-t*dz);if(d<best){best=d;found=i;parameter=t;}}
  if(found<0)return path;var s=network.segments.get(found);double dx=s.b().x()-s.a().x(),dz=s.b().z()-s.a().z(),len=Math.hypot(dx,dz);if(len<.001)return path;
  var before=path.get(path.size()-2);double ix=end.x()-before.x(),iz=end.z()-before.z(),in=Math.hypot(ix,iz);if(in<.001||(ix*dx+iz*dz)/(in*len)>.94)return path;
  double advance=Math.max(20,Math.min(64,(end.radius()+s.a().radius())*2))*advanceScale,remaining=advance;var target=RiverRibbon.lerp(s.a(),s.b(),parameter);double tx=dx/len,tz=dz/len;
  for(int i=found;i<network.segments.size();i++){s=network.segments.get(i);double start=i==found?parameter:0;if(i>found&&Math.hypot(target.x()-s.a().x(),target.z()-s.a().z())>.01)break;dx=s.b().x()-s.a().x();dz=s.b().z()-s.a().z();len=Math.hypot(dx,dz);if(len<.001)continue;double take=Math.min(remaining,len*(1-start));target=RiverRibbon.lerp(s.a(),s.b(),start+take/len);tx=dx/len;tz=dz/len;remaining-=take;if(remaining<.001)break;}
  int cut=path.size()-2;double reach=0,wanted=Math.max(28,Math.min(96,(end.radius()+target.radius())*4));for(int i=path.size()-1;i>1;i--){reach+=Math.hypot(path.get(i).x()-path.get(i-1).x(),path.get(i).z()-path.get(i-1).z());cut=i-1;if(reach>=wanted)break;}
  var start=path.get(cut);if(target.water()>start.water()+.01)return path;var earlier=path.get(Math.max(0,cut-2));ix=start.x()-earlier.x();iz=start.z()-earlier.z();in=Math.hypot(ix,iz);if(in<.001)return path;double distance=Math.hypot(target.x()-start.x(),target.z()-start.z()),handle=Math.max(8,Math.min(distance*handleScale,reach*.8)),c1x=start.x()+ix/in*handle,c1z=start.z()+iz/in*handle,c2x=target.x()-tx*handle,c2z=target.z()-tz*handle;
  var result=new ArrayList<RiverRibbon.Point>(path.subList(0,cut+1));var space=new RiverBranchSpace(network);int owner=space.owner();double arc=0;for(int i=1;i<result.size();i++){space.add(result.get(i-1),result.get(i),owner,arc);arc+=Math.hypot(result.get(i).x()-result.get(i-1).x(),result.get(i).z()-result.get(i-1).z());}
  int steps=Math.max(8,(int)Math.ceil((distance+handle)/3));for(int j=1;j<=steps;j++){double t=j/(double)steps,u=1-t,x=u*u*u*start.x()+3*u*u*t*c1x+3*u*t*t*c2x+t*t*t*target.x(),z=u*u*u*start.z()+3*u*u*t*c1z+3*u*t*t*c2z+t*t*t*target.z();var anchor=path.get(Math.min(path.size()-1,cut+(int)Math.round((path.size()-1-cut)*t)));if(!RiverBendFairing.valid(anchor,x-anchor.x(),z-anchor.z(),terrain,n,block))return path;var p=new RiverRibbon.Point(x,z,start.radius()+(end.radius()-start.radius())*t,start.water()+(target.water()-start.water())*t,RiverRibbon.flowLerp(start.discharge(),end.discharge(),t));var previous=result.get(result.size()-1);if(space.crosses(previous,p))return path;var contact=space.contact(p.x(),p.z(),p.radius()+block*.65,owner,arc,null);if(contact!=null&&contact.clearance()<1&&(contact.owner()==owner||Math.abs(contact.point().water()-p.water())>3))return path;space.add(previous,p,owner,arc);arc+=Math.hypot(p.x()-previous.x(),p.z()-previous.z());result.add(p);}
  return List.copyOf(result);
 }
}
