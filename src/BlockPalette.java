import java.util.Arrays;

/** Reusable primitive lookup for one 4096-block section, in first-seen order. */
final class BlockPalette {
    private static final ThreadLocal<BlockPalette> LOCAL=ThreadLocal.withInitial(BlockPalette::new);
    private final int[] keys=new int[8192],values=new int[8192],stamps=new int[8192];
    final int[] ids=new int[4096];
    int size;
    private int generation;
    static BlockPalette begin(){var palette=LOCAL.get();palette.size=0;if(++palette.generation==0){Arrays.fill(palette.stamps,0);palette.generation=1;}return palette;}
    int index(int id){
        int hash=id*0x9e3779b9;int slot=(hash^(hash>>>16))&8191;
        while(stamps[slot]==generation){if(keys[slot]==id)return values[slot];slot=(slot+1)&8191;}
        int index=size++;stamps[slot]=generation;keys[slot]=id;values[slot]=index;ids[index]=id;return index;
    }
}
