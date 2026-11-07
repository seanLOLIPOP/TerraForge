import java.util.*;

/** Only sections touched by trees consume block storage. */
final class TreeOverlay {
    final Map<Integer,int[]> sections=new HashMap<>();
    final int[] top=new int[256];int highest=-65;
    TreeOverlay(){Arrays.fill(top,-65);}
    void put(int y,int column,int id){int level=(y+64)>>4;sections.computeIfAbsent(level,k->new int[4096])[((y+64)&15)*256+column]=id;top[column]=Math.max(top[column],y);highest=Math.max(highest,y);}
    int get(int y,int column){int[] section=sections.get((y+64)>>4);return section==null?0:section[((y+64)&15)*256+column];}
    int[] dense(int height){int[] blocks=new int[height*256];for(var entry:sections.entrySet())System.arraycopy(entry.getValue(),0,blocks,entry.getKey()*4096,4096);return blocks;}
}
