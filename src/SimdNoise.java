import java.util.function.BooleanSupplier;
final class SimdNoise {
    static boolean apply(TerrainGraph.Node node,float[] output,int n,int sea,long seed,BooleanSupplier stop){
        if(!Boolean.parseBoolean(System.getProperty("terraforge.simd","false")))return false;
        try{VectorNoise.generate(node,output,n,sea,seed,stop);return true;}catch(NoClassDefFoundError unavailable){return false;}
    }
}
