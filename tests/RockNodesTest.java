import java.util.*;import java.io.*;
public class RockNodesTest{
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
 public static void main(String[] args)throws Exception{
  var graph=new TerrainGraph();var land=graph.add(TerrainGraph.Type.CONSTANT,0,0);land.values.put("height",120.0);var rock=graph.add(TerrainGraph.Type.ROCKS,230,0);graph.connect(land.id,rock.id,0);var output=graph.add(TerrainGraph.Type.OUTPUT,460,0);graph.connect(rock.id,output.id,0);
  var a=graph.evaluate(128,128,62,42,output.id,()->false);var b=graph.evaluate(128,128,62,42,output.id,()->false);check(Arrays.equals(a.output(),b.output()),"deterministic");int raised=0;for(int i=0;i<a.output().length;i++)if(a.output()[i]>120){raised++;check(a.environment().material[i]==3,"stone on boulders");}check(raised>20,"boulders generated");
  rock.values.put("seed",1.0);check(!Arrays.equals(a.output(),graph.evaluate(128,128,62,42,output.id,()->false).output()),"seed reroll");
  var bytes=new ByteArrayOutputStream();graph.write(new DataOutputStream(bytes));var saved=TerrainGraph.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));check(saved.nodes.get(rock.id).p("seed")==1,"saved rock node");
  var mask=graph.add(TerrainGraph.Type.HEIGHT_MASK,0,200);mask.values.put("min",140.0);mask.values.put("max",160.0);graph.connect(land.id,mask.id,0);graph.connect(mask.id,rock.id,1);var masked=graph.evaluate(128,128,62,42,output.id,()->false);for(float h:masked.output())check(h==120,"black mask excludes rocks");
  rock.input[1]=-1;rock.values.put("density",0.0);rock.values.put("stoneSlope",0.0);rock.values.put("stoneCoverage",1.0);var stone=graph.evaluate(128,128,62,42,output.id,()->false);for(byte m:stone.environment().material)check(m==3,"full exposed stone");
  var w=new TerraForge.World(128,62,42);w.height=stone.output();w.environment=stone.environment();check(TerrainGenerator.surface(w,5,5)==3,"export stone surface");check(TerraForge.chunk(w,0,0).length>0,"Minecraft chunk serialization");
  land.values.put("height",50.0);var wet=graph.evaluate(128,128,62,42,output.id,()->false);for(byte m:wet.environment().material)check(m==0,"underwater excluded");
  System.out.println("PASS: rock outcrops, exposed stone, fixed seeds, reroll, mask, graph persistence, underwater exclusion and Minecraft chunk serialization");
 }
}
