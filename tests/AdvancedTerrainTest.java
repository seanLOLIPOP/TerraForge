import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.swing.*;

public class AdvancedTerrainTest {
    static void check(boolean test,String label){if(!test)throw new AssertionError(label);}
    static int land(TerraForge.World w){int count=0;for(float h:w.height)if(h>w.sea)count++;return count;}
    static TerrainGenerator.Settings settings(int index,int value){int[] a={3,6,50,35,30,80,100,0,45,0,0,3,145,4,0};a[index]=value;return new TerrainGenerator.Settings(a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],a[8],a[9],a[10],a[11],a[12],a[13],a[14],true);}
    public static void main(String[] args)throws Exception {
        var defaults=TerrainGenerator.Settings.defaults();
        TerraForge.World base=TerrainGenerator.generate(64,63,2024,100,"Island",defaults);
        for(int[] option:new int[][]{{0,7},{1,3},{2,75},{3,95},{4,90},{5,45},{6,160},{8,70},{9,12},{10,90},{14,20}}){
            var generated=TerrainGenerator.generate(64,63,2024,100,"Island",settings(option[0],option[1]));
            check(!Arrays.equals(base.height,generated.height),"terrain control affects output: "+option[0]);
            for(float h:generated.height)check(Float.isFinite(h)&&h>=-60&&h<=300,"bounded output");
        }
        var stretched=settings(6,160);
        var rotated=new TerrainGenerator.Settings(3,6,50,35,30,80,160,65,45,0,0,3,145,4,0,true);
        check(!Arrays.equals(TerrainGenerator.generate(64,63,2024,100,"Island",stretched).height,TerrainGenerator.generate(64,63,2024,100,"Island",rotated).height),"rotation affects elongated islands");
        check(land(TerrainGenerator.generate(128,63,2024,100,"Island",settings(5,95)))>land(TerrainGenerator.generate(128,63,2024,100,"Island",settings(5,40))),"larger islands have more land");
        for(String name:new String[]{"Balanced island","Alpine ranges","Archipelago","Mesa plateaus","Rolling plains"}){
            var p=TerrainGenerator.preset(name);var a=TerrainGenerator.generate(64,p.sea(),42,p.relief(),p.shape(),p.settings());var b=TerrainGenerator.generate(64,p.sea(),42,p.relief(),p.shape(),p.settings());
            check(Arrays.equals(a.height,b.height),"preset determinism: "+name);check(land(a)>0,"preset has land: "+name);
        }
        TerraForge.World flat=new TerraForge.World(32,63,8);Arrays.fill(flat.height,100);flat.settings=settings(12,90);check(TerrainGenerator.surface(flat,10,10)==4,"snow line");flat.settings=settings(11,12);Arrays.fill(flat.height,70);check(TerrainGenerator.surface(flat,10,10)==2,"beach band");
        Arrays.fill(flat.height,100);flat.settings=defaults;flat.height[10*32+11]=120;check(TerrainGenerator.surface(flat,10,10)==3,"slope rock");flat.material[10*32+10]=1;check(TerrainGenerator.surface(flat,10,10)==1,"paint overrides automatic materials");
        Path temp=Files.createTempDirectory("advanced-terrain-test-");
        base.recipeShape="Archipelago";base.recipeRelief=125;base.settings=settings(3,95);Path modern=temp.resolve("new.terra");TerraForge.writeProject(base,modern);var restored=TerraForge.readProject(modern);
        check(restored.settings.equals(base.settings)&&restored.recipeShape.equals(base.recipeShape)&&restored.recipeRelief==125,"complete recipe round trip");
        check(restored.copy().settings.equals(base.settings),"snapshot copy keeps settings");
        Path old=temp.resolve("old.terra");try(DataOutputStream d=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(old)))){d.writeUTF("TERRAFORGE1");d.writeInt(base.size);d.writeInt(base.sea);d.writeLong(base.seed);for(float h:base.height)d.writeFloat(h);d.write(base.material);}
        var legacy=TerraForge.readProject(old);check(Arrays.equals(legacy.height,base.height)&&!legacy.settings.rockSlopes(),"legacy projects preserve heights and surface rules");
        Path invalid=temp.resolve("invalid.terra");try(DataOutputStream d=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(invalid)))){d.writeUTF("TERRAFORGE2");d.writeInt(base.size);d.writeInt(base.sea);d.writeLong(base.seed);for(float h:base.height)d.writeFloat(h);d.write(base.material);d.writeInt(999);}
        boolean rejected=false;try{TerraForge.readProject(invalid);}catch(IOException expected){rejected=true;}check(rejected,"malformed settings rejected");
        for(Path f:new Path[]{modern,old,invalid})Files.delete(f);Files.delete(temp);
        System.out.println("PASS: generation controls, all presets, bounds, island coverage, automatic materials, recipe persistence and legacy projects");
        if(args.length>0&&args[0].equals("--no-ui"))return;
        TerraForge.theme();TerraForge[] frame=new TerraForge[1];SwingUtilities.invokeAndWait(()->{frame[0]=new TerraForge();frame[0].setVisible(true);});
        for(int t=0;t<100;t++){boolean[] ready={false};SwingUtilities.invokeAndWait(()->ready[0]=!frame[0].busy&&frame[0].world!=null);if(ready[0])break;Thread.sleep(100);}
        SwingUtilities.invokeAndWait(()->{var f=frame[0];try{
            check(!f.busy&&f.world!=null,"desktop startup");float[] before=f.world.height.clone();f.preset.setSelectedItem("Alpine ranges");check(f.generationSettings().equals(TerrainGenerator.preset("Alpine ranges").settings()),"preset populates advanced controls");check(Arrays.equals(before,f.world.height),"preset does not replace current map");f.ridges.setValue(65);check(f.preset.getSelectedItem().equals("Custom"),"manual settings mark custom");f.setGenerationSettings(restored.settings);check(f.generationSettings().equals(restored.settings),"UI settings round trip");
            System.out.println("PASS: preset UI, pending settings safety, custom indication and control round trip");
        }finally{f.dispose();}});System.exit(0);
    }
}
