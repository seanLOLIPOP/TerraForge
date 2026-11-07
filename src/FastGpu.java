import java.util.function.BooleanSupplier;

/** Explicit fast algorithms; hydraulic and distance results differ from legacy CPU algorithms. */
final class FastGpu {
    private static final ThreadLocal<Boolean> OVERRIDE=new ThreadLocal<>();
    static boolean requested(){return OVERRIDE.get()!=null?OVERRIDE.get():Boolean.getBoolean("terraforge.fastGpu");}
    static boolean enabled(){return GpuGeneration.enabled()&&requested();}
    static Scope scope(boolean value){return new Scope(value);}
    static final class Scope implements AutoCloseable {final Boolean previous=OVERRIDE.get();Scope(boolean value){OVERRIDE.set(value);}public void close(){if(previous==null)OVERRIDE.remove();else OVERRIDE.set(previous);}}
    static float[] erosion(float[] input,int n,int size,int sea,TerrainGraph.Node node,long seed,BooleanSupplier stop){
        if(!enabled()||node.p("iterations")<=0)return null;
        String body="""
            if(x==0||z==0||x==n-1||z==n-1||a<=sea){value=a;}else{
              double low=fmin(fmin(A[i-1],A[i+1]),fmin(A[i-n],A[i+n]));
              double cut=fmin(fmax(0.0,a-low)*params[0],fmax(0.0,a-fmax(sea,B[i]-params[1])));
              double incoming=0;int neighbours[4]={i-1,i+1,i-n,i+n};
              for(int t=0;t<4;t++){int j=neighbours[t],xx=j%n,zz=j/n;if(xx==0||zz==0||xx==n-1||zz==n-1||A[j]<=sea)continue;
                double bottom=fmin(fmin(A[j-1],A[j+1]),fmin(A[j-n],A[j+n]));
                double amount=fmin(fmax(0.0,A[j]-bottom)*params[0],fmax(0.0,A[j]-fmax(sea,B[j]-params[1])));
                if(a==bottom)incoming+=amount*params[2]*.25;
              }
              value=clamp(a-cut+incoming,-60.0,1024.0);
            }
            """;
        return GpuGeneration.iterate("fast-hydraulic-v1",body,new double[]{Math.min(.2,.035*node.p("strength")),node.p("maxDepth"),node.p("deposition")},input,input,n,size,sea,seed,Math.max(1,Math.min(256,(int)(node.p("iterations")*4))),false,stop);
    }
    static float[] distance(float[] seeds,int n,BooleanSupplier stop){
        if(!enabled())return null;
        String body="""
            int best=(int)a;double distance=1.0e30;
            if(best>=0){double dx=x-best%n,dz=z-best/n;distance=dx*dx+dz*dz;}
            int step=(int)params[0];for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){
              int xx=x+dx*step,zz=z+dz*step;if(xx<0||zz<0||xx>=n||zz>=n)continue;int candidate=(int)A[zz*n+xx];if(candidate<0)continue;
              double cx=x-candidate%n,cz=z-candidate/n,d=cx*cx+cz*cz;
              if(d<distance||(d==distance&&candidate<best)){best=candidate;distance=d;}
            }value=best;
            """;
        int passes=32-Integer.numberOfLeadingZeros(n-1)+2;
        float[] nearest=GpuGeneration.iterate("jump-flood-v1",body,new double[]{n/2.0},seeds,seeds,n,n,0,0,passes,true,stop);
        if(nearest==null)return null;float[] distance=new float[nearest.length];CpuWork.rows(n,n,stop,z->{for(int x=0;x<n;x++){int i=z*n+x,site=(int)nearest[i];distance[i]=site<0?Float.POSITIVE_INFINITY:(float)Math.hypot(x-site%n,z-site/n);}});return distance;
    }
}
