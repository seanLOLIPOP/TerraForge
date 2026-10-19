import java.util.*;
import java.util.function.BooleanSupplier;

/** Exposed mountain stone and embedded boulder outcrops in the height field. */
final class RockNodes {
 static EnvironmentMap apply(TerrainGraph.Node node,EnvironmentMap base,float[] mask,int n,int size,int sea,long worldSeed,BooleanSupplier stop){
  if(node.p("minY")>node.p("maxY"))throw new IllegalArgumentException("Mountain stone & rocks: minimum elevation exceeds maximum.");
  EnvironmentMap out=base.share(base.height.clone());out.material=EnvironmentNodes.channel(base.material,n*n);
  long seed=worldSeed^((long)node.p("seed")*0x632BE59BD9B4E019L)^node.id*0x9E3779B97F4A7C15L;
  for(int z=0;z<n;z++){EnvironmentNodes.cancel(stop);for(int x=0;x<n;x++){
   int i=z*n+x;if(!eligible(node,base,mask,i,sea))continue;
   double coverage=node.p("stoneCoverage")*(mask==null?1:TerrainGenerator.clamp(mask[i],0,1));
   if(node.p("stoneTransition")>0)coverage*=TerraForge.smooth(TerrainGenerator.clamp((base.height[i]-node.p("minY"))/node.p("stoneTransition"),0,1));
   double noise=TerraForge.sample(x*(double)size/n/18,z*(double)size/n/18,seed);
   if(EnvironmentNodes.slope(base.height,i,n,size)>=node.p("stoneSlope")&&noise<coverage)out.material[i]=3;
  }}
  Random random=new Random(seed);double scale=n/(double)size,spacing=Math.max(node.p("spacing"),node.p("radius")*2+2)*scale;
  for(double gz=spacing*.5;gz<n;gz+=spacing){EnvironmentNodes.cancel(stop);for(double gx=spacing*.5;gx<n;gx+=spacing){
   double cx=gx+(random.nextDouble()-.5)*spacing*.5,cz=gz+(random.nextDouble()-.5)*spacing*.5;
   int x=(int)Math.round(cx),z=(int)Math.round(cz);if(x<0||z<0||x>=n||z>=n)continue;int center=z*n+x;
   double chance=node.p("density")*(mask==null?1:TerrainGenerator.clamp(mask[center],0,1));
   if(random.nextDouble()>chance||chance<=0||!eligible(node,base,mask,center,sea))continue;
   double radius=node.p("radius")*scale*(.7+random.nextDouble()*.6),aspect=.7+random.nextDouble()*.6,relief=node.p("relief")*(.7+random.nextDouble()*.6),angle=random.nextDouble()*Math.PI*2;
   double ground=base.height[center];int extent=(int)Math.ceil(radius*Math.max(1,aspect)*1.2);
   for(int zz=Math.max(0,z-extent);zz<=Math.min(n-1,z+extent);zz++)for(int xx=Math.max(0,x-extent);xx<=Math.min(n-1,x+extent);xx++){
    int i=zz*n+xx;if(!eligible(node,base,mask,i,sea))continue;
    double dx=(xx-cx)/radius,dz=(zz-cz)/radius,u=dx*Math.cos(angle)+dz*Math.sin(angle),v=(-dx*Math.sin(angle)+dz*Math.cos(angle))/aspect;
    double irregular=.88+.2*TerraForge.sample(xx*(double)size/n/3,zz*(double)size/n/3,seed+31),d=(u*u+v*v)/irregular;
    if(d>=1)continue;double top=ground-relief*.3+relief*Math.sqrt(1-d);
    if(top>out.height[i]+.25){out.height[i]=(float)Math.min(1024,top);out.material[i]=3;}
   }
  }}
  return out;
 }
 static boolean eligible(TerrainGraph.Node node,EnvironmentMap base,float[] mask,int i,int sea){return base.height[i]>=node.p("minY")&&base.height[i]<=node.p("maxY")&&base.height[i]>base.waterY(i,sea)&&EnvironmentMap.at(base.ice,i,0)==0&&(mask==null||mask[i]>.001);}
}
