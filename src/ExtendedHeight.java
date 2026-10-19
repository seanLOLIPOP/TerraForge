import java.nio.file.*;import java.io.*;
final class ExtendedHeight{
 static int forPeak(double peak){return peak>560?1152:peak>300?640:384;}
 static int heightmapBits(int height){return 32-Integer.numberOfLeadingZeros(height);}
 static void configure(TerraForge.World world){for(float h:world.height)world.buildHeight=Math.max(world.buildHeight,forPeak(h));}
 static String type(int height){return height>640?"terraforge:overworld1024":"terraforge:overworld512";}
 static void writePack(Path world)throws IOException{writePack(world,640);}
 static void writePack(Path world,int height)throws IOException{
  Path pack=world.resolve("datapacks/terraforge_height"),types=pack.resolve("data/terraforge/dimension_type");Files.createDirectories(types);
  Files.writeString(pack.resolve("pack.mcmeta"),"{\"pack\":{\"pack_format\":26,\"description\":\"TerraForge extended height: Y -64 to "+(height-65)+"\"}}");
  Files.writeString(types.resolve(height>640?"overworld1024.json":"overworld512.json"),"{\"ambient_light\":0.0,\"bed_works\":true,\"coordinate_scale\":1.0,\"effects\":\"minecraft:overworld\",\"has_ceiling\":false,\"has_raids\":true,\"has_skylight\":true,\"height\":"+height+",\"infiniburn\":\"#minecraft:infiniburn_overworld\",\"logical_height\":"+height+",\"min_y\":-64,\"monster_spawn_block_light_limit\":0,\"monster_spawn_light_level\":{\"type\":\"minecraft:uniform\",\"value\":{\"min_inclusive\":0,\"max_inclusive\":7}},\"natural\":true,\"piglin_safe\":false,\"respawn_anchor_works\":false,\"ultrawarm\":false}");
 }
}
