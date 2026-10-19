import java.util.function.BooleanSupplier;
/** Patch coverage and layer thickness are separate, so thin snow does not form a solid white contour. */
final class NaturalSnow {
 static float cover(double h,double slope,double cold,double x,double z,long seed,double line,double transition,double patch){
  double drift=(TerraForge.sample(x/97,z/97,seed+8101)-.5)*transition*.65;
  double progress=EnvironmentNodes.clamp((h-line+transition+drift)/Math.max(1,transition),0,1);
  double retention=1-TerraForge.smooth(EnvironmentNodes.clamp((slope-.35)/1.2,0,1));
  double coverage=TerraForge.smooth(progress)*Math.max(.1,cold)*retention;
  double grain=.75*TerraForge.sample(x/6,z/6,seed+8103)+.25*TerraForge.sample(x/2.3,z/2.3,seed+8105);
  double threshold=grain*(.7+.3*patch);if(coverage<=threshold)return 0;
  int layers=Math.max(1,Math.min(8,(int)Math.round(1+7*progress*retention+(grain-.5)*1.5)));
  return layers/8f;
 }
 static EnvironmentMap apply(TerrainGraph.Node node,EnvironmentMap base,EnvironmentMap climate,int n,int size,int sea,long seed,int ox,int oz,BooleanSupplier stop){
  var e=base.share(base.height);e.snow=new float[n*n];double block=size/(double)n;
  for(int z=0;z<n;z++){EnvironmentNodes.cancel(stop);for(int x=0;x<n;x++){int i=z*n+x;if(base.waterY(i,sea)>Math.round(base.height[i]))continue;double cold=climate==null?1:EnvironmentNodes.clamp((.65-EnvironmentNodes.temperature(climate,i,base.height[i],sea))*3,0,1);e.snow[i]=cover(base.height[i],EnvironmentNodes.slope(base.height,i,n,size)*4/Math.max(1,node.p("slope")),cold,ox+x*block,oz+z*block,seed,node.p("line"),node.p("transition"),node.p("patch"));}}
  return e;
 }
}
