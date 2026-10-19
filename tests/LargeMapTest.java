import java.util.*;
public class LargeMapTest{
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[]args){
  MapLimits.checkSize(16384);check((long)16384*16384==268435456L,"safe map cell count");check(16384/16==1024,"1024 chunks per side");
  boolean rejected=false;try{MapLimits.checkSize(32768);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"size limit enforced");
  rejected=false;try{MapLimits.checkMemory(16384,64,true);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"oversized graph memory preflight");
  var w=new TerraForge.World(4096,62,42);Arrays.fill(w.height,70);var image=EnvironmentView.render(w,0);check(image.getWidth()==2048&&image.getHeight()==2048,"large map display downsampling");check(w.height.length==4096*4096,"full terrain resolution retained");
  var copy=w.copy();copy.height[0]=99;check(w.height[0]==70&&copy.environment.height==copy.height,"large copy isolated and consistent");
  System.out.println("PASS: 16384 size limit, long size calculations, memory preflight, 4096 terrain display and isolated copy");
 }
}
