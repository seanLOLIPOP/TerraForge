import java.nio.file.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.swing.*;

/** Run on a desktop: java -cp classes:test-classes TerraForgeTest */
public class TerraForgeTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static double sum(float[] a){double v=0;for(float x:a)v+=x;return v;}
    static double rough(TerraForge.World w){double r=0;for(int z=1;z<w.size-1;z++)for(int x=1;x<w.size-1;x++){int i=z*w.size+x;r+=Math.abs(w.height[i]-w.height[i+1])+Math.abs(w.height[i]-w.height[i+w.size]);}return r;}
    public static void main(String[] args)throws Exception{
        TerraForge.World w=TerraForge.generateWorld(64,63,45,150,"Highlands");
        check(Arrays.equals(w.height,TerraForge.generateWorld(64,63,45,150,"Highlands").height),"seed reproducibility");
        float[] initial=w.height.clone();double mass=sum(w.height),r=rough(w);TerraForge.thermalErode(w,30);
        check(Math.abs(sum(w.height)-mass)<.1,"erosion conserves material");check(rough(w)<r,"erosion reduces steep slopes");check(!Arrays.equals(initial,w.height),"erosion changes terrain");
        Arrays.fill(w.height,100);TerraForge.applyBrush(w,32,32,8,3,"River",100);check(w.h(32,32)<w.sea,"river underwater");check(w.h(0,0)==100,"brush locality");check(w.material[32*64+32]==2,"river bed sand");
        Arrays.fill(w.height,299);TerraForge.applyBrush(w,32,32,8,12,"Raise",299);check(w.h(32,32)==300,"brush height limit");
        TerraForge.applyBrush(w,32,32,8,3,"Natural",100);check(w.material[32*64+32]==0,"natural material reset");
        Path temp=Files.createTempDirectory("terraforge-tests-");Path file=temp.resolve("project.terra");TerraForge.writeProject(w,file);TerraForge.World restored=TerraForge.readProject(file);
        check(Arrays.equals(w.height,restored.height)&&Arrays.equals(w.material,restored.material)&&w.seed==restored.seed,"project round trip");
        Files.write(file,new byte[]{0,1,2});boolean rejected=false;try{TerraForge.readProject(file);}catch(java.io.IOException expected){rejected=true;}check(rejected,"reject corrupt project");Files.delete(file);Files.delete(temp);
        System.out.println("PASS: deterministic terrain, conservative erosion, river/material/height brushes, project round trip and corrupt-file rejection");
        if(args.length>0&&args[0].equals("--no-ui"))return;
        TerraForge.theme();TerraForge[] frame=new TerraForge[1];SwingUtilities.invokeAndWait(()->{frame[0]=new TerraForge();frame[0].setVisible(true);});
        for(int t=0;t<100;t++){boolean[] ready={false};SwingUtilities.invokeAndWait(()->ready[0]=!frame[0].busy&&frame[0].world!=null);if(ready[0])break;Thread.sleep(100);}
        SwingUtilities.invokeAndWait(()->{TerraForge f=frame[0];try{
            check(!f.busy&&f.world!=null,"generation completes");f.world=new TerraForge.World(128,63,1);Arrays.fill(f.world.height,100);f.undo.clear();f.redo.clear();f.map.fit();f.map.invalidateTerrain();
            f.checkpoint();TerraForge.applyBrush(f.world,64,64,8,3,"Raise",100);f.changed();float raised=f.world.height[64*128+64];f.undo();check(f.world.height[64*128+64]==100,"undo");f.redo();check(f.world.height[64*128+64]==raised,"redo");
            f.map.fit();int px=f.map.getWidth()/2+40,py=f.map.getHeight()/2+25;Point before=f.map.point(px,py);f.map.zoomAt(2,px,py);Point after=f.map.point(px,py);check(before.distance(after)<=1.5,"cursor anchored zoom");
            f.map.fit();f.world=new TerraForge.World(128,63,1);Arrays.fill(f.world.height,100);f.radius.setValue(3);f.brush.setSelectedItem("Raise");int startX=f.map.ox()+f.map.extent()/4,startY=f.map.oy()+f.map.extent()/2,endX=f.map.ox()+f.map.extent()*3/4;
            f.map.dispatchEvent(new MouseEvent(f.map,MouseEvent.MOUSE_PRESSED,0,0,startX,startY,1,false,MouseEvent.BUTTON1));f.map.dispatchEvent(new MouseEvent(f.map,MouseEvent.MOUSE_DRAGGED,1,InputEvent.BUTTON1_DOWN_MASK,endX,startY,0,false,MouseEvent.NOBUTTON));f.map.dispatchEvent(new MouseEvent(f.map,MouseEvent.MOUSE_RELEASED,2,0,endX,startY,1,false,MouseEvent.BUTTON1));
            for(int x=33;x<95;x++)check(f.world.height[64*128+x]>100,"continuous stroke at "+x);
            BufferedImage image=new BufferedImage(f.getWidth(),f.getHeight(),BufferedImage.TYPE_INT_RGB);f.perspective.setSelected(true);f.map.yaw=.7;f.paint(image.getGraphics());check(f.map.terrainImage()==f.map.terrainImage(),"cached terrain image");
            System.out.println("PASS: desktop startup, undo/redo, cursor zoom, interpolated strokes, orbit-view rendering and image cache");
        }finally{f.dispose();}});System.exit(0);
    }
}
