final class MapLimits {
 static final int MAX_SIZE=16384;
 static void checkSize(int n){if(n<16||n>MAX_SIZE||n%16!=0)throw new IllegalArgumentException("Map size must be a multiple of 16, up to 16384.");}
 static long estimatedBytes(int n,int nodes,boolean drainage){return (long)n*n*(24L+4L*nodes+(drainage?64L:0));}
 static void checkEditorCapacity(int n){checkSize(n);if(n>2048&&(long)n*n*48>Runtime.getRuntime().maxMemory()*.85)throw new IllegalArgumentException("This editor build needs at least 16 GB of Java memory. Start TerraForge with launch.sh on a machine with 32 GB RAM.");}
 static void checkMemory(int n,int nodes,boolean drainage){checkSize(n);if(n<=2048)return;long need=estimatedBytes(n,nodes,drainage);if(need>Runtime.getRuntime().maxMemory()*.75)throw new IllegalArgumentException("This "+n+" × "+n+" build needs about "+(long)Math.ceil(need/1073741824.0)+" GB of working memory, plus headroom. Choose a smaller map or use a machine with more RAM. The map limit is 16384.");}
}
