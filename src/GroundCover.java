import java.util.Map;
/**
 * Small plants and forest-floor details placed on top of the exported ground: grass, ferns, flowers, berry
 * bushes, mushrooms, moss carpets and fallen logs. Choices follow the biome, the ground block and rainfall, so
 * alpine meadows flower, spruce forests get ferns and berries, and dry slopes stay sparse.
 * Block names are Minecraft Java 1.20.4 (where "grass" is called short_grass).
 */
final class GroundCover {
    static final int BASE=2000000;
    static final TreeLibrary.State[] STATES={
        s("short_grass"),s("fern"),
        s("tall_grass","half","lower"),s("tall_grass","half","upper"),
        s("large_fern","half","lower"),s("large_fern","half","upper"),
        s("dandelion"),s("poppy"),s("cornflower"),s("azure_bluet"),s("oxeye_daisy"),s("allium"),s("lily_of_the_valley"),
        s("sweet_berry_bush","age","3"),s("moss_carpet"),
        s("spruce_log","axis","x"),s("spruce_log","axis","z"),s("oak_log","axis","x"),s("oak_log","axis","z"),s("birch_log","axis","x"),s("birch_log","axis","z"),
        s("brown_mushroom"),s("red_mushroom")};
    static final int SHORT_GRASS=0,FERN=1,TALL_GRASS=2,LARGE_FERN=4,DANDELION=6,POPPY=7,CORNFLOWER=8,AZURE=9,OXEYE=10,ALLIUM=11,LILY=12,BERRY=13,MOSS=14,SPRUCE_LOG=15,OAK_LOG=17,BIRCH_LOG=19,BROWN_MUSHROOM=21,RED_MUSHROOM=22;
    static TreeLibrary.State s(String name,String... props){
        java.util.Map<String,String> p=new java.util.TreeMap<>();for(int i=0;i+1<props.length;i+=2)p.put(props[i],props[i+1]);
        return new TreeLibrary.State("minecraft:"+name,Map.copyOf(p));
    }
    static TreeLibrary.State state(int id){return STATES[id-BASE];}
    static Map<String,TerraForge.Tag> stateTag(int id){
        var state=state(id);var tag=TerraForge.tags("Name",TerraForge.str(state.name()));
        if(!state.properties().isEmpty()){Map<String,TerraForge.Tag> props=new java.util.TreeMap<>();for(var e:state.properties().entrySet())props.put(e.getKey(),TerraForge.str(e.getValue()));tag.put("Properties",TerraForge.compound(props));}
        return tag;
    }
    static double hash(long x,long z,long seed){
        long h=x*0x9E3779B97F4A7C15L^z*0xC2B2AE3D27D4EB4FL^seed*0x165667B19E3779F9L;h^=h>>>33;h*=0xFF51AFD7ED558CCDL;h^=h>>>33;h*=0xC4CEB9FE1A85EC53L;h^=h>>>33;
        return (h>>>11)*0x1.0p-53;
    }
    static boolean plantable(int mat){return mat==1||mat==10||mat==14||mat==15||mat==16;}
    interface Ground{int height(int localX,int localZ);int material(int localX,int localZ);}
    /**
     * Decoration for one column: {block at h+1, block at h+2} as export ids, or 0.
     * wx,wz are world coordinates; lx,lz local tile coordinates used to read neighbouring ground for logs.
     */
    static int[] at(int wx,int wz,int lx,int lz,int h,int mat,int biome,double rain,long seed,Ground ground){
        int[] none={0,0};
        if(!plantable(mat))return none;
        int log=fallenLog(wx,wz,lx,lz,h,biome,seed,ground);
        if(log!=0){boolean moss=hash(wx,wz,seed+77)<.45;return new int[]{log,moss&&log<BASE+OAK_LOG?BASE+MOSS:0};}
        double r=hash(wx,wz,seed),wet=Math.max(.25,Math.min(1.4,.45+rain));
        double patch=TerraForge.sample(wx/26.,wz/26.,seed+31),cluster=TerraForge.sample(wx/7.,wz/7.,seed+37);
        String b=EnvironmentView.BIOMES[Math.max(0,Math.min(EnvironmentView.BIOMES.length-1,biome))];
        switch(b){
            case "meadow":{
                if(r<.30*wet)return one(SHORT_GRASS);
                if(r<.36*wet)return two(TALL_GRASS);
                if(r<.36*wet+.26*cluster){int[] f={DANDELION,POPPY,CORNFLOWER,AZURE,OXEYE,ALLIUM};return one(f[(int)Math.min(5,patch*6)]);}
                return none;
            }
            case "taiga":case "grove":case "forest":{
                boolean conifer=!b.equals("forest");
                if(mat==16&&r<.35)return one(MOSS);
                if(r<.09*wet)return one(FERN);
                if(r<.12*wet)return two(LARGE_FERN);
                if(r<.22*wet)return one(SHORT_GRASS);
                if(conifer&&mat!=10&&r<.232&&patch>.55)return one(BERRY);
                if(r<.240&&(mat==14||mat==16||rain>.7))return one(cluster>.5?BROWN_MUSHROOM:RED_MUSHROOM);
                if(!conifer&&r<.25&&patch>.6)return one(LILY);
                return none;
            }
            case "plains":case "swamp":case "river":{
                if(mat==10)return r<.08*wet?one(SHORT_GRASS):none;
                if(r<.26*wet)return one(SHORT_GRASS);
                if(r<.31*wet)return two(TALL_GRASS);
                if(r<.31*wet+.06*cluster){int[] f={DANDELION,POPPY,OXEYE,AZURE};return one(f[(int)Math.min(3,patch*4)]);}
                return none;
            }
            case "stony_peaks":
                return mat==1&&r<.12*wet?one(SHORT_GRASS):none;
            default:return none;
        }
    }
    static int[] one(int k){return new int[]{BASE+k,0};}
    static int[] two(int k){return new int[]{BASE+k,BASE+k+1};}
    /** Fallen trunks 4-6 blocks long on flat forest floor, anchored on a 20-block grid so every column agrees. */
    static int fallenLog(int wx,int wz,int lx,int lz,int h,int biome,long seed,Ground ground){
        String b=EnvironmentView.BIOMES[Math.max(0,Math.min(EnvironmentView.BIOMES.length-1,biome))];
        if(!b.equals("taiga")&&!b.equals("grove")&&!b.equals("forest"))return 0;
        int gx=Math.floorDiv(wx,20),gz=Math.floorDiv(wz,20);
        for(int cz=gz-1;cz<=gz+1;cz++)for(int cx=gx-1;cx<=gx+1;cx++){
            if(hash(cx,cz,seed+501)>.16)continue;
            int sx=cx*20+(int)(hash(cx,cz,seed+503)*14),sz=cz*20+(int)(hash(cx,cz,seed+505)*14),len=4+(int)(hash(cx,cz,seed+507)*3);
            boolean alongX=hash(cx,cz,seed+509)<.5;
            int along=alongX?wx-sx:wz-sz,across=alongX?wz-sz:wx-sx;
            if(across!=0||along<0||along>=len)continue;
            // the whole trunk must lie on level, plantable ground inside this tile
            int ox=lx-(alongX?along:0),oz=lz-(alongX?0:along);
            boolean ok=true;
            for(int k=0;k<len&&ok;k++){int tx=ox+(alongX?k:0),tz=oz+(alongX?0:k);int th=ground.height(tx,tz);if(th==Integer.MIN_VALUE||th!=h||!plantable(ground.material(tx,tz)))ok=false;}
            if(!ok)continue;
            int base=b.equals("forest")?(hash(cx,cz,seed+511)<.5?OAK_LOG:BIRCH_LOG):SPRUCE_LOG;
            return BASE+base+(alongX?0:1);
        }
        return 0;
    }
}
