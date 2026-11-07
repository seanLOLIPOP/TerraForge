import java.util.List;
import java.util.function.BooleanSupplier;

/** Rasterize the final drainage-first centerline, in either layout or direct-build coordinates. */
final class RiverChannels {
    static void carve(EnvironmentMap e,float[] original,List<RiverRibbon.Segment> segments,int first,int n,int size,int sea,BooleanSupplier stop){
        double block=size/(double)n;
        for(int k=first;k<segments.size();k++){
            EnvironmentNodes.cancel(stop);var s=segments.get(k);var a=s.a();var b=s.b();
            double ax=a.x()/block,az=a.z()/block,bx=b.x()/block,bz=b.z()/block,dx=bx-ax,dz=bz-az,len2=Math.max(.000001,dx*dx+dz*dz);
            double pad=Math.max(.65,Math.max(a.radius(),b.radius())/block)*3+1;
            int x0=Math.max(0,(int)Math.floor(Math.min(ax,bx)-pad)),x1=Math.min(n-1,(int)Math.ceil(Math.max(ax,bx)+pad));
            int z0=Math.max(0,(int)Math.floor(Math.min(az,bz)-pad)),z1=Math.min(n-1,(int)Math.ceil(Math.max(az,bz)+pad));
            for(int z=z0;z<=z1;z++)for(int x=x0;x<=x1;x++){
                int i=z*n+x;if(original[i]<=sea-1||e.lakeMask!=null&&e.lakeMask[i]!=0)continue;
                double t=Math.max(0,Math.min(1,((x-ax)*dx+(z-az)*dz)/len2));
                double radius=Math.max(.65,(a.radius()+(b.radius()-a.radius())*t)/block),distance=Math.hypot(x-ax-dx*t,z-az-dz*t),bank=radius*2+1;
                if(distance>radius+bank)continue;
                float water=(float)Math.max(sea,Math.floor(a.water()+(b.water()-a.water())*t));
                if(water<=sea)continue;
                if(distance<=radius){
                    double f=distance/radius,depth=Math.max(1,s.depth()*(1-f*f));
                    boolean reservoir=e.water[i]>original[i]+s.depth()+2;
                    e.height[i]=(float)Math.min(e.height[i],water-depth);
                    if(!reservoir)e.water[i]=e.river[i]>.05?Math.min(e.water[i],water):Math.max(e.water[i],water);
                    e.river[i]=(float)Math.max(e.river[i],1-f*.5);e.material[i]=7;if(e.snow!=null)e.snow[i]=0;
                }else if(original[i]>e.waterY(i,sea)){
                    double blend=1-TerraForge.smooth((distance-radius)/bank),target=water+.7+(distance-radius)*block*.22;
                    e.height[i]=(float)(e.height[i]+Math.min(0,target-e.height[i])*blend);
                    if(distance<radius*1.5)e.material[i]=7;
                }
            }
        }
    }
}
