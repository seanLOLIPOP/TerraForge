import java.util.*;import java.util.concurrent.*;
/** Warped ridge spine and asymmetric tapering spurs inside a broad foothill envelope. */
final class OrganicMountainRanges {
 record Spur(double u,double center,double direction,double length,double sweep,double bend,double height,double width){}
 static final ConcurrentHashMap<Long,List<Spur>> shapes=new ConcurrentHashMap<>();
 static double spine(double u,long seed){return (TerraForge.sample((u+2)*2.1,.43,seed+9171)-.5)*.48+(TerraForge.sample((u+3)*5.3,.71,seed+9173)-.5)*.06;}
 static List<Spur> shape(long seed){return shapes.computeIfAbsent(seed,key->{var random=new Random(key^0x72af31L);var result=new ArrayList<Spur>();for(int i=0;i<7;i++){double u=-.78+i*.25+(random.nextDouble()-.5)*.14;result.add(new Spur(u,spine(u,key),i%2==0?-1:1,.48+random.nextDouble()*.53,(random.nextDouble()-.5)*.6,(random.nextDouble()-.5)*.22,.4+random.nextDouble()*.28,.065+random.nextDouble()*.06));}return List.copyOf(result);});}
 static double fade(double value){double t=Math.max(0,Math.min(1,value));return t*t*t*(t*(t*6-15)+10);}
 static double profile(double along,double across,double radius,double aspect,double noise,long seed){return profile(along,across,radius,aspect,noise,seed,0);}
 static double profile(double along,double across,double radius,double aspect,double noise,long seed,double spurs){return profile(along,across,radius,aspect,noise,seed,spurs,0);}
 static double profile(double along,double across,double radius,double aspect,double noise,long seed,double spurs,double definition){
  double u=along/(radius*aspect),v=across/radius,warp=(TerraForge.sample((u+2)*2.7,(v+2)*2.7,seed+9181)-.5)*.12;
  double extension=Math.max(0,spurs-1),strength=Math.min(1,spurs);
  double center=spine(u,seed),radial=Math.hypot(u/(1.08+extension*.12),(v-center*.4)/(1.25+warp+extension*.38));if(radial>=1)return 0;double foot=fade(1-radial);
  double distance=Math.abs(v-center+warp*.18),side=v<center?-1:1;
  double width=.11+.13*TerraForge.sample((u+2)*3.1,side+2,seed+9183);
  double summit=.52+.48*TerraForge.sample((u+3)*6.7,.37,seed+9185);
  double crags=.86+.14*(1-Math.abs(TerrainGenerator.fractal((u+3)*13,(v+3)*13,seed+9187,3,.5)*2-1));
  double core=Math.exp(-Math.pow(distance/width,1.28))*summit*crags,branches=0;
  for(int branchIndex=0;branchIndex<(extension>0?14:7);branchIndex++){var spur=shape(branchIndex<7?seed:seed+19471).get(branchIndex%7);double root=branchIndex<7?spur.center:spine(spur.u,seed),length=spur.length*(1+extension*.55);double t=Math.max(0,Math.min(1,(v-root)*spur.direction/length));double targetU=spur.u+spur.sweep*t+spur.bend*Math.sin(t*Math.PI),targetV=root+spur.direction*length*t;
   double d=Math.hypot((u-targetU)*aspect,v-targetV),w=spur.width*(1-.68*t)*(1+strength*.65)*(1-definition*.35);double branch=spur.height*(1+strength*.8)*(1-fade(t))*Math.exp(-Math.pow(d/Math.max(.02,w),1.45));branches=Math.max(branches,branch);
  }
  return .18*foot*foot+.82*Math.max(core*Math.pow(foot,.8),branches*Math.pow(foot,.8-definition*.3));
 }

