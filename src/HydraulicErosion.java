import java.util.Random;
import java.util.function.BooleanSupplier;
/**
 * Water erosion by simulated rain droplets. Each droplet runs downhill with a little momentum, picks up
 * sediment where it speeds up on steep ground (branching gullies and V-shaped valleys) and drops it where it
 * slows (valley floors, fans at mountain fronts, small deltas at the coast). Heights are handled in units of
 * one grid cell, so a 2048 layout of a 16384 map and a 1024 preview give the same landforms.
 */
final class HydraulicErosion {
    static float[] apply(float[] input,int n,int worldSize,int sea,TerrainGraph.Node node,long worldSeed,BooleanSupplier stop){
        double cell=worldSize/(double)n;
        double droplets=node.p("iterations")/10.0,erodeSpeed=.035*node.p("strength"),depositSpeed=.05+.4*node.p("deposition"),maxCut=node.p("maxDepth")/cell;
        int count=n*n;
        double[] h=new double[count];
        for(int i=0;i<count;i++)h[i]=input[i]/cell;
        double seaLevel=sea/cell;
        // erosion brush: a soft disc about two cells across
        int radius=2;java.util.List<int[]> offsets=new java.util.ArrayList<>();java.util.List<Double> weights=new java.util.ArrayList<>();double total=0;
        for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++){double d=Math.hypot(dx,dz);if(d<=radius){offsets.add(new int[]{dx,dz});double w=radius-d+.25;weights.add(w);total+=w;}}
        int brushSize=offsets.size();int[] bx=new int[brushSize],bz=new int[brushSize];double[] bw=new double[brushSize];
        for(int k=0;k<brushSize;k++){bx[k]=offsets.get(k)[0];bz[k]=offsets.get(k)[1];bw[k]=weights.get(k)/total;}
        int land=0;for(int i=0;i<count;i++)if(h[i]>seaLevel+.05)land++;
        long drops=Math.round(land*droplets);
        Random random=new Random(worldSeed^0x5EEDL^node.id*0x9E3779B97F4A7C15L);
        double inertia=.12,capacityFactor=6,minSlope=.004,gravity=4,evaporate=.025;int lifetime=60;
        for(long d=0;d<drops;d++){
            if((d&0x3FFF)==0&&stop.getAsBoolean())throw new java.util.concurrent.CancellationException();
            double x=random.nextDouble()*(n-3)+1,z=random.nextDouble()*(n-3)+1;
            if(h[(int)z*n+(int)x]<=seaLevel+.05)continue;
            double dirX=0,dirZ=0,speed=1,water=1,sediment=0;
            for(int step=0;step<lifetime;step++){
                int cx=(int)x,cz=(int)z;double fx=x-cx,fz=z-cz;int i=cz*n+cx;
                double h00=h[i],h10=h[i+1],h01=h[i+n],h11=h[i+n+1];
                double gx=(h10-h00)*(1-fz)+(h11-h01)*fz,gz=(h01-h00)*(1-fx)+(h11-h10)*fx;
                double height=h00*(1-fx)*(1-fz)+h10*fx*(1-fz)+h01*(1-fx)*fz+h11*fx*fz;
                if(height<=seaLevel){// reached the sea: leave part of the load as a shallow delta
                    deposit(h,n,i,fx,fz,Math.min(sediment*.5,Math.max(0,seaLevel-.25-height)));
                    break;
                }
                dirX=dirX*inertia-gx*(1-inertia);dirZ=dirZ*inertia-gz*(1-inertia);
                double len=Math.hypot(dirX,dirZ);
                if(len<1e-9){double a=random.nextDouble()*Math.PI*2;dirX=Math.cos(a);dirZ=Math.sin(a);len=1;}
                dirX/=len;dirZ/=len;
                double nx=x+dirX,nz=z+dirZ;
                if(nx<1||nz<1||nx>=n-2||nz>=n-2)break;
                int ncx=(int)nx,ncz=(int)nz;double nfx=nx-ncx,nfz=nz-ncz;int j=ncz*n+ncx;
                double newHeight=h[j]*(1-nfx)*(1-nfz)+h[j+1]*nfx*(1-nfz)+h[j+n]*(1-nfx)*nfz+h[j+n+1]*nfx*nfz;
                double dh=newHeight-height;
                double capacity=Math.max(-dh,minSlope)*speed*water*capacityFactor;
                if(sediment>capacity||dh>0){
                    double amount=dh>0?Math.min(dh,sediment):(sediment-capacity)*depositSpeed;
                    sediment-=amount;deposit(h,n,i,fx,fz,amount);
                }else{
                    double amount=Math.min((capacity-sediment)*erodeSpeed,-dh);
                    for(int k=0;k<brushSize;k++){
                        int xx=cx+bx[k],zz=cz+bz[k];if(xx<0||zz<0||xx>=n||zz>=n)continue;int t=zz*n+xx;
                        if(h[t]<=seaLevel)continue;
                        double floor=input[t]/cell-maxCut,take=Math.min(amount*bw[k],Math.max(0,h[t]-floor));
                        h[t]-=take;sediment+=take;
                    }
                }
                speed=Math.sqrt(Math.max(0,speed*speed-dh*gravity));
                if(speed>8)speed=8;
                water*=1-evaporate;
                x=nx;z=nz;
            }
        }
        float[] out=new float[count];
        for(int i=0;i<count;i++){out[i]=(float)(h[i]*cell);if(input[i]<=sea)out[i]=Math.min(out[i],Math.max(input[i],sea-.6f));}/* deltas stay under water */
        return out;
    }
    static void deposit(double[] h,int n,int i,double fx,double fz,double amount){
        if(amount<=0)return;
        h[i]+=amount*(1-fx)*(1-fz);h[i+1]+=amount*fx*(1-fz);h[i+n]+=amount*(1-fx)*fz;h[i+n+1]+=amount*fx*fz;
    }
}
