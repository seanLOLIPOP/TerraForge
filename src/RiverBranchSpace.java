import java.util.*;
/** Shared channel clearance for recursive branches; prevents sibling crossings and parallel overlaps. */
final class RiverBranchSpace{
 record Entry(RiverRibbon.Point a,RiverRibbon.Point b,int owner,double arc){}
 record Contact(RiverRibbon.Point point,double clearance,int owner,boolean wet){}
 final Map<Long,List<Entry>> cells=new HashMap<>();final Map<Integer,Double> wetEnds=new HashMap<>();final Set<Integer> retired=new HashSet<>();int next=1;
 RiverBranchSpace(RiverRibbon.Network network){for(var s:network.segments)add(s.a(),s.b(),0,0);}
 static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
 int owner(){return next++;}void retire(int owner){retired.add(owner);}
 void wetEnd(int owner,double arc){wetEnds.put(owner,arc);}
 void add(RiverRibbon.Point a,RiverRibbon.Point b,int owner,double arc){var e=new Entry(a,b,owner,arc);double pad=Math.max(a.radius(),b.radius())+12;for(int z=(int)Math.floor((Math.min(a.z(),b.z())-pad)/64);z<=(int)Math.floor((Math.max(a.z(),b.z())+pad)/64);z++)for(int x=(int)Math.floor((Math.min(a.x(),b.x())-pad)/64);x<=(int)Math.floor((Math.max(a.x(),b.x())+pad)/64);x++)cells.computeIfAbsent(key(x,z),k->new ArrayList<>()).add(e);}
 Contact contact(double x,double z,double radius,int owner,double arc,RiverRibbon.Point root){Contact best=null;for(var e:cells.getOrDefault(key((int)Math.floor(x/64),(int)Math.floor(z/64)),List.of())){if(retired.contains(e.owner)||e.owner==owner&&arc-e.arc<48)continue;double dx=e.b.x()-e.a.x(),dz=e.b.z()-e.a.z(),t=Math.max(0,Math.min(1,((x-e.a.x())*dx+(z-e.a.z())*dz)/Math.max(.001,dx*dx+dz*dz)));var p=RiverRibbon.lerp(e.a,e.b,t);if(Math.hypot(x-root.x(),z-root.z())<48&&Math.hypot(p.x()-root.x(),p.z()-root.z())<48)continue;double clearance=Math.hypot(x-p.x(),z-p.z())-radius-p.radius();if(best==null||clearance<best.clearance)best=new Contact(p,clearance,e.owner,e.arc<=wetEnds.getOrDefault(e.owner,Double.POSITIVE_INFINITY));}return best;}
 boolean crosses(RiverRibbon.Point a,RiverRibbon.Point b){var candidate=new RiverRibbon.Segment(a,b,1);for(int z=(int)Math.floor(Math.min(a.z(),b.z())/64);z<=(int)Math.floor(Math.max(a.z(),b.z())/64);z++)for(int x=(int)Math.floor(Math.min(a.x(),b.x())/64);x<=(int)Math.floor(Math.max(a.x(),b.x())/64);x++)for(var e:cells.getOrDefault(key(x,z),List.of()))if(!retired.contains(e.owner)&&RiverCrossingAudit.crosses(candidate,new RiverRibbon.Segment(e.a,e.b,1)))return true;return false;}

}
