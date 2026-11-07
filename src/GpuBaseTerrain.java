/** GPU counterpart of the standard Generate presets. */
final class GpuBaseTerrain {
 static boolean apply(TerraForge.World world,int relief,String shape,TerrainGenerator.Settings s){
  double angle=Math.toRadians(s.rotation());double[] p={s.scale(),s.octaves(),s.roughness(),s.ridges(),s.warp(),s.islandSize(),s.aspect(),s.rotation(),s.oceanDepth(),s.terraces(),s.plateau(),switch(shape){case "Continents"->1;case "Highlands"->2;case "Archipelago"->3;default->0;},Math.cos(angle),Math.sin(angle),relief};
  return GpuGeneration.compute("baseTerrain",CODE,p,new float[0][],world.height,world.size,world.size,world.sea,world.seed,()->false);
 }
 static final String CODE="""
double co=params[12],si=params[13],persistence=params[2]/100.0,relief=params[14];{            double nx=(x+.5)/n,nz=(z+.5)/n;
            double wx=(sampleNoise(nx*2,nz*2,seed+1709)-.5)*params[4]/70.0;
            double wz=(sampleNoise(nx*2,nz*2,seed-3511)-.5)*params[4]/70.0;
            double u=nx*params[0]+wx,v=nz*params[0]+wz;
            double f=fractal(u,v,seed,(int)params[1],persistence);
            double ridge=1-fabs(fractal(u*.85+11,v*.85+11,seed+995,3,.5)*2-1);
            double peaks=(ridge*ridge-.5)*relief*params[3]/100.0;
            double offset=params[11]==2?.28:params[11]==1?.48:params[11]==3?.54:.38;
            double land=(f-offset)*relief*2.6+peaks;
            if(params[11]==0||params[11]==3) {
                double cx=(nx-.5)*2,cz=(nz-.5)*2;
                double a=(cx*co-cz*si)/(params[6]/100.0),b=cx*si+cz*co;
                double d=hypot(a,b)/(params[5]/100.0);
                double coastalWarp=(sampleNoise(nx*5,nz*5,seed+461)-.5)*params[4]/160.0;
                double fall=curve(clamp((d+coastalWarp-.28)/.95,0.0,1.0));
                land-=fall*(relief*.85+params[8]);
            }
            if(land>0&&params[10]>0) {
                double mask=curve(clamp((land-relief*.1)/(relief*.4),0.0,1.0));
                land+=(relief*.55-land)*mask*params[10]/100.0;
            }
            // Compress seabeds smoothly toward a configurable depth instead of making a flat floor.
            if(land<0)land=-params[8]*(1-exp(land/params[8]));
            double elevation=sea+land;
            if(params[9]>0&&land>2) {
                double band=floor(elevation/params[9]),fraction=elevation/params[9]-band;
                double stepped=(band+curve(clamp((fraction-.65)/.35,0.0,1.0)))*params[9];
                elevation+=(stepped-elevation)*.9;
            }
            value=clamp(elevation,-60.0,300.0);}
 """;
}
