import java.util.*;import java.io.*;import javax.swing.*;
public class RandomRiverTest {
 static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
 public static void main(String[] args)throws Exception{
  var graph=TerrainGraph.template("Coastal watershed");var river=graph.nodes.values().stream().filter(n->n.type==TerrainGraph.Type.RIVERS).findFirst().orElseThrow();
  var initial=graph.evaluate(128,128,63,2024,river.id,()->false);var repeat=graph.evaluate(128,128,63,2024,river.id,()->false);
  check(Arrays.equals(initial.environment().river,repeat.environment().river),"repeatable seed");
  river.values.put("riverSeed",1.0);var rerolled=graph.evaluate(128,128,63,2024,river.id,()->false);
  check(!Arrays.equals(initial.environment().river,rerolled.environment().river),"reroll changes rivers");check(Arrays.equals(initial.fields().get(river.input[0]),rerolled.fields().get(river.input[0])),"upstream terrain unchanged");
  var bytes=new ByteArrayOutputStream();graph.write(new DataOutputStream(bytes));var saved=TerrainGraph.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
  check(Arrays.equals(rerolled.output(),saved.evaluate(128,128,63,2024,river.id,()->false).output()),"saved result repeatable");
  river.values.remove("riverSeed");bytes.reset();graph.write(new DataOutputStream(bytes));var legacy=TerrainGraph.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));check(legacy.nodes.get(river.id).p("riverSeed")==0,"old graph keeps zero seed");
  TerraForge.theme();TerraForge[] app={null};SwingUtilities.invokeAndWait(()->{app[0]=new TerraForge();app[0].setVisible(true);});
  try{for(int i=0;i<400;i++){boolean[] ready={false};SwingUtilities.invokeAndWait(()->ready[0]=!app[0].busy&&app[0].world!=null);if(ready[0])break;Thread.sleep(50);}
   SwingUtilities.invokeAndWait(()->{var a=app[0];a.openNodes();var editor=a.graphEditor;editor.graph=graph.copy();editor.selected=river.id;editor.inspector();JButton button=null;for(var c:editor.properties.getComponents())if(c instanceof JButton b&&b.getText().equals("Randomize rivers"))button=b;check(button!=null,"randomize button visible");button.doClick();check(editor.graph.nodes.get(river.id).p("riverSeed")==1,"button increments seed");check(a.world.graph.nodes.get(river.id).p("riverSeed")==1,"draft saves seed");editor.undo();check(editor.graph.nodes.get(river.id).p("riverSeed")==0,"reroll undo");editor.redo();check(editor.graph.nodes.get(river.id).p("riverSeed")==1,"reroll redo");});
  }finally{SwingUtilities.invokeAndWait(()->{if(app[0].graphEditor!=null)app[0].graphEditor.dispose();app[0].dispose();});}
  System.out.println("PASS: river reroll, fixed terrain, repeatability, persistence, legacy graphs, desktop button and undo/redo");System.exit(0);
 }
}
