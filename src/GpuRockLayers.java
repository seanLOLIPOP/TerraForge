import java.util.function.BooleanSupplier;
final class GpuRockLayers {
    static void prepare(TerraForge.World world,int originX,int originZ,BooleanSupplier stop){
        if(!FastGpu.enabled()||world.size<512||world.size>512)return;
        int eligible=0;for(int i=0;i<world.height.length;i++)if(world.height[i]>ExportBlocks.STRATA_FLOOR+64&&world.height[i]>world.environment.waterY(i,world.sea))eligible++;
        if(eligible<world.height.length/4)return;
        int n=world.size;float[] output=new float[n*n*64];
        String body="""
            int xx=x+(int)params[0],zz=z+(int)params[1],y=(int)floor(a+.5)-1-channel;
            double drift=y/40.0,w=(sampleNoise(xx/70.0,zz/70.0,seed+6101)-.5)*24;
            double r=.6*sampleNoise((xx+w)/26.0+drift*.7,(zz-w)/26.0-drift*.4,seed+6109)+.4*sampleNoise(xx/9.0+drift,zz/9.0,seed+6111);
            double region=sampleNoise(xx/520.0,zz/520.0,seed+6113);
            value=r<.45?3:r<.6?1000002:r<.7?1000009:r<.77?(region>.5?1000008:1000007):r<.82&&y<260?2:3;
            """;
        if(GpuGeneration.compute("rock-layers-v1",body,new double[]{originX,originZ},new float[][]{world.height},output,n,n,world.sea,world.seed,stop))world.gpuStrata=output;
    }
}
