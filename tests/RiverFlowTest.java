class RiverFlowTest {
 static void near(double actual,double expected){if(Math.abs(actual-expected)>1e-8*Math.max(1,Math.abs(expected)))throw new AssertionError(actual+" != "+expected);}
 public static void main(String[]args){
  var graph=RiverFlow.solve(new double[]{12,8,0,2,0,0,0},new int[][]{{2},{2},{3,4},{5},{5},{6},{}},new double[][]{{1},{1},{4,1},{1},{1},{1},{}});
  near(graph.nodeFlow()[2],20);near(graph.edgeFlow()[2][0],16);near(graph.edgeFlow()[2][1],4);near(graph.nodeFlow()[5],22);near(graph.supplied(),22);near(graph.terminal(),22);near(graph.maxResidual(),0);
  var equal=RiverFlow.split(100,new RiverFlow.Arm(3,3,.01,.035),new RiverFlow.Arm(3,3,.01,.035));near(equal.left(),50);near(equal.right(),50);
  var unequal=RiverFlow.split(100,new RiverFlow.Arm(3,4,.01,.028),new RiverFlow.Arm(3,1.5,.005,.06));if(unequal.leftFraction()<.8||unequal.leftWidth()<=unequal.rightWidth()||unequal.leftDepth()<=unequal.rightDepth())throw new AssertionError("Conveyance does not affect branch geometry: "+unequal);near(unequal.left()+unequal.right(),100);
  var closed=RiverFlow.split(100,new RiverFlow.Arm(0,0,.01,.035),new RiverFlow.Arm(3,3,.01,.035));near(closed.left(),0);near(closed.right(),100);near(RiverFlow.split(0,new RiverFlow.Arm(0,0,0,.035),new RiverFlow.Arm(0,0,0,.035)).incoming(),0);
  boolean rejected=false;try{RiverFlow.solve(new double[]{1,0},new int[][]{{1},{0}},new double[][]{{1},{1}});}catch(IllegalArgumentException ex){rejected=true;}if(!rejected)throw new AssertionError("Active cycle accepted");
  for(int n:new int[]{32,64}){int count=n*n;int[] receiver=new int[count];for(int i=0;i<count;i++)receiver[i]=i+1<count?i+1:-1;var field=RiverFlow.runoff(receiver,EnvironmentMap.filled(count,92),null,n,256,62,1);near(field.supplied(),65536);near(field.terminal(),65536);near(field.residual(),0);near(RiverFlow.runoff(receiver,EnvironmentMap.filled(count,92),null,n,256,62,2).terminal(),131072);}
  System.out.println("PASS: source/merge/split/rejoin budgets, unequal conveyance, blocked arm, zero runoff, cycle rejection and physical-area resolution invariance");
 }
}
