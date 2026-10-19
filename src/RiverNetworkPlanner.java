import java.util.*;
import java.util.function.BooleanSupplier;
/** Selects complete drainage trees: a trunk first, then separate headwater branches that join it. */
final class RiverNetworkPlanner {
 static List<Integer> select(List<Integer> candidates,float[] height,EnvironmentNodes.Drainage flow,int n,int sea,int wanted,BitSet existing,BooleanSupplier stop){
  return select(candidates,height,flow,n,sea,wanted,existing,stop,Math.max(4,n*.006));
 }
 static List<Integer> select(List<Integer> candidates,float[] height,EnvironmentNodes.Drainage flow,int n,int sea,int wanted,BitSet existing,BooleanSupplier stop,double separation){
  var paths=new ArrayList<int[]>();var starts=new ArrayList<Integer>();
  for(int start:candidates){EnvironmentNodes.cancel(stop);int cell=start,length=0;int[] path=new int[Math.min(n*n,n*8)];while(cell>=0&&length<path.length){path[length++]=cell;if(height[cell]<=sea)break;cell=flow.parent()[cell];}
   if(length<Math.max(8,n/64)||cell>=0&&height[cell]>sea&&flow.parent()[cell]>=0)continue;
   int end=path[length-1];if(height[end]>sea&&(end%n==0||end/n==0||end%n==n-1||end/n==n-1))continue;
   paths.add(Arrays.copyOf(path,length));starts.add(start);
  }
  BitSet selected=existing==null?new BitSet(n*n):(BitSet)existing.clone();var result=new ArrayList<Integer>();boolean[] used=new boolean[paths.size()];
  double minimumBranch=Math.max(8,separation);var corridor=new BitSet(n*n);var outlets=new ArrayList<Integer>();int padding=Math.max(2,(int)Math.round(separation*.55));
  for(int cell=selected.nextSetBit(0);cell>=0;cell=selected.nextSetBit(cell+1))mark(corridor,cell,n,padding);
  for(int round=0;round<wanted;round++){EnvironmentNodes.cancel(stop);int best=-1;double score=-1;
   for(int j=0;j<paths.size();j++){if(used[j])continue;int start=starts.get(j);boolean near=false;for(int other:result)if(Math.hypot(start%n-other%n,start/n-other/n)<separation){near=true;break;}if(near)continue;
    int[] path=paths.get(j);int branch=0;while(branch<path.length&&!selected.get(path[branch]))branch++;if(branch<minimumBranch)continue;
    boolean joins=branch<path.length;int outlet=path[path.length-1];
    if(!joins){boolean crowded=false;for(int other:outlets)if(Math.hypot(outlet%n-other%n,outlet/n-other/n)<separation*2.5){crowded=true;break;}if(crowded)continue;}
    int samples=0,parallel=0,reach=Math.max(0,branch-(int)separation);for(int k=0;k<reach;k+=Math.max(1,reach/24)){samples++;if(corridor.get(path[k]))parallel++;}if(samples>3&&parallel>samples*.3)continue;
    double drop=height[start]-height[path[Math.min(branch,path.length-1)]];if(drop<12)continue;
    // Usually grow tributaries; periodically admit another basin to cover both mountain groups.
    double value=Math.sqrt(branch)*Math.sqrt(Math.max(1,drop))*(joins&&round%8!=0?5:1);
    if(value>score){score=value;best=j;}
   }
   if(best<0)break;used[best]=true;result.add(starts.get(best));int[] chosen=paths.get(best);for(int cell:chosen){selected.set(cell);mark(corridor,cell,n,padding);}outlets.add(chosen[chosen.length-1]);
  }
  return result;
 }
 static void mark(BitSet corridor,int cell,int n,int radius){int x=cell%n,z=cell/n;for(int zz=Math.max(0,z-radius);zz<=Math.min(n-1,z+radius);zz++){int span=(int)Math.sqrt(Math.max(0,radius*radius-(zz-z)*(zz-z)));corridor.set(zz*n+Math.max(0,x-span),zz*n+Math.min(n,x+span+1));}}

}
