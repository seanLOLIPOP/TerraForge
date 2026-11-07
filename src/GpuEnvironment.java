import java.util.function.BooleanSupplier;

/** Expensive independent environmental passes. Routing and neighborhood preparation remain shared CPU work. */
final class GpuEnvironment {
    static EnvironmentMap simple(TerrainGraph.Node node,EnvironmentMap base,EnvironmentMap climate,int n,int size,int sea,long seed,BooleanSupplier stop){
        if(!System.getProperty("terraforge.gpu","auto").equalsIgnoreCase("gpu")||n*(long)n*64>(256L<<20))return null;
        int outputs=switch(node.type){case FREEZE,BIOMES->1;case SNOW->node.p("natural")>.5?0:1;case GLACIER->4;case RAPIDS->3;default->0;};if(outputs==0)return null;
        int count=n*n;float[] data=new float[count*9];
        for(int i=0;i<count;i++){
            if(i%n==0)GpuGeneration.checkStop(stop);data[i]=base.waterY(i,sea);data[count+i]=EnvironmentMap.at(base.river,i,0);data[count*2+i]=base.material==null?0:base.material[i];
            data[count*3+i]=EnvironmentMap.at(base.snow,i,0);data[count*4+i]=EnvironmentMap.at(base.ice,i,0);data[count*5+i]=climate==null?.5f:EnvironmentMap.at(climate.rain,i,.5f);
            data[count*6+i]=EnvironmentMap.at(base.glacier,i,0);data[count*7+i]=EnvironmentMap.at(base.water,i,sea);data[count*8+i]=EnvironmentNodes.temperature(climate,i,base.height[i],sea);
        }
        String prefix="int count=n*n;double water=b,river=B[count+i],material=B[count*2+i],snow=B[count*3+i],ice=B[count*4+i],rain=B[count*5+i],glacier=B[count*6+i],temp=B[count*8+i];float sl=0;if(x>0)sl=fmax(sl,fabs(A[i]-A[i-1]));if(x<n-1)sl=fmax(sl,fabs(A[i]-A[i+1]));if(z>0)sl=fmax(sl,fabs(A[i]-A[i-n]));if(z<n-1)sl=fmax(sl,fabs(A[i]-A[i+n]));sl=sl*n/size;";
        String code=switch(node.type){
            case FREEZE->"value=ice;if(water>floor(a+.5)&&temp<P(threshold))value=P(thickness);";
            case BIOMES->"bool wet=water>floor(a+.5),r=river>.05||water>sea;int id;if(wet)id=ice>0?(r?9:8):r?3:2;else if(material==5)id=5;else if(snow>.25||temp<.25)id=4;else if(temp>.7&&rain<.4)id=6;else if(rain>.55)id=7;else id=1;value=id;";
            case GLACIER->"double cold=(float)clamp((.55-temp)*5,0.0,1.0),height=(float)clamp((a-P(line))/30,0.0,1.0),valley=(float)clamp(1-sl/P(slope),0.0,1.0),mask=cold*height*valley;value=channel==0?a:channel==1?material:channel==2?glacier:snow;if(mask>.1){value=channel==0?clamp(a+P(thickness)*mask,-60.0,1024.0):channel==1?6:channel==2?P(thickness)*mask:fmax(snow,mask);}";
            case RAPIDS->"value=channel==0?a:channel==1?B[count*7+i]:material;if(river>=.1&&water>sea){double level=sea+floor((B[count*7+i]-sea)/P(step))*P(step),rough=sampleNoise(x*.5,z*.5,seed+88)*P(rough);value=channel==0?fmin(a,level-P(depth)+rough*.4):channel==1?level:3;}";
            case SNOW->"value=snow;double height=curve((float)clamp((a-P(line)+P(transition))/P(transition),0.0,1.0)),cold="+(climate==null?"1":"(float)clamp((.65-temp)*3,0.0,1.0)")+",slope=(float)clamp(1-sl/P(slope),0.0,1.0),noise=sampleNoise(x/(double)n*27,z/(double)n*27,seed+967);value=a>=water?(float)clamp((height*cold-(1-noise)*P(patch))/fmax(.2,1-P(patch)*.5)*slope,0.0,1.0):0;";
            default->throw new IllegalStateException();
        };
        float[] out=new float[count*outputs];if(!GpuNodes.compute(node,prefix+code,new float[][]{base.height,data},out,n,size,sea,seed,stop))return null;
        var e=base.share(base.height);
        switch(node.type){
            case FREEZE->e.ice=out;
            case SNOW->e.snow=out;
            case BIOMES->{e.biome=bytes(out,0,count);if(climate!=null){e.temperature=climate.temperature;e.rain=climate.rain;}}
            case GLACIER->{e=e.share(java.util.Arrays.copyOf(out,count));e.material=bytes(out,count,count);e.glacier=java.util.Arrays.copyOfRange(out,count*2,count*3);e.snow=java.util.Arrays.copyOfRange(out,count*3,count*4);}
            case RAPIDS->{e=e.share(java.util.Arrays.copyOf(out,count));e.water=java.util.Arrays.copyOfRange(out,count,count*2);e.material=bytes(out,count*2,count);}
            default->throw new IllegalStateException();
        }
        return e;
    }
    static byte[] bytes(float[] values,int offset,int count){byte[] out=new byte[count];for(int i=0;i<count;i++)out[i]=(byte)values[offset+i];return out;}
    static EnvironmentMap apply(TerrainGraph.Node node,EnvironmentMap b,EnvironmentMap climate,int n,int size,int sea,long seed,BooleanSupplier stop){
        if(!GpuGeneration.enabled()||n<512)return null;
        if(node.type==TerrainGraph.Type.OCEAN){
            float[] out=new float[n*n];
            String code="double depth=sea-a;if(depth>0){double u=(x+.5)/n,v=(z+.5)/n,transition=curve((float)clamp((depth-P(shelf))/P(reach),0.0,1.0));double abyss=fmax(P(depth),P(shelf));double ridge=1-fabs(sampleNoise(u*9,v*9,seed+643)*2-1);double trench=pow(ridge,12.0)*P(trench)*transition;double mountains=(sampleNoise(u*14,v*14,seed+95)-.5)*P(mountains)*transition;double d=fmin(depth,P(shelf))+transition*(abyss-P(shelf))+trench-mountains;value=(float)clamp(sea-fmax(.5,d),-60.0,1024.0);}";
            if(!GpuNodes.compute(node,code,new float[][]{b.height},out,n,size,sea,seed,stop))return null;
            var e=b.share(out);e.water=EnvironmentNodes.water(b,n*n,sea);return e;
        }
        if(node.type==TerrainGraph.Type.CLIMATE){
            float[] output=new float[n*n*2];
            float[] shadow=EnvironmentNodes.rainShadow(node,b.height,n,size,stop);
            String prefix="double u=(x+.5)/n,v=(z+.5)/n,noise=fractal(u*3,v*3,seed+1071,3,.5)-.5;float h=A[i];double altitude=fmax(0.0,(double)(h-sea))/180.0;";
            String code=prefix+"if(channel==0){value=clamp(P(temperature)-altitude*P(lapse)+noise*P(variation)-(v-.5)*.2,0.0,1.0);}else{double shadow=x>0?fmax(0.0,(double)(h-A[i-1]))*.02:0;value=clamp(P(rain)+noise*P(variation)-shadow+b,0.0,1.0);}";
            if(!GpuNodes.compute(node,code,new float[][]{b.height,shadow},output,n,size,sea,seed,stop))return null;
            var e=b.share(b.height);e.temperature=java.util.Arrays.copyOf(output,n*n);e.rain=java.util.Arrays.copyOfRange(output,n*n,n*n*2);return e;
        }
        return simple(node,b,climate,n,size,sea,seed,stop);
    }
    static EnvironmentMap detail(TerrainGraph.Node node,EnvironmentMap base,int n,int size,int sea,long seed,double ox,double oz,BooleanSupplier stop){
        if(!GpuGeneration.enabled()||n<512)return null;
        var copy=node.copy();copy.values.put("originX",ox);copy.values.put("originZ",oz);
        float[] water=new float[n*n];for(int i=0;i<water.length;i++)water[i]=base.waterY(i,sea);
        float[] sand=null;if(base.material!=null){sand=new float[n*n];for(int i=0;i<sand.length;i++)sand[i]=base.material[i]==2?1:0;}
        float[] out=new float[n*n];
        String code="double h=a,water=b;if(h>water+1&&h<510){double shore=curve(fmin(1.0,fmax(0.0,(h-water-1)/10))),summit=curve(fmin(1.0,(510-h)/16));double xx=P(originX)+x*(double)size/n,zz=P(originZ)+z*(double)size/n;ulong detailSeed=seed+(ulong)((long)P(seed));double scale=P(scale);double broad=sampleNoise(xx/scale,zz/scale,detailSeed+3907)*2-1,fine=sampleNoise(xx/(scale*.25),zz/(scale*.25),detailSeed+7283)*2-1;double amplitude=P(amount)*shore*summit;"+(sand==null?"":"if(c>0)amplitude*=.3;")+"value=h+amplitude*(broad*.8+fine*.2);}";
        return GpuNodes.compute(copy,code,new float[][]{base.height,water,sand},out,n,size,sea,seed,stop)?base.share(out):null;
    }
    static EnvironmentMap snow(TerrainGraph.Node node,EnvironmentMap base,EnvironmentMap climate,int n,int size,int sea,long seed,int ox,int oz,BooleanSupplier stop){
        if(!GpuGeneration.enabled()||n<512||n*(long)n*40>(256L<<20))return null;
        int count=n*n;boolean zones=base.zoneSlope!=null&&base.zoneSlope.length==count&&base.zoneSource==base.height;
        float[] data=new float[count*6];
        for(int i=0;i<count;i++){
            if(i%n==0)GpuGeneration.checkStop(stop);
            data[i]=base.waterY(i,sea);data[count+i]=climate==null?1:EnvironmentNodes.clamp((.65-EnvironmentNodes.temperature(climate,i,base.height[i],sea))*3,0,1);
            data[count*2+i]=zones?base.zoneSlope[i]:0;data[count*3+i]=zones?base.zoneNorth[i]:0;
            data[count*4+i]=base.material!=null&&base.material[i]==6&&EnvironmentMap.at(base.glacier,i,0)>0?1:0;
        }
        var copy=node.copy();copy.values.put("originX",(double)ox);copy.values.put("originZ",(double)oz);
        String code="int count=n*n;double h=a,block=size/(double)n,wx=P(originX)+x*block,wz=P(originZ)+z*block;value=0;if(b<=floor(h+.5)){"+
            "float cellSlope=0;if(x>0)cellSlope=fmax(cellSlope,fabs(A[i]-A[i-1]));if(x<n-1)cellSlope=fmax(cellSlope,fabs(A[i]-A[i+1]));if(z>0)cellSlope=fmax(cellSlope,fabs(A[i]-A[i-n]));if(z<n-1)cellSlope=fmax(cellSlope,fabs(A[i]-A[i+n]));cellSlope=cellSlope*n/size;"+
            "double slope="+(zones?"B[count*2+i]":"cellSlope")+",line=P(line);"+(zones?"line+=(sampleNoise(wx/720,wz/720,seed+7105)-.5)*150+(sampleNoise(wx/95,wz/95,seed+7107)-.5)*40-B[count*3+i]*120;":"")+
            "slope=slope*4/fmax(1.0,P(slope));double cold=B[count+i],drift=(sampleNoise(wx/97,wz/97,seed+8101)-.5)*P(transition)*.65;double progress=(float)clamp((h-line+P(transition)+drift)/fmax(1.0,P(transition)),0.0,1.0);double retention=1-curve((float)clamp((slope-.35)/1.2,0.0,1.0));double coverage=curve(progress)*fmax(.1,cold)*retention;double grain=.75*sampleNoise(wx/6,wz/6,seed+8103)+.25*sampleNoise(wx/2.3,wz/2.3,seed+8105);double threshold=grain*(.7+.3*P(patch));if(coverage>threshold){int layers=max(1,min(8,(int)floor(1+7*progress*retention+(grain-.5)*1.5+.5)));value=layers/8.0f;}if(B[count*4+i]>0&&h<line+90+sampleNoise(wx/40,wz/40,seed+8111)*60)value=0;}";
        float[] out=new float[count];if(!GpuNodes.compute(copy,code,new float[][]{base.height,data},out,n,size,sea,seed,stop))return null;
        var e=base.share(base.height);e.snow=out;return e;
    }
}
