/** Distinct summits over a broad, smoothly fading foothill envelope. */
final class MountainPeaks {
 static double profile(double along,double across,double radius,double aspect,double noise,long seed){
  double d=Math.hypot(along/aspect,across)/(radius*(.85+noise*.3));if(d>=1)return 0;
  double foothills=.26*Math.pow(1-TerraForge.smooth(d),1.25),peaks=0;
  for(int k=-1;k<=1;k++){double center=k*radius*aspect*.43,offset=k==0?0:(TerraForge.sample(k+4.2,.7,seed+881)-.5)*radius*.2;double r=Math.hypot((along-center)/(radius*.52),(across-offset)/(radius*.38));double height=k==0?1:k<0?.78:.87;peaks=Math.max(peaks,height*Math.pow(Math.max(0,1-r),1.45));}
  double ridge=.10*Math.pow(Math.max(0,1-Math.abs(across)/(radius*.22)),2)*(1-TerraForge.smooth(Math.min(1,Math.abs(along)/(radius*aspect*.9))));
  return foothills+.64*peaks+ridge;
 }
}
