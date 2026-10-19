import java.io.*;

/** Deterministic heightfield generation. All distances in the noise domain scale with the map. */
final class TerrainGenerator {
    record Settings(int scale, int octaves, int roughness, int ridges, int warp,
                    int islandSize, int aspect, int rotation, int oceanDepth,
                    int terraces, int plateau, int beach, int snow, int cliff,
                    int erosion, boolean rockSlopes) {
        Settings {
            range(scale,1,10); range(octaves,1,8); range(roughness,20,80);
            range(ridges,0,100); range(warp,0,100); range(islandSize,35,100);
            range(aspect,50,180); range(rotation,0,180); range(oceanDepth,8,80);
            range(terraces,0,24); range(plateau,0,100); range(beach,1,12);
            range(snow,90,250); range(cliff,1,20); range(erosion,0,50);
        }
        private static void range(int v,int min,int max) {
            if(v<min||v>max)throw new IllegalArgumentException("Terrain setting outside its supported range");
        }
        static Settings defaults(){return new Settings(3,6,50,35,30,80,100,0,45,0,0,3,145,4,0,true);}
        static Settings legacy(){return new Settings(3,6,50,0,0,80,100,0,45,0,0,3,146,4,0,false);}
        void write(DataOutput d)throws IOException {
            for(int v:new int[]{scale,octaves,roughness,ridges,warp,islandSize,aspect,rotation,
                    oceanDepth,terraces,plateau,beach,snow,cliff,erosion})d.writeInt(v);
            d.writeBoolean(rockSlopes);
        }
        static Settings read(DataInput d)throws IOException {
            try{return new Settings(d.readInt(),d.readInt(),d.readInt(),d.readInt(),d.readInt(),
                d.readInt(),d.readInt(),d.readInt(),d.readInt(),d.readInt(),d.readInt(),
                d.readInt(),d.readInt(),d.readInt(),d.readInt(),d.readBoolean());}
            catch(IllegalArgumentException e){throw new IOException("Invalid generation settings",e);}
        }
    }
    record Preset(String shape,int relief,int sea,Settings settings){}
    static Preset preset(String name) {
        return switch(name) {
            case "Alpine ranges" -> new Preset("Highlands",135,63,new Settings(3,7,55,90,50,80,100,0,45,0,0,2,170,3,12,true));
            case "Archipelago" -> new Preset("Archipelago",70,63,new Settings(6,6,45,10,60,90,100,0,38,0,0,5,165,6,4,true));
            case "Mesa plateaus" -> new Preset("Continents",110,55,new Settings(3,5,35,15,35,85,130,25,38,12,85,2,245,3,0,true));
            case "Rolling plains" -> new Preset("Highlands",30,63,new Settings(2,4,35,0,25,90,100,0,30,0,0,4,200,8,15,true));
            default -> new Preset("Island",85,63,Settings.defaults());
        };
    }
    static double fractal(double x,double z,long seed,int octaves,double persistence) {
        double value=0,amp=1,total=0;
        for(int o=0;o<octaves;o++){value+=TerraForge.sample(x,z,seed+o*71)*amp;total+=amp;amp*=persistence;x*=2;z*=2;}
        return value/total;
    }
    static double clamp(double value,double low,double high){return Math.max(low,Math.min(high,value));}
    static TerraForge.World generate(int n,int sea,long seed,int relief,String shape,Settings s) {
        MapLimits.checkMemory(n,4,false);
        if(n<16||n>2048||n%16!=0||sea<20||sea>100||relief<20||relief>170)
            throw new IllegalArgumentException("Unsupported world dimensions or elevation");
        TerraForge.World w=new TerraForge.World(n,sea,seed);w.settings=s;w.recipeShape=shape;w.recipeRelief=relief;
        double co=Math.cos(Math.toRadians(s.rotation)),si=Math.sin(Math.toRadians(s.rotation));
        double persistence=s.roughness/100.0;
        for(int z=0;z<n;z++)for(int x=0;x<n;x++) {
            double nx=(x+.5)/n,nz=(z+.5)/n;
            double wx=(TerraForge.sample(nx*2,nz*2,seed+1709)-.5)*s.warp/70.0;
            double wz=(TerraForge.sample(nx*2,nz*2,seed-3511)-.5)*s.warp/70.0;
            double u=nx*s.scale+wx,v=nz*s.scale+wz;
            double f=fractal(u,v,seed,s.octaves,persistence);
            double ridge=1-Math.abs(fractal(u*.85+11,v*.85+11,seed+995,3,.5)*2-1);
            double peaks=(ridge*ridge-.5)*relief*s.ridges/100.0;
            double offset=shape.equals("Highlands")?.28:shape.equals("Continents")?.48:shape.equals("Archipelago")?.54:.38;
            double land=(f-offset)*relief*2.6+peaks;
            if(shape.equals("Island")||shape.equals("Archipelago")) {
                double cx=(nx-.5)*2,cz=(nz-.5)*2;
                double a=(cx*co-cz*si)/(s.aspect/100.0),b=cx*si+cz*co;
                double d=Math.hypot(a,b)/(s.islandSize/100.0);
                double coastalWarp=(TerraForge.sample(nx*5,nz*5,seed+461)-.5)*s.warp/160.0;
                double fall=TerraForge.smooth(clamp((d+coastalWarp-.28)/.95,0,1));
                land-=fall*(relief*.85+s.oceanDepth);
            }
            if(land>0&&s.plateau>0) {
                double mask=TerraForge.smooth(clamp((land-relief*.1)/(relief*.4),0,1));
                land+=(relief*.55-land)*mask*s.plateau/100.0;
            }
            // Compress seabeds smoothly toward a configurable depth instead of making a flat floor.
            if(land<0)land=-s.oceanDepth*(1-Math.exp(land/s.oceanDepth));
            double elevation=sea+land;
            if(s.terraces>0&&land>2) {
                double band=Math.floor(elevation/s.terraces),fraction=elevation/s.terraces-band;
                double stepped=(band+TerraForge.smooth(clamp((fraction-.65)/.35,0,1)))*s.terraces;
                elevation+=(stepped-elevation)*.9;
            }
            w.height[z*n+x]=(float)clamp(elevation,-60,300);
        }
        if(s.erosion>0)TerraForge.thermalErode(w,s.erosion);
        return w;
    }
    /** Resolve automatic materials in one place for the editor and Minecraft exporter. */
    static int surface(TerraForge.World w,int x,int z) {
        int painted=w.material[z*w.size+x];if(painted!=0)return painted;
        int index=z*w.size+x;int env=w.environment.material==null?0:w.environment.material[index];if(env!=0)return env;
        int h=w.h(x,z);Settings s=w.settings;
        if(w.environment.snow==null&&h>=s.snow)return 4;
        if(h<w.waterY(x,z)+s.beach)return 2;
        if(s.rockSlopes) {
            float center=w.height[z*w.size+x],slope=0;
            if(x>0)slope=Math.max(slope,Math.abs(center-w.height[z*w.size+x-1]));
            if(x<w.size-1)slope=Math.max(slope,Math.abs(center-w.height[z*w.size+x+1]));
            if(z>0)slope=Math.max(slope,Math.abs(center-w.height[(z-1)*w.size+x]));
            if(z<w.size-1)slope=Math.max(slope,Math.abs(center-w.height[(z+1)*w.size+x]));
            if(slope>=s.cliff)return 3;
        }
        return 1;
    }
}
