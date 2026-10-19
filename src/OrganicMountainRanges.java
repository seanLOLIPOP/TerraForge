import java.util.*;import java.util.concurrent.*;
/** Warped ridge spine and asymmetric tapering spurs inside a broad foothill envelope. */
final class OrganicMountainRanges {
 record Spur(double u,double center,double direction,double length,double sweep,double bend,double height,double width){}
 static final ConcurrentHashMap<Long,List<Spur>> shapes=new ConcurrentHashMap<>();
 static double spine(double u,long seed){return (TerraForge.sample((u+2)*2.1,.43,seed+9171)-.5)*.48+(TerraForge.sample((u+3)*5.3,.71,seed+9173)-.5)*.06;}
 static List<Spur> shape(long seed){return shapes.computeIfAbsent(seed,key->{var random=new Random(key^0x72af31L);var result=new ArrayList<Spur>();for(int i=0;i<7;i++){double u=-.78+i*.25+(random.nextDouble()-.5)*.14;result.add(new Spur(u,spine(u,key),i%2==0?-1:1,.48+random.nextDouble()*.53,(random.nextDouble()-.5)*.6,(random.nextDouble()-.5)*.22,.4+random.nextDouble()*.28,.065+random.nextDouble()*.06));}return List.copyOf(result);});}
 static double fade(double value){double t=Math.max(0,Math.min(1,value));return t*t*t*(t*(t*6-15)+10);}
 static double profile(double along,double across,double radius,double aspect,double noise,long seed){return profile(along,across,radius,aspect,noise,seed,0);}
 static double profile(double along,double across,double radius,double aspect,double noise,long seed,double spurs){
  double u=along/(radius*aspect),v=across/radius,warp=(TerraForge.sample((u+2)*2.7,(v+2)*2.7,seed+9181)-.5)*.12;
  double extension=Math.max(0,spurs-1),strength=Math.min(1,spurs);
  double center=spine(u,seed),radial=Math.hypot(u/(1.08+extension*.12),(v-center*.4)/(1.25+warp+extension*.38));if(radial>=1)return 0;double foot=fade(1-radial);
  double distance=Math.abs(v-center+warp*.18),side=v<center?-1:1;
  double width=.11+.13*TerraForge.sample((u+2)*3.1,side+2,seed+9183);
  double summit=.52+.48*TerraForge.sample((u+3)*6.7,.37,seed+9185);
  double crags=.86+.14*(1-Math.abs(TerrainGenerator.fractal((u+3)*13,(v+3)*13,seed+9187,3,.5)*2-1));
  double core=Math.exp(-Math.pow(distance/width,1.28))*summit*crags;
  for(int branchIndex=0;branchIndex<(extension>0?14:7);branchIndex++){var spur=shape(branchIndex<7?seed:seed+19471).get(branchIndex%7);double root=branchIndex<7?spur.center:spine(spur.u,seed),length=spur.length*(1+extension*.55);double t=Math.max(0,Math.min(1,(v-root)*spur.direction/length));double targetU=spur.u+spur.sweep*t+spur.bend*Math.sin(t*Math.PI),targetV=root+spur.direction*length*t;
   double d=Math.hypot((u-targetU)*aspect,v-targetV),w=spur.width*(1-.68*t)*(1+strength*.65);double branch=spur.height*(1+strength*.8)*(1-fade(t))*Math.exp(-Math.pow(d/Math.max(.02,w),1.45));core=Math.max(core,branch);
  }
  return .18*foot*foot+.82*core*Math.pow(foot,.8);
 }
}
