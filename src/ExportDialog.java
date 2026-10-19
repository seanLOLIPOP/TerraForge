import java.awt.*;
import java.nio.file.*;
import javax.swing.*;

/** Export directly to Minecraft with an optional graph build in the same job. */
final class ExportDialog {
 static Path defaultSaves(){return Path.of(System.getProperty("user.home"),".minecraft","saves");}
 static Path destination(String folder,String name){
  String clean=name.trim();if(clean.isEmpty()||!clean.matches("[A-Za-z0-9 _-]+"))throw new IllegalArgumentException("Enter a world name using letters, numbers, spaces, underscores or hyphens.");
  if(folder.isBlank())throw new IllegalArgumentException("Choose a saves folder.");Path parent=Path.of(folder.trim()).toAbsolutePath().normalize();Path dest=parent.resolve(clean);
  if(Files.exists(parent)&&!Files.isDirectory(parent))throw new IllegalArgumentException("The saves location must be a folder.");
  if(Files.exists(dest))throw new IllegalArgumentException("That world already exists. Enter a different name.");return dest;
 }
 static String availableName(Path folder){String base="TerraForge World",name=base;for(int i=2;Files.exists(folder.resolve(name));i++)name=base+" "+i;return name;}
 static TerraForge.World build(TerrainGraph graph,int size,int sea,long seed,TerrainGenerator.Settings settings){
  var result=graph.evaluate(size,size,sea,seed,graph.output(),()->Thread.currentThread().isInterrupted());var w=new TerraForge.World(size,sea,seed);w.height=result.output();w.environment=result.environment();w.graph=graph;w.settings=settings;return w;
 }
 static TerraForge.World oceanEdges(TerraForge.World source){
  var w=source.copy();int n=w.size;double width=Math.max(4,Math.min(64,n/12.0));
  w.environment.height=w.height;if(w.environment.water==null)w.environment.water=EnvironmentMap.filled(n*n,w.sea);
  for(int z=0;z<n;z++)for(int x=0;x<n;x++){int distance=Math.min(Math.min(x,z),Math.min(n-1-x,n-1-z));if(distance>=width)continue;int i=z*n+x;double t=TerraForge.smooth(distance/width);w.height[i]=(float)((w.sea-24)*(1-t)+w.height[i]*t);w.environment.water[i]=(float)(w.sea+(w.environment.water[i]-w.sea)*t);if(w.height[i]<=w.environment.water[i]){w.material[i]=0;if(w.environment.material!=null)w.environment.material[i]=2;if(w.environment.snow!=null)w.environment.snow[i]=0;if(w.environment.ice!=null)w.environment.ice[i]=0;}}
  w.environment.trees=w.environment.trees.stream().filter(p->p.x()>width&&p.z()>width&&p.x()<n-width&&p.z()<n-width).toList();w.treeHeights=null;return w;
 }
 static void show(TerraForge app){show(app,false);}
 static void show(TerraForge app,boolean forceBuild){
  Path saves=defaultSaves();JTextField name=new JTextField(availableName(saves),24),folder=new JTextField(saves.toString(),28);
  JPanel form=new JPanel();form.setLayout(new BoxLayout(form,BoxLayout.Y_AXIS));form.add(new JLabel("Export for Minecraft Java 1.20.4"));form.add(Box.createVerticalStrut(12));form.add(new JLabel("World name"));form.add(name);form.add(Box.createVerticalStrut(10));form.add(new JLabel("Minecraft saves folder"));
  JPanel location=new JPanel(new BorderLayout(6,0));location.add(folder);JButton browse=new JButton("Browse…");location.add(browse,BorderLayout.EAST);form.add(location);
  browse.addActionListener(e->{JFileChooser chooser=new JFileChooser();chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);chooser.setDialogTitle("Choose Minecraft saves folder");Path path=Path.of(folder.getText());if(Files.isDirectory(path))chooser.setCurrentDirectory(path.toFile());if(chooser.showOpenDialog(form)==JFileChooser.APPROVE_OPTION)folder.setText(chooser.getSelectedFile().toString());});
  JCheckBox build=new JCheckBox("Build latest node graph before export",forceBuild||Integer.parseInt((String)app.size.getSelectedItem())!=app.world.size);build.setEnabled(app.world.graph!=null);form.add(Box.createVerticalStrut(10));form.add(build);form.add(new JLabel("Unchecked: export the current map, including painted edits."));if("16384".equals(app.size.getSelectedItem()))form.add(new JLabel("16K graph export uses region mode with a shared 2048 layout."));JCheckBox ocean=new JCheckBox("Blend map edges into surrounding ocean",app.world.size<=2048);form.add(ocean);form.add(Box.createVerticalStrut(10));form.add(new JLabel("Default folder: your world appears in Minecraft Singleplayer."));form.add(new JLabel("Use Browse for a separate launcher instance."));
  while(JOptionPane.showOptionDialog(app,form,"Export world",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE,null,new String[]{"Export now","Cancel"},"Export now")==0){
   try{
    Path target=destination(folder.getText(),name.getText());TerrainGraph recipe=build.isSelected()?app.world.graph.copy():null;
    if(recipe!=null){recipe.validateStructure();recipe.requireInputs(recipe.output(),new java.util.HashSet<>());}
    TerraForge.World current=recipe==null?(app.world.size>2048?app.world:app.world.copy()):null;int size=Integer.parseInt((String)app.size.getSelectedItem()),sea=app.sea.getValue();long seed=app.seedValue();var settings=app.generationSettings();boolean blendEdges=ocean.isSelected();
    app.task(recipe==null?"Exporting Minecraft world…":"Building nodes and exporting Minecraft world…",()->{
     if(recipe!=null&&size==16384){LargeWorldBuilder.exportGraph(target,recipe,seed,sea);}else{TerraForge.World exportWorld=recipe==null?current:build(recipe,size,sea,seed,settings);if(blendEdges&&exportWorld.size<=2048)exportWorld=oceanEdges(exportWorld);TerraForge.writeWorld(exportWorld,target);}
     SwingUtilities.invokeLater(()->{
      Object[] options={"Done","Open world folder"};int choice=JOptionPane.showOptionDialog(app,"World exported successfully.\n\n"+target+"\n\nOpen Minecraft Java 1.20.4 → Singleplayer.\nIf you chose another folder, use that launcher's instance.","World ready",JOptionPane.DEFAULT_OPTION,JOptionPane.INFORMATION_MESSAGE,null,options,options[0]);
      if(choice==1)try{Desktop.getDesktop().open(target.toFile());}catch(Exception e){JOptionPane.showMessageDialog(app,"World saved at:\n"+target,"World folder",JOptionPane.INFORMATION_MESSAGE);}
     });
    });return;
   }catch(IllegalArgumentException e){JOptionPane.showMessageDialog(app,e.getMessage(),"Check export settings",JOptionPane.WARNING_MESSAGE);}
  }
 }
}
