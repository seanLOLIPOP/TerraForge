/** Shared solid, fluid and snow rules for Minecraft export and the block preview. */
final class ExportBlocks {
 static int block(int h,int mat,int water,int snow,int ice,int glacier,int y){
  return block(h,mat,water,snow,ice,glacier,y,3);
 }
 static boolean mountainRock(int mat){return mat==12||mat==17||mat==18||mat==19||mat==20;}
 static int surfaceDepth(int mat){return mountainRock(mat)?48:6;}
 static int soilDepth(int x,int z,long seed){return 2+(int)Math.floor(TerraForge.sample(x/43.,z/43.,seed+3071)*4);}
 /** Mountain interiors start this high; below it rock stays plain stone as before. */
    static final int STRATA_FLOOR=72,STRATA_DEPTH=64;
    /** As block(), but rock in mountains follows wavy, gently tilted strata so cliff faces show layers. */
    static int block(int h,int mat,int water,int snow,int ice,int glacier,int y,int soilDepth,int x,int z,long seed){
        int v=block(h,mat,water,snow,ice,glacier,y,soilDepth);
        if(y>=h||y<=STRATA_FLOOR||y<h-STRATA_DEPTH)return v;
        boolean rock=v==3||mountainRock(mat)&&v>=1000000;
        return rock?strata(x,y,z,seed):v;
    }
    static int strata(int x,int y,int z,long seed){
        double drift=y/40.0,w=(TerraForge.sample(x/70.,z/70.,seed+6101)-.5)*24;
        double r=.6*TerraForge.sample((x+w)/26.+drift*.7,(z-w)/26.-drift*.4,seed+6109)+.4*TerraForge.sample(x/9.+drift,z/9.,seed+6111);
        double region=TerraForge.sample(x/520.,z/520.,seed+6113);
        if(r<.45)return 3;
        if(r<.6)return 1000002;/* andesite */
        if(r<.7)return 1000009;/* tuff */
        if(r<.77)return region>.5?1000008:1000007;/* granite or diorite, by region */
        if(r<.82&&y<260)return 2;/* deepslate low in the range */
        return 3;
    }
    static int block(int h,int mat,int water,int snow,int ice,int glacier,int y,int soilDepth){
  if(y==-64)return 1;
  if(y<=h){if(mat==6&&y>h-Math.max(4,glacier))return 10;if(y<h-(mountainRock(mat)?48:soilDepth))return y<0?2:3;if(mat==3)return 3;if(mat==2)return 6;if(mat==5)return 11;if(mat==7)return 12;if(mat>=10&&mat<=20){if((mat==10||mat>=14&&mat<=16)&&y<h)return 4;return 1000000+mat-10;}if(mat==8)return 4;if(mat==9)return 9;if(y<h)return 4;return mat==4?8:snow>0?21:5;}
  if(y<=water)return ice>0&&y>water-ice?9:7;
  return y==h+1&&snow>0?12+snow:0;
 }
 static TreeLibrary.State state(int id){if(id>=GroundCover.BASE)return GroundCover.state(id);if(id>=1000000)return new TreeLibrary.State("minecraft:"+RiverBanks.EXTRA_BLOCKS[id-1000000],java.util.Map.of());if(id>=22)return TreeLibrary.state(id);if(id>=13&&id<=20)return new TreeLibrary.State("minecraft:snow",java.util.Map.of("layers",Integer.toString(id-12)));if(id==5||id==21)return new TreeLibrary.State("minecraft:grass_block",java.util.Map.of("snowy",id==21?"true":"false"));return new TreeLibrary.State("minecraft:"+TerraForge.blocks[id],id==7?java.util.Map.of("level","0"):java.util.Map.of());}
}
