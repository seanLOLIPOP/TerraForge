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
  for(int z=0;z<n;z++){EnvironmentNodes.cancel(stop);for(int x=0;x<n;x++){int i=z*n+x;double gx=x/(double)step,gz=z/(double)step,p=mix(patch,g,gx,gz),d=mix(detail,g,gx,gz),distance=mix(wet,g,gx,gz)*spacing,h=base.height[i],slope=EnvironmentNodes.slope(base.height,i,n,size),cover=mix(forest,g,gx,gz),rain=EnvironmentMap.at(base.rain,i,.65f);boolean underwater=base.waterY(i,sea)>Math.round(h),seaCloser=base.shoreDistance!=null&&!(distance<=base.shoreDistance[i]),river=EnvironmentMap.at(base.river,i,0)>.1||!seaCloser&&mix(levels,g,gx,gz)>sea+2;/* open sea beyond the tile edge outranks inland water seen inside the tile */int mat=1;if(base.material!=null&&(base.material[i]==6||base.material[i]==9))continue;
   double rock=EnvironmentNodes.clamp((h-node.p("rockLine")+(p-.5)*100)/110,0,1),exposure=Math.max(rock,EnvironmentNodes.clamp((slope-.25)/.65,0,1));
   double rg=base.riverGrad==null?0:base.riverGrad[i],fl=TerraForge.sample((ox+x*block)/1.9,(oz+z*block)/1.9,seed+2391);
   if(underwater&&river&&rg>.03){mat=fl<.33?3:fl<.58?11:fl<.8?12:13;}/* rapids and falls: rocky, mossy bed */
   else if(underwater){mat=river?(p<.28&&h<sea+55?2:d<.24?3:d>.76?12:7):(slope>.6?3:p<.63?2:7);}
   else if(rg>.09&&distance<5){mat=fl<.45?3:fl<.75?13:12;}/* spray-washed rock beside waterfalls */
   else if(distance<node.p("bankWidth")*(.55+p)&&h-mix(levels,g,gx,gz)<8&&river){double t=distance/Math.max(1,node.p("bankWidth")*(.55+p));mat=t<.25?(d<.2&&h<sea+45?2:d>.82?12:7):t<.65?(p<.37&&rain>.6?5:d<.6?10:7):d<.5?10:1;}
   else if(!river&&beachSand(node,p,d,ox+x*block,oz+z*block,seed,base.shoreDistance==null?distance:base.shoreDistance[i],h,sea)){mat=slope>.6||p>.8?(d>.65?12:7):2;}
   else {mat=mountainSurface(mountainMaterial(exposure,p,d,ox+x*block,oz+z*block,seed,node.p("blendWidth")/110.),p,d,ox+x*block,oz+z*block,seed,h,slope,node.p("rockLine"));
    if(mat==1&&cover>node.p("forestFloor")*(.55+p)&&slope<.65)mat=rain>.72&&d>.62?16:d<.28?15:p<.6?14:10;
   }
   e.material[i]=(byte)mat;
   double snow=EnvironmentMap.at(base.snow,i,0);if(snow>0){double retention=1-TerraForge.smooth(EnvironmentNodes.clamp((slope-.45)/1.2,0,1));double organic=EnvironmentNodes.clamp(snow+(p-.5)*.55+(d-.5)*.12,0,1);e.snow[i]=underwater?0:(float)(organic*retention);}
  }}
  if(node.p("zones")>.5)return MountainZones.apply(node,e,n,size,sea,seed,ox,oz,stop);
  return e;
 }
    /** Beach band along the shore. Width follows "Beach width" and wanders between sheltered coves and narrow
     *  headlands with "Beach width variation"; "Dune transition" adds a patchy sand-into-grass zone behind it.
     *  With the default settings (48 blocks, no variation, no dunes) this is the original beach rule. */
    static boolean beachSand(TerrainGraph.Node node,double p,double d,double wx,double wz,long seed,double distance,double h,int sea){
        double variation=node.values.getOrDefault("beachVariation",0.0),dunes=node.values.getOrDefault("dunes",0.0),base=node.values.getOrDefault("beachWidth",48.0);
        double cove=TerraForge.smooth(EnvironmentNodes.clamp(.72*TerraForge.sample(wx/520,wz/520,seed+2311)+.28*TerraForge.sample(wx/150,wz/150,seed+2313),0,1));
        double width=base*(.55+p)*(1-variation+variation*(.2+1.6*cove));
        double rise=node.p("beachRise")*(.65+p)*Math.sqrt(Math.max(1,base/48));
        if(distance<width)return h<sea+rise;
        if(dunes<=0)return false;
        double zone=width*(.35+.65*dunes),t=(distance-width)/Math.max(1,zone);
        if(t>=1||h>=sea+rise+6*dunes)return false;
        return d>.3+.7*t;// sand thins out into dune grass
    }
    /** Mountain texture on top of the rock/grass choice: banded bare rock on cliffs, scree on steep slopes,
     *  pale calcite outcrops on high gentle ground and stony, dirt-patched alpine meadows below the rock line. */
    static int mountainSurface(int base,double p,double d,double x,double z,long seed,double h,double slope,double rockLine){
        boolean rock=base==3||base==12||base==17||base==18;
        double fleck=TerraForge.sample(x/2.3,z/2.3,seed+6201);
        if(rock){
            if(slope>1.1)return MountainZones.rockPatch(h,x,z,seed);
            if(slope>.5)return fleck<.28?7:fleck<.46?11:fleck<.74?12:3;
            if(h>rockLine+380&&d>.62)return 20;
            // grassy and mossy ledges on gentle ground between the rock faces, thinning out with height
            double ledge=TerraForge.sample(x/38,z/38,seed+6221)*.7+p*.3,limit=.4+.4*EnvironmentNodes.clamp((h-rockLine)/320,0,1)+slope*.35;
            if(slope<.42&&h<rockLine+330&&ledge>limit)return d<.22?10:d>.8?16:1;
            return base;
        }
        if(base==1&&h>rockLine-70&&slope<.6){
            if(d<.16)return 10;
            if(fleck>.9)return slope>.35?12:3;
            if(d>.86&&p<.45)return 16;
        }
        return base;
    }
}
