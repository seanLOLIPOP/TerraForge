/** Shared CPU basin profile; the GPU lake kernel mirrors this arithmetic. */
final class RealisticLakes {
    static double terrainDistance(TerrainGraph.Node node,double d,double height,double level,double depth){
        double follow=node.p("valleyFollow");
        double low=TerraForge.smooth(EnvironmentNodes.clamp((level+12-height)/(depth+24),0,1));
        double high=TerraForge.smooth(EnvironmentNodes.clamp((height-level-4)/node.p("ridgeCut"),0,1));
        d*=1-follow*.35*low+follow*.85*high;
        if(height>level+node.p("ridgeCut")/Math.max(.1,follow)&&d>.25)d=Math.max(d,1.35);
        return d;
    }
    static void banks(TerrainGraph.Node node,EnvironmentMap e,EnvironmentMap base,float[] distance,int n,int size,int sea,double level,long seed,java.util.function.BooleanSupplier stop){
        RiverBanks.spread(distance,null,n,stop);
        e.lakeBank=base.lakeBank==null?new byte[n*n]:base.lakeBank.clone();
        double spacing=size/(double)n,reach=node.p("bankReach"),amount=node.p("bankVariety");
        CpuWork.rows(n,n,stop,z->{for(int x=0;x<n;x++){
            int i=z*n+x;double blocks=distance[i]*spacing;
            if(blocks<=0||blocks>reach||e.height[i]<=e.waterY(i,sea)||EnvironmentMap.at(e.river,i,0)>.05)continue;
            double slope=EnvironmentNodes.slope(base.height,i,n,size),wx=base.originX+x*spacing,wz=base.originZ+z*spacing;
            double patch=TerraForge.sample(wx/130,wz/130,seed+node.id*163L),detail=TerraForge.sample(wx/27,wz/27,seed+node.id*167L);
            double gentle=TerraForge.smooth(EnvironmentNodes.clamp((.8-slope)/.65,0,1));
            // Restore steep faces that the raw basin rim might otherwise round off.
            boolean ridge=node.p("valleyFollow")>0&&base.height[i]>level+node.p("ridgeCut")/Math.max(.1,node.p("valleyFollow"));
            if(slope>=.8||ridge)e.height[i]=Math.max(e.height[i],base.height[i]);
            else{
                double target=level+.65+blocks*node.p("bankSlope")*(.35+.9*patch);
                double blend=(1-TerraForge.smooth(blocks/reach))*amount*gentle;
                e.height[i]=(float)Math.max(level+.5,e.height[i]+Math.min(0,target-e.height[i])*blend);
            }
            if(amount<=0||blocks>Math.min(reach,10+24*patch)||EnvironmentMap.at(base.glacier,i,0)>0||EnvironmentMap.at(base.ice,i,0)>0||EnvironmentMap.at(base.snow,i,0)>=.2||base.material!=null&&(base.material[i]==6||base.material[i]==9))continue;
            int material=ridge||slope>.7?3:slope>.24?7:EnvironmentMap.at(base.rain,i,.6f)>.65&&patch<.45?(detail<.55?5:10):patch>.7?7:2;
            e.material[i]=(byte)material;e.lakeBank[i]=(byte)material;
        }});
    }
    static void finishMaterials(EnvironmentMap e,int sea,java.util.function.BooleanSupplier stop){
        if(e.lakeBank==null||e.material==null)return;
        for(int i=0;i<e.height.length;i++){
            if((i&4095)==0)EnvironmentNodes.cancel(stop);
            if(e.lakeBank[i]!=0&&e.waterY(i,sea)<=Math.round(e.height[i])&&EnvironmentMap.at(e.river,i,0)<.05&&EnvironmentMap.at(e.snow,i,0)<.2&&EnvironmentMap.at(e.ice,i,0)==0&&EnvironmentMap.at(e.glacier,i,0)==0&&e.material[i]!=6&&e.material[i]!=9)e.material[i]=e.lakeBank[i];
        }
    }
    /** Keep the new footprint connected; noisy coves must not leave accidental isolated puddles. */
    static void connect(EnvironmentMap e,EnvironmentMap base,float[] distance,int n,int sea,double cx,double cz,java.util.function.BooleanSupplier stop){
        int wet=0,start=-1;double best=Double.POSITIVE_INFINITY;
        for(int i=0;i<distance.length;i++){
            if(i%n==0)EnvironmentNodes.cancel(stop);
            if(distance[i]!=0)continue;wet++;double d=Math.hypot(i%n-cx,i/n-cz);
            if(d<best){best=d;start=i;}
        }
        if(start<0)return;
        int[] queue=new int[wet];int read=0,write=0;queue[write++]=start;distance[start]=-1;
        while(read<write){
            if((read&2047)==0)EnvironmentNodes.cancel(stop);
            int i=queue[read++],x=i%n,z=i/n;
            for(int direction=0;direction<4;direction++){
                int j=switch(direction){case 0->x>0?i-1:-1;case 1->x<n-1?i+1:-1;case 2->z>0?i-n:-1;default->z<n-1?i+n:-1;};
                if(j>=0&&distance[j]==0){distance[j]=-1;queue[write++]=j;}
            }
        }
        for(int i=0;i<distance.length;i++){
            if(i%n==0)EnvironmentNodes.cancel(stop);
            if(distance[i]<0)distance[i]=0;
            else if(distance[i]==0){distance[i]=Float.POSITIVE_INFINITY;e.height[i]=base.height[i];e.water[i]=EnvironmentMap.at(base.water,i,sea);e.material[i]=base.material==null?0:base.material[i];}
        }
    }
    static double bed(TerrainGraph.Node node,double d,double dx,double dz,double level,double depth,long seed){
        double inward=Math.max(0,1-d),shelf=node.p("shallows");
        double shallow=1.2+2.5*Math.min(1,inward/shelf);
        double deep=Math.pow(TerraForge.smooth(EnvironmentNodes.clamp((inward-shelf)/(1-shelf),0,1)),.8);
        double variation=TerraForge.sample(dx*1.7+71,dz*1.7+29,seed+node.id*149L);
        double cut=shallow+Math.max(0,depth-shallow)*deep*(1-node.p("bedVariation")*.45+node.p("bedVariation")*.6*variation);
        return level-Math.min(depth,cut);
    }
}
