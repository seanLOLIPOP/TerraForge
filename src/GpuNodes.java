import java.util.function.BooleanSupplier;

/** GPU equivalents of independent terrain operations; shared topology stays on the CPU. */
final class GpuNodes {
    static final String SLOPE="double slope=0;if(x>0)slope=fmax(slope,fabs(a-A[i-1]));if(x<n-1)slope=fmax(slope,fabs(a-A[i+1]));if(z>0)slope=fmax(slope,fabs(a-A[i-n]));if(z<n-1)slope=fmax(slope,fabs(a-A[i+n]));slope*=n/(double)size;";
    static boolean apply(TerrainGraph.Node node,float[][] inputs,float[] out,int n,int size,int sea,long seed,float[] seaDistance,BooleanSupplier stop){
        String code=operation(node,inputs.length>2&&inputs[2]!=null,seaDistance!=null);
        if(code==null)return false;
        boolean cheap=switch(node.type){case CONSTANT,REMAP,CLAMP,TERRACE,HEIGHT_MASK,SLOPE_MASK,BLEND,OUTPUT->true;default->false;};
        if(cheap&&!GpuGeneration.deferring()&&!GpuGeneration.mode().equalsIgnoreCase("gpu"))return false;
        code+="value=clamp(value,"+(node.type.mask?"0.0,1.0":"-60.0,1024.0")+");";
        if(node.type==TerrainGraph.Type.MOUNTAIN)inputs=new float[][]{inputs[0],seaDistance};
        return compute(node,code,inputs,out,n,size,sea,seed,stop);
    }
    static String operation(TerrainGraph.Node node,boolean hasMask,boolean hasSeaDistance){
        return switch(node.type){
            case NOISE->"double f=fractal((x+.5)/n*P(scale),(z+.5)/n*P(scale),seed+(ulong)((long)P(seed)),(int)P(detail),P(rough));value=sea+(f-.4)*P(height)*2;";
            case RIDGES->"double f=fractal((x+.5)/n*P(scale),(z+.5)/n*P(scale),seed+(ulong)((long)P(seed)),5,.5);value=sea+(pow(1-fabs(f*2-1),P(sharp))-.45)*P(height)*2;";
            case OUTPUT->"value=a;";
            case CONSTANT->"value=P(height);";
            case REMAP->"value=sea+(a-sea)*P(scale)+P(offset);";
            case CLAMP->"value=clamp(a,P(min),P(max));";
            case TERRACE->"double step=P(step),band=floor(a/step),fraction=a/step-band;double v=(band+curve(clamp((fraction-.65)/.35,0.0,1.0)))*step;value=a+(v-a)*P(strength);";
            case HEIGHT_MASK->"value=curve(clamp((a-P(min))/(P(max)-P(min)),0.0,1.0));";
            case SLOPE_MASK->SLOPE+"value=curve(clamp((slope-P(min))/(P(max)-P(min)),0.0,1.0));";
            case BLEND->"double t=P(mix)*"+(!hasMask?"1.0":"clamp(c,0.0,1.0)")+";double combined=b;switch((int)P(mode)){case 1:combined=a+b-sea;break;case 2:combined=fmax(a,b);break;case 3:combined=fmin(a,b);break;case 4:combined=a-(b-sea);break;case 5:combined=sea+(a-sea)*(b-sea)/100.0;break;}value=a+(combined-a)*t;";
            case REGIONAL_RELIEF->"double u=(x+.5)/n,v=(z+.5)/n,noise=fractal(u*9,v*9,seed+4721+(ulong)((long)P(seed)),4,.5);double dx=(u-P(x)/100.0)/1.25,dz=v-P(z)/100.0,radius=P(radius)/100.0,distance=hypot(dx,dz),irregular=radius*(.85+noise*.3);double mountain=curve(clamp((irregular-distance)/(radius*P(transition)),0.0,1.0));double plains=P(plain)+(noise-.5)*P(rolling)*2,low=fmin(a,plains);value=a<=sea?a:low+(a-low)*mountain;";
            case MOUNTAIN->"double u=(x+.5)/n,v=(z+.5)/n,r=P(radius)/100.0,dx=u-P(x)/100.0,dz=v-P(z)/100.0;ulong mountainSeed=seed+(ulong)((long)P(seed));double noise=fractal(u*45,v*45,mountainSeed+6701,4,.55);double angle=P(rotation)*0.017453292519943295,along=dx*cos(angle)+dz*sin(angle),across=-dx*sin(angle)+dz*cos(angle);double d=hypot(along/P(aspect),across)/(r*(.85+noise*.3));double profile=pow(1-curve(fmin(1.0,d)),1-P(ridge)*.3);double crestValue=1-P(ridge)*.4*(.5-.5*cos(along/(r*P(aspect))*10));profile*=crestValue;if(P(peaks)>0)profile=profile*(1-P(peaks))+(mountainPeaks(along,across,r,P(aspect),noise,mountainSeed)*(1-P(organic))+(P(spurStyle)>.5?naturalProfile(along,across,r,P(aspect),noise,mountainSeed,P(spurs),P(definition),p+SHAPESTART,SHAPECOUNT):organicProfile(along,across,r,P(aspect),noise,mountainSeed,P(spurs),P(definition),p+SHAPESTART))*P(organic))*P(peaks);double dry=curve(clamp((a-sea)/20.0,0.0,1.0));double coastal="+(!hasSeaDistance?"1.0":"curve(clamp(b*size/n/P(coastalReach),0.0,1.0))")+";value=a+P(height)*profile*(.78+noise*.44)*dry*coastal;";
            case ISLAND->"double angle=P(rotation)*0.017453292519943295,u=(x+.5)/n*2-1-(P(cx)-50)/50,v=(z+.5)/n*2-1-(P(cz)-50)/50;double rx=(u*cos(angle)-v*sin(angle))/P(aspect),rz=u*sin(angle)+v*cos(angle);double warpX=(sampleNoise(rx*4+11,rz*4+7,seed+8123)*2-1)*P(shoreDetail)*.18,warpZ=(sampleNoise(rx*4+3,rz*4+19,seed+1937)*2-1)*P(shoreDetail)*.18;rx+=warpX;rz+=warpZ;double angleAround=atan2(rz,rx),shoreNoise=fractal(rx*3.2+7,rz*3.2+11,seed+81031,3,.5);double irregular=1+P(shoreVariation)*((shoreNoise-.5)*2.8+.65*sin(angleAround*3+1.7)+.25*sin(angleAround*5-.4));double fall=curve(clamp((hypot(rx,rz)/(P(radius)*fmax(.45,irregular))-.2)/.95,0.0,1.0));if(P(shoreVariation)>0)fall=fmax(fall,curve(clamp((hypot(u,v)-.78)/.32,0.0,1.0)));value=a-fall*P(depth);";
            default->null;
        };
    }
    static boolean compute(TerrainGraph.Node node,String code,float[][] inputs,float[] out,int n,int size,int sea,long seed,BooleanSupplier stop){
        double[] shapes=node.type==TerrainGraph.Type.MOUNTAIN?GpuMountains.shapes(node,seed+(long)node.p("seed")):new double[0];
        double[] params=new double[node.values.size()+shapes.length];int index=0;
        for(var entry:node.values.entrySet()){code=code.replace("P("+entry.getKey()+")","params["+index+"]");params[index++]=entry.getValue();}
        code=code.replace("p+SHAPESTART","params+SHAPESTART").replace("SHAPESTART",Integer.toString(index)).replace("SHAPECOUNT",Integer.toString(shapes.length/8));System.arraycopy(shapes,0,params,index,shapes.length);
        String key=node.type.name()+":"+Integer.toHexString(code.hashCode());
        return GpuGeneration.compute(key,code,params,inputs,out,n,size,sea,seed,stop);
    }
}
