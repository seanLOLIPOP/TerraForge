import javax.swing.*;
import java.nio.file.*;
public class EnvironmentDesktopTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 interface Condition{boolean get();}
 static void await(Condition test)throws Exception{for(int i=0;i<400;i++){boolean[] done={false};SwingUtilities.invokeAndWait(()->done[0]=test.get());if(done[0])return;Thread.sleep(50);}throw new AssertionError("Operation timed out");}
 public static void main(String[] args)throws Exception{TerraForge.theme();TerraForge[] app=new TerraForge[1];SwingUtilities.invokeAndWait(()->{app[0]=new TerraForge();app[0].setVisible(true);});Path file=Files.createTempFile("environment-ui-",".terra");try{
  await(()->!app[0].busy&&app[0].world!=null);SwingUtilities.invokeAndWait(()->{var a=app[0];a.openNodes();int originalSize=a.world.size;a.size.setSelectedItem("512");check(a.graphEditor.mapSize.getSelectedItem().equals("512"),"main-to-node size sync");a.graphEditor.mapSize.setSelectedItem("128");check(a.size.getSelectedItem().equals("128")&&a.world.size==originalSize,"node size sync without replacing terrain");a.dirty=false;a.buildGraph(TerrainGraph.template("Frozen highlands"));});await(()->!app[0].busy&&app[0].world.environment.biome!=null);
  SwingUtilities.invokeAndWait(()->{var a=app[0];check(a.world.size==128,"node-selected build size");var e=a.world.environment;check(e.water!=null&&e.snow!=null&&e.ice!=null&&e.glacier!=null&&e.temperature!=null&&e.rain!=null,"built all environment channels");for(int layer=0;layer<7;layer++){a.viewLayer.setSelectedIndex(layer);check(a.map.terrainImage().getWidth()==128,"view layer renders");}a.saveTo(file.toFile());});await(()->!app[0].busy&&!app[0].dirty);
  SwingUtilities.invokeAndWait(()->app[0].load(file.toFile(),false));await(()->!app[0].busy);SwingUtilities.invokeAndWait(()->{check(app[0].world.environment.biome!=null&&app[0].world.graph.nodes.size()==13,"project restores graph and layers");});await(()->!app[0].graphEditor.fields.isEmpty());
  System.out.println("PASS: bidirectional node map-size control, selected build size, environmental graph build, seven map views, save/open, graph restoration and live preview");
 }finally{SwingUtilities.invokeAndWait(()->app[0].dispose());Files.deleteIfExists(file);}System.exit(0);}
}
