import java.util.function.BooleanSupplier;
/** GPU surface classification after the shared water-distance and forest fields are prepared. */
final class GpuMaterials {
 static String helpers(){String code=HELPERS;int i=0;for(var param:TerrainGraph.Type.ORGANIC_MATERIALS.params)code=code.replace("P("+param.key()+")","settings["+(i++)+"]");return code;}
 static boolean apply(TerrainGraph.Node node,EnvironmentMap base,EnvironmentMap result,int n,int size,int sea,long seed,int ox,int oz,int step,int g,double spacing,float[] patch,float[] detail,float[] wet,float[] levels,float[] forest,BooleanSupplier stop){
  if(!GpuGeneration.enabled()||n<512||n*(long)n*48>(256L<<20))return false;
  int count=n*n;float[] grids=new float[g*g*5];int offset=0;for(float[] field:new float[][]{patch,detail,wet,levels,forest}){System.arraycopy(field,0,grids,offset,field.length);offset+=field.length;}
  float[] channels=new float[count*7];
  for(int i=0;i<count;i++){if(i%n==0)GpuGeneration.checkStop(stop);channels[i]=base.material==null?0:base.material[i];channels[count+i]=EnvironmentMap.at(base.snow,i,0);channels[count*2+i]=base.waterY(i,sea);channels[count*3+i]=EnvironmentMap.at(base.rain,i,.65f);channels[count*4+i]=EnvironmentMap.at(base.river,i,0);channels[count*5+i]=EnvironmentMap.at(base.shoreDistance,i,0);channels[count*6+i]=EnvironmentMap.at(base.riverGrad,i,0);}
  var copy=node.copy();copy.values.put("grid",(double)g);copy.values.put("step",(double)step);copy.values.put("spacing",spacing);copy.values.put("originX",(double)ox);copy.values.put("originZ",(double)oz);copy.values.put("hasShore",base.shoreDistance==null?0.0:1.0);
  float[] output=new float[count*2];float[][] inputs={base.height,grids,channels};
  if(!GpuNodes.compute(copy,CODE.replace("CHANNEL","channel"),inputs,output,n,size,sea,seed,stop))return false;
  for(int i=0;i<count;i++)result.material[i]=(byte)output[i];result.snow=java.util.Arrays.copyOfRange(output,count,count*2);return true;
 }
 static final String HELPERS="""

int rockPatch(double h,double x,double z,ulong seed){
        double drift=h/90.0,w=(sampleNoise(x/60,z/60,seed+6203)-.5)*30;
        double r=.65*sampleNoise((x+w)/34+drift,(z-w)/34,seed+6209)+.35*sampleNoise(x/11,z/11+drift,seed+6213);
        if(r<.42)return 3;
        if(r<.6)return 12;
        if(r<.7)return 19;
        if(r<.76)return sampleNoise(x/520,z/520,seed+6211)>.5?18:17;
        if(r>.9)return 20;
        return 3;
    }
int mountainMaterial(double exposure,double p,double d,double x,double z,ulong seed,double width){
  double weight=curve(fclamp(.5+(exposure-(p*.85+.1))/fmax(.1,width),0,1));
  if(weight<=0)return 1;
  double grain=sampleNoise(x/3.5,z/3.5,seed+4919),fleck=sampleNoise(x/1.7,z/1.7,seed+4921);
  if(grain<weight){double granite=fclamp((.36-p)/.18,0,1),diorite=fclamp((p-.63)/.18,0,1);if(fleck<granite*.8)return 18;if(fleck>1-diorite*.8)return 17;return d*.65+fleck*.35>.57?12:3;}
  if(weight>.06&&fleck<weight*.7)return grain<.55?7:10;
  return 1;
 }
int mountainSurface(int base,double p,double d,double x,double z,ulong seed,double h,double slope,double rockLine){
        bool rock=base==3||base==12||base==17||base==18;
        double fleck=sampleNoise(x/2.3,z/2.3,seed+6201);
        if(rock){
            if(slope>1.1)return rockPatch(h,x,z,seed);
            if(slope>.5)return fleck<.28?7:fleck<.46?11:fleck<.74?12:3;
            if(h>rockLine+380&&d>.62)return 20;
            // grassy and mossy ledges on gentle ground between the rock faces, thinning out with height
            double ledge=sampleNoise(x/38,z/38,seed+6221)*.7+p*.3,limit=.4+.4*fclamp((h-rockLine)/320,0,1)+slope*.35;
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
bool beachSand(global const double* settings,double p,double d,double wx,double wz,ulong seed,double distance,double h,int sea){
        double variation=P(beachVariation),dunes=P(dunes),base=P(beachWidth);
        double cove=curve(fclamp(.72*sampleNoise(wx/520,wz/520,seed+2311)+.28*sampleNoise(wx/150,wz/150,seed+2313),0,1));
        double width=base*(.55+p)*(1-variation+variation*(.2+1.6*cove));
        double rise=P(beachRise)*(.65+p)*sqrt(fmax(1,base/48));
        if(distance<width)return h<sea+rise;
        if(dunes<=0)return false;
        double zone=width*(.35+.65*dunes),t=(distance-width)/fmax(1,zone);
        if(t>=1||h>=sea+rise+6*dunes)return false;
        return d>.3+.7*t;// sand thins out into dune grass
    }
double fieldMix(global const float* h,int n,double x,double z){x=fmax(0,fmin(n-1,x));z=fmax(0,fmin(n-1,z));int ix=(int)x,iz=(int)z,jx=min(n-1,ix+1),jz=min(n-1,iz+1);double fx=x-ix,fz=z-iz;return (h[iz*n+ix]*(1-fx)+h[iz*n+jx]*fx)*(1-fz)+(h[jz*n+ix]*(1-fx)+h[jz*n+jx]*fx)*fz;}

 """;
 static final String CODE="""
int count=n*n,g=(int)P(grid);double step=P(step),spacing=P(spacing),block=size/(double)n,ox=P(originX),oz=P(originZ);float localSlope=0;if(x>0)localSlope=fmax(localSlope,fabs(A[i]-A[i-1]));if(x<n-1)localSlope=fmax(localSlope,fabs(A[i]-A[i+1]));if(z>0)localSlope=fmax(localSlope,fabs(A[i]-A[i-n]));if(z<n-1)localSlope=fmax(localSlope,fabs(A[i]-A[i+n]));localSlope=localSlope*n/size;double gx=x/(double)step,gz=z/(double)step,p=fieldMix(B+0*g*g,g,gx,gz),d=fieldMix(B+1*g*g,g,gx,gz),distance=fieldMix(B+2*g*g,g,gx,gz)*spacing,h=A[i],slope=localSlope,cover=fieldMix(B+4*g*g,g,gx,gz),rain=C[count*3+i];bool underwater=C[count*2+i]>floor(h+.5),seaCloser=P(hasShore)>0&&!(distance<=C[count*5+i]),river=C[count*4+i]>.1||!seaCloser&&fieldMix(B+3*g*g,g,gx,gz)>sea+2;/* open sea beyond the tile edge outranks inland water seen inside the tile */int mat=1;if((C[i]==6||C[i]==9)){value=CHANNEL==0?C[i]:C[count+i];out[j]=(float)value;return;}
   double rock=fclamp((h-P(rockLine)+(p-.5)*100)/110,0,1),exposure=fmax(rock,fclamp((slope-.25)/.65,0,1));
   double rg=C[count*6+i],fl=sampleNoise((ox+x*block)/1.9,(oz+z*block)/1.9,seed+2391);
   if(underwater&&river&&rg>.03){mat=fl<.33?3:fl<.58?11:fl<.8?12:13;}/* rapids and falls: rocky, mossy bed */
   else if(underwater){mat=river?(p<.28&&h<sea+55?2:d<.24?3:d>.76?12:7):(slope>.6?3:p<.63?2:7);}
   else if(rg>.09&&distance<5){mat=fl<.45?3:fl<.75?13:12;}/* spray-washed rock beside waterfalls */
   else if(distance<P(bankWidth)*(.55+p)&&h-fieldMix(B+3*g*g,g,gx,gz)<8&&river){double t=distance/fmax(1,P(bankWidth)*(.55+p));mat=t<.25?(d<.2&&h<sea+45?2:d>.82?12:7):t<.65?(p<.37&&rain>.6?5:d<.6?10:7):d<.5?10:1;}
   else if(!river&&beachSand(params,p,d,ox+x*block,oz+z*block,seed,P(hasShore)==0?distance:C[count*5+i],h,sea)){mat=slope>.6||p>.8?(d>.65?12:7):2;}
   else {mat=mountainSurface(mountainMaterial(exposure,p,d,ox+x*block,oz+z*block,seed,P(blendWidth)/110.),p,d,ox+x*block,oz+z*block,seed,h,slope,P(rockLine));
    if(mat==1&&cover>P(forestFloor)*(.55+p)&&slope<.65)mat=rain>.72&&d>.62?16:d<.28?15:p<.6?14:10;
   }
   double resultSnow=C[count+i];
   double snow=C[count+i];if(snow>0){double retention=1-curve(fclamp((slope-.45)/1.2,0,1));double organic=fclamp(snow+(p-.5)*.55+(d-.5)*.12,0,1);resultSnow=underwater?0:(float)(organic*retention);}value=CHANNEL==0?mat:resultSnow;
 """;
}
