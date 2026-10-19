import java.util.*;
/** Generation-time water budgets and relative open-channel conveyance, in normalized runoff units. */
final class RiverFlow {
 static final double REFERENCE_DISCHARGE=131072;
 record Field(double[] discharge,double supplied,double terminal,double residual){}
 record Arm(double width,double depth,double slope,double roughness){
  Arm{if(!Double.isFinite(width)||!Double.isFinite(depth)||!Double.isFinite(slope)||!Double.isFinite(roughness)||width<0||depth<0||slope<0||roughness<=0)throw new IllegalArgumentException("Invalid river-arm geometry");}
  double conveyance(){if(width==0||depth==0||slope==0)return 0;double area=width*depth,radius=area/(width+2*depth);return area*Math.pow(radius,2./3)*Math.sqrt(slope)/roughness;}
 }
 record Split(double incoming,double left,double right,double leftWidth,double rightWidth,double leftDepth,double rightDepth){
  double residual(){return incoming-left-right;}
  double leftFraction(){return incoming>0?left/incoming:0;}
 }
 record BranchPlan(Split flow,double water,double radius,double depth,boolean preserveParent){BranchPlan(Split flow,double water,double radius,double depth){this(flow,water,radius,depth,false);}}
 record Solution(double[] nodeFlow,double[][] edgeFlow,double supplied,double terminal,double maxResidual){}
 static Solution solve(double[] runoff,int[][] receivers,double[][] weights){
  int n=runoff.length;if(receivers.length!=n||weights.length!=n)throw new IllegalArgumentException("Flow graph dimensions differ");double[] q=runoff.clone();double[][] edges=new double[n][];int[] donors=new int[n],queue=new int[n];double supplied=0;
  for(int i=0;i<n;i++){if(!Double.isFinite(q[i])||q[i]<0||receivers[i].length!=weights[i].length)throw new IllegalArgumentException("Invalid flow graph");supplied+=q[i];edges[i]=new double[receivers[i].length];for(int k=0;k<receivers[i].length;k++){int j=receivers[i][k];double weight=weights[i][k];if(j<0||j>=n||j==i||!Double.isFinite(weight)||weight<0)throw new IllegalArgumentException("Invalid flow edge");if(weight>0)donors[j]++;}}
  int head=0,tail=0;for(int i=0;i<n;i++)if(donors[i]==0)queue[tail++]=i;double terminal=0,maxResidual=0;
  while(head<tail){int i=queue[head++],last=-1;double total=0;for(int k=0;k<weights[i].length;k++)if(weights[i][k]>0){total+=weights[i][k];last=k;}if(last<0){terminal+=q[i];continue;}double assigned=0;for(int k=0;k<weights[i].length;k++){if(weights[i][k]==0)continue;double flow=k==last?q[i]-assigned:q[i]*(weights[i][k]/total);edges[i][k]=flow;assigned+=flow;int j=receivers[i][k];q[j]+=flow;if(--donors[j]==0)queue[tail++]=j;}maxResidual=Math.max(maxResidual,Math.abs(q[i]-assigned));}
  if(tail!=n)throw new IllegalArgumentException("River flow graph contains an active cycle");return new Solution(q,edges,supplied,terminal,Math.max(maxResidual,Math.abs(supplied-terminal)));
 }
 static Field accumulate(int[] receiver,double[] local){
  int n=receiver.length;if(local.length!=n)throw new IllegalArgumentException("Flow field size mismatch");double[] q=local.clone();int[] donors=new int[n],queue=new int[n];double supplied=0;
  for(int i=0;i<n;i++){if(!Double.isFinite(q[i])||q[i]<0)throw new IllegalArgumentException("Runoff must be finite and nonnegative");supplied+=q[i];int p=receiver[i];if(p>=n||p< -1||p==i)throw new IllegalArgumentException("Invalid drainage receiver");if(p>=0)donors[p]++;}
  int head=0,tail=0;for(int i=0;i<n;i++)if(donors[i]==0)queue[tail++]=i;
  while(head<tail){int i=queue[head++],p=receiver[i];if(p>=0){q[p]+=q[i];if(--donors[p]==0)queue[tail++]=p;}}
  if(tail!=n)throw new IllegalArgumentException("River flow graph contains a cycle");double terminal=0;for(int i=0;i<n;i++)if(receiver[i]<0)terminal+=q[i];
  return new Field(q,supplied,terminal,Math.abs(supplied-terminal));
 }
 static Field runoff(int[] receiver,float[] height,float[] rain,int n,int size,int sea,double amount){
  if(!Double.isFinite(amount)||amount<0)throw new IllegalArgumentException("Invalid runoff amount");double cellArea=Math.pow(size/(double)n,2);double[] local=new double[receiver.length];
  for(int i=0;i<local.length;i++)if(height[i]>sea)local[i]=cellArea*amount*(rain==null?1:Math.max(0,Math.min(1,rain[i])));
  return accumulate(receiver,local);
 }
 static double pathDischarge(Field field,List<Integer> path,double progress,boolean joined){if(field==null||path.isEmpty())return Double.NaN;int end=Math.max(0,path.size()-(joined?2:1));double p=Math.max(0,Math.min(1,progress))*end;int lo=(int)p,hi=Math.min(end,lo+1);return field.discharge[path.get(lo)]*(1-(p-lo))+field.discharge[path.get(hi)]*(p-lo);}
 static double widthFactor(double q,double maturity){if(!Double.isFinite(q)||q<=0)return 1;return Math.max(.45,Math.min(2.2,Math.pow(q/REFERENCE_DISCHARGE,.3)*(.65+.5*maturity)));}
 static double depthFactor(double q){return !Double.isFinite(q)||q<=0?1:Math.max(.7,Math.min(1.4,Math.pow(q/REFERENCE_DISCHARGE,.15)));}
 static Split split(double incoming,Arm left,Arm right){
  if(!Double.isFinite(incoming)||incoming<0)throw new IllegalArgumentException("Invalid incoming discharge");double lc=left.conveyance(),rc=right.conveyance();if(incoming==0)return new Split(0,0,0,0,0,0,0);if(lc+rc==0)throw new IllegalArgumentException("Both river arms are blocked");
  double f=lc/(lc+rc);double lw=0,rw=0,ld=0,rd=0;
  // Relax the share and channel dimensions together; preserve genuinely closed arms.
  for(int k=0;k<160;k++){lw=lc==0?0:Math.max(1.25,left.width*Math.pow(2*f,.3));rw=rc==0?0:Math.max(1.25,right.width*Math.pow(2*(1-f),.3));ld=lc==0?0:Math.min(5,Math.max(1,left.depth*Math.pow(2*f,.2)));rd=rc==0?0:Math.min(5,Math.max(1,right.depth*Math.pow(2*(1-f),.2)));double a=new Arm(lw,ld,left.slope,left.roughness).conveyance(),b=new Arm(rw,rd,right.slope,right.roughness).conveyance(),next=a/(a+b);if(Math.abs(next-f)<1e-11){f=next;break;}f=(f+next)*.5;}
  var solved=solve(new double[]{incoming,0,0,0},new int[][]{{1,2},{3},{3},{}},new double[][]{{f,1-f},{1},{1},{}});double l=solved.edgeFlow[0][0],r=solved.edgeFlow[0][1];return new Split(incoming,l,r,lw,rw,Math.min(5,ld),Math.min(5,rd));
 }
 static double roughness(int material){return switch(material){case 3,11,12->.05;case 7,13->.04;case 1,8,10->.045;case 2,5->.028;default->.035;};}
 static BranchPlan plan(EnvironmentMap global,RiverRibbon.Network network,RiverRibbon.SedimentIsland bar,int sea){return plan(global,network,bar,sea,16384);}
 static BranchPlan plan(EnvironmentMap global,RiverRibbon.Network network,RiverRibbon.SedimentIsland bar,int sea,int worldSize){
  RiverRibbon.Segment best=null;double nearest=Double.POSITIVE_INFINITY;for(var s:network.query((int)bar.x(),(int)bar.z())){double distance=Math.hypot(s.a().x()-bar.x(),s.a().z()-bar.z());if(distance<nearest){nearest=distance;best=s;}}
  if(best==null)throw new IllegalStateException("Sediment bar has no parent river");var point=best.a();double water=worldSize==16384&&global.height.length==2048*2048?RiverRibbon.surfaceAt(global,point,sea):point.water();if(Math.round(water)<=sea+(network.splitDensity>1?4:0)||!safeIsland(network,bar))return null;double radius=point.radius(),depth=Math.min(5,best.depth()),q=point.discharge();if(!Double.isFinite(q)||q<=0)q=REFERENCE_DISCHARGE*Math.pow(radius/3.5,1/.3);
  double nx=-Math.sin(bar.angle()),nz=Math.cos(bar.angle()),offset=(radius+bar.halfWidth())*.5,bend=Math.min(.22,Math.abs(best.curvature())*20)*Math.signum(best.curvature()),slope=Math.max(.001,Math.abs(best.a().water()-best.b().water())/Math.max(1,Math.hypot(best.b().x()-best.a().x(),best.b().z()-best.a().z())));
  int lx=(int)Math.round(bar.x()+nx*offset),lz=(int)Math.round(bar.z()+nz*offset),rx=(int)Math.round(bar.x()-nx*offset),rz=(int)Math.round(bar.z()-nz*offset);double leftGround=height(global,worldSize,lx,lz),rightGround=height(global,worldSize,rx,rz),difference=Math.max(-.2,Math.min(.2,(rightGround-leftGround)*.04));
  double baseWidth=Math.max(1.25,radius-bar.halfWidth()),leftDepth=Math.max(1,depth*(1-bend+difference)),rightDepth=Math.max(1,depth*(1+bend-difference));int lm=material(global,worldSize,lx,lz),rm=material(global,worldSize,rx,rz);
  var split=split(q,new Arm(baseWidth,leftDepth,slope,roughness(lm)),new Arm(baseWidth,rightDepth,slope,roughness(rm)));return new BranchPlan(split,water,radius,depth,network.splitDensity>1);
 }
 static boolean safeIsland(RiverRibbon.Network network,RiverRibbon.SedimentIsland bar){
  // Keep the entire dry bar inside an existing wet ribbon, leaving an arm on each side.
  var nearby=network.query((int)bar.x(),(int)bar.z());double cos=Math.cos(bar.angle()),sin=Math.sin(bar.angle());
  for(double along=-bar.halfLength();along<=bar.halfLength();along+=2){double x=bar.x()+cos*along,z=bar.z()+sin*along,half=bar.halfWidth()*Math.sqrt(Math.max(0,1-along*along/(bar.halfLength()*bar.halfLength()))),clearance=-Double.MAX_VALUE;
   for(var segment:nearby){var a=segment.a();var b=segment.b();double dx=b.x()-a.x(),dz=b.z()-a.z(),t=Math.max(0,Math.min(1,((x-a.x())*dx+(z-a.z())*dz)/Math.max(.001,dx*dx+dz*dz))),radius=a.radius()+(b.radius()-a.radius())*t;clearance=Math.max(clearance,radius-Math.hypot(x-a.x()-dx*t,z-a.z()-dz*t));}
   if(clearance<half+(network.splitDensity>1?2:1.25))return false;
  }
  return true;
 }
 static double height(EnvironmentMap e,int size,double x,double z){int n=(int)Math.sqrt(e.height.length);return RiverShape.height(e.height,n,x*n/size,z*n/size);}
 static int material(EnvironmentMap e,int size,double x,double z){if(e.material==null)return 0;int n=(int)Math.sqrt(e.height.length),xx=Math.max(0,Math.min(n-1,(int)(x*n/size))),zz=Math.max(0,Math.min(n-1,(int)(z*n/size)));return e.material[zz*n+xx];}
 static void localSplits(EnvironmentMap e,int n,int size,int sea){if(n!=size||e.ribbons==null||e.ribbons.islands.isEmpty())return;var plans=new ArrayList<BranchPlan>();for(var bar:e.ribbons.islands)plans.add(plan(e,e.ribbons,bar,sea,size));for(int i=0;i<plans.size();i++)if(plans.get(i)!=null)RiverRibbon.island(e,n,0,0,sea,e.ribbons.islands.get(i),plans.get(i));}
 static String report(EnvironmentMap global,int sea){var text=new StringBuilder("TerraForge river flow budget\nModel: generation-time normalized runoff and relative Manning conveyance; no unsteady fluid simulation.\n");
  if(global.riverFlow!=null){var f=global.riverFlow;text.append("Runoff supplied=").append(f.supplied).append("\nTerminal/closed-basin/lake flow=").append(f.terminal).append("\nRunoff budget residual=").append(f.residual).append('\n');}
  text.append("Short river rills use terrain-guided local branching; primary runoff totals above describe the primary drainage field.\n");text.append("Short river fork roots=").append(global.forkRoots).append(" fork arms=").append(global.mountainArms.size()).append(" tapered fork tips=").append(global.forkTipCount).append('\n');text.append("River groups with forks=").append(global.forkRiverGroups).append(" / ").append(global.forkEligibleGroups).append('\n');text.append("Mountain forks=").append(global.mountainForks.size()).append('\n');int forkIndex=0;for(var fork:global.mountainForks)text.append(String.format(Locale.ROOT,"Mountain fork %d at %.1f,%.1f: arm=%.1f,%.1f elevation=%.1f separation=%.1f generation=%d%n",++forkIndex,fork.x(),fork.z(),fork.armX(),fork.armZ(),fork.elevation(),fork.separation(),fork.generation()));
  int count=0;double max=0;if(global.ribbons!=null)for(var bar:global.ribbons.islands){var p=global.ribbons.branchPlan(global,bar,sea);if(p==null)continue;var s=p.flow;count++;max=Math.max(max,Math.abs(s.residual()));text.append(String.format(Locale.ROOT,"Split %d at %.1f,%.1f: water=%.3f angle=%.6f incoming=%.9f left=%.9f right=%.9f shares=%.3f%%/%.3f%% width=%.3f/%.3f depth=%.3f/%.3f rejoin=%.9f%n",count,bar.x(),bar.z(),p.water(),bar.angle(),s.incoming,s.left,s.right,100*s.leftFraction(),100*(1-s.leftFraction()),s.leftWidth,s.rightWidth,s.leftDepth,s.rightDepth,s.left+s.right));}
  if(global.ribbons!=null){text.append("Tapered side branches=").append(global.ribbons.sideBranches.size()).append('\n');int index=0;for(var branch:global.ribbons.sideBranches){var points=branch.points();var start=points.get(0);var stem=points.get(1);var tip=points.get(points.size()-1);text.append(String.format(Locale.ROOT,"Side branch %d at %.1f,%.1f: stem=%.1f,%.1f tip=%.1f,%.1f%n",++index,start.x(),start.z(),stem.x(),stem.z(),tip.x(),tip.z()));}}
  return text.append("Split/rejoin plans=").append(count).append("\nMaximum split budget residual=").append(max).append('\n').toString();
 }
}
