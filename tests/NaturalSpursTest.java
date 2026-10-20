class NaturalSpursTest{
 static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
 public static void main(String[] a){
  double aspect=2,r=1;long seed=321;
  // ring of samples halfway down one flank: spur crests and valleys must alternate with real contrast
  int n=720;double[] ring=new double[n];double min=9,max=-9,maxStep=0;
  for(int i=0;i<n;i++){double along=-1.4+2.8*i/(n-1);ring[i]=OrganicMountainRanges.naturalProfile(along,.55,r,aspect,.5,seed,1.2,.9);min=Math.min(min,ring[i]);max=Math.max(max,ring[i]);if(i>0)maxStep=Math.max(maxStep,Math.abs(ring[i]-ring[i-1]));}
  int peaks=0;for(int i=2;i<n-2;i++)if(ring[i]>ring[i-1]&&ring[i]>=ring[i+1]&&ring[i]-Math.min(ring[i-2],ring[i+2])>.002)peaks++;
  check(max-min>.12,"spur and valley contrast on the flank: "+(max-min));
  check(peaks>=4,"several distinct spur crests along the flank: "+peaks);
  check(maxStep<.05,"spur sides slope instead of forming walls: "+maxStep);
  // spurs descend: the same spur line is lower further from the crest
  double near=0,far=0;for(int i=0;i<200;i++){double along=-1.2+2.4*i/199;near=Math.max(near,OrganicMountainRanges.naturalProfile(along,.3,r,aspect,.5,seed,1.2,.9));far=Math.max(far,OrganicMountainRanges.naturalProfile(along,.9,r,aspect,.5,seed,1.2,.9));}
  check(far<near*.8,"spurs taper toward the foothills: near="+near+" far="+far);
  check(OrganicMountainRanges.naturalProfile(0,3,r,aspect,.5,seed,1.2,.9)==0,"nothing outside the foothill envelope");
  check(OrganicMountainRanges.naturalProfile(.3,.4,r,aspect,.5,seed,1.2,.9)==OrganicMountainRanges.naturalProfile(.3,.4,r,aspect,.5,seed,1.2,.9),"deterministic");
  var node=new TerrainGraph.Node(1,TerrainGraph.Type.MOUNTAIN,0,0);check(node.p("spurStyle")==0,"existing graphs keep the original spur style by default");
  System.out.println("PASS: natural spurs alternate with valleys ("+peaks+" crests, contrast "+Math.round((max-min)*100)+"%), slope smoothly, taper downhill and stay inside the envelope; default style unchanged");
 }
}
