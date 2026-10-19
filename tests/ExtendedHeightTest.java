import java.nio.file.*;import java.util.*;
public class ExtendedHeightTest{
 public static void main(String[]args)throws Exception{var w=new TerraForge.World(32,62,2024);Arrays.fill(w.height,512);if(w.h(8,8)!=512)throw new AssertionError("peak clipped");Path dir=Path.of(args[0]);TerraForge.writeWorld(w,dir);if(w.buildHeight!=640||!Files.exists(dir.resolve("datapacks/terraforge_height/data/terraforge/dimension_type/overworld512.json")))throw new AssertionError("custom height pack");System.out.println("PASS: Y512 terrain with 640-block dimension and bundled data pack");}
}
