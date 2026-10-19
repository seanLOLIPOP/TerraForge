import java.nio.file.*;
import java.awt.*;
import javax.swing.*;
public class OpenGraphTest {
 static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
 static <T> T find(Container root,Class<T> type){for(Component c:root.getComponents()){if(type.isInstance(c))return type.cast(c);if(c instanceof Container nested){T result=find(nested,type);if(result!=null)return result;}}return null;}
 public static void main(String[] args)throws Exception{
  Path examples=Path.of("outputs/TerraForge/examples");
  try(var paths=Files.list(examples)){for(Path p:paths.filter(p->p.toString().endsWith(".tgraph")||p.toString().endsWith(".terra")).toList())check(GraphEditor.readFile(p).output()>=0,"open "+p);}
  Path bad=Files.createTempFile("bad-graph-",".tgraph");Files.writeString(bad,"invalid");try{GraphEditor.readFile(bad);throw new AssertionError("invalid accepted");}catch(java.io.IOException expected){}finally{Files.delete(bad);}
  TerraForge.theme();TerraForge[] app={null};SwingUtilities.invokeAndWait(()->{app[0]=new TerraForge();app[0].setVisible(true);});
  try{
   for(int i=0;i<400&&app[0].busy;i++)Thread.sleep(50);
   SwingUtilities.invokeAndWait(()->{
    app[0].openNodes();GraphEditor editor=app[0].graphEditor;float[] terrain=app[0].world.height;
    check(editor.graphChooser().getCurrentDirectory().getName().equals("examples"),"picker starts in examples");
    javax.swing.Timer timer=new javax.swing.Timer(100,null);int[] phase={0};
    timer.addActionListener(e->{for(Window window:Window.getWindows())if(window.isShowing()&&window instanceof JDialog dialog&&dialog!=editor){
     if(phase[0]==0){JFileChooser chooser=find(dialog,JFileChooser.class);if(chooser!=null){chooser.setSelectedFile(examples.resolve("Repository-grove.terra").toAbsolutePath().toFile());phase[0]=1;chooser.approveSelection();return;}}
     else{JOptionPane pane=find(dialog,JOptionPane.class);if(pane!=null){phase[0]=2;pane.setValue(JOptionPane.OK_OPTION);timer.stop();return;}}
    }});timer.start();editor.openGraph();timer.stop();
    check(phase[0]==2,"file picker and confirmation completed");check(editor.graph.nodes.values().stream().anyMatch(n->n.type==TerrainGraph.Type.TREES),"project graph opened");check(app[0].world.height==terrain,"open keeps terrain");check(!editor.undo.isEmpty(),"open can undo");editor.undo();
   });
   System.out.println("PASS: all bundled graphs/projects, invalid file, actual Open graph dialog, project graph import and undo");
  }finally{SwingUtilities.invokeAndWait(()->{if(app[0].graphEditor!=null)app[0].graphEditor.dispose();app[0].dispose();});}System.exit(0);
 }
}
