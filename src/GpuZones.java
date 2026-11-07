import java.util.function.BooleanSupplier;
final class GpuZones {
 static boolean apply(TerrainGraph.Node node,EnvironmentMap base,EnvironmentMap result,int n,int size,int sea,long seed,int ox,int oz,float[] slope,float[] north,float[] bowl,float[] talus,BooleanSupplier stop){
  if(!GpuGeneration.enabled()||n<512||n*(long)n*64>(256L<<20))return false;
  int count=n*n;float[] data=new float[count*7],previous=new float[count*3];
  for(int i=0;i<count;i++){if(i%n==0)GpuGeneration.checkStop(stop);data[i]=base.waterY(i,sea);data[count+i]=EnvironmentMap.at(base.river,i,0);data[count*2+i]=EnvironmentMap.at(base.rain,i,.6f);data[count*3+i]=slope[i];data[count*4+i]=north[i];data[count*5+i]=EnvironmentMap.at(bowl,i,0);data[count*6+i]=talus[i];previous[i]=result.material[i];previous[count+i]=result.biome[i];previous[count*2+i]=EnvironmentMap.at(result.glacier,i,0);}
  var copy=node.copy();copy.values.put("originX",(double)ox);copy.values.put("originZ",(double)oz);copy.values.put("hasBowl",bowl==null?0.0:1.0);copy.values.putIfAbsent("glaciers",0.0);
  float[] out=new float[count*3];if(!GpuNodes.compute(copy,CODE,new float[][]{base.height,data,previous},out,n,size,sea,seed,stop))return false;
  for(int i=0;i<count;i++){result.material[i]=(byte)out[i];result.biome[i]=(byte)out[count+i];if(result.glacier!=null)result.glacier[i]=out[count*2+i];}return true;
 }
 static final String HELPERS="""
double treeline(double base,double x,double z,ulong seed,double north){
        return base+(sampleNoise(x/640,z/640,seed+7101)-.5)*130+(sampleNoise(x/150,z/150,seed+7103)-.5)*45-north*50;
    }
double snowline(double base,double x,double z,ulong seed,double north){
        return base+(sampleNoise(x/720,z/720,seed+7105)-.5)*150+(sampleNoise(x/95,z/95,seed+7107)-.5)*40-north*120;
    }
bool rock(int m){return m==3||m==7||m==11||m==12||m==17||m==18||m==19||m==20;}
int cliff(double h,double x,double z,ulong seed){return rockPatch(h,x,z,seed);}
int scree(double f,bool high){
        if(high)return f<.35?3:f<.6?7:f<.8?12:20;
        return f<.32?7:f<.52?12:f<.72?11:3;
    }
 """;
 static final String CODE="""
int count=n*n;double block=size/(double)n,ox=P(originX),oz=P(originZ),treelineBase=P(treeline),snowBase=P(snowline),glaciers=P(glaciers);float localSlope=0;if(x>0)localSlope=fmax(localSlope,fabs(A[i]-A[i-1]));if(x<n-1)localSlope=fmax(localSlope,fabs(A[i]-A[i+1]));if(z>0)localSlope=fmax(localSlope,fabs(A[i]-A[i-n]));if(z<n-1)localSlope=fmax(localSlope,fabs(A[i]-A[i+n]));localSlope=localSlope*n/size;double matResult=C[i],biomeResult=C[count+i],glacierResult=C[count*2+i];double h=A[i];
                if(h<sea+12||B[i]>floor(h+.5)||B[count+i]>.02){value=channel==0?C[i]:channel==1?C[count+i]:C[count*2+i];out[j]=(float)value;return;}
                int old=(int)C[i];
                if(old==2||old==5||old==6||old==9){value=channel==0?C[i]:channel==1?C[count+i]:C[count*2+i];out[j]=(float)value;return;}
                double wx=ox+x*block,wz=oz+z*block,sl=B[count*3+i],no=B[count*4+i];
                double tl=treeline(treelineBase,wx,wz,seed,no),sn=snowline(snowBase,wx,wz,seed,no),rel=h-tl;
                double wet=B[count*2+i]-.6,p=sampleNoise(wx/42,wz/42,seed+7121),d=sampleNoise(wx/9,wz/9,seed+7123),f=sampleNoise(wx/2.3,wz/2.3,seed+7125);
                int mat=old;
                if(P(hasBowl)>0&&sl<.55){// glaciers fill shaded high bowls and reach down north-facing valleys
                    double margin=(sampleNoise(wx/60,wz/60,seed+7141)-.5)*50,ice=(h-(sn-40+margin))/60+no*1.2+B[count*5+i]/8-sl*1.5;
                    if(ice>.6*(1.4-glaciers)){matResult=6;glacierResult=(float)fmin(24,4+ice*6);biomeResult=12;{value=channel==0?matResult:channel==1?biomeResult:glacierResult;out[j]=(float)value;return;}}
                }
                double cellSlope=localSlope;
                if(old==3&&cellSlope>.75&&sl<1.15){// boulders and outcrops keep their rock; mossy in damp woods
                    mat=rel<-40&&wet>-.15&&d>.45?13:f<.4?3:f<.65?12:f<.85?18:11;
                }
                else if(sl>1.15-(rel>0?.15:0))mat=cliff(h,wx,wz,seed);
                else if(B[count*6+i]>0&&B[count*6+i]>f*.9+.05&&sl<1.15)mat=scree(f,h>sn);
                else if(sl>.72&&rel>-90)mat=scree(f,h>sn);
                else if(h>sn-40)mat=d>.82?20:p<.3?7:f<.5?3:f<.75?12:7;
                else if(rel>170){
                    double grass=.35-(rel-170)/500+(.35-sl)*.6-no*.1+wet*.6;
                    mat=p<grass&&d>.35?(d>.85?16:1):(f<.3?7:f<.55?12:f<.85?3:19);
                }
                else if(rel>0){
                    double rockChance=rel/170*.45+fmax(0,sl-.3)*.9+(no>0?.06:0)-wet*.6;
                    if(sampleNoise(wx/17,wz/17,seed+7127)<rockChance)mat=f<.35?7:f<.6?3:f<.8?12:11;
                    else mat=d<.14-wet*.3?10:d>.84-wet*.5&&no>-.2?16:f>.94?3:1;
                }
                else if(rel>-150){
                    if(sl>.5&&sampleNoise(wx/17,wz/17,seed+7127)<(sl-.5)*1.6)mat=f<.4?3:f<.7?12:10;
                    else{double litter=sampleNoise(wx/16,wz/16,seed+7131)*.65+d*.35-(rel+150)/150*.25;mat=litter>.62-wet*.3?(f<.75+wet?14:10):d<.12-wet*.4?10:d>.88-wet*.6&&no>-.3?16:1;}
                }
                else if(rock(old)&&sl<.6)mat=d<.25?10:1;
                matResult=mat;
                // Mountain biomes, so grass colours, snow behaviour and mobs match the zone.
                if(h>sn)biomeResult=sl>.9?13:12;
                else if(rel>170)biomeResult=no>.3&&h>sn-120?12:14;
                else if(rel>0)biomeResult=10;
                else if(rel>-150)biomeResult=h>sn-260?11:15;
value=channel==0?matResult:channel==1?biomeResult:glacierResult;
 """;
}
