import java.util.*;
/** Resolve accidental intersections as real endpoint junctions, never X crossings. */
final class RiverJunctions {
 static List<RiverRibbon.Point> fit(List<RiverRibbon.Point> points,RiverRibbon.Network network){
  return fit(points,network,0);
 }
 static List<RiverRibbon.Point> fit(List<RiverRibbon.Point> points,RiverRibbon.Network network,double padding){
  if(points.size()<2)return points;var space=new RiverBranchSpace(network);int owner=space.owner();
  var out=new ArrayList<RiverRibbon.Point>();out.add(points.get(0));double arc=0;var root=points.get(0);
  for(int k=1;k<points.size();k++){
   var a=out.get(out.size()-1);var b=points.get(k);double length=Math.hypot(b.x()-a.x(),b.z()-a.z());
   RiverBranchSpace.Entry hit=null;double first=Double.POSITIVE_INFINITY,uHit=0;
   var seen=Collections.newSetFromMap(new IdentityHashMap<RiverBranchSpace.Entry,Boolean>());
   for(int z=(int)Math.floor(Math.min(a.z(),b.z())/64);z<=(int)Math.floor(Math.max(a.z(),b.z())/64);z++)for(int x=(int)Math.floor(Math.min(a.x(),b.x())/64);x<=(int)Math.floor(Math.max(a.x(),b.x())/64);x++)for(var e:space.cells.getOrDefault(RiverBranchSpace.key(x,z),List.of())){
    if(!seen.add(e))continue;
    double dx=b.x()-a.x(),dz=b.z()-a.z(),ex=e.b().x()-e.a().x(),ez=e.b().z()-e.a().z(),den=dx*ez-dz*ex;
    if(Math.abs(den)<1e-9)continue;
    double vx=e.a().x()-a.x(),vz=e.a().z()-a.z(),t=(vx*ez-vz*ex)/den,u=(vx*dz-vz*dx)/den;
    if(t>1e-7&&t<=1&&u>=0&&u<=1&&t<first){first=t;uHit=u;hit=e;}
   }
   if(hit!=null){
    var target=RiverRibbon.lerp(hit.a(),hit.b(),uHit);var p=RiverRibbon.lerp(a,b,first);
    if(hit.owner()==owner){
     int cut=-1;for(int j=1;j<out.size();j++)if(out.get(j-1)==hit.a()&&out.get(j)==hit.b()){cut=j;break;}
     if(cut<0)break;while(out.size()>cut)out.remove(out.size()-1);out.add(new RiverRibbon.Point(target.x(),target.z(),p.radius(),p.water(),p.discharge()));
     space=new RiverBranchSpace(network);owner=space.owner();arc=0;for(int j=1;j<out.size();j++){space.add(out.get(j-1),out.get(j),owner,arc);arc+=Math.hypot(out.get(j).x()-out.get(j-1).x(),out.get(j).z()-out.get(j-1).z());}k--;continue;
    }
    if(hit.arc()<=space.wetEnds.getOrDefault(hit.owner(),Double.POSITIVE_INFINITY)&&Math.abs(p.water()-target.water())<=3){out.add(new RiverRibbon.Point(target.x(),target.z(),p.radius(),target.water(),p.discharge()));}
    else{double gap=p.radius()+target.radius()+8,t=Math.max(0,first-gap/Math.max(.001,length));if(t>0)out.add(RiverRibbon.lerp(a,b,t));}
    break;
   }
   // Bank contact matters even when the centerlines never cross. Do not exempt
   // the first 64 blocks: a source beside an existing channel can form a loop there.
   var contact=space.contact(b.x(),b.z(),b.radius()+padding,owner,arc,null);
   if(contact!=null&&contact.clearance()<2){
    if(contact.owner()==owner){
     // Erase a near-self loop, retaining the downstream continuation rather than
     // leaving both sides of a closed water polygon in the rasterized map.
     int cut=-1;double nearest=Double.POSITIVE_INFINITY;
     for(int j=1;j<out.size();j++){var u=out.get(j-1);var v=out.get(j);double dx=v.x()-u.x(),dz=v.z()-u.z(),t=Math.max(0,Math.min(1,((contact.point().x()-u.x())*dx+(contact.point().z()-u.z())*dz)/Math.max(.000001,dx*dx+dz*dz))),d=Math.hypot(u.x()+t*dx-contact.point().x(),u.z()+t*dz-contact.point().z());if(d<nearest){nearest=d;cut=j;}}
     if(cut>0&&cut<out.size()-1){while(out.size()>cut)out.remove(out.size()-1);space=new RiverBranchSpace(network);owner=space.owner();arc=0;for(int j=1;j<out.size();j++){space.add(out.get(j-1),out.get(j),owner,arc);arc+=Math.hypot(out.get(j).x()-out.get(j-1).x(),out.get(j).z()-out.get(j-1).z());}k--;continue;}
    }else{
     if(contact.wet()&&Math.abs(b.water()-contact.point().water())<=3){var p=contact.point();var joined=new RiverRibbon.Point(p.x(),p.z(),b.radius(),p.water(),b.discharge());if(!space.crosses(a,joined))out.add(joined);}
     break;
    }
   }
   out.add(b);space.add(a,b,owner,arc);arc+=length;
  }
  return List.copyOf(out);
 }
}
