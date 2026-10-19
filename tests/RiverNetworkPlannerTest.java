import java.util.*;
class RiverNetworkPlannerTest {
 public static void main(String[] args){
  int n=192;float[] h=new float[n*n];
  for(int z=0;z<n;z++)for(int x=0;x<n;x++){
   double center=96+9*Math.sin(z/27.),fork=Math.max(0,100-z)*.55;
   double distance=Math.min(Math.abs(x-center-fork),Math.abs(x-center+fork));
   h[z*n+x]=(float)(250-z*1.1+distance*.9);
  }
  var e=new EnvironmentMap(h);var routing=RiverCourse.route(h,e,n,1536,62,()->false);
  var candidates=new ArrayList<Integer>();for(int i=0;i<h.length;i++)if(h[i]>160&&routing.area()[i]>=3&&routing.area()[i]<100)candidates.add(i);
  var sources=RiverNetworkPlanner.select(candidates,h,routing.flow(),n,62,16,null,()->false);
  var selected=new BitSet(h.length);int joins=0,ocean=0;double upstream=0,downstream=0;
  for(int start:sources){int cell=start,steps=0;boolean joinsExisting=false;while(cell>=0&&steps++<h.length){if(selected.get(cell))joinsExisting=true;selected.set(cell);if(h[cell]<=62){ocean++;break;}cell=routing.flow().parent()[cell];}if(joinsExisting)joins++;upstream+=routing.water().discharge()[start];if(cell>=0)downstream+=routing.water().discharge()[cell];}
  if(sources.size()<12||joins<8||ocean!=sources.size()||downstream<=upstream*3)throw new AssertionError("Incomplete drainage family: sources="+sources.size()+" joins="+joins+" ocean="+ocean);
  int count=64*64;float[] ground=EnvironmentMap.filled(count,100),spill=ground.clone();ground[32*64+32]=60;
  var safe=NaturalRivers.safe(35,32,new double[]{29,32},ground,spill,64,true);
  if(safe[0]!=29)throw new AssertionError("Curve displacement crossed a hole");
  System.out.println("PASS: "+sources.size()+" mountain sources, "+joins+" tributary joins, all reach ocean; downstream discharge grows; curve corridor rejects hole crossings");
 }
}
