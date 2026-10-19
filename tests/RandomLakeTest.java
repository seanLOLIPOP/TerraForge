import java.util.*;import java.io.*;
public class RandomLakeTest {
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
 public static void main(String[] args)throws Exception{
  var g=new TerrainGraph();var lake=g.add(TerrainGraph.Type.LAKE,0,0);check(lake.p("random")==1,"new lakes default random");double[] first=EnvironmentNodes.lakeLocation(lake,2024);check(Arrays.equals(first,EnvironmentNodes.lakeLocation(lake,2024)),"reproducible location");lake.values.put("locationSeed",1.0);check(!Arrays.equals(first,EnvironmentNodes.lakeLocation(lake,2024)),"reroll changes location");check(!Arrays.equals(first,EnvironmentNodes.lakeLocation(lake,25)),"world seed changes location");
  lake.values.put("radius",35.0);for(int seed=0;seed<100;seed++){double[] p=EnvironmentNodes.lakeLocation(lake,seed);for(double v:p)check(v>.455&&v<.545,"radius and banks stay inside map");}
  lake.values.put("random",0.0);lake.values.put("x",25.0);lake.values.put("z",75.0);check(Arrays.equals(EnvironmentNodes.lakeLocation(lake,1),new double[]{.25,.75}),"manual coordinates");
  // Older self-describing graph records lack placement fields.
  lake.values.remove("random");lake.values.remove("locationSeed");ByteArrayOutputStream bytes=new ByteArrayOutputStream();g.write(new DataOutputStream(bytes));var old=TerrainGraph.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));check(old.nodes.get(lake.id).p("random")==0,"older lake recipes preserve placement");
  var node=new TerrainGraph.Node(1,TerrainGraph.Type.LAKE,0,0);node.values.put("overflow",0.0);node.values.put("level",120.0);var terrain=new EnvironmentMap(EnvironmentMap.filled(64*64,100));var a=EnvironmentNodes.apply(node,terrain,null,64,64,63,2024,()->false);var b=EnvironmentNodes.apply(node,terrain,null,64,64,63,2024,()->false);check(Arrays.equals(a.height,b.height)&&Arrays.equals(a.water,b.water),"reproducible lake geometry");node.values.put("locationSeed",2.0);var c=EnvironmentNodes.apply(node,terrain,null,64,64,63,2024,()->false);check(!Arrays.equals(a.water,c.water),"reroll moves water footprint");
  System.out.println("PASS: random default, repeatable placement/geometry, reroll, world seed, bank margins, manual placement and legacy compatibility");
 }
}
