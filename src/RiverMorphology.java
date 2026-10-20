import java.util.*;
/** Shapes a connected drainage path inside its valley; no independent random/sine turns. */
final class RiverMorphology {
 static List<RiverRibbon.Point> shape(List<RiverRibbon.Point> path,float[] terrain,int n,double block,long seed){return shape(path,terrain,n,block,seed,1);}
 static List<RiverRibbon.Point> shape(List<RiverRibbon.Point> path,float[] terrain,int n,double block,long seed,double sweep){
  if(path.size()<3||terrain==null)return path;
  List<RiverRibbon.Point> points=new ArrayList<>();points.add(path.get(0));double until=8;var previous=path.get(0);
  for(int k=1;k<path.size();k++){var next=path.get(k);double length=Math.hypot(next.x()-previous.x(),next.z()-previous.z());while(length>=until&&length>.001){previous=RiverRibbon.lerp(previous,next,until/length);points.add(previous);length=Math.hypot(next.x()-previous.x(),next.z()-previous.z());until=8;}until-=length;previous=next;}
  var end=path.get(path.size()-1);if(Math.hypot(end.x()-points.get(points.size()-1).x(),end.z()-points.get(points.size()-1).z())>.01)points.add(end);
  points=broadCurves(points,terrain,n,block,sweep);points=meanders(points,terrain,n,block,seed);
  var anchor=List.copyOf(points);
  for(int pass=0;pass<12;pass++){var next=new ArrayList<RiverRibbon.Point>();next.add(points.get(0));for(int i=1;i<points.size()-1;i++){var a=points.get(i-1);var b=points.get(i);var c=points.get(i+1);double dx=c.x()-a.x(),dz=c.z()-a.z(),len=Math.max(.001,Math.hypot(dx,dz)),nx=-dz/len,nz=dx/len;
    double gradient=Math.max(0,(a.water()-c.water())/len),m=RiverCourse.maturity(b.water(),LargeWorldBuilder.SEA,gradient,i/(double)points.size());
    var broadA=points.get(Math.max(0,i-4));var broadC=points.get(Math.min(points.size()-1,i+4));double ax=b.x()-broadA.x(),az=b.z()-broadA.z(),bx=broadC.x()-b.x(),bz=broadC.z()-b.z(),turn=Math.atan2(ax*bz-az*bx,ax*bx+az*bz);
    // Curvature-driven bank retreat grows bends only where the valley allows it.
    double erodibility=TerraForge.sample(b.x()/180,b.z()/180,seed+9021),migration=-Math.max(-.15,Math.min(.15,turn*.5))*m*(.3+.7*erodibility);
    if(Double.isFinite(b.discharge()))migration*=Math.max(.5,Math.min(1.5,Math.sqrt(b.discharge()/RiverFlow.REFERENCE_DISCHARGE)));
    double xx=b.x()+(a.x()+c.x()-2*b.x())*.12+nx*migration,zz=b.z()+(a.z()+c.z()-2*b.z())*.12+nz*migration;
    var origin=anchor.get(i);double corridor=Math.max(10,Math.min(36,b.radius()*4));double drift=Math.hypot(xx-origin.x(),zz-origin.z());if(drift>corridor){xx=origin.x()+(xx-origin.x())*corridor/drift;zz=origin.z()+(zz-origin.z())*corridor/drift;}
    // Never smooth across a ridge or through an enclosed basin masked by the routing surface.
    double base=RiverShape.height(terrain,n,origin.x()/block,origin.z()/block);boolean safe=true;
    int samples=Math.max(2,(int)Math.ceil(Math.hypot(xx-b.x(),zz-b.z())/block*4));
    for(int j=1;j<=samples;j++){double t=j/(double)samples,ground=RiverShape.height(terrain,n,(b.x()+(xx-b.x())*t)/block,(b.z()+(zz-b.z())*t)/block);if(ground>Math.min(base+1.5,b.water()+2)||RiverCourse.terrainSlope(terrain,n,(b.x()+(xx-b.x())*t)/block,(b.z()+(zz-b.z())*t)/block,block)>Math.min(.28,RiverCourse.terrainSlope(terrain,n,origin.x()/block,origin.z()/block,block)+.01)){safe=false;break;}}
    if(!safe){xx=b.x();zz=b.z();}
    next.add(new RiverRibbon.Point(xx,zz,b.radius(),b.water(),b.discharge()));
   }next.add(end);points=next;}
  return points;
 }
 static List<RiverRibbon.Point> broadCurves(List<RiverRibbon.Point> input,float[] terrain,int n,double block){return broadCurves(input,terrain,n,block,1);}
 static List<RiverRibbon.Point> broadCurves(List<RiverRibbon.Point> input,float[] terrain,int n,double block,double sweep){
  var anchor=List.copyOf(input);var points=input;
  for(int pass=0;pass<5;pass++){var next=new ArrayList<RiverRibbon.Point>();next.add(points.get(0));
   for(int i=1;i<points.size()-1;i++){var b=points.get(i);var origin=anchor.get(i);int before=Math.max(0,i-8),after=Math.min(points.size()-1,i+8);var a=points.get(before);var c=points.get(after);double gradient=Math.max(0,(a.water()-c.water())/Math.max(8,Math.hypot(c.x()-a.x(),c.z()-a.z()))),m=RiverCourse.maturity(b.water(),LargeWorldBuilder.SEA,gradient,i/(double)points.size()),window=(64+256*m)*(1+Math.max(0,sweep-1)*.8),sigma=window*.5;
    int span=Math.max(2,(int)Math.ceil(window/8));double sum=0,xx=0,zz=0;for(int j=Math.max(0,i-span);j<=Math.min(points.size()-1,i+span);j++){double weight=Math.exp(-Math.pow((j-i)*8/sigma,2)*.5);var p=points.get(j);sum+=weight;xx+=p.x()*weight;zz+=p.z()*weight;}xx/=sum;zz/=sum;
    double fade=TerraForge.smooth(Math.min(1,Math.min(i,points.size()-1-i)/(double)span));xx=b.x()+(xx-b.x())*fade;zz=b.z()+(zz-b.z())*fade;
    double corridor=Math.max(16,window*.8),drift=Math.hypot(xx-origin.x(),zz-origin.z());if(drift>corridor){xx=origin.x()+(xx-origin.x())*corridor/drift;zz=origin.z()+(zz-origin.z())*corridor/drift;}
    double base=RiverShape.height(terrain,n,origin.x()/block,origin.z()/block);boolean safe=false;
    for(int attempt=0;attempt<8;attempt++){safe=true;int samples=Math.max(2,(int)Math.ceil(Math.hypot(xx-b.x(),zz-b.z())/block*4));for(int j=1;j<=samples;j++){double t=j/(double)samples,h=RiverShape.height(terrain,n,(b.x()+(xx-b.x())*t)/block,(b.z()+(zz-b.z())*t)/block);if(h>Math.min(base+1.5,b.water()+2)||RiverCourse.terrainSlope(terrain,n,(b.x()+(xx-b.x())*t)/block,(b.z()+(zz-b.z())*t)/block,block)>Math.min(.28,RiverCourse.terrainSlope(terrain,n,origin.x()/block,origin.z()/block,block)+.01)){safe=false;break;}}if(safe)break;xx=(xx+b.x())*.5;zz=(zz+b.z())*.5;}
    if(!safe){xx=b.x();zz=b.z();}next.add(new RiverRibbon.Point(xx,zz,b.radius(),b.water(),b.discharge()));
   }next.add(points.get(points.size()-1));points=next;
  }
  return points;
 }


