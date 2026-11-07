import java.util.function.BooleanSupplier;
final class GpuWaterShapes {
 static boolean eligible(int n){return GpuGeneration.enabled()&&System.getProperty("terraforge.gpu","auto").equalsIgnoreCase("gpu")&&n>=512&&n*(long)n*100<=(256L<<20);}
 static boolean lake(TerrainGraph.Node node,EnvironmentMap base,EnvironmentMap result,float[] distance,int n,int size,int sea,long seed,double cx,double cz,double radius,BooleanSupplier stop){
  if(!eligible(n))return false;int count=n*n;var copy=node.copy();copy.values.put("centerX",cx);copy.values.put("centerZ",cz);copy.values.put("cellRadius",radius);copy.values.put("nodeId",(double)node.id);
  float[] material=new float[count];for(int i=0;i<count;i++)material[i]=result.material[i];float[] out=new float[count*4];
  if(!GpuNodes.compute(copy,LAKE,new float[][]{base.height,result.water,material},out,n,size,sea,seed,stop))return false;
  System.arraycopy(out,0,result.height,0,count);System.arraycopy(out,count,result.water,0,count);for(int i=0;i<count;i++)result.material[i]=(byte)out[count*2+i];System.arraycopy(out,count*3,distance,0,count);return true;
 }
 static boolean edges(TerrainGraph.Node node,EnvironmentMap base,EnvironmentMap climate,EnvironmentMap result,EnvironmentNodes.NearWater near,float[] envelope,int n,int size,int sea,long seed,BooleanSupplier stop){
  if(!eligible(n))return false;int count=n*n;float[] fields=new float[count*3],previous=new float[count*3];
  for(int i=0;i<count;i++){if(i%n==0)GpuGeneration.checkStop(stop);fields[i]=near.distance()[i];fields[count+i]=near.level()[i];fields[count*2+i]=EnvironmentMap.at(climate==null?null:climate.rain,i,.65f);previous[i]=EnvironmentMap.at(result.water,i,sea);previous[count+i]=result.material[i];previous[count*2+i]=result.biome==null?0:result.biome[i];}
  var copy=node.copy();String code=EDGES;for(var type:new TerrainGraph.Type[]{TerrainGraph.Type.COAST,TerrainGraph.Type.SEDIMENT,TerrainGraph.Type.WETLAND}){code=code.replace("t==TerrainGraph.Type."+type.name(),node.type==type?"true":"false");for(var p:type.params)copy.values.putIfAbsent(p.key(),p.value());}
  float[] out=new float[count*5];if(!GpuNodes.compute(copy,code,new float[][]{base.height,fields,previous},out,n,size,sea,seed,stop))return false;
  System.arraycopy(out,0,result.height,0,count);if(result.water!=null)System.arraycopy(out,count,result.water,0,count);for(int i=0;i<count;i++){result.material[i]=(byte)out[count*2+i];if(result.biome!=null)result.biome[i]=(byte)out[count*3+i];}if(envelope!=null)System.arraycopy(out,count*4,envelope,0,count);return true;
 }
 static final String LAKE="""
int count=n*n;double cx=P(centerX),cz=P(centerZ),radius=P(cellRadius),level=P(level),depth=P(depth),newHeight=a,newWater=B[i],newMaterial=C[i],distanceResult=INFINITY;double dx=(x-cx)/radius,dz=(z-cz)/radius,d=hypot(dx,dz);
            if(P(natural)>.5){
                double angle=atan2(dz,dx),phase=sampleNoise(0.7,1.3,seed+((ulong)P(nodeId))*131L+((long)P(locationSeed)))*3.141592653589793*2;
                double shore=1+.16*sin(3*angle+phase)+.09*sin(5*angle-phase*.7)+.04*sin(9*angle+phase*1.3);
                d/=shore;
                if(P(terrainFit)>0){double uphill=curve(fclamp((A[i]-level-12)/180,0,1));d*=1+P(terrainFit)*uphill*.85*curve(fclamp(d/.8,0,1));}
                if(d<1.35){
                    double bedNoise=sampleNoise(dx*2+19,dz*2+31,seed+((ulong)P(nodeId))*73L);
                    double bowl=level-.6-depth*pow(fmax(0,1-d*d),1.35)*(.8+.3*bedNoise);
                    double shoreHeight=level+(d-1)*depth*.7;
                    double target=d<1?bowl:shoreHeight;
                    double blend=curve(fclamp((1.35-d)/.45,0,1));
                    newHeight=(float)(A[i]+(target-A[i])*blend);
                    if(newHeight<level&&d<1.08){distanceResult=0;newWater=(float)fmax(newWater,level);newMaterial=(bedNoise<.3?5:bedNoise>.65?7:2);}
                }
            }else if(d<1){newHeight=(float)fmin(newHeight,level-depth*(1-d*d)-1);distanceResult=0;newWater=(float)fmax(newWater,level);newMaterial=2;}else if(d<1.3){double rim=curve(fclamp((1.3-d)/.3,0,1));newHeight=(float)fmax(newHeight,level+rim*3);}value=channel==0?newHeight:channel==1?newWater:channel==2?newMaterial:distanceResult;
 """;
 static final String EDGES="""
int count=n*n;double newHeight=a,newWater=C[i],newMaterial=C[count+i],newBiome=C[count*2+i],newEnvelope=INFINITY;float cellSlope=0;if(x>0)cellSlope=fmax(cellSlope,fabs(A[i]-A[i-1]));if(x<n-1)cellSlope=fmax(cellSlope,fabs(A[i]-A[i+1]));if(z>0)cellSlope=fmax(cellSlope,fabs(A[i]-A[i-n]));if(z<n-1)cellSlope=fmax(cellSlope,fabs(A[i]-A[i+n]));cellSlope=cellSlope*n/size;double distance=B[i]*(double)size/n,level=B[count+i],height=A[i],slope=cellSlope;if(!isfinite(B[i])){value=channel==0?a:channel==1?C[i]:channel==2?C[count+i]:channel==3?C[count*2+i]:INFINITY;out[j]=(float)value;return;}
                if(t==TerrainGraph.Type.COAST&&distance<=P(width)&&height>level&&height<level+40){double f=1-distance/P(width),rock=sampleNoise((i%n)/(double)n*8,(i/n)/(double)n*8,seed+831);bool cliff=rock<P(rocky);double target=level+1+P(rise)*distance/P(width);newHeight=(float)(height+(target-height)*f);newMaterial=(cliff?3:2);if(cliff)newHeight=(float)fmax(newHeight,level+P(rise));}
                if(t==TerrainGraph.Type.COAST&&P(flatAmount)>0&&P(flatCoverage)>0&&height>level){
                    double stretch=P(flatScale);ulong coastSeed=seed+419;
                    double noise=sampleNoise((i%n)*(double)size/n/stretch,(i/n)*(double)size/n/stretch,coastSeed+((long)P(flatSeed))*0x632BE59BD9B4E019UL);
                    double coverage=P(flatCoverage);
                    double patch=coverage>=1?1:curve(fclamp((coverage+.08-noise)/.16,0,1));
                    double reach=P(flatReach);
                    double rolling=sampleNoise((i%n)*(double)size/n/32,(i/n)*(double)size/n/32,coastSeed+97);
                    double target=level+1+distance*.005+rolling*.35*curve(fclamp(distance/12,0,1));
                    // Keep the selected patch flat through its inland reach, then reshape
                    // surrounding hills over a height-dependent transition distance.
                    double transition=fmax(24,fmax(0,height-target)*2);
                    double fade=1-curve(fclamp((distance-reach)/transition,0,1));
                    double strength=P(flatAmount);
                    double amount=(1-(1-strength)*(1-strength))*patch*fade;
                    if(target<newHeight&&amount>0){newHeight=(float)(newHeight+(target-newHeight)*amount);if(patch>.5&&distance<reach*.65)newMaterial=2;newEnvelope=newHeight;}
                }
                if(t==TerrainGraph.Type.SEDIMENT&&distance<=P(width)&&slope<=P(slope)){double f=sampleNoise((i%n)/(double)n*24,(i/n)/(double)n*24,seed+72);newMaterial=(f<P(mud)?5:f>.7?7:2);}
                if(t==TerrainGraph.Type.WETLAND&&distance<=P(width)&&height>=level-3&&height<=level+P(relief)&&B[count*2+i]>=P(rain)){double noise=sampleNoise((i%n)/(double)n*26,(i/n)/(double)n*26,seed+615);newHeight=(float)(level+(noise-.48)*3);newWater=(float)level;newMaterial=5;newBiome=5;}
            value=channel==0?newHeight:channel==1?newWater:channel==2?newMaterial:channel==3?newBiome:newEnvelope;
 """;
}
