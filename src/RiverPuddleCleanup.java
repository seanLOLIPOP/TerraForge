import java.util.BitSet;
/** Removes tiny disconnected river fragments while retaining short branches attached to larger water. */
final class RiverPuddleCleanup {
 static boolean wet(EnvironmentMap e,int i,int sea){return e.waterY(i,sea)>Math.round(e.height[i]);}
 static int apply(EnvironmentMap e,int n,int sea,int limit){return apply(e,n,sea,limit,null);}
 static int apply(EnvironmentMap e,int n,int sea,int limit,RiverRibbon.Network planned){return apply(e,n,sea,limit,planned,0,0);}
 static int apply(EnvironmentMap e,int n,int sea,int limit,RiverRibbon.Network planned,int ox,int oz){
  BitSet protectedCells=new BitSet();if(planned!=null)for(var s:(n==512?planned.query(ox,oz):planned.segments))if(s.a().water()>sea+400){int x=(int)Math.round(s.a().x())-ox,z=(int)Math.round(s.a().z())-oz;for(int zz=Math.max(0,z-2);zz<=Math.min(n-1,z+2);zz++)for(int xx=Math.max(0,x-2);xx<=Math.min(n-1,x+2);xx++)protectedCells.set(zz*n+xx);}
  if(e.river==null||e.water==null)return 0;
  var visited=new BitSet(n*n);var current=new BitSet(n*n);int[] queue=new int[limit+1];int removed=0;
  for(int start=0;start<e.height.length;start++){
   if(visited.get(start)||e.river[start]<=.05||!wet(e,start,sea)||e.waterY(start,sea)<=sea)continue;
   int head=0,tail=1;queue[0]=start;visited.set(start);current.set(start);boolean connected=false;
   while(head<tail&&tail<=limit){int i=queue[head++],x=i%n,z=i/n;if(x==0||z==0||x==n-1||z==n-1||protectedCells.get(i))connected=true;
    for(int j:new int[]{x>0?i-1:-1,x+1<n?i+1:-1,z>0?i-n:-1,z+1<n?i+n:-1}){
     if(j<0||!wet(e,j,sea))continue;
     if(e.river[j]<=.05||e.waterY(j,sea)<=sea){connected=true;continue;}
     if(visited.get(j)){if(!current.get(j))connected=true;continue;}
     if(tail>limit){connected=true;break;}visited.set(j);current.set(j);queue[tail++]=j;
    }
   }
   if(tail>limit)connected=true;
   if(!connected)for(int k=0;k<tail;k++){int i=queue[k];e.height[i]=Math.max(e.height[i],e.waterY(i,sea));e.water[i]=sea;e.river[i]=0;e.material[i]=10;removed++;}
   for(int k=0;k<tail;k++)current.clear(queue[k]);
  }
  return removed;
 }
}
