import java.util.*;

/** Deterministic rectangle occupancy, indexed in 32-block cells. */
final class TreeCollisions {
    record Bounds(int minX,int maxX,int minZ,int maxZ){}
    private final Map<Long,List<Bounds>> cells=new HashMap<>();
    private static long key(int x,int z){return (long)x<<32|(z&0xffffffffL);}
    private static Bounds bounds(TreeLibrary.Placement p){int[] b=TreeLibrary.bounds(TreeLibrary.catalog().trees().get(p.tree()),p.rotation());return new Bounds(p.x()+b[0],p.x()+b[1],p.z()+b[2],p.z()+b[3]);}
    TreeCollisions(List<TreeLibrary.Placement> placements){for(var p:placements)add(p);}
    void add(TreeLibrary.Placement p){var b=bounds(p);for(int z=Math.floorDiv(b.minZ,32);z<=Math.floorDiv(b.maxZ,32);z++)for(int x=Math.floorDiv(b.minX,32);x<=Math.floorDiv(b.maxX,32);x++)cells.computeIfAbsent(key(x,z),k->new ArrayList<>()).add(b);}
    boolean intersects(int x,int z,int[] extent){int minX=x+extent[0],maxX=x+extent[1],minZ=z+extent[2],maxZ=z+extent[3];
        for(int cz=Math.floorDiv(minZ,32);cz<=Math.floorDiv(maxZ,32);cz++)for(int cx=Math.floorDiv(minX,32);cx<=Math.floorDiv(maxX,32);cx++)for(var b:cells.getOrDefault(key(cx,cz),List.of()))if(minX<=b.maxX&&maxX>=b.minX&&minZ<=b.maxZ&&maxZ>=b.minZ)return true;return false;}
}