    /** One straight piece of a spur ridge in (u*aspect, v) space; height and width taper from start to end. */
    record Ridge(double ax,double av,double bx,double bv,double startHeight,double endHeight,double startWidth,double endWidth){}
    static final ConcurrentHashMap<Long,List<Ridge>> naturalShapes=new ConcurrentHashMap<>();
    static double crest(double u,long seed){return .52+.48*TerraForge.sample((u+3)*6.7,.37,seed+9185);}
    /** Dendritic spur ridges: each spur leaves the crest at crest height, descends steadily to the foothills,
     *  narrows toward its tip, curves gently and forks into shorter sub-spurs. */
    static List<Ridge> naturalSpurs(long seed,double aspect,double spurs){
        long key=seed*1000003L^Double.doubleToLongBits(aspect)*31^Double.doubleToLongBits(spurs);
        return naturalShapes.computeIfAbsent(key,cacheKey->{
            var random=new Random(seed^0x5a17e5L);var result=new ArrayList<Ridge>();
            int count=10+(int)Math.round(5*Math.min(2,spurs));
            for(int i=0;i<count;i++){
                double u=-.85+1.7*(i+.5)/count+(random.nextDouble()-.5)*.8/count;
                double side=(i%2==0?1:-1)*(random.nextDouble()<.2?-1:1);
                double root=spine(u,seed),rootHeight=crest(u,seed)*(.9+.08*random.nextDouble());
                double length=(.8+.4*random.nextDouble())*(.8+.2*Math.min(2,spurs));
                double heading=Math.atan2(side,(random.nextDouble()-.5)*1.1);
                double bend=(random.nextDouble()-.5)*.8,width=.2+.1*random.nextDouble();
                double x=u*aspect,v=root,h=rootHeight,w=width;int pieces=6;
                for(int k=1;k<=pieces;k++){
                    double t=k/(double)pieces,dir=heading+bend*(t-.5);
                    double nx=x+Math.cos(dir)*length/pieces,nv=v+Math.sin(dir)*length/pieces,nh=rootHeight*Math.pow(1-t,1.05),nw=width*(1-.6*t);
                    result.add(new Ridge(x,v,nx,nv,h,nh,w,nw));
                    if((k==2||k==3||k==4)&&random.nextDouble()<.7){
                        double turn=(random.nextBoolean()?1:-1)*(.7+.4*random.nextDouble()),sub=length*(1-t)*(.5+.3*random.nextDouble());
                        double sd=dir+turn,mx=nx+Math.cos(sd)*sub*.5,mv=nv+Math.sin(sd)*sub*.5,sx=nx+Math.cos(sd+turn*.25)*sub,sv=nv+Math.sin(sd+turn*.25)*sub;
                        result.add(new Ridge(nx,nv,mx,mv,nh*.97,nh*.55,nw*.75,nw*.5));
                        result.add(new Ridge(mx,mv,sx,sv,nh*.55,0,nw*.5,nw*.25));
                    }
                    x=nx;v=nv;h=nh;w=nw;
                }
            }
            return List.copyOf(result);
        });
    }
    /** A coherent massif falling from the crest to the foothills, with tapering, forking spur ridges and the
     *  V-shaped valleys between them carved into its flanks. */
    static double naturalProfile(double along,double across,double radius,double aspect,double noise,long seed,double spurs,double definition){
        double u=along/(radius*aspect),v=across/radius,warp=(TerraForge.sample((u+2)*2.7,(v+2)*2.7,seed+9181)-.5)*.12;
        double extension=Math.max(0,spurs-1);
        double center=spine(u,seed),radial=Math.hypot(u/(1.08+extension*.12),(v-center*.4)/(1.3+warp+extension*.38));
        if(radial>=1)return 0;
        double foot=fade(1-radial),sharp=1.4-.5*Math.min(1,definition),strength=Math.min(1,spurs);
        double crags=.9+.1*(1-Math.abs(TerrainGenerator.fractal((u+3)*13,(v+3)*13,seed+9187,3,.5)*2-1));
        double x=u*aspect;
        // jitter the sample point so ridge crests wander instead of running ruler-straight
        double wx=x+(TerraForge.sample(x*4.3+5,v*4.3+9,seed+9191)-.5)*.07,wv=v+(TerraForge.sample(x*4.3+13,v*4.3+3,seed+9193)-.5)*.07;
        double distance=Math.abs(wv-center);
        // massif body: highest along the crest, falling steadily across the flanks to the foothill envelope
        double body=crest(u,seed)*Math.pow(foot,.75)*(.55+.45*Math.max(0,1-distance/.9));
        // ridge mask: 1 on the crest line and along spur crests, 0 in the valley floors between them
        double mask=Math.pow(Math.max(0,1-distance/(.18+.06*TerraForge.sample((u+2)*3.1,1.5,seed+9183))),sharp);
        for(var r:naturalSpurs(seed,aspect,spurs)){
            double dx=r.bx()-r.ax(),dv=r.bv()-r.av(),len2=dx*dx+dv*dv;
            double t=len2<=0?0:Math.max(0,Math.min(1,((wx-r.ax())*dx+(wv-r.av())*dv)/len2));
            double d=Math.hypot(wx-(r.ax()+dx*t),wv-(r.av()+dv*t)),w=r.startWidth()+(r.endWidth()-r.startWidth())*t;
            if(d>=w)continue;
            double reach=Math.min(1,(r.startHeight()+(r.endHeight()-r.startHeight())*t)/Math.max(.01,crest(u,seed))*1.4);
            mask=Math.max(mask,Math.pow(1-d/w,sharp)*reach);
        }
        double relief=(.3+.3*Math.min(1,definition))*strength;
        return .16*foot*foot+.84*body*crags*(1-relief+relief*mask);
    }
}
