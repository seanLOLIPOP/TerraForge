import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;

public class TerrainGraphTest {
    static void check(boolean v,String name){if(!v)throw new AssertionError(name);}
    static float[] evaluate(TerrainGraph g){return g.evaluate(32,32,63,2024,g.output(),()->false).output();}
    static void expectInvalid(Runnable r,String message){boolean failed=false;try{r.run();}catch(IllegalArgumentException expected){failed=true;}check(failed,message);}
    static TerrainGraph constant(double value){TerrainGraph g=new TerrainGraph();var n=g.add(TerrainGraph.Type.CONSTANT,0,0);n.values.put("height",value);var o=g.add(TerrainGraph.Type.OUTPUT,300,0);g.connect(n.id,o.id,0);return g;}
    static byte[] bytes(TerrainGraph g)throws Exception{ByteArrayOutputStream bytes=new ByteArrayOutputStream();g.write(new DataOutputStream(bytes));return bytes.toByteArray();}
    public static void main(String[] args)throws Exception {
        for(String name:new String[]{"Island mountains","Terraced ranges","Masked mountains"}){TerrainGraph g=TerrainGraph.template(name);float[] a=evaluate(g);check(Arrays.equals(a,evaluate(g)),"deterministic template");for(float f:a)check(Float.isFinite(f)&&f>=-60&&f<=300,"bounded heights");}
        TerrainGraph g=constant(100);var source=g.nodes.get(1);var output=g.nodes.get(2);check(evaluate(g)[0]==100,"constant and output");
        var remap=g.add(TerrainGraph.Type.REMAP,0,0);g.connect(source.id,remap.id,0);remap.values.put("scale",2.0);remap.values.put("offset",10.0);g.connect(remap.id,output.id,0);check(evaluate(g)[0]==147,"height remap about sea");
        int old=remap.input[0];expectInvalid(()->g.connect(remap.id,remap.id,0),"self cycle");check(remap.input[0]==old,"rejected wire rolls back");var clamp=g.add(TerrainGraph.Type.CLAMP,0,0);g.connect(remap.id,clamp.id,0);expectInvalid(()->g.connect(clamp.id,remap.id,0),"multi-node cycle");clamp.values.put("max",120.0);g.connect(clamp.id,output.id,0);check(evaluate(g)[0]==120,"clamp operation");
        var mask=g.add(TerrainGraph.Type.HEIGHT_MASK,0,0);g.connect(source.id,mask.id,0);mask.values.put("min",90.0);mask.values.put("max",110.0);float m=g.evaluate(32,32,63,0,mask.id,()->false).output()[0];check(Math.abs(m-.5)<1e-6,"height mask normalization");expectInvalid(()->g.connect(mask.id,output.id,0),"mask to terrain rejected");
        var b=g.add(TerrainGraph.Type.CONSTANT,0,0);b.values.put("height",200.0);var blend=g.add(TerrainGraph.Type.BLEND,0,0);g.connect(source.id,blend.id,0);g.connect(b.id,blend.id,1);g.connect(mask.id,blend.id,2);g.connect(blend.id,output.id,0);blend.values.put("mix",1.0);check(evaluate(g)[0]==150,"masked blend");expectInvalid(()->g.connect(source.id,blend.id,2),"terrain to mask rejected");
        blend.input[2]=-1;for(int mode=0;mode<=5;mode++){blend.values.put("mode",(double)mode);check(Float.isFinite(evaluate(g)[0]),"combine mode "+mode);}
        var slope=g.add(TerrainGraph.Type.SLOPE_MASK,0,0);g.connect(source.id,slope.id,0);float[] slopeData=g.evaluate(32,32,63,0,slope.id,()->false).output();check(slopeData[50]==0,"flat slope mask");
        var terrace=g.add(TerrainGraph.Type.TERRACE,0,0);g.connect(remap.id,terrace.id,0);g.connect(terrace.id,output.id,0);check(evaluate(g)[0]<147,"terracing");
        boolean cancelled=false;try{g.evaluate(32,32,63,0,g.output(),()->true);}catch(java.util.concurrent.CancellationException expected){cancelled=true;}check(cancelled,"preview cancellation");
        var copy=TerrainGraph.read(new DataInputStream(new ByteArrayInputStream(bytes(g))));check(Arrays.equals(evaluate(g),evaluate(copy)),"graph serialization preserves result");check(Arrays.equals(bytes(g),bytes(copy)),"serialization preserves layout and parameters");
        Path temp=Files.createTempDirectory("terrain-graph-test-");Path graphFile=temp.resolve("recipe.tgraph");GraphEditor.writeFile(g,graphFile);check(Arrays.equals(bytes(g),bytes(GraphEditor.readFile(graphFile))),"standalone graph round trip");
        TerraForge.World world=TerraForge.generateWorld(32,63,2024,85,"Island");world.graph=g.copy();Path project=temp.resolve("project.terra");TerraForge.writeProject(world,project);var restored=TerraForge.readProject(project);check(Arrays.equals(bytes(g),bytes(restored.graph)),"project contains editable graph");world.copy().graph.nodes.get(1).values.put("height",50.0);check(world.graph.nodes.get(1).p("height")==100,"snapshot graph is independent");
        var draft=g.copy();draft.remove(source.id);GraphEditor.writeFile(draft,graphFile);var loadedDraft=GraphEditor.readFile(graphFile);expectInvalid(()->evaluate(loadedDraft),"incomplete drafts save but do not build");
        var bad=g.copy();bad.nodes.get(output.id).input[0]=999;boolean invalid=false;try{TerrainGraph.read(new DataInputStream(new ByteArrayInputStream(bytes(bad))));}catch(IOException expected){invalid=true;}check(invalid,"corrupt graph references rejected");
        // Version 2 projects remain readable even after adding graph data to version 3.
        Path v2=temp.resolve("old-v2.terra");try(DataOutputStream d=new DataOutputStream(new java.util.zip.GZIPOutputStream(Files.newOutputStream(v2)))){d.writeUTF("TERRAFORGE2");d.writeInt(world.size);d.writeInt(world.sea);d.writeLong(world.seed);for(float h:world.height)d.writeFloat(h);d.write(world.material);world.settings.write(d);d.writeUTF(world.recipeShape);d.writeInt(world.recipeRelief);}check(TerraForge.readProject(v2).graph==null,"version 2 compatibility");
        for(Path p:new Path[]{graphFile,project,v2})Files.delete(p);Files.delete(temp);
        System.out.println("PASS: node evaluation, masks, six combine modes, port types, cycles, cancellation, drafts, standalone/project persistence and legacy v2 projects");
        if(args.length>0&&args[0].equals("--no-ui"))return;
        TerraForge.theme();TerraForge[] owner=new TerraForge[1];SwingUtilities.invokeAndWait(()->{owner[0]=new TerraForge();owner[0].setVisible(true);});await(()->!owner[0].busy&&owner[0].world!=null);SwingUtilities.invokeAndWait(()->owner[0].openNodes());await(()->!owner[0].graphEditor.fields.isEmpty());
        final TerrainGraph[] built=new TerrainGraph[1];SwingUtilities.invokeAndWait(()->{var app=owner[0];GraphEditor editor=app.graphEditor;try{
            check(editor.isVisible(),"node editor opens");var first=editor.graph.nodes.values().iterator().next();var sink=editor.graph.nodes.get(editor.graph.output());int prior=sink.input[0];click(editor,editor.canvas.port(first,-1));click(editor,editor.canvas.port(sink,0));check(sink.input[0]==first.id,"wire through mouse ports");editor.undo();check(editor.graph.nodes.get(sink.id).input[0]==prior,"graph undo");editor.redo();check(editor.graph.nodes.get(sink.id).input[0]==first.id,"graph redo");
            editor.undo();editor.palette.setSelectedItem(TerrainGraph.Type.CONSTANT);editor.addNode();int added=editor.selected;check(editor.graph.nodes.containsKey(added)&&app.world.graph.nodes.containsKey(added),"add node updates project draft");editor.deleteSelected();check(!editor.graph.nodes.containsKey(added),"delete node");editor.undo();check(editor.graph.nodes.containsKey(added),"undo delete");
            app.size.setSelectedItem("128");app.dirty=false;built[0]=editor.graph.copy();app.buildGraph(built[0]);
        }catch(Throwable e){app.dispose();throw e;}});
        await(()->!owner[0].busy);SwingUtilities.invokeAndWait(()->{var app=owner[0];try{check(app.world.graph!=null&&app.world.size==128,"full graph build installs map");float[] expected=built[0].evaluate(128,128,app.world.sea,app.world.seed,built[0].output(),()->false).output();check(Arrays.equals(expected,app.world.height),"built map equals graph output");System.out.println("PASS: live preview, mouse wiring, graph undo/redo, node add/delete, project draft and full-resolution build");}finally{app.dispose();}});System.exit(0);
    }
    interface Condition{boolean get();}
    static void await(Condition condition)throws Exception{for(int i=0;i<200;i++){boolean[] ready={false};SwingUtilities.invokeAndWait(()->ready[0]=condition.get());if(ready[0])return;Thread.sleep(50);}throw new AssertionError("UI operation timed out");}
    static void click(GraphEditor e,java.awt.geom.Point2D point){int x=(int)Math.round(point.getX()*e.canvas.zoom+e.canvas.panX),y=(int)Math.round(point.getY()*e.canvas.zoom+e.canvas.panY);e.canvas.dispatchEvent(new MouseEvent(e.canvas,MouseEvent.MOUSE_PRESSED,0,0,x,y,1,false,MouseEvent.BUTTON1));e.canvas.dispatchEvent(new MouseEvent(e.canvas,MouseEvent.MOUSE_RELEASED,1,0,x,y,1,false,MouseEvent.BUTTON1));}
}
