import javax.swing.*;
import java.util.*;
public class PreviewResolutionTest {
 static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
 interface Condition{boolean get();}
 static void await(Condition test)throws Exception{for(int i=0;i<600;i++){boolean[] ready={false};SwingUtilities.invokeAndWait(()->ready[0]=test.get());if(ready[0])return;Thread.sleep(50);}throw new AssertionError("Preview timed out");}
 public static void main(String[] args)throws Exception{TerraForge.theme();TerraForge[] app=new TerraForge[1];SwingUtilities.invokeAndWait(()->{app[0]=new TerraForge();app[0].setVisible(true);});try{
  await(()->!app[0].busy&&app[0].world!=null);SwingUtilities.invokeAndWait(()->{app[0].openNodes();var editor=app[0].graphEditor;editor.graph=TerrainGraph.template("Frozen highlands");editor.changed(true);editor.mapSize.setSelectedItem("128");});
  for(String size:new String[]{"128","256","512"}){int n=Integer.parseInt(size);SwingUtilities.invokeAndWait(()->{app[0].graphEditor.mapSize.setSelectedItem(size);app[0].graphEditor.queuePreview();});await(()->{var e=app[0].graphEditor;return e.previewResolution==n&&e.fields.containsKey(e.graph.output())&&e.fields.get(e.graph.output()).length==n*n&&e.worker!=null&&e.worker.isDone();});
   SwingUtilities.invokeAndWait(()->{var e=app[0].graphEditor;var expected=e.graph.evaluate(n,n,app[0].sea.getValue(),app[0].seedValue(),e.graph.output(),()->false);check(Arrays.equals(e.fields.get(e.graph.output()),expected.output()),"height preview equals build at "+n);var environment=e.environments.get(e.graph.output());check(Arrays.equals(environment.water,expected.environment().water)&&Arrays.equals(environment.snow,expected.environment().snow)&&Arrays.equals(environment.biome,expected.environment().biome),"environment preview equals build");check(e.preview.image.getWidth()==n,"preview image resolution");});
  }
  // Rapid size changes must not publish an obsolete worker's dimensions.
  SwingUtilities.invokeAndWait(()->{var e=app[0].graphEditor;e.mapSize.setSelectedItem("1024");e.refreshPreview();e.mapSize.setSelectedItem("128");e.queuePreview();});await(()->{var e=app[0].graphEditor;return e.previewResolution==128&&e.preview.image!=null&&e.preview.image.getWidth()==128&&e.worker.isDone();});
  System.out.println("PASS: 128/256/512 preview and build heights/environment match; rapid changes cancel stale 1024 preview");
 }finally{SwingUtilities.invokeAndWait(()->app[0].dispose());}System.exit(0);}
}
