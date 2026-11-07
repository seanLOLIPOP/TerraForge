/** Shared CPU basin profile; the GPU lake kernel mirrors this arithmetic. */
final class RealisticLakes {
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
