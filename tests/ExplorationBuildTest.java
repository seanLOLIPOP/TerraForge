import java.nio.file.*;
public class ExplorationBuildTest {
    static long count(Path folder)throws Exception{try(var files=Files.list(folder.resolve("region"))){return files.filter(p->p.toString().endsWith(".mca")).count();}}
    static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("terraforge-exploration-");System.setProperty("terraforge.regionBuilds",root.resolve("builds").toString());System.setProperty("terraforge.regionCache",root.resolve("cache").toString());System.setProperty("terraforge.gpu","cpu");
        var graph=TerrainGraph.template("Island mountains");var world=RegionMap.build(graph,4242,62,2048,x->{},()->false,true);var map=world.region;
        check(count(map.folder)==9,"only initial nine regions built");int missing=-1;for(int i=0;i<16;i++)if(!Files.exists(map.folder.resolve("region/r."+i%4+"."+i/4+".mca"))){missing=i;break;}
        check(missing>=0,"unbuilt area exists");map.tile(missing%4,missing/4);check(count(map.folder)==10,"requested region saved");map.finish();check(count(map.folder)==16,"finish completes every region");map.copyWorld(root.resolve("Export"));check(count(root.resolve("Export"))==16,"export is complete");
        check(RegionOrder.near(4,2,1)[0]==6,"nearby regions first");check(LargeWorldBuilder.REGION_LIMIT==0,"build mode restored");System.out.println("PASS: nearby partial build, on-demand persistence, complete export and restored build state");
    }
}
