/** Two parallel passes; gather incoming material in the original row-major floating-point order. */
final class ThermalErosion {
    static void apply(TerraForge.World world,int iterations){
        Thread owner=Thread.currentThread();apply(world,iterations,owner::isInterrupted);
    }
    static void apply(TerraForge.World world,int iterations,java.util.function.BooleanSupplier stop){
        if(iterations<=0)return;
        if(GpuGeneration.thermal(world.height,world.size,iterations,stop))return;
        int n=world.size;float[] height=world.height,amount=new float[n*n];byte[] flow=new byte[n*n];
        for(int pass=0;pass<iterations;pass++){
            CpuWork.rows(n,n,stop,z->{
                if(z==0||z==n-1)return;
                for(int x=1;x<n-1;x++){
                    int i=z*n+x;float drop=2;byte direction=0;
                    float d=height[i]-height[i-1];if(d>drop){drop=d;direction=1;}
                    d=height[i]-height[i+1];if(d>drop){drop=d;direction=2;}
                    d=height[i]-height[i-n];if(d>drop){drop=d;direction=3;}
                    d=height[i]-height[i+n];if(d>drop){drop=d;direction=4;}
                    flow[i]=direction;amount[i]=direction==0?0:(drop-2)*.22f;
                }
            });
            CpuWork.rows(n,n,stop,z->{
                for(int x=0;x<n;x++){
                    int i=z*n+x;float delta=0;
                    if(z>0&&flow[i-n]==4)delta+=amount[i-n];
                    if(x>0&&flow[i-1]==2)delta+=amount[i-1];
                    if(flow[i]!=0)delta-=amount[i];
                    if(x+1<n&&flow[i+1]==1)delta+=amount[i+1];
                    if(z+1<n&&flow[i+n]==3)delta+=amount[i+n];
                    height[i]+=delta;
                }
            });
        }
    }
}
