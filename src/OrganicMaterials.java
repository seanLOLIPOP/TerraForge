import java.util.*;import java.util.function.BooleanSupplier;
/** Coherent, world-coordinate deposits. Changes materials and snow, never terrain or water. */
final class OrganicMaterials {
 static double mix(float[] f,int n,double x,double z){return RiverShape.height(f,n,x,z);}
 static int mountainMaterial(double exposure,double p,double d,double x,double z,long seed,double width){
  double weight=TerraForge.smooth(EnvironmentNodes.clamp(.5+(exposure-(p*.85+.1))/Math.max(.1,width),0,1));
  if(weight<=0)return 1;
  double grain=TerraForge.sample(x/3.5,z/3.5,seed+4919),fleck=TerraForge.sample(x/1.7,z/1.7,seed+4921);
  if(grain<weight){double granite=EnvironmentNodes.clamp((.36-p)/.18,0,1),diorite=EnvironmentNodes.clamp((p-.63)/.18,0,1);if(fleck<granite*.8)return 18;if(fleck>1-diorite*.8)return 17;return d*.65+fleck*.35>.57?12:3;}
  if(weight>.06&&fleck<weight*.7)return grain<.55?7:10;
  return 1;
 }
 static EnvironmentMap apply(TerrainGraph.Node node,EnvironmentMap base,int n,int size,int sea,long seed,int ox,int oz,BooleanSupplier stop){
  var e=base.share(base.height);e.material=EnvironmentNodes.channel(base.material,n*n);e.snow=EnvironmentNodes.channel(base.snow,n*n);double block=size/(double)n;int step=Math.max(1,(int)Math.ceil(4/block)),g=(n+step-1)/step+1;double spacing=step*block;float[] levels=new float[g*g],wet=new float[g*g],forest=new float[g*g],patch=new float[g*g],detail=new float[g*g];Arrays.fill(wet,Float.POSITIVE_INFINITY);
  for(int z=0;z<g;z++)for(int x=0;x<g;x++){int i=z*g+x,xx=Math.min(n-1,x*step),zz=Math.min(n-1,z*step);double wx=ox+xx*block,wz=oz+zz*block,warp=(TerraForge.sample(wx/110,wz/110,seed+2301)-.5)*34;patch[i]=(float)(.72*TerraForge.sample((wx+warp)/node.p("patchSize"),(wz-warp)/node.p("patchSize"),seed+2303)+.28*TerraForge.sample(wx/17,wz/17,seed+2305));detail[i]=(float)TerraForge.sample((wx+warp)/13,(wz-warp)/13,seed+2307);int k=zz*n+xx;if(base.waterY(k,sea)>Math.round(base.height[k])){wet[i]=0;levels[i]=base.waterY(k,sea);}}
  RiverBanks.spread(wet,levels,g,stop);
  for(var tree:base.trees){int tx=(int)Math.round(tree.x()/spacing),tz=(int)Math.round(tree.z()/spacing),r=(int)Math.ceil(18/spacing);for(int z=Math.max(0,tz-r);z<=Math.min(g-1,tz+r);z++)for(int x=Math.max(0,tx-r);x<=Math.min(g-1,tx+r);x++){double distance=Math.hypot(x-tx,z-tz)*spacing;forest[z*g+x]=Math.max(forest[z*g+x],(float)Math.max(0,1-distance/18));}}
  for(int z=0;z<n;z++){EnvironmentNodes.cancel(stop);for(int x=0;x<n;x++){int i=z*n+x;double gx=x/(double)step,gz=z/(double)step,p=mix(patch,g,gx,gz),d=mix(detail,g,gx,gz),distance=mix(wet,g,gx,gz)*spacing,h=base.height[i],slope=EnvironmentNodes.slope(base.height,i,n,size),cover=mix(forest,g,gx,gz),rain=EnvironmentMap.at(base.rain,i,.65f);boolean underwater=base.waterY(i,sea)>Math.round(h),river=EnvironmentMap.at(base.river,i,0)>.1||mix(levels,g,gx,gz)>sea+2;int mat=1;if(base.material!=null&&(base.material[i]==6||base.material[i]==9))continue;
   double rock=EnvironmentNodes.clamp((h-node.p("rockLine")+(p-.5)*100)/110,0,1),exposure=Math.max(rock,EnvironmentNodes.clamp((slope-.25)/.65,0,1));
   if(underwater){mat=river?(p<.28&&h<sea+55?2:d<.24?3:d>.76?12:7):(slope>.6?3:p<.63?2:7);}
   else if(distance<node.p("bankWidth")*(.55+p)&&h-mix(levels,g,gx,gz)<8&&river){double t=distance/Math.max(1,node.p("bankWidth")*(.55+p));mat=t<.25?(d<.2&&h<sea+45?2:d>.82?12:7):t<.65?(p<.37&&rain>.6?5:d<.6?10:7):d<.5?10:1;}
   else if(h<sea+node.p("beachRise")*(.65+p)&&distance<48*(.55+p)&&!river){mat=slope>.6||p>.8?(d>.65?12:7):2;}
   else {mat=mountainMaterial(exposure,p,d,ox+x*block,oz+z*block,seed,node.p("blendWidth")/110.);
    if(mat==1&&cover>node.p("forestFloor")*(.55+p)&&slope<.65)mat=rain>.72&&d>.62?16:d<.28?15:p<.6?14:10;
   }
   e.material[i]=(byte)mat;
   double snow=EnvironmentMap.at(base.snow,i,0);if(snow>0){double retention=1-TerraForge.smooth(EnvironmentNodes.clamp((slope-.45)/1.2,0,1));double organic=EnvironmentNodes.clamp(snow+(p-.5)*.55+(d-.5)*.12,0,1);e.snow[i]=underwater?0:(float)(organic*retention);}
  }}return e;
 }
}
