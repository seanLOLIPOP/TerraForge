import java.util.*;
final class RiverCrossingAudit{
 static double side(RiverRibbon.Point a,RiverRibbon.Point b,RiverRibbon.Point p){return (b.x()-a.x())*(p.z()-a.z())-(b.z()-a.z())*(p.x()-a.x());}
 static boolean crosses(RiverRibbon.Segment a,RiverRibbon.Segment b){return side(a.a(),a.b(),b.a())*side(a.a(),a.b(),b.b())<-.000001&&side(b.a(),b.b(),a.a())*side(b.a(),b.b(),a.b())<-.000001;}
 static int count(RiverRibbon.Network network){return pairs(network).size();}
 static List<int[]> pairs(RiverRibbon.Network network){var cells=new HashMap<Long,List<Integer>>();for(int i=0;i<network.segments.size();i++){var s=network.segments.get(i);for(int z=(int)Math.floor(Math.min(s.a().z(),s.b().z())/64);z<=(int)Math.floor(Math.max(s.a().z(),s.b().z())/64);z++)for(int x=(int)Math.floor(Math.min(s.a().x(),s.b().x())/64);x<=(int)Math.floor(Math.max(s.a().x(),s.b().x())/64);x++)cells.computeIfAbsent(RiverBranchSpace.key(x,z),k->new ArrayList<>()).add(i);}var seen=new HashSet<Long>();var result=new ArrayList<int[]>();for(var list:cells.values())for(int k=0;k<list.size();k++)for(int j=k+1;j<list.size();j++){int a=list.get(k),b=list.get(j);long key=((long)Math.min(a,b)<<32)|Math.max(a,b);if(!seen.add(key))continue;if(crosses(network.segments.get(a),network.segments.get(b)))result.add(new int[]{a,b});}return result;}
}
