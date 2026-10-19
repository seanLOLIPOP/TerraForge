import javax.swing.*;
public class LargeBuildRouteTest{
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 public static void main(String[]args)throws Exception{
  TerraForge.theme();TerraForge[] app={null};int[] calls={0};
  SwingUtilities.invokeAndWait(()->{app[0]=new TerraForge(){private static final long serialVersionUID=1L;void buildGraph(TerrainGraph recipe){check("16384".equals(size.getSelectedItem()),"selected size reaches build action");calls[0]++;}};app[0].setVisible(true);});
  try{for(int i=0;i<400;i++){boolean[] ready={false};SwingUtilities.invokeAndWait(()->ready[0]=!app[0].busy&&app[0].world!=null);if(ready[0])break;Thread.sleep(50);}
   SwingUtilities.invokeAndWait(()->{var a=app[0];a.openNodes();var e=a.graphEditor;e.mapSize.setSelectedItem("16384");check(e.buildMapButton.getText().equals("Build into map"),"large build keeps editor action");check(e.buildHint.getText().contains("opens in editor"),"large build guidance");e.buildMapButton.doClick();check(calls[0]==1,"button invokes editor build");});
   System.out.println("PASS: 16K size keeps Build into map and invokes the editor build action");
  }finally{SwingUtilities.invokeAndWait(()->{if(app[0].graphEditor!=null)app[0].graphEditor.dispose();app[0].dispose();});}System.exit(0);
 }
}
