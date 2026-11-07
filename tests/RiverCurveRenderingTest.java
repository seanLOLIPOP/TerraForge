import java.util.*;
public class RiverCurveRenderingTest {
 static EnvironmentMap generate(int n,int size,double strength){
  float[] h=new float[n*n];int[] parent=new int[n*n];Arrays.fill(parent,-1);
  for(int z=0;z<n;z++)for(int x=0;x<n;x++){int i=z*n+x;h[i]=(float)(110-z*size/(double)n*.02);if(z<n-1)parent[i]=i+n;}
  var e=new EnvironmentMap(h.clone());e.water=EnvironmentMap.filled(n*n,62);e.river=new float[n*n];e.material=new byte[n*n];
  NaturalRivers.trace(e,h,new EnvironmentNodes.Drainage(parent,h),20*n+n/2,n,size,62,4*n/(double)size,3,Float.POSITIVE_INFINITY,strength,4242,.85,.55,.65,true,()->false);return e;
 }
 static double span(EnvironmentMap e,int n){double min=Double.POSITIVE_INFINITY,max=-min;int rows=0;for(int z=40;z<n-35;z++){double sum=0,weight=0;for(int x=0;x<n;x++){int i=z*n+x;if(e.river[i]>.3&&e.waterY(i,62)>Math.round(e.height[i])){sum+=x*e.river[i];weight+=e.river[i];}}if(weight>0){double c=sum/weight;min=Math.min(min,c);max=Math.max(max,c);rows++;}}if(rows<(n-75)*.9)throw new AssertionError("disconnected rendered river rows="+rows);return max-min;}
 public static void main(String[] args)throws Exception{
  int n=512;var straight=generate(n,n,0);var bent=generate(n,n,2);double straightSpan=span(straight,n),bentSpan=span(bent,n);if(bentSpan<30||bentSpan<straightSpan+20)throw new AssertionError("direct build did not carve bends: "+straightSpan+" / "+bentSpan);
  double distance=0;int count=0;for(var s:bent.ribbons.segments){var p=s.a();int x=(int)Math.round(p.x()),z=(int)Math.round(p.z());if(x<0||z<0||x>=n||z>=n)throw new AssertionError("curve leaves map");int i=z*n+x;if(z>30&&z<n-30){if(bent.river[i]<=.1||bent.waterY(i,62)<=Math.round(bent.height[i]))throw new AssertionError("final curve is dry");count++;}distance+=Math.hypot(s.b().x()-p.x(),s.b().z()-p.z());}
  if(count<20||distance<450)throw new AssertionError("missing final river geometry");
  var coarse=generate(128,1024,2);double coarseSpan=span(coarse,128)*8;if(coarseSpan<30)throw new AssertionError("layout still straight");
  if(!Arrays.equals(bent.height,generate(n,n,2).height))throw new AssertionError("non-deterministic river");
  var corner=new ArrayList<RiverRibbon.Point>();for(int x=100;x<=700;x+=8)corner.add(new RiverRibbon.Point(x,300,4,90,100000));for(int z=308;z<=900;z+=8)corner.add(new RiverRibbon.Point(700,z,4,90,100000));float[] terrain=EnvironmentMap.filled(128*128,92);for(int z=0;z<128;z++)for(int x=0;x<128;x++)if(Math.hypot(x*8-650,z*8-350)<28)terrain[z*128+x]=180;var guarded=new RiverRibbon.Network(List.of()).append(corner,3,83,terrain,128,8,1,true,1);for(var s:guarded.segments)for(int j=0;j<=8;j++){var p=RiverRibbon.lerp(s.a(),s.b(),j/8.);if(RiverShape.height(terrain,128,p.x()/8,p.z()/8)>120)throw new AssertionError("final spline crosses ridge");}
  if(args.length==2){
   var graph=GraphEditor.readFile(java.nio.file.Path.of(args[0]));GraphEditor.writeFile(graph,java.nio.file.Path.of(args[1]));LargeWorldBuilder.configure(16384);LargeWorldBuilder.SEED=4242;LargeWorldBuilder.ACTIVE_GRAPH=graph;
   var layout=LargeWorldBuilder.layout();if(layout.ribbons==null||layout.ribbons.segments.isEmpty())throw new AssertionError("City rivers missing");
   RiverRibbon.Segment selected=null;double best=0;int turns=0;
   for(var segment:layout.ribbons.segments){for(var p:List.of(segment.a(),segment.b()))if(!Double.isFinite(p.x()+p.z()+p.water())||p.x()<0||p.z()<0||p.x()>16384||p.z()>16384)throw new AssertionError("City river geometry outside map");double score=Math.abs(segment.curvature());if(score>.005)turns++;var p=segment.a();int i=Math.min(LargeWorldBuilder.GRID-1,(int)p.z()/8)*LargeWorldBuilder.GRID+Math.min(LargeWorldBuilder.GRID-1,(int)p.x()/8);if(score>best&&p.radius()>2&&p.water()>64&&p.x()>512&&p.z()>512&&p.x()<15872&&p.z()<15872&&(layout.lakeMask==null||layout.lakeMask[i]==0)){best=score;selected=segment;}}
   if(selected==null||turns<20)throw new AssertionError("City river bends missing");int rx=(int)selected.a().x()/512,rz=(int)selected.a().z()/512;var tile=LargeWorldBuilder.tile(layout,rx,rz);int wet=0;for(int i=0;i<tile.height.length;i++)if(tile.environment.river[i]>.1&&tile.environment.waterY(i,62)>Math.round(tile.height[i]))wet++;if(wet<10)throw new AssertionError("City detailed river is dry");javax.imageio.ImageIO.write(EnvironmentView.render(tile,0),"png",java.nio.file.Path.of("dist/city-natural-rivers-preview.png").toFile());
   System.out.println("CITY segments="+layout.ribbons.segments.size()+" turns="+turns+" region="+rx+","+rz+" wet river cells="+wet);
  }
  System.out.println("PASS final curves are carved and wet: direct centroid span="+bentSpan+" vs zero-strength="+straightSpan+"; layout span="+coarseSpan);
 }
}