    /** Seeded lateral meanders for reaches the drainage grid left straight. Bends scale with channel width,
     *  grow with river maturity, fade at sources and junctions, and never climb out of the valley floor. */
    static List<RiverRibbon.Point> meanders(List<RiverRibbon.Point> input,float[] terrain,int n,double block,long seed){
        int count=input.size();if(count<8||terrain==null)return input;
        double[] arc=new double[count];for(int i=1;i<count;i++)arc[i]=arc[i-1]+Math.hypot(input.get(i).x()-input.get(i-1).x(),input.get(i).z()-input.get(i-1).z());
        double total=arc[count-1];if(total<96)return input;
        double[] nx=new double[count],nz=new double[count],offset=new double[count];
        double[] widths=new double[count];for(int i=0;i<count;i++)widths[i]=Math.max(2,input.get(i).radius());widths=gaussian(widths,12);
        double[] phaseT=new double[count];for(int i=1;i<count;i++)phaseT[i]=phaseT[i-1]+(arc[i]-arc[i-1])/Math.max(300,Math.min(680,widths[i]*42));// integrate so width changes never jump the phase
        for(int i=0;i<count;i++){
            var a=input.get(Math.max(0,i-6));var c=input.get(Math.min(count-1,i+6));
            double tx=c.x()-a.x(),tz=c.z()-a.z(),len=Math.max(.001,Math.hypot(tx,tz));nx[i]=-tz/len;nz[i]=tx/len;
            var b=input.get(i);
            var u=input.get(Math.max(0,i-8));var d=input.get(Math.min(count-1,i+8));
            double gradient=Math.max(0,(u.water()-d.water())/Math.max(8,Math.hypot(d.x()-u.x(),d.z()-u.z())));
            double maturity=RiverCourse.maturity(b.water(),LargeWorldBuilder.SEA,gradient,i/(double)count);
            double width=widths[i];
            double wavelength=Math.max(300,Math.min(680,width*42));
            double amplitude=Math.max(45,Math.min(90,width*8))*(.6+.4*TerraForge.smooth(Math.max(0,Math.min(1,maturity))));
            double t=phaseT[i];
            // Phase-wandering sine: regular alternating bends whose spacing and size vary along the river.
            double phase=t+.9*(TerraForge.sample(t*.45,.5,seed+77031)-.5)+.35*(TerraForge.sample(t*1.3,2.5,seed+77037)-.5);
            double size=.55+.45*TerraForge.sample(t*.38,4.5,seed+77039);
            double wave=Math.sin(phase*Math.PI*2)*size;
            double fade=TerraForge.smooth(Math.min(1,Math.min(arc[i],total-arc[i])/(wavelength*.6)));
            // Broad valley-scale sway so long rivers do not read as ruler lines at map scale.
            double broadLength=Math.max(900,Math.min(2600,total/1.5)),broadAmplitude=Math.min(150,total*.09),bt=arc[i]/broadLength;
            double broad=Math.sin((bt+.6*(TerraForge.sample(bt*.5,6.5,seed+77043)-.5))*Math.PI*2)*(.6+.4*TerraForge.sample(bt*.4,8.5,seed+77047));
            double broadFade=TerraForge.smooth(Math.min(1,Math.min(arc[i],total-arc[i])/(broadLength*.25)));
            offset[i]=wave*amplitude*fade+broad*broadAmplitude*broadFade;
        }
        // Fit a smooth amplitude envelope: wherever a bend would leave the valley floor or climb a steep face,
        // shrink the envelope locally and re-smooth, so bends narrow gradually instead of jittering point by point.
        double[] envelope=new double[count];java.util.Arrays.fill(envelope,1);double[] raw=offset.clone();
        for(int round=0;round<8;round++){
            offset=gaussian(multiply(raw,envelope),5);
            boolean changed=false;
            for(int i=0;i<count;i++){if(!valid(input.get(i),nx[i],nz[i],offset[i],terrain,n,block)){envelope[i]*=.55;changed=true;}}
            if(!changed)break;
            envelope=gaussian(minimum(envelope,4),3);
        }
        offset=gaussian(multiply(raw,envelope),5);
        for(int i=0;i<count;i++)for(int k=0;k<6&&!valid(input.get(i),nx[i],nz[i],offset[i],terrain,n,block);k++)offset[i]*=.6;
        offset=gaussian(offset,2);
        offset[0]=0;offset[count-1]=0;
        var result=new ArrayList<RiverRibbon.Point>(count);
        for(int i=0;i<count;i++){var b=input.get(i);result.add(new RiverRibbon.Point(b.x()+nx[i]*offset[i],b.z()+nz[i]*offset[i],b.radius(),b.water(),b.discharge()));}
        return result;
    }

