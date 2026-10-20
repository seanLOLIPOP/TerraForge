import java.util.*;
class VisibleRiverBranchesTest {
 public static void main(String[] args) {
  int n=256,size=4096;float[] h=new float[n*n],spill=new float[n*n];
  for(int z=0;z<n;z++)for(int x=0;x<n;x++)h[z*n+x]=spill[z*n+x]=(float)(500-x*.32);
  var base=new RiverRibbon.Network(List.of());var space=new RiverBranchSpace(base);
  var builder=new MountainRiverForks.Builder(h,spill,n,size,62,.18,.28,83,base,()->false,space,2);
  var tree=builder.grow(new RiverRibbon.Point(800,2048,3.5,484),0,1100,3.5,4);
  if(tree==null||tree.left==null||tree.right==null)throw new AssertionError("Both diverging branches must remain visible");
  double rootLength=(tree.points.size()-1)*8;
  if(rootLength>320)throw new AssertionError("First split is too late");
  var segments=new ArrayList<RiverRibbon.Segment>();var arms=new ArrayList<MountainRiverForks.Arm>();var tails=new ArrayList<RiverRibbon.SideBranch>();var forks=new ArrayList<MountainRiverForks.Fork>();
  MountainRiverForks.emit(tree,0,segments,arms,tails,forks);
  double minWidth=100,maxLength=0;int deepest=0;
  for(var arm:arms){double length=0;deepest=Math.max(deepest,arm.generation());for(var p:arm.points())minWidth=Math.min(minWidth,p.radius()*2);for(int i=1;i<arm.points().size();i++)length+=Math.hypot(arm.points().get(i).x()-arm.points().get(i-1).x(),arm.points().get(i).z()-arm.points().get(i-1).z());maxLength=Math.max(maxLength,length);}
  if(minWidth<3.3||deepest<2||maxLength<384)throw new AssertionError("Visible width, repeated forks or long branches missing: "+minWidth+" "+deepest+" "+maxLength);
  if(RiverCrossingAudit.count(new RiverRibbon.Network(segments))!=0)throw new AssertionError("Branches cross");
  System.out.println("PASS: early visible forks, recursive depth="+deepest+", minimum wet width="+minWidth+", longest arm="+maxLength+", no crossings");
 }
}
