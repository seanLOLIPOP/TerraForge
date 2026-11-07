import java.util.Arrays;
import java.util.function.BooleanSupplier;

/** Opt-in coastal profile. Regional builds shape the shared layout before sampling tiles. */
final class NaturalShorelines {
    static EnvironmentMap apply(TerrainGraph.Node node,EnvironmentMap b,int n,int size,int sea,long seed,BooleanSupplier stop){
        var near=EnvironmentNodes.nearWater(b,n,sea,stop,node.p("oceanOnly")>.5);
        float[] land=new float[n*n],levels=new float[n*n];Arrays.fill(land,Float.POSITIVE_INFINITY);
        boolean anyLand=false;
        for(int i=0;i<land.length;i++)if(b.height[i]>=b.waterY(i,sea)){land[i]=0;anyLand=true;}
        if(anyLand)RiverBanks.spread(land,levels,n,stop);
        var e=b.share(b.height.clone());e.material=EnvironmentNodes.channel(b.material,n*n);
        double spacing=size/(double)n;
        CpuWork.rows(n,n,stop,z->{for(int x=0;x<n;x++){
            int i=z*n+x;double h=b.height[i],level=b.waterY(i,sea),wx=b.originX+x*spacing,wz=b.originZ+z*spacing;
            if(node.p("oceanOnly")>.5&&level>sea+1||EnvironmentMap.at(b.river,i,0)>.05)continue;
            double noise=TerraForge.sample(wx/190,wz/190,seed+73391),grain=TerraForge.sample(wx/23,wz/23,seed+73397);
            double slope=EnvironmentNodes.slope(b.height,i,n,size),width=node.p("width")*(1+node.p("variation")*(noise*1.6-.8));
            width*=EnvironmentNodes.clamp(1.35-slope*1.5,.15,1.35);
            int style=(int)node.p("shoreType");
            int mat=style==1?2:style==2?7:style==3?3:slope>.8?3:slope>.28||grain>1-node.p("roughness")*.5?7:2;
            if(h>level){
                double distance=near.distance()[i]*spacing;
                if(distance>width||h>near.level()[i]+node.p("rise")+width*.5)continue;
                // Preserve cliffs; gently grade low beaches with zero change at the inland edge.
                double t=EnvironmentNodes.clamp(distance/width,0,1),blend=(1-TerraForge.smooth(t))*(mat==3?0:.65);
                double target=near.level()[i]+.75+node.p("rise")*t;
                e.height[i]=(float)Math.max(near.level()[i]+.5,h+(target-h)*blend);e.material[i]=(byte)mat;
            }else if(h<level){
                double t=land[i]*spacing/node.p("shelfWidth");
                if(t>=1||!Double.isFinite(t))continue;
                double shelf=level-1-node.p("shelfDepth")*TerraForge.smooth(t);
                double blend=(1-TerraForge.smooth(t))*(mat==3?.15:1);
                e.height[i]=(float)Math.min(level-1,h+Math.max(0,shelf-h)*blend);e.material[i]=(byte)mat;
            }
        }});
        return e;
    }
}
