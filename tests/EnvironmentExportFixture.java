import java.nio.file.*;
public class EnvironmentExportFixture {
 public static void main(String[] args)throws Exception{
  var w=new TerraForge.World(32,63,2024);var e=w.environment;e.water=EnvironmentMap.filled(1024,63);e.snow=new float[1024];e.ice=new float[1024];e.glacier=new float[1024];e.material=new byte[1024];e.biome=new byte[1024];
  for(int z=0;z<32;z++)for(int x=0;x<32;x++){int i=z*32+x;if(z<16&&x<16){w.height[i]=50;e.water[i]=110;e.ice[i]=x<8?2:0;e.biome[i]=(byte)(x<8?8:3);}else if(z<16){w.height[i]=100;e.snow[i]=.5f;e.material[i]=1;e.biome[i]=4;}else if(x<16){w.height[i]=120;e.material[i]=6;e.glacier[i]=12;e.biome[i]=4;}else{w.height[i]=75;e.material[i]=(byte)(x<24?5:7);e.biome[i]=(byte)(x<24?5:7);}}
  TerraForge.writeWorld(w,Path.of(args[0]));TerraForge.writeProject(w,Path.of(args[0]+".terra"));
 }
}
