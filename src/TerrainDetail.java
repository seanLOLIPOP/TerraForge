import java.util.function.BooleanSupplier;
final class TerrainDetail {
 static EnvironmentMap apply(TerrainGraph.Node node,EnvironmentMap base,int n,int size,int sea,long seed,double originX,double originZ,BooleanSupplier stop){
  var result=base.share(base.height.clone());
  for(int z=0;z<n;z++){if(stop.getAsBoolean())throw new java.util.concurrent.CancellationException();for(int x=0;x<n;x++){int i=z*n+x;double h=base.height[i],water=base.waterY(i,sea);if(h<=water+1||h>=510)continue;
   double shore=TerraForge.smooth(Math.min(1,Math.max(0,(h-water-1)/10))),summit=TerraForge.smooth(Math.min(1,(510-h)/16));double xx=originX+x*(double)size/n,zz=originZ+z*(double)size/n;long detailSeed=seed+(long)node.p("seed");double scale=node.p("scale");
   double broad=TerraForge.sample(xx/scale,zz/scale,detailSeed+3907)*2-1,fine=TerraForge.sample(xx/(scale*.25),zz/(scale*.25),detailSeed+7283)*2-1;double amplitude=node.p("amount")*shore*summit;if(base.material!=null&&base.material[i]==2)amplitude*=.3;
   result.height[i]=(float)(h+amplitude*(broad*.8+fine*.2));
  }}return result;
 }
}
