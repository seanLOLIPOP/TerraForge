import java.util.*;import java.util.function.BooleanSupplier;
/** Removes artificial vertical ledges in river valleys without changing water or distant terrain. */
final class GroundSmoothing {
 static long apply(EnvironmentMap e,int n,int size,int sea,double reach,BooleanSupplier stop){
  if(e.water==null||e.river==null)return 0;float[] distance=new float[n*n];Arrays.fill(distance,Float.POSITIVE_INFINITY);boolean found=false;
  for(int i=0;i<distance.length;i++)if(e.river[i]>.2&&e.waterY(i,sea)>Math.round(e.height[i])){distance[i]=0;found=true;}if(!found)return 0;RiverBanks.spread(distance,null,n,stop);float[] original=e.height.clone();double block=size/(double)n;long updates=0;
  for(int pass=0;pass<8;pass++){boolean changed=false;for(int row=0;row<n;row++){EnvironmentNodes.cancel(stop);int z=(pass&1)==0?row:n-1-row;for(int col=0;col<n;col++){int x=(pass&1)==0?col:n-1-col,i=z*n+x;double d=distance[i]*block;if(d<=0||d>=reach||e.waterY(i,sea)>Math.round(e.height[i]))continue;double t=TerraForge.smooth(d/reach),rise=(.55+1.45*t*t*t*t)*block,limit=e.height[i],floor=-60;
   if(x>0){int j=i-1;boolean wet=e.waterY(j,sea)>Math.round(e.height[j]);limit=Math.min(limit,(wet?e.waterY(j,sea)+1:e.height[j])+rise);if(wet)floor=Math.max(floor,e.waterY(j,sea)+.6);}
   if(x+1<n){int j=i+1;boolean wet=e.waterY(j,sea)>Math.round(e.height[j]);limit=Math.min(limit,(wet?e.waterY(j,sea)+1:e.height[j])+rise);if(wet)floor=Math.max(floor,e.waterY(j,sea)+.6);}
   if(z>0){int j=i-n;boolean wet=e.waterY(j,sea)>Math.round(e.height[j]);limit=Math.min(limit,(wet?e.waterY(j,sea)+1:e.height[j])+rise);if(wet)floor=Math.max(floor,e.waterY(j,sea)+.6);}
   if(z+1<n){int j=i+n;boolean wet=e.waterY(j,sea)>Math.round(e.height[j]);limit=Math.min(limit,(wet?e.waterY(j,sea)+1:e.height[j])+rise);if(wet)floor=Math.max(floor,e.waterY(j,sea)+.6);}
   double fade=1-TerraForge.smooth(d/reach);float height=(float)Math.max(original[i]-8*fade,Math.max(floor,limit));if(height<e.height[i]-.001){e.height[i]=height;changed=true;updates++;}
  }}if(!changed)break;}
  return updates;
 }
}
