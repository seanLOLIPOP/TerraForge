final class MapLimits {
 static final int MAX_SIZE=16384;
 static void checkSize(int n){if(n<16||n>MAX_SIZE||n%16!=0)throw new IllegalArgumentException("Map size must be a multiple of 16, up to 16384.");}
 static long estimatedBytes(int n,int nodes,boolean drainage){return (long)n*n*(24L+4L*nodes+(drainage?64L:0));}
 static void checkEditorCapacity(int n){checkSize(n);if(n>2048&&(long)n*n*48>Runtime.getRuntime().maxMemory()*.85)throw new IllegalArgumentException("Building a "+n+" map into the editor needs at least 16 GB of Java memory. On this computer, preview at 1024 or 2048, then use Export with \"Low-memory region export\" at "+n+" to make the full world.");}
 static void checkMemory(int n,int nodes,boolean drainage){checkSize(n);if(n<=2048)return;long need=estimatedBytes(n,nodes,drainage);if(need>Runtime.getRuntime().maxMemory()*.75)throw new IllegalArgumentException("This "+n+" × "+n+" build needs about "+(long)Math.ceil(need/1073741824.0)+" GB of working memory, plus headroom. Choose a smaller map or use a machine with more RAM. The map limit is 16384.");}
    /** Whether a graph build of this size fits in the editor's memory; if not, the editor builds region by region on disk. */
    static boolean fitsInEditor(int n,TerrainGraph graph){
        if(Boolean.getBoolean("terraforge.regionBuild"))return false;
        try{if(n==16384)checkEditorCapacity(n);else checkMemory(n,graph.nodes.size(),graph.nodes.values().stream().anyMatch(v->v.type==TerrainGraph.Type.RIVERS||v.type==TerrainGraph.Type.LAKE));return true;}
        catch(IllegalArgumentException e){return false;}
    }
}
