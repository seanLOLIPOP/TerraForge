import javax.swing.*;import java.nio.file.*;
public class RegionEditorBuildTest{
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] a)throws Exception{
  Path root=Files.createTempDirectory("tf-region-editor");System.setProperty("terraforge.regionBuilds",root.toString());System.setProperty("terraforge.regionBuild","true");
  TerraForge.theme();TerraForge[] app={null};
  SwingUtilities.invokeAndWait(()->{app[0]=new TerraForge();app[0].setVisible(true);});
  try{
   for(int i=0;i<400;i++){boolean[] r={false};SwingUtilities.invokeAndWait(()->r[0]=!app[0].busy&&app[0].world!=null);if(r[0])break;Thread.sleep(50);}
   var graph=a.length>0?GraphEditor.readFile(Path.of(a[0])):TerrainGraph.template("Island mountains");int size=a.length>1?Integer.parseInt(a[1]):2048;
   SwingUtilities.invokeAndWait(()->{app[0].size.setSelectedItem(Integer.toString(size));app[0].seed.setText("4242");app[0].dirty=false;app[0].buildGraph(graph);});
   for(int i=0;i<36000;i++){boolean[] r={false};SwingUtilities.invokeAndWait(()->r[0]=!app[0].busy);if(r[0]&&app[0].world.region!=null)break;if(r[0]&&i>40)break;Thread.sleep(100);}
   var w=app[0].world;check(w.region!=null,"editor build went region by region");
   check(w.size==size/8,"editor holds an overview, not the full map: "+w.size);
   long mca;try(var s=Files.list(w.region.folder.resolve("region"))){mca=s.filter(p->p.toString().endsWith(".mca")).count();}
   check(mca==(size/512)*(size/512),"all regions written to disk: "+mca);
   float max=0;for(float h:w.height)max=Math.max(max,h);check(max>w.sea+20,"overview has terrain");
   // full-resolution window around the centre, same blocks as the export
   var win=w.region.window(size/2,size/2,RegionMap.WINDOW);
   check(win.regionWindow&&win.size==Math.min(size,1536/512*512),"window size "+win.size);
   var tile=w.region.tile(win.originX/512,win.originZ/512);
   check(win.h(5,7)==tile.h(5,7)&&TerrainGenerator.surface(win,5,7)==TerrainGenerator.surface(tile,5,7),"window matches the region it came from");
   // export copies the built world
   Path target=root.resolve("saves/Copied");Files.createDirectories(target.getParent());w.region.copyWorld(target);
   check(Files.exists(target.resolve("level.dat"))&&!Files.exists(target.resolve("overview")),"export copies the world without the editor overview");
   // rebuilding the same graph reuses the folder (resume): fast and same folder
   long t=System.nanoTime();var again=RegionMap.build(graph,4242,app[0].sea.getValue(),size,null,()->false);
   check(again.region.folder.equals(w.region.folder),"same graph reuses its build folder");
   System.out.println("PASS: editor Build into map uses the region method ("+mca+" regions on disk, "+w.size+" overview), block windows match the export, Export copies the world, rebuild reuses finished regions in "+(System.nanoTime()-t)/1000000000+" s");
  }finally{SwingUtilities.invokeAndWait(()->app[0].dispose());}
  System.exit(0);
 }
}
