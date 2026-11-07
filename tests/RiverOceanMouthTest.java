import java.util.*;
public class RiverOceanMouthTest {
 static final int SEA=63,X=384;
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static float terrain(int z){return z<256?73:z<512?63.8f:z<640?63:z<768?63.3f:59;}
 static EnvironmentMap base(int n,int block,int oz){var e=new EnvironmentMap(new float[n*n]);e.water=EnvironmentMap.filled(n*n,SEA);e.river=new float[n*n];e.material=new byte[n*n];e.snow=new float[n*n];for(int z=0;z<n;z++)Arrays.fill(e.height,z*n,(z+1)*n,terrain(oz+z*block));return e;}
 static void wet(EnvironmentMap e,int n,int x,int z){int i=z*n+x;check(e.waterY(i,SEA)>Math.round(e.height[i]),"dry mouth at "+x+","+z+" height="+e.height[i]);}
 static TerraForge.World tile(EnvironmentMap global,int n,int oz){var w=new TerraForge.World(n,SEA,83);w.environment=base(n,1,oz);w.height=w.environment.height;RiverRibbon.refine(global,w,0,oz,SEA);return w;}
 public static void main(String[] args)throws Exception{
  LargeWorldBuilder.configure(2048);LargeWorldBuilder.SEA=SEA;
  var a=new RiverRibbon.Point(X,128,4,73);var b=new RiverRibbon.Point(X,256,4,SEA);var c=new RiverRibbon.Point(X,800,4,SEA);
  var network=new RiverRibbon.Network(List.of(new RiverRibbon.Segment(a,b,3,.8,0),new RiverRibbon.Segment(b,c,3,.9,0)));
  var global=base(256,8,0);global.ribbons=network;RiverChannels.carve(global,global.height.clone(),network.segments,0,256,2048,SEA,()->false);
  for(int z=32;z<=100;z++)wet(global,256,X/8,z);
  var direct=base(1024,1,0);RiverChannels.carve(direct,direct.height.clone(),network.segments,0,1024,1024,SEA,()->false);for(int z=256;z<=800;z++)wet(direct,1024,X,z);
  var whole=tile(global,1024,0);for(int z=256;z<=800;z++)wet(whole.environment,1024,X,z);
  for(int offset:new int[]{0,512}){var part=tile(global,512,offset);for(int z=Math.max(0,256-offset);z<Math.min(512,801-offset);z++){wet(part.environment,512,X,z);int i=z*512+X,j=(z+offset)*1024+X;check(part.height[i]==whole.height[j]&&part.environment.water[i]==whole.environment.water[j],"mouth differs at region seam");}}
  var coast=new TerrainGraph.Node(1,TerrainGraph.Type.COAST,0,0);coast.values.put("naturalShore",1.0);var shore=NaturalShorelines.apply(coast,global,256,2048,SEA,83,()->false);for(int z=32;z<=100;z++)wet(shore,256,X/8,z);
  var banks=RiverBanks.apply(new TerrainGraph.Node(2,TerrainGraph.Type.RIVER_BANKS,0,0),shore,256,2048,SEA,83,0,0,()->false);for(int z=32;z<=100;z++)wet(banks,256,X/8,z);
  check(direct.waterY(600*1024+X+20,SEA)<=Math.round(direct.height[600*1024+X+20]),"dry beach outside mouth retained");
  if(args.length>0){
   LargeWorldBuilder.configure(16384);LargeWorldBuilder.SEED=74839261;LargeWorldBuilder.SEA=SEA;LargeWorldBuilder.ACTIVE_GRAPH=GraphEditor.readFile(java.nio.file.Path.of(args[0]));var map=LargeWorldBuilder.layout();
   RiverRibbon.Point outlet=null,start=null;int ox=0,oz=0;
   var segments=map.ribbons.segments;
   for(int i=0;i<segments.size();i++){var q=segments.get(i);var end=q.b();if(end.water()>SEA||i+1<segments.size()&&Math.hypot(end.x()-segments.get(i+1).a().x(),end.z()-segments.get(i+1).a().z())<.01)continue;int tx=(int)end.x()/512*512,tz=(int)end.z()/512*512;if(end.x()-tx<64||end.x()-tx>448||end.z()-tz<64||end.z()-tz>448)continue;
    boolean ocean=false;for(int z=-8;z<=8;z++)for(int x=-8;x<=8;x++){int cx=Math.max(0,Math.min(2047,(int)end.x()/8+x)),cz=Math.max(0,Math.min(2047,(int)end.z()/8+z)),j=cz*2048+cx;if(map.height[j]<SEA-2&&EnvironmentMap.at(map.river,j,0)<.05)ocean=true;}if(!ocean)continue;
    var up=end;double reach=0;for(int j=i;j>=0;j--){var r=segments.get(j);if(Math.hypot(r.b().x()-up.x(),r.b().z()-up.z())>.01||r.a().x()<tx||r.a().x()>=tx+512||r.a().z()<tz||r.a().z()>=tz+512)break;reach+=Math.hypot(r.b().x()-r.a().x(),r.b().z()-r.a().z());up=r.a();if(reach>=128)break;}if(reach<64)continue;outlet=end;start=up;ox=tx;oz=tz;break;
   }
   check(outlet!=null,"actual map has an ocean mouth fixture");var region=LargeWorldBuilder.tile(map,ox/512,oz/512);int sx=Math.max(0,Math.min(511,(int)Math.round(start.x())-ox)),sz=Math.max(0,Math.min(511,(int)Math.round(start.z())-oz));wet(region.environment,512,sx,sz);
   var seen=new BitSet(512*512);var queue=new ArrayDeque<Integer>();queue.add(sz*512+sx);seen.set(sz*512+sx);boolean connected=false;
   while(!queue.isEmpty()){int i=queue.removeFirst(),x=i%512,z=i/512,gx=(ox+x)/8,gz=(oz+z)/8,gi=gz*2048+gx;if(map.height[gi]<SEA-2&&EnvironmentMap.at(map.river,gi,0)<.05){connected=true;break;}for(int d:new int[]{-1,1,-512,512}){int j=i+d;if(j<0||j>=512*512||Math.abs(j%512-x)+Math.abs(j/512-z)!=1||seen.get(j)||region.environment.waterY(j,SEA)<=Math.round(region.height[j]))continue;seen.set(j);queue.add(j);}}
   check(connected,"actual detailed river mouth connects to ocean water");javax.imageio.ImageIO.write(EnvironmentView.render(region,0),"png",java.nio.file.Path.of("dist/river-ocean-mouth-city.png").toFile());System.out.println("CITY detailed mouth connected to ocean at "+outlet.x()+","+outlet.z());
  }
  System.out.println("PASS sea-level mouth stays wet through broad beach: layout, direct, detailed regions, seam, shores and banks");
 }
}
