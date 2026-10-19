import java.nio.file.*;import java.util.*;
public class EasyExportTest{
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("easy-export-");try{
   check(ExportDialog.destination(dir.toString()," My Map ").equals(dir.resolve("My Map")),"destination trims name");
   boolean bad=false;try{ExportDialog.destination(dir.toString(),"../overwrite");}catch(IllegalArgumentException e){bad=true;}check(bad,"invalid names rejected");
   Files.createDirectory(dir.resolve("TerraForge World"));check(ExportDialog.availableName(dir).equals("TerraForge World 2"),"unused default name");
   bad=false;try{ExportDialog.destination(dir.toString(),"TerraForge World");}catch(IllegalArgumentException e){bad=true;}check(bad,"existing world protected");
   var graph=TerrainGraph.template("Island mountains");var world=ExportDialog.build(graph,32,62,42,TerrainGenerator.Settings.legacy());Path target=ExportDialog.destination(dir.toString(),"New World");TerraForge.writeWorld(world,target);
   check(Files.size(target.resolve("level.dat"))>0&&Files.size(target.resolve("region/r.0.0.mca"))>8192,"build and export complete");
   var edged=ExportDialog.oceanEdges(world);check(edged.height[0]==edged.sea-24,"ocean border matches outside seabed");check(edged.height[16*32+16]==world.height[16*32+16],"interior unchanged");check(world.height[0]!=edged.height[0],"export border does not alter source map");
   check(ExportDialog.defaultSaves().endsWith(Path.of(".minecraft","saves")),"default saves path");
   System.out.println("PASS: direct saves destination, world names, collision protection and combined graph build/export");
  }finally{try(var paths=Files.walk(dir)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
 }
}
