import javax.swing.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;

public class RegionBatchControlsTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        String previous=System.getProperty("terraforge.regionThreads");System.clearProperty("terraforge.regionThreads");
        TerraForge.theme();TerraForge[] frame={null};
        SwingUtilities.invokeAndWait(()->{frame[0]=new TerraForge();frame[0].setVisible(true);});
        try{
            boolean ready=false;for(int i=0;i<200;i++){
                boolean[] done={false};SwingUtilities.invokeAndWait(()->done[0]=!frame[0].busy&&frame[0].world!=null);
                if(done[0]){ready=true;break;}Thread.sleep(50);
            }
            check(ready,"editor generation ready");
            SwingUtilities.invokeAndWait(()->{
                var app=frame[0];app.openNodes();var editor=app.graphEditor;
                editor.previewTouched=true;editor.previewFull.setSelected(false);editor.regionTouched=true;editor.regionMode.setSelected(true);
                editor.mapSize.setSelectedItem("2048");editor.updateBuildMode();
                check(editor.regionBatch.isEnabled(),"batch control enabled for disk-backed builds");
                check("Auto".equals(editor.regionBatch.getSelectedItem()),"automatic batch default");
                editor.regionBatch.setSelectedItem("4");check("4".equals(System.getProperty("terraforge.regionThreads")),"manual batch bound applied");
                check(LargeWorldBuilder.workers()<=4,"manual setting respects worker cap");
                editor.regionBatch.setSelectedItem("Auto");check(System.getProperty("terraforge.regionThreads")==null,"Auto clears manual override");
                editor.mapSize.setSelectedItem("512");editor.updateBuildMode();check(!editor.regionBatch.isEnabled(),"no region batches on small maps");
                editor.setBusy(true);editor.setBusy(false);check(!editor.regionBatch.isEnabled(),"busy state preserves disabled controls");
                editor.mapSize.setSelectedItem("2048");editor.updateBuildMode();
                editor.validate();editor.repaint();
                try{
                    var image=new BufferedImage(editor.getWidth(),editor.getHeight(),BufferedImage.TYPE_INT_RGB);
                    var graphics=image.createGraphics();editor.paint(graphics);graphics.dispose();
                    Path output=Path.of("dist/region-batch-controls.png");Files.createDirectories(output.getParent());javax.imageio.ImageIO.write(image,"png",output.toFile());
                }catch(java.io.IOException error){throw new RuntimeException(error);}
                System.out.println("PASS: Auto/manual batch settings, CPU/memory caps, region-only enablement, busy-state restore, and node toolbar rendering");
            });
        }finally{
            SwingUtilities.invokeAndWait(()->frame[0].dispose());
            if(previous==null)System.clearProperty("terraforge.regionThreads");else System.setProperty("terraforge.regionThreads",previous);
        }
        System.exit(0);
    }
}
