import jdk.incubator.vector.*;
import java.util.function.BooleanSupplier;

/** CPU SIMD hashing and interpolation; explicit arithmetic preserves scalar rounding. */
final class VectorNoise {
    static final VectorSpecies<Long> L=LongVector.SPECIES_PREFERRED;
    static final VectorSpecies<Double> D=DoubleVector.SPECIES_PREFERRED;
    private static DoubleVector hash(LongVector x,long z,long seed){
        var v=x.mul(374761393L).add(z*668265263L).add(seed*1442695040888963407L);
        v=v.lanewise(VectorOperators.XOR,v.lanewise(VectorOperators.LSHR,13)).mul(1274126177L);
        v=v.lanewise(VectorOperators.XOR,v.lanewise(VectorOperators.LSHR,16)).and(0x7fffffffL);
        return ((DoubleVector)v.convert(VectorOperators.L2D,0)).div((double)0x7fffffff);
    }
    static void samples(double[] x,double z,long seed,double[] out){
        samples(x,z,seed,out,new long[x.length],new double[x.length]);
    }
    private static void samples(double[] x,double z,long seed,double[] out,long[] floor,double[] fractions){
        int b=(int)Math.floor(z);double v=TerraForge.smooth(z-b);
        for(int i=0;i<x.length;i++){int a=(int)Math.floor(x[i]);floor[i]=a;fractions[i]=TerraForge.smooth(x[i]-a);}
        int end=D.loopBound(x.length),i=0;
        for(;i<end;i+=D.length()){
            var a=LongVector.fromArray(L,floor,i);var u=DoubleVector.fromArray(D,fractions,i);var one=DoubleVector.broadcast(D,1).sub(u);
            var upper=hash(a,b,seed).mul(one).add(hash(a.add(1),b,seed).mul(u));
            var lower=hash(a,b+1,seed).mul(one).add(hash(a.add(1),b+1,seed).mul(u));
            upper.mul(1-v).add(lower.mul(v)).intoArray(out,i);
        }
        for(;i<x.length;i++)out[i]=TerraForge.sample(x[i],z,seed);
    }
    static void generate(TerrainGraph.Node node,float[] output,int n,int sea,long seed,BooleanSupplier stop){
        boolean ridges=node.type==TerrainGraph.Type.RIDGES;int octaves=ridges?5:(int)node.p("detail");double persistence=ridges?.5:node.p("rough"),scale=node.p("scale");long sourceSeed=seed+(long)node.p("seed");
        CpuWork.rows(n,n,stop,z->{double[] x=new double[n],sample=new double[n],sum=new double[n],fractions=new double[n];long[] floor=new long[n];for(int i=0;i<n;i++)x[i]=(i+.5)/n*scale;
            double zz=(z+.5)/n*scale,amp=1,total=0;
            for(int octave=0;octave<octaves;octave++){samples(x,zz,sourceSeed+octave*71,sample,floor,fractions);for(int i=0;i<n;i++){sum[i]+=sample[i]*amp;x[i]*=2;}total+=amp;amp*=persistence;zz*=2;}
            for(int i=0;i<n;i++){double f=sum[i]/total,value=ridges?sea+(Math.pow(1-Math.abs(f*2-1),node.p("sharp"))-.45)*node.p("height")*2:sea+(f-.4)*node.p("height")*2;output[z*n+i]=(float)Math.max(-60,Math.min(1024,value));}
        });
    }
}
