import java.util.function.BooleanSupplier;
/**
 * Realistic mountain surface zones. Blocks are chosen from steepness, height relative to a wandering
 * treeline and snowline, and which way the slope faces, instead of from height alone:
 * subalpine forest floor, alpine meadow, high rocky alpine ground, scree, talus fans below cliffs,
 * banded cliff rock and bare summit ground. Also assigns Minecraft mountain biomes.
 * Slope and aspect come from a smoothed height field, so zones never follow the coarse layout grid.
 */
final class MountainZones {
    static final byte MEADOW=10,GROVE=11,SNOWY_SLOPES=12,JAGGED_PEAKS=13,STONY_PEAKS=14,TAIGA=15;

    /** Box blur (two passes) of the height field with a radius of about five blocks. */
    static float[] smooth(float[] h,int n,double block){
        int r=Math.max(1,(int)Math.round(5/block));
        float[] a=h.clone(),b=new float[h.length];
        for(int pass=0;pass<2;pass++){
            for(int z=0;z<n;z++){double sum=0;int count=0;for(int x=-r;x<=r;x++)if(x>=0&&x<n){sum+=a[z*n+x];count++;}
                for(int x=0;x<n;x++){b[z*n+x]=(float)(sum/count);int out=x-r,in=x+r+1;if(out>=0){sum-=a[z*n+out];count--;}if(in<n){sum+=a[z*n+in];count++;}}}
            for(int x=0;x<n;x++){double sum=0;int count=0;for(int z=-r;z<=r;z++)if(z>=0&&z<n){sum+=b[z*n+x];count++;}
                for(int z=0;z<n;z++){a[z*n+x]=(float)(sum/count);int out=z-r,in=z+r+1;if(out>=0){sum-=b[out*n+x];count--;}if(in<n){sum+=b[in*n+x];count++;}}}
        }
        return a;
    }
    /** Fills e.zoneSlope (rise per block) and e.zoneNorth (+1 facing north, -1 facing south) from smoothed terrain. */
    static void fields(EnvironmentMap e,int n,int size){
        if(e.zoneSlope!=null&&e.zoneSlope.length==n*n&&e.zoneSource==e.height)return;
        double block=size/(double)n;float[] s=smooth(e.height,n,block);
        float[] slope=new float[n*n],north=new float[n*n];
        for(int z=0;z<n;z++)for(int x=0;x<n;x++){
            int i=z*n+x,xl=Math.max(0,x-1),xr=Math.min(n-1,x+1),zu=Math.max(0,z-1),zd=Math.min(n-1,z+1);
            double dx=(s[z*n+xr]-s[z*n+xl])/((xr-xl)*block),dz=(s[zd*n+x]-s[zu*n+x])/((zd-zu)*block),g=Math.hypot(dx,dz);
            slope[i]=(float)g;
            // Minecraft north is -Z: a slope faces north when the ground falls toward -Z, i.e. height rises with +Z.
            north[i]=(float)(g<1e-4?0:dz/g*Math.min(1,g/.15));
        }
        e.zoneSlope=slope;e.zoneNorth=north;e.zoneSource=e.height;
    }
    static double treeline(double base,double x,double z,long seed,double north){
        return base+(TerraForge.sample(x/640,z/640,seed+7101)-.5)*130+(TerraForge.sample(x/150,z/150,seed+7103)-.5)*45-north*50;
    }
    static double snowline(double base,double x,double z,long seed,double north){
        return base+(TerraForge.sample(x/720,z/720,seed+7105)-.5)*150+(TerraForge.sample(x/95,z/95,seed+7107)-.5)*40-north*120;
    }
    static boolean rock(int m){return m==3||m==7||m==11||m==12||m==17||m==18||m==19||m==20;}
    /** Banded cliff rock following tilted strata, as on the exported cliff faces. */
    /** Bare rock as irregular patches: mostly stone and andesite, with tuff patches, a regional granite or diorite, and rare calcite.
     *  Height only drifts the pattern slowly, so stepped slopes never line up into contour stripes. */
    static int rockPatch(double h,double x,double z,long seed){
        double drift=h/90.0,w=(TerraForge.sample(x/60,z/60,seed+6203)-.5)*30;
        double r=.65*TerraForge.sample((x+w)/34+drift,(z-w)/34,seed+6209)+.35*TerraForge.sample(x/11,z/11+drift,seed+6213);
        if(r<.42)return 3;
        if(r<.6)return 12;
        if(r<.7)return 19;
        if(r<.76)return TerraForge.sample(x/520,z/520,seed+6211)>.5?18:17;
        if(r>.9)return 20;
        return 3;
    }
    static int cliff(double h,double x,double z,long seed){return rockPatch(h,x,z,seed);}
    static int scree(double f,boolean high){
        if(high)return f<.35?3:f<.6?7:f<.8?12:20;
        return f<.32?7:f<.52?12:f<.72?11:3;
    }
    static EnvironmentMap apply(TerrainGraph.Node node,EnvironmentMap base,int n,int size,int sea,long seed,int ox,int oz,BooleanSupplier stop){
        var e=base.share(base.height);
        e.material=EnvironmentNodes.channel(base.material,n*n);
        e.biome=EnvironmentNodes.channel(base.biome,n*n);
        fields(e,n,size);
        double block=size/(double)n,treelineBase=node.p("treeline"),snowBase=node.p("snowline");
        float[] slope=e.zoneSlope,north=e.zoneNorth;
        double glaciers=node.values.getOrDefault("glaciers",0.0);
        float[] bowl=null;
        if(glaciers>0){// how far each point sits below its wider surroundings: cirques and glacial valleys
            if(base.zoneBowl!=null&&base.zoneBowl.length==n*n)bowl=base.zoneBowl;/* whole-map measure on large builds */
            else{float[] near=smooth(base.height,n,block),wide=smooth(base.height,n,block/7);bowl=new float[n*n];
            for(int i=0;i<n*n;i++)bowl[i]=wide[i]-near[i];}
            e.glacier=EnvironmentNodes.channel(base.glacier,n*n);
        }
        // Talus: loose rock spreads downhill from cliff bases for up to ~16 blocks, thinning with distance.
        float[] talus=new float[n*n];
        for(int i=0;i<n*n;i++)if(slope[i]>1.15&&base.height[i]>sea+20)talus[i]=1;
        int steps=Math.max(1,(int)Math.round(16/block));float decay=(float)(1.0/steps);
        for(int k=0;k<steps;k++){
            EnvironmentNodes.cancel(stop);
            float[] next=talus.clone();
            for(int z=1;z<n-1;z++)for(int x=1;x<n-1;x++){
                int i=z*n+x;if(talus[i]>=1)continue;
                float best=0;
                for(int j:new int[]{i-1,i+1,i-n,i+n})if(talus[j]>0&&base.height[j]>=base.height[i]-.2f)best=Math.max(best,talus[j]-decay);
                if(best>next[i])next[i]=best;
            }
            talus=next;
        }
        for(int z=0;z<n;z++){
            EnvironmentNodes.cancel(stop);
            for(int x=0;x<n;x++){
                int i=z*n+x;double h=base.height[i];
                if(h<sea+12||base.waterY(i,sea)>Math.round(h)||EnvironmentMap.at(base.river,i,0)>.02)continue;
                int old=e.material[i];
                if(old==2||old==5||old==6||old==9)continue;
                double wx=ox+x*block,wz=oz+z*block,sl=slope[i],no=north[i];
                double tl=treeline(treelineBase,wx,wz,seed,no),sn=snowline(snowBase,wx,wz,seed,no),rel=h-tl;
                double wet=EnvironmentMap.at(base.rain,i,.6f)-.6,p=TerraForge.sample(wx/42,wz/42,seed+7121),d=TerraForge.sample(wx/9,wz/9,seed+7123),f=TerraForge.sample(wx/2.3,wz/2.3,seed+7125);
                int mat=old;
                if(bowl!=null&&sl<.55){// glaciers fill shaded high bowls and reach down north-facing valleys
                    double margin=(TerraForge.sample(wx/60,wz/60,seed+7141)-.5)*50,ice=(h-(sn-40+margin))/60+no*1.2+bowl[i]/8-sl*1.5;
                    if(ice>.6*(1.4-glaciers)){e.material[i]=6;e.glacier[i]=(float)Math.min(24,4+ice*6);e.biome[i]=SNOWY_SLOPES;continue;}
                }
                double local=EnvironmentNodes.slope(base.height,i,n,size);
                if(old==3&&local>.75&&sl<1.15){// boulders and outcrops keep their rock; mossy in damp woods
                    mat=rel<-40&&wet>-.15&&d>.45?13:f<.4?3:f<.65?12:f<.85?18:11;
                }
                else if(sl>1.15-(rel>0?.15:0))mat=cliff(h,wx,wz,seed);
                else if(talus[i]>0&&talus[i]>f*.9+.05&&sl<1.15)mat=scree(f,h>sn);
                else if(sl>.72&&rel>-90)mat=scree(f,h>sn);
                else if(h>sn-40)mat=d>.82?20:p<.3?7:f<.5?3:f<.75?12:7;
                else if(rel>170){
                    double grass=.35-(rel-170)/500+(.35-sl)*.6-no*.1+wet*.6;
                    mat=p<grass&&d>.35?(d>.85?16:1):(f<.3?7:f<.55?12:f<.85?3:19);
                }
                else if(rel>0){
                    double rockChance=rel/170*.45+Math.max(0,sl-.3)*.9+(no>0?.06:0)-wet*.6;
                    if(TerraForge.sample(wx/17,wz/17,seed+7127)<rockChance)mat=f<.35?7:f<.6?3:f<.8?12:11;
                    else mat=d<.14-wet*.3?10:d>.84-wet*.5&&no>-.2?16:f>.94?3:1;
                }
                else if(rel>-150){
                    if(sl>.5&&TerraForge.sample(wx/17,wz/17,seed+7127)<(sl-.5)*1.6)mat=f<.4?3:f<.7?12:10;
                    else{double litter=TerraForge.sample(wx/16,wz/16,seed+7131)*.65+d*.35-(rel+150)/150*.25;mat=litter>.62-wet*.3?(f<.75+wet?14:10):d<.12-wet*.4?10:d>.88-wet*.6&&no>-.3?16:1;}
                }
                else if(rock(old)&&sl<.6)mat=d<.25?10:1;
                e.material[i]=(byte)mat;
                // Mountain biomes, so grass colours, snow behaviour and mobs match the zone.
                if(h>sn)e.biome[i]=sl>.9?JAGGED_PEAKS:SNOWY_SLOPES;
                else if(rel>170)e.biome[i]=no>.3&&h>sn-120?SNOWY_SLOPES:STONY_PEAKS;
                else if(rel>0)e.biome[i]=MEADOW;
                else if(rel>-150)e.biome[i]=h>sn-260?GROVE:TAIGA;
            }
        }
        return e;
    }
}
