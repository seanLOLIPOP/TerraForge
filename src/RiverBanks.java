import java.util.*;import java.util.function.BooleanSupplier;
/** Final channel containment and riparian surfaces; absolute coordinates keep regional seams stable. */
final class RiverBanks {
 static final String[] EXTRA_BLOCKS={"coarse_dirt","cobblestone","andesite","mossy_cobblestone","podzol","rooted_dirt","moss_block","diorite","granite","tuff","calcite"};
 static float[] waterDistance(EnvironmentMap b,int n,int sea,BooleanSupplier stop){float[] d=new float[n*n];for(int i=0;i<d.length;i++)d[i]=b.waterY(i,sea)>Math.round(b.height[i])?0:Float.POSITIVE_INFINITY;spread(d,null,n,stop);return d;}
 static void spread(float[] d,float[] level,int n,BooleanSupplier stop){for(int pass=0;pass<2;pass++)for(int row=0;row<n;row++){EnvironmentNodes.cancel(stop);int z=pass==0?row:n-1-row,step=pass==0?-1:1;for(int col=0;col<n;col++){int x=pass==0?col:n-1-col,i=z*n+x,xx=x+step,zz=z+step;if(xx>=0&&xx<n)relax(d,level,i,z*n+xx,1);if(zz>=0&&zz<n){relax(d,level,i,zz*n+x,1);if(x>0)relax(d,level,i,zz*n+x-1,1.4142136f);if(x<n-1)relax(d,level,i,zz*n+x+1,1.4142136f);}}}}
 static void relax(float[] d,float[] levels,int i,int j,float step){if(d[j]+step<d[i]){d[i]=d[j]+step;if(levels!=null)levels[i]=levels[j];}}
 static void containWater(EnvironmentMap e,int n,int sea){
  if(e.water==null||e.river==null)return;
  // Seal small low spots in otherwise dry banks without draining the channel.
  for(int z=0;z<n;z++)for(int x=0;x<n;x++){int i=z*n+x,ground=Math.round(e.height[i]);if(e.waterY(i,sea)>ground)continue;int bank=ground;
   if(x>0)bank=bankLevel(e,i-1,bank,sea);if(x+1<n)bank=bankLevel(e,i+1,bank,sea);if(z>0)bank=bankLevel(e,i-n,bank,sea);if(z+1<n)bank=bankLevel(e,i+n,bank,sea);if(bank>ground)e.height[i]=Math.max(e.height[i],bank);
  }
 }
 static void crossSections(EnvironmentMap e,int n,int size,int sea,double depth,BooleanSupplier stop){
  if(e.water==null||e.river==null)return;float[] distance=new float[n*n],levels=new float[n*n];Arrays.fill(distance,Float.POSITIVE_INFINITY);
  for(int z=1;z<n-1;z++)for(int x=1;x<n-1;x++){int i=z*n+x;float r=e.river[i];int water=e.waterY(i,sea);if(r<.8||water<=sea||water<=Math.round(e.height[i])||water-e.height[i]>depth+1)continue;boolean ridge=(r>=e.river[i-1]&&r>=e.river[i+1]&&2*r>e.river[i-1]+e.river[i+1]+.00001)||(r>=e.river[i-n]&&r>=e.river[i+n]&&2*r>e.river[i-n]+e.river[i+n]+.00001);if(ridge||r>.99){distance[i]=0;levels[i]=water;}}
  spread(distance,levels,n,stop);double block=size/(double)n;
  for(int i=0;i<distance.length;i++){int water=e.waterY(i,sea);if(e.river[i]<=.2||water<=sea||water<=Math.round(e.height[i])||water-e.height[i]>depth+1||distance[i]*block>32)continue;float target=Math.max(sea,Math.round(levels[i]));double center=TerraForge.smooth(Math.max(0,Math.min(1,(e.river[i]-.2)/.65)));e.water[i]=target;e.height[i]=(float)(target-(1+(depth-1)*center));}
 }
 static void finishChannels(EnvironmentMap e,int n,int sea,double depth){
  if(e.water==null||e.river==null)return;
  // Reconcile adjoining tiles. Move bed and surface together so lowering a step never dries the channel.
  for(int pass=0;pass<8;pass++){boolean changed=false;for(int row=0;row<n;row++){int z=pass%2==0?row:n-1-row;for(int col=0;col<n;col++){int x=pass%2==0?col:n-1-col,i=z*n+x,water=e.waterY(i,sea);if(e.river[i]<=.2||water<=sea||water<=Math.round(e.height[i])||water-e.height[i]>depth+1)continue;int level=water;if(x>0)level=stepLimit(e,i-1,level,sea,depth);if(x+1<n)level=stepLimit(e,i+1,level,sea,depth);if(z>0)level=stepLimit(e,i-n,level,sea,depth);if(z+1<n)level=stepLimit(e,i+n,level,sea,depth);if(level<water){e.water[i]=level;e.height[i]-=water-level;changed=true;}}}if(!changed)break;}
  settleRiverLevels(e,n,sea,depth);joinLakes(e,n,sea,depth);polishSurface(e,n,sea,depth);
 }
 record WaterStep(int index,int level){}
 static void settleRiverLevels(EnvironmentMap e,int n,int sea,double depth){
  PriorityQueue<WaterStep> queue=new PriorityQueue<>(Comparator.comparingInt(WaterStep::level).thenComparingInt(WaterStep::index));
  for(int z=0;z<n;z++)for(int x=0;x<n;x++){int i=z*n+x,water=e.waterY(i,sea);if(e.river[i]<=.2||water<=sea||water<=Math.round(e.height[i])||water-e.height[i]>depth+1.01)continue;int level=water;if(x>0)level=stepLimit(e,i-1,level,sea,depth);if(x+1<n)level=stepLimit(e,i+1,level,sea,depth);if(z>0)level=stepLimit(e,i-n,level,sea,depth);if(z+1<n)level=stepLimit(e,i+n,level,sea,depth);if(level<water)queue.add(new WaterStep(i,level));}
  while(!queue.isEmpty()){var step=queue.remove();int i=step.index,water=e.waterY(i,sea);if(step.level>=water)continue;e.water[i]=step.level;e.height[i]-=water-step.level;int x=i%n,z=i/n;for(int j:new int[]{x>0?i-1:-1,x+1<n?i+1:-1,z>0?i-n:-1,z+1<n?i+n:-1}){if(j<0)continue;int next=e.waterY(j,sea);if(e.river[j]>.2&&next>Math.round(e.height[j])&&next-e.height[j]<=depth+1.01&&next>step.level+1)queue.add(new WaterStep(j,step.level+1));}}
 }
 static void polishSurface(EnvironmentMap e,int n,int sea,double depth){
  for(int pass=0;pass<8;pass++){boolean changed=false;for(int row=0;row<n;row++){int z=pass%2==0?row:n-1-row;for(int col=0;col<n;col++){int x=pass%2==0?col:n-1-col,i=z*n+x,water=e.waterY(i,sea);if(water<=sea||water<=Math.round(e.height[i]))continue;int level=water;if(x>0)level=wetLimit(e,i-1,level,sea);if(x+1<n)level=wetLimit(e,i+1,level,sea);if(z>0)level=wetLimit(e,i-n,level,sea);if(z+1<n)level=wetLimit(e,i+n,level,sea);if(level<water){e.water[i]=level;if(water-e.height[i]<=depth+1)e.height[i]-=water-level;else e.height[i]=Math.min(e.height[i],level-1);changed=true;}}}if(!changed)break;}
 }
 static int wetLimit(EnvironmentMap e,int j,int level,int sea){int water=e.waterY(j,sea);return water>=sea&&water>Math.round(e.height[j])?Math.min(level,water+1):level;}
 static void joinLakes(EnvironmentMap e,int n,int sea,double depth){
  float[] floor=new float[n*n];Arrays.fill(floor,Float.NEGATIVE_INFINITY);boolean lake=false;
  for(int i=0;i<floor.length;i++){int water=e.waterY(i,sea);if(water>sea&&water-e.height[i]>depth+1){floor[i]=water;lake=true;}}if(!lake)return;
  for(int pass=0;pass<4;pass++)for(int row=0;row<n;row++){int z=pass%2==0?row:n-1-row,step=pass%2==0?-1:1;for(int col=0;col<n;col++){int x=pass%2==0?col:n-1-col,i=z*n+x,water=e.waterY(i,sea);if(e.river[i]<=.2||water<=sea||water<=Math.round(e.height[i])||water-e.height[i]>depth+1)continue;int xx=x+step,zz=z+step;if(xx>=0&&xx<n)floor[i]=Math.max(floor[i],floor[z*n+xx]-1);if(zz>=0&&zz<n)floor[i]=Math.max(floor[i],floor[zz*n+x]-1);}}
  for(int i=0;i<floor.length;i++)if(Float.isFinite(floor[i])&&floor[i]>e.waterY(i,sea)&&e.river[i]>.2){int water=e.waterY(i,sea);if(water>Math.round(e.height[i])&&water-e.height[i]<=depth+1){e.water[i]=floor[i];e.height[i]+=floor[i]-water;}}
 }
 static int stepLimit(EnvironmentMap e,int j,int level,int sea,double depth){int water=e.waterY(j,sea);return e.river[j]>.2&&water>sea&&water>Math.round(e.height[j])&&water-e.height[j]<=depth+1?Math.min(level,water+1):level;}
 static int bankLevel(EnvironmentMap e,int j,int ground,int sea){int water=e.waterY(j,sea);return e.river[j]>.05&&water>sea&&water>Math.round(e.height[j])?Math.max(ground,water):ground;}
 static double bankShape(TerrainGraph.Node node,float[] d,int n,int x,int z,double wx,double wz,long seed){double gx=d[z*n+Math.min(n-1,x+1)]-d[z*n+Math.max(0,x-1)],gz=d[Math.min(n-1,z+1)*n+x]-d[Math.max(0,z-1)*n+x];return RiverShape.bankFactor(wx,wz,gx,gz,node.p("bankVariation"),seed+(long)node.p("seed"));}
 static void gradeValley(TerrainGraph.Node node,EnvironmentMap e,int n,int size,int sea,long seed,int ox,int oz,BooleanSupplier stop){
  if(e.water==null||e.river==null)return;int count=n*n;double block=size/(double)n,maximum=node.p("reach"),width=node.p("width"),rise=node.p("slope");float[] distance=new float[count],level=new float[count];Arrays.fill(distance,Float.POSITIVE_INFINITY);
  for(int i=0;i<count;i++)if(e.river[i]>.2&&e.waterY(i,sea)>Math.round(e.height[i])){distance[i]=0;level[i]=e.waterY(i,sea);}spread(distance,level,n,stop);
  for(int z=0;z<n;z++){EnvironmentNodes.cancel(stop);for(int x=0;x<n;x++){int i=z*n+x;double d=distance[i]*block;if(d<=0||d>maximum*1.5||!Float.isFinite(distance[i])||e.waterY(i,sea)>Math.round(e.height[i]))continue;double shape=bankShape(node,distance,n,x,z,ox+x*block,oz+z*block,seed),water=level[i],old=e.height[i],maturity=RiverCourse.maturity(water,sea,0,.5),course=node.p("courses"),bar=0;
   if(course>.5&&x>0&&z>0&&x<n-1&&z<n-1){double lap=distance[i-1]+distance[i+1]+distance[i-n]+distance[i+n]-4*distance[i];bar=Math.max(0,Math.min(1,-lap*8));}
   double localWidth=width*shape*(course>.5?(.5+.7*maturity)*(1+.5*bar):1),localRise=(course>.5?RiverCourse.bankRise(maturity):rise)/Math.sqrt(shape),reach=Math.min(maximum*(.7+.3*shape)*(course>.5?.4+.6*maturity:1),Math.max(localWidth*3,Math.max(0,old-water-1)/localRise*2));if(d<reach){double noise=TerraForge.sample((ox+x*block)/35,(oz+z*block)/35,seed+731),target=water+1+d*localRise+(noise-.5)*Math.min(.6,d*.08)+(course>.5&&maturity>.8?.65*Math.exp(-Math.pow((d-localWidth*.8)/Math.max(1,localWidth*.4),2)):0),blend=TerraForge.smooth(d/reach);double ground=Math.max(old-4*(1-blend),Math.min(old,target+(old-target)*blend));e.height[i]=(float)Math.max(water+.6,ground);}
   // Repaint the entire valley so enlarged coarse bank categories cannot survive as squares.
   {double patch=TerraForge.sample((ox+x*block)/9,(oz+z*block)/9,seed+997);double deposit=localWidth*(.78+.44*TerraForge.sample((ox+x*block)/47,(oz+z*block)/47,seed+1013)),fraction=d/Math.max(1,deposit);int mat=fraction<.23?(patch<.16?5:7):fraction<.65?(patch<.48?7:patch<.84?10:1):fraction<1?(patch<.14?7:patch<.52?10:1):1;if(course>.5&&fraction<.65){if(maturity<.35)mat=patch<.4?3:patch<.7?12:7;else if(maturity>.75)mat=patch<.32?2:patch<.6?5:patch<.85?7:10;}e.material[i]=(byte)mat;}
  }}
 }
 static EnvironmentMap apply(TerrainGraph.Node node,EnvironmentMap b,int n,int size,int sea,long seed,int originX,int originZ,BooleanSupplier stop){
  if(b.water==null||b.river==null)return b;int count=n*n;double block=size/(double)n,maxDepth=node.p("depth"),width=node.p("width");var e=b.share(b.height.clone());e.water=b.water.clone();e.material=EnvironmentNodes.channel(b.material,count);if(b.snow!=null)e.snow=b.snow.clone();
  float[] wetDistance=new float[count];for(int i=0;i<count;i++)wetDistance[i]=b.waterY(i,sea)>Math.round(b.height[i])?Float.POSITIVE_INFINITY:0;spread(wetDistance,null,n,stop);boolean[] channel=new boolean[count];float[] safe=new float[count];Arrays.fill(safe,Float.POSITIVE_INFINITY);
  // Include dry obstructions inside a river's footprint, not just already wet cells.
  for(int i=0;i<count;i++)if(b.river[i]>.2&&b.water[i]>sea+.5&&wetDistance[i]*block<=node.p("lakeGuard")){channel[i]=true;safe[i]=b.water[i];}
  // Nearby dry bank elevations anchor a perched channel without following its uneven bed.
  float[] shoreDistance=new float[count],shoreLevel=new float[count];
  for(int i=0;i<count;i++){boolean bank=!channel[i]&&Math.round(b.height[i])>=b.waterY(i,sea);shoreDistance[i]=bank?0:Float.POSITIVE_INFINITY;shoreLevel[i]=bank?Math.round(b.height[i])-1:0;}
  spread(shoreDistance,shoreLevel,n,stop);
  float[] samples=new float[81];int radius=Math.max(1,Math.min(4,(int)Math.ceil(4/block)));
  for(int z=0;z<n;z++){EnvironmentNodes.cancel(stop);for(int x=0;x<n;x++){int i=z*n+x;if(!channel[i])continue;int used=0;
   for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++){int xx=x+dx,zz=z+dz;if(xx>=0&&zz>=0&&xx<n&&zz<n){int j=zz*n+xx;if(b.river[j]>.2&&b.water[j]>sea+.5)samples[used++]=b.water[j];}}
   Arrays.sort(samples,0,used);safe[i]=samples[used/2];if(shoreDistance[i]*block<=node.p("lakeGuard"))safe[i]=Math.min(safe[i],shoreLevel[i]);
  }}
  // A half-block maximum change per horizontal block prevents isolated water towers.
  for(int pass=0;pass<4;pass++)for(int row=0;row<n;row++){EnvironmentNodes.cancel(stop);int z=pass%2==0?row:n-1-row,step=pass%2==0?-1:1;for(int col=0;col<n;col++){int x=pass%2==0?col:n-1-col,i=z*n+x;if(!channel[i])continue;int xx=x+step,zz=z+step;if(xx>=0&&xx<n&&channel[z*n+xx])safe[i]=Math.min(safe[i],safe[z*n+xx]+(float)(block*.5));if(zz>=0&&zz<n&&channel[zz*n+x])safe[i]=Math.min(safe[i],safe[zz*n+x]+(float)(block*.5));}}
  float[] distance=new float[count],levels=new float[count];Arrays.fill(distance,Float.POSITIVE_INFINITY);
  for(int i=0;i<count;i++)if(channel[i]){float water=Math.max(sea,Math.round(safe[i]));e.water[i]=water;double center=TerraForge.smooth(Math.max(0,Math.min(1,(b.river[i]-.2)/.65)));double depth=1+(maxDepth-1)*center*(node.p("courses")>.5?.4+.6*RiverCourse.maturity(water,sea,0,.5):1);if(node.p("courses")>.5&&b.resolvedRiverChannels&&b.waterY(i,sea)>Math.round(b.height[i]))depth=Math.max(1,Math.min(maxDepth,b.waterY(i,sea)-b.height[i]));e.height[i]=(float)(water-depth);distance[i]=0;levels[i]=water;if(e.snow!=null)e.snow[i]=0;}
  spread(distance,levels,n,stop);
  for(int z=0;z<n;z++){EnvironmentNodes.cancel(stop);for(int x=0;x<n;x++){int i=z*n+x;double d=distance[i]*block;if(d>width*1.6||!Float.isFinite(distance[i]))continue;
   if(!channel[i]&&b.waterY(i,sea)>Math.round(b.height[i]))continue;double worldX=originX+x*block,worldZ=originZ+z*block,large=TerraForge.sample(worldX/27,worldZ/27,seed+(long)node.p("seed")+313),fine=TerraForge.sample(worldX/5,worldZ/5,seed+7183),shape=bankShape(node,distance,n,x,z,worldX,worldZ,seed),localWidth=width*shape,fade=1-TerraForge.smooth(Math.min(1,d/localWidth));if(d>localWidth)continue;
   if(!channel[i]){double target=levels[i]+1+d*node.p("slope")+(large-.5)*.8;e.height[i]=(float)Math.max(levels[i],e.height[i]-Math.min(4,Math.max(0,e.height[i]-target))*fade);}
   double relief=e.height[i]-levels[i],slope=EnvironmentNodes.slope(e.height,i,n,size);int material=1;
   if(channel[i]){if(slope>.3)material=fine<.5?7:fine<.76?12:11;else material=large<node.p("sand")?2:fine<.22?5:7;}
   else {double exposure=fade*(.45+.55*Math.max(0,1-relief/7));if(large<exposure){if(relief<1.7&&fine<.25)material=5;else if(relief<2.5&&large<node.p("sand")*.5)material=2;else if(slope>.3)material=fine<.45?7:fine<.7?12:fine<.9?11:13;else material=fine<.38?10:fine<.68?8:7;}else if(fine<fade*.15)material=10;}
   e.material[i]=(byte)material;
  }}if(node.p("courses")<.5)crossSections(e,n,size,sea,maxDepth,stop);finishChannels(e,n,sea,maxDepth);gradeValley(node,e,n,size,sea,seed,originX,originZ,stop);GroundSmoothing.apply(e,n,size,sea,node.p("reach")*(1+node.p("bankVariation")*.5),stop);if(node.p("courses")>.5&&n==size&&e.ribbons!=null&&!e.ribbons.islands.isEmpty()){RiverFlow.localSplits(e,n,size,sea);finishChannels(e,n,sea,maxDepth);}containWater(e,n,sea);return e;
 }
}
