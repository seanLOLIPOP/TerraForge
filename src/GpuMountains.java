import java.util.*;
/** CPU prepares the small seeded ridge tables; GPU evaluates their profiles per cell. */
final class GpuMountains {
    static double[] shapes(TerrainGraph.Node node,long seed){
        if(node.p("peaks")==0)return new double[0];
        var data=new ArrayList<Double>();
        if(node.p("spurStyle")>.5){for(var r:OrganicMountainRanges.naturalSpurs(seed,node.p("aspect"),node.p("spurs"))){for(double v:new double[]{r.ax(),r.av(),r.bx(),r.bv(),r.startHeight(),r.endHeight(),r.startWidth(),r.endWidth()})data.add(v);}}
        else for(int k=0;k<(node.p("spurs")>1?2:1);k++)for(var r:OrganicMountainRanges.shape(seed+k*19471L)){for(double v:new double[]{r.u(),r.center(),r.direction(),r.length(),r.sweep(),r.bend(),r.height(),r.width()})data.add(v);}
        return data.stream().mapToDouble(Double::doubleValue).toArray();
    }
    static final String HELPERS="""
double spine(double u,ulong seed){return (sampleNoise((u+2)*2.1,.43,seed+9171)-.5)*.48+(sampleNoise((u+3)*5.3,.71,seed+9173)-.5)*.06;}
double fade(double value){double t=fmax(0,fmin(1,value));return t*t*t*(t*(t*6-15)+10);}
double crest(double u,ulong seed){return .52+.48*sampleNoise((u+3)*6.7,.37,seed+9185);}
double mountainPeaks(double along,double across,double radius,double aspect,double noise,ulong seed){
  double d=hypot(along/aspect,across)/(radius*(.85+noise*.3));if(d>=1)return 0;
  double foothills=.26*pow(1-curve(d),1.25),peaks=0;
  for(int k=-1;k<=1;k++){double center=k*radius*aspect*.43,offset=k==0?0:(sampleNoise(k+4.2,.7,seed+881)-.5)*radius*.2;double r=hypot((along-center)/(radius*.52),(across-offset)/(radius*.38));double height=k==0?1:k<0?.78:.87;peaks=fmax(peaks,height*pow(fmax(0,1-r),1.45));}
  double ridge=.10*pow(fmax(0,1-fabs(across)/(radius*.22)),2)*(1-curve(fmin(1,fabs(along)/(radius*aspect*.9))));
  return foothills+.64*peaks+ridge;
 }
double organicProfile(double along,double across,double radius,double aspect,double noise,ulong seed,double spurs,double definition,global const double* shapeData){
  double u=along/(radius*aspect),v=across/radius,warp=(sampleNoise((u+2)*2.7,(v+2)*2.7,seed+9181)-.5)*.12;
  double extension=fmax(0,spurs-1),strength=fmin(1,spurs);
  double center=spine(u,seed),radial=hypot(u/(1.08+extension*.12),(v-center*.4)/(1.25+warp+extension*.38));if(radial>=1)return 0;double foot=fade(1-radial);
  double distance=fabs(v-center+warp*.18),side=v<center?-1:1;
  double width=.11+.13*sampleNoise((u+2)*3.1,side+2,seed+9183);
  double summit=.52+.48*sampleNoise((u+3)*6.7,.37,seed+9185);
  double crags=.86+.14*(1-fabs(fractal((u+3)*13,(v+3)*13,seed+9187,3,.5)*2-1));
  double core=exp(-pow(distance/width,1.28))*summit*crags,branches=0;
  for(int branchIndex=0;branchIndex<(extension>0?14:7);branchIndex++){global const double* spur=shapeData+branchIndex*8;double root=branchIndex<7?spur[1]:spine(spur[0],seed),length=spur[3]*(1+extension*.55);double t=fmax(0,fmin(1,(v-root)*spur[2]/length));double targetU=spur[0]+spur[4]*t+spur[5]*sin(t*3.141592653589793),targetV=root+spur[2]*length*t;
   double d=hypot((u-targetU)*aspect,v-targetV),w=spur[7]*(1-.68*t)*(1+strength*.65)*(1-definition*.35);double branch=spur[6]*(1+strength*.8)*(1-fade(t))*exp(-pow(d/fmax(.02,w),1.45));branches=fmax(branches,branch);
  }
  return .18*foot*foot+.82*fmax(core*pow(foot,.8),branches*pow(foot,.8-definition*.3));
 }
double naturalProfile(double along,double across,double radius,double aspect,double noise,ulong seed,double spurs,double definition,global const double* shapeData,int shapeCount){
        double u=along/(radius*aspect),v=across/radius,warp=(sampleNoise((u+2)*2.7,(v+2)*2.7,seed+9181)-.5)*.12;
        double extension=fmax(0,spurs-1);
        double center=spine(u,seed),radial=hypot(u/(1.08+extension*.12),(v-center*.4)/(1.3+warp+extension*.38));
        if(radial>=1)return 0;
        double foot=fade(1-radial),sharp=1.4-.5*fmin(1,definition),strength=fmin(1,spurs);
        double crags=.9+.1*(1-fabs(fractal((u+3)*13,(v+3)*13,seed+9187,3,.5)*2-1));
        double x=u*aspect;
        // jitter the sample point so ridge crests wander instead of running ruler-straight
        double wx=x+(sampleNoise(x*4.3+5,v*4.3+9,seed+9191)-.5)*.07,wv=v+(sampleNoise(x*4.3+13,v*4.3+3,seed+9193)-.5)*.07;
        double distance=fabs(wv-center);
        // massif body: highest along the crest, falling steadily across the flanks to the foothill envelope
        double body=crest(u,seed)*pow(foot,.75)*(.55+.45*fmax(0,1-distance/.9));
        // ridge mask: 1 on the crest line and along spur crests, 0 in the valley floors between them
        double mask=pow(fmax(0,1-distance/(.18+.06*sampleNoise((u+2)*3.1,1.5,seed+9183))),sharp);
        for(int ri=0;ri<shapeCount;ri++){global const double* r=shapeData+ri*8;
            double dx=r[2]-r[0],dv=r[3]-r[1],len2=dx*dx+dv*dv;
            double t=len2<=0?0:fmax(0,fmin(1,((wx-r[0])*dx+(wv-r[1])*dv)/len2));
            double d=hypot(wx-(r[0]+dx*t),wv-(r[1]+dv*t)),w=r[6]+(r[7]-r[6])*t;
            if(d>=w)continue;
            double reach=fmin(1,(r[4]+(r[5]-r[4])*t)/fmax(.01,crest(u,seed))*1.4);
            mask=fmax(mask,pow(1-d/w,sharp)*reach);
        }
        double relief=(.3+.3*fmin(1,definition))*strength;
        return .16*foot*foot+.84*body*crags*(1-relief+relief*mask);
    }
        """;
}
