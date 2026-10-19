import java.io.*;
import java.util.*;

/** Immutable-by-convention channels; processors copy only the arrays they modify. */
final class EnvironmentMap {
    int forkRoots,forkTipCount,forkRiverGroups,forkEligibleGroups;java.util.List<MountainRiverForks.Arm> mountainArms=java.util.List.of();
    java.util.List<MountainRiverForks.Fork> mountainForks=java.util.List.of();
    RiverFlow.Field riverFlow;RiverRibbon.Network ribbons;byte[] lakeMask;float[] riverCatchment;BitSet riverNetworkCells;boolean resolvedRiverChannels;
    java.util.List<TreeLibrary.Placement> trees=java.util.List.of();
    float[] height,water,snow,ice,temperature,rain,river,glacier;byte[] material,biome;
    EnvironmentMap(float[] height){this.height=height;}
    EnvironmentMap share(float[] newHeight){EnvironmentMap e=new EnvironmentMap(newHeight);e.water=water;e.snow=snow;e.ice=ice;e.temperature=temperature;e.rain=rain;e.river=river;e.glacier=glacier;e.material=material;e.biome=biome;e.trees=trees;e.forkRiverGroups=forkRiverGroups;e.forkEligibleGroups=forkEligibleGroups;e.forkRoots=forkRoots;e.forkTipCount=forkTipCount;e.mountainArms=mountainArms;e.mountainForks=mountainForks;e.riverFlow=riverFlow;e.ribbons=ribbons;e.lakeMask=lakeMask;e.riverCatchment=riverCatchment;e.riverNetworkCells=riverNetworkCells;e.resolvedRiverChannels=resolvedRiverChannels;return e;}
    EnvironmentMap deepCopy(){return deepCopy(copy(height));}
    EnvironmentMap deepCopy(float[] newHeight){EnvironmentMap e=share(newHeight);e.water=copy(water);e.snow=copy(snow);e.ice=copy(ice);e.temperature=copy(temperature);e.rain=copy(rain);e.river=copy(river);e.glacier=copy(glacier);e.material=material==null?null:material.clone();e.biome=biome==null?null:biome.clone();return e;}
    static float[] copy(float[] a){return a==null?null:a.clone();}
    static float[] filled(int count,float value){float[] a=new float[count];Arrays.fill(a,value);return a;}
    static float at(float[] a,int i,float fallback){return a==null?fallback:a[i];}
    int waterY(int i,int sea){return Math.round(at(water,i,sea));}
    static void writeFloat(DataOutput d,float[] a)throws IOException{d.writeBoolean(a!=null);if(a!=null)for(float f:a)d.writeFloat(f);}
    static float[] readFloat(DataInput d,int count,float min,float max)throws IOException{if(!d.readBoolean())return null;float[] a=new float[count];for(int i=0;i<count;i++){a[i]=d.readFloat();if(!Float.isFinite(a[i])||a[i]<min||a[i]>max)throw new IOException("Invalid environment channel");}return a;}
    static void writeByte(DataOutput d,byte[] a)throws IOException{d.writeBoolean(a!=null);if(a!=null)d.write(a);}
    static byte[] readByte(DataInput d,int count,int max)throws IOException{if(!d.readBoolean())return null;byte[] a=new byte[count];d.readFully(a);for(byte b:a)if(b<0||b>max)throw new IOException("Invalid environment category");return a;}
    void write(DataOutput d)throws IOException{writeFloat(d,water);writeFloat(d,snow);writeFloat(d,ice);writeFloat(d,temperature);writeFloat(d,rain);writeFloat(d,river);writeFloat(d,glacier);writeByte(d,material);writeByte(d,biome);TreeLibrary.writePlacements(d,trees);}
    static EnvironmentMap read(DataInput d,float[] height,boolean withTrees)throws IOException{EnvironmentMap e=new EnvironmentMap(height);int count=height.length;e.water=readFloat(d,count,-64,1024);e.snow=readFloat(d,count,0,1);e.ice=readFloat(d,count,0,5);e.temperature=readFloat(d,count,0,1);e.rain=readFloat(d,count,0,1);e.river=readFloat(d,count,0,1);e.glacier=readFloat(d,count,0,30);e.material=readByte(d,count,18);e.biome=readByte(d,count,9);if(withTrees)e.trees=TreeLibrary.readPlacements(d,(int)Math.sqrt(count));return e;}
}
