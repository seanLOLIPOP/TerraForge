import java.util.*;import java.util.function.BooleanSupplier;
/** Terrain-aware drainage and geometric upper/middle/lower-course profiles. */
final class RiverCourse {
 record Cell(int index,double cost){}
 record Routing(EnvironmentNodes.Drainage flow,float[] area,RiverFlow.Field water){}
 static Routing route(float[] height,EnvironmentMap environment,int n,int size,int sea,BooleanSupplier stop){
  return route(height,environment,n,size,sea,stop,false);
 }
 static Routing route(float[] height,EnvironmentMap environment,int n,int size,int sea,BooleanSupplier stop,boolean lakeEscape){
  return route(height,environment,n,size,sea,stop,lakeEscape,1);
 }
 static Routing route(float[] height,EnvironmentMap environment,int n,int size,int sea,BooleanSupplier stop,boolean lakeEscape,double runoff){
  return route(height,environment,n,size,sea,stop,lakeEscape,runoff,43119L);
 }
 static Routing route(float[] height,EnvironmentMap environment,int n,int size,int sea,BooleanSupplier stop,boolean lakeEscape,double runoff,long riverSeed){
  return route(height,environment,n,size,sea,stop,lakeEscape,runoff,riverSeed,Double.POSITIVE_INFINITY);
 }
 static Routing route(float[] height,EnvironmentMap environment,int n,int size,int sea,BooleanSupplier stop,boolean lakeEscape,double runoff,long riverSeed,double preferredSlope){return route(height,environment,n,size,sea,stop,lakeEscape,runoff,riverSeed,preferredSlope,preferredSlope,false);}
 static Routing route(float[] height,EnvironmentMap environment,int n,int size,int sea,BooleanSupplier stop,boolean lakeEscape,double runoff,long riverSeed,double preferredSlope,double maximumTerrainSlope){return route(height,environment,n,size,sea,stop,lakeEscape,runoff,riverSeed,preferredSlope,maximumTerrainSlope,true);}
 static Routing route(float[] height,EnvironmentMap environment,int n,int size,int sea,BooleanSupplier stop,boolean lakeEscape,double runoff,long riverSeed,double preferredSlope,double maximumTerrainSlope,boolean requireWetOutlet){
  var filled=EnvironmentNodes.drainage(height,n,stop,false);int count=n*n;double block=size/(double)n;float[] cost=new float[count];Arrays.fill(cost,Float.POSITIVE_INFINITY);int[] parent=new int[count];Arrays.fill(parent,-1);boolean[] wet=new boolean[count],done=new boolean[count],blocked=new boolean[count];
  for(int i=0;i<count;i++){wet[i]=height[i]<=sea||!lakeEscape&&environment.lakeMask!=null&&environment.lakeMask[i]!=0;blocked[i]=!wet[i]&&!(lakeEscape&&environment.lakeMask!=null&&environment.lakeMask[i]!=0)&&(filled.level()[i]-height[i]>1.5||Double.isFinite(preferredSlope)&&terrainSlope(height,n,i%n,i/n,block)>maximumTerrainSlope);}
  PriorityQueue<Cell> queue=new PriorityQueue<>(Comparator.comparingDouble(Cell::cost).thenComparingInt(Cell::index));
  for(int z=0;z<n;z++)for(int x=0;x<n;x++){int i=z*n+x;if(!wet[i]&&(requireWetOutlet||x>0&&z>0&&x<n-1&&z<n-1))continue;cost[i]=0;boolean shore=!wet[i];for(int dz=-1;dz<=1&&!shore;dz++)for(int dx=-1;dx<=1;dx++){int xx=x+dx,zz=z+dz;if(xx>=0&&zz>=0&&xx<n&&zz<n&&!wet[zz*n+xx])shore=true;}if(shore)queue.add(new Cell(i,cost[i]));}
  int[] order=new int[count];int used=0;
  while(!queue.isEmpty()){if((used&2047)==0)EnvironmentNodes.cancel(stop);var cell=queue.remove();int i=cell.index;if(done[i])continue;done[i]=true;order[used++]=i;int x=i%n,z=i/n;for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){if(dx==0&&dz==0)continue;int xx=x+dx,zz=z+dz;if(xx<0||zz<0||xx>=n||zz>=n)continue;int j=zz*n+xx;if(wet[j]||done[j]||blocked[j]||dx!=0&&dz!=0&&(blocked[z*n+xx]||blocked[zz*n+x]))continue;
    // Enclosed pits cost more than skirting their rim; climbing a ridge costs much more still.
    double pit=Math.max(0,filled.level()[j]-height[j]-.25),uphill=Math.max(0,height[i]-height[j]-.05),length=Math.hypot(dx,dz)*block;
    double drop=Math.max(0,height[j]-height[i])/length;if(drop>preferredSlope||Double.isFinite(preferredSlope)&&height[i]>height[j]+.05)continue;double localLow=height[j];for(int ez=-1;ez<=1;ez++)for(int ex=-1;ex<=1;ex++){int vx=xx+ex,vz=zz+ez;if(vx>=0&&vz>=0&&vx<n&&vz<n)localLow=Math.min(localLow,height[vz*n+vx]);}double ridge=Math.max(0,height[j]-localLow);double next=cost[i]+length*(.7+.6*TerraForge.sample(xx*block/(requireWetOutlet?640.:240.),zz*block/(requireWetOutlet?640.:240.),riverSeed)+pit*pit*2+ridge*.35+Math.pow(Math.max(0,drop-preferredSlope),2)*80)/(1+Math.min(2,drop*5))+uphill*350;
    if(next<cost[j]){cost[j]=(float)next;parent[j]=i;queue.add(new Cell(j,cost[j]));}
  }}
  // Prefer the local downhill direction when it remains connected to the outlet.
  for(int k=1;k<used;k++){int i=order[k];if(wet[i])continue;int x=i%n,z=i/n,best=parent[i];double score=-Double.MAX_VALUE;for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){if(dx==0&&dz==0)continue;int xx=x+dx,zz=z+dz;if(xx<0||zz<0||xx>=n||zz>=n)continue;int j=zz*n+xx;if(blocked[j]||dx!=0&&dz!=0&&(blocked[z*n+xx]||blocked[zz*n+x])||!Float.isFinite(cost[j])||cost[j]>=cost[i]||height[j]>height[i]+.05)continue;double slope=(height[i]-height[j])/(Math.hypot(dx,dz)*block),candidate=slope>preferredSlope?-Double.MAX_VALUE:slope*.35+(cost[i]-cost[j])/(Math.hypot(dx,dz)*block)*.12-Math.pow(Math.max(0,slope-preferredSlope),2)*80;if(candidate>score){score=candidate;best=j;}}parent[i]=best;}
  float[] area=new float[count];Arrays.fill(area,1);for(int k=used-1;k>=0;k--){int i=order[k],p=parent[i];if(p>=0)area[p]=Math.min(1e8f,area[p]+area[i]);}
  return new Routing(new EnvironmentNodes.Drainage(parent,filled.level()),area,RiverFlow.runoff(parent,height,environment.rain,n,size,sea,runoff));
 }
 static double terrainSlope(float[] h,int n,double x,double z,double block){double dx=(RiverShape.height(h,n,x+1,z)-RiverShape.height(h,n,x-1,z))/(2*block),dz=(RiverShape.height(h,n,x,z+1)-RiverShape.height(h,n,x,z-1))/(2*block);return Math.hypot(dx,dz);}
 static List<Integer> headwaters(float[] height,float[] area,EnvironmentNodes.Drainage flow,int n,int sea,double minimum,long seed){
  List<PriorityQueue<Integer>> buckets=new ArrayList<>();Comparator<Integer> score=Comparator.comparingDouble((Integer i)->height[i]+Math.log1p(area[i])*2+TerraForge.sample((i%n)/83.,(i/n)/83.,seed+9137)*8).thenComparingInt(i->i);
  for(int k=0;k<256;k++)buckets.add(new PriorityQueue<>(score));for(int z=2;z<n-2;z++)for(int x=2;x<n-2;x++){int i=z*n+x;if(height[i]<=Math.max(sea+10,minimum)||area[i]<3||area[i]>1200||flow.parent()[i]<0)continue;var bucket=buckets.get(Math.min(15,z*16/n)*16+Math.min(15,x*16/n));bucket.add(i);if(bucket.size()>48)bucket.remove();}var result=new ArrayList<Integer>();for(var bucket:buckets)result.addAll(bucket);result.sort(score.reversed());return result;
 }
 static double maturity(double water,int sea,double gradient,double progress){double altitude=1-Math.max(0,Math.min(1,(water-sea)/220));return Math.max(0,Math.min(1,altitude*.78+progress*.22-Math.max(0,gradient-.035)*2));}
 static double width(double maturity,double contribution){return (.48+.85*maturity)*Math.max(.85,Math.min(1.22,Math.pow(Math.max(1,contribution)/1500.,.055)));}
 static double depth(double nominal,double maturity){return Math.max(1.5,nominal*(.65+.65*maturity));}
 static double bend(double maturity){return .12+1.15*TerraForge.smooth(maturity);}
 static double bankRise(double maturity){return .52*(1-maturity)+.075*maturity;}
}
