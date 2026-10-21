import java.nio.file.*;
class RegionExportSizeTest{
 static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
 public static void main(String[] a)throws Exception{
  Path dir=Files.createTempDirectory("tf-region-size"),dest=dir.resolve("World2048");
  var graph=TerrainGraph.template("Island mountains");
  LargeWorldBuilder.exportGraph(dest,graph,4242,62,2048);
  long files;try(var s=Files.list(dest.resolve("region"))){files=s.filter(p->p.toString().endsWith(".mca")).count();}
  check(files==16,"2048 map is 4 x 4 regions: "+files);
  check(Files.exists(dest.resolve("level.dat")),"level.dat written");
  String settings=Files.readString(dest.resolve("export-settings.json"));
  check(settings.contains("\"size\":2048")&&settings.contains("\"seed\":4242")&&settings.contains("\"regions\":16"),"settings record the chosen size and seed: "+settings);
  check(Files.readString(dest.resolve("build-details.txt")).contains("Regions=16"),"build details record region count");
  check(LargeWorldBuilder.SIZE==16384&&LargeWorldBuilder.GRID==2048,"export restores the default 16384 size afterwards");
  // resume: a deleted region is rebuilt, existing ones are kept byte-for-byte
  Path keep=dest.resolve("region/r.0.0.mca"),gone=dest.resolve("region/r.3.3.mca");byte[] before=Files.readAllBytes(keep);long stamp=Files.getLastModifiedTime(keep).toMillis();
  Files.delete(gone);LargeWorldBuilder.exportGraph(dest,graph,4242,62,2048);
  check(Files.exists(gone),"missing region rebuilt on resume");
  check(java.util.Arrays.equals(before,Files.readAllBytes(keep))&&Files.getLastModifiedTime(keep).toMillis()==stamp,"finished regions are not rewritten on resume");
  boolean rejected=false;try{LargeWorldBuilder.configure(5000);}catch(IllegalArgumentException e){rejected=true;}
  check(rejected,"sizes must be multiples of 512");
  System.out.println("PASS: region export at a chosen size (2048 = 16 regions), settings recorded, default restored, resume rebuilds only missing regions");
 }
}