    static boolean valid(RiverRibbon.Point b,double nx,double nz,double offset,float[] terrain,int n,double block){
        if(Math.abs(offset)<.5)return true;
        double base=RiverShape.height(terrain,n,b.x()/block,b.z()/block),slope=RiverCourse.terrainSlope(terrain,n,b.x()/block,b.z()/block,block);
        int samples=Math.max(2,(int)Math.ceil(Math.abs(offset)/block*3));
        for(int j=1;j<=samples;j++){double f=offset*j/samples,x=(b.x()+nx*f)/block,z=(b.z()+nz*f)/block,allowance=2+Math.abs(f)*.1;
            if(RiverShape.height(terrain,n,x,z)>Math.min(base+allowance,b.water()+allowance+.5)||RiverCourse.terrainSlope(terrain,n,x,z,block)>Math.min(.4,slope+.1))return false;}
        return true;
    }
    static double[] multiply(double[] a,double[] b){double[] r=new double[a.length];for(int i=0;i<a.length;i++)r[i]=a[i]*b[i];return r;}
    static double[] minimum(double[] a,int radius){double[] r=new double[a.length];for(int i=0;i<a.length;i++){double m=a[i];for(int j=Math.max(0,i-radius);j<=Math.min(a.length-1,i+radius);j++)m=Math.min(m,a[j]);r[i]=m;}return r;}
    static double[] gaussian(double[] a,double sigma){int radius=(int)Math.ceil(sigma*2.5);double[] r=new double[a.length];for(int i=0;i<a.length;i++){double sum=0,weight=0;for(int j=Math.max(0,i-radius);j<=Math.min(a.length-1,i+radius);j++){double w=Math.exp(-.5*(j-i)*(j-i)/(sigma*sigma));sum+=a[j]*w;weight+=w;}r[i]=sum/weight;}return r;}
}
