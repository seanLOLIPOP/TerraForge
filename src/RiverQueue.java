import java.util.Arrays;
import java.util.NoSuchElementException;

/** Primitive routing heap: exactly cost order, then cell index, without per-entry objects. */
final class RiverQueue {
    private int[] cells=new int[1024];
    private float[] costs=new float[1024];
    private int size;
    boolean isEmpty(){return size==0;}
    private static int compare(float a,int ai,float b,int bi){int c=Float.compare(a,b);return c!=0?c:Integer.compare(ai,bi);}
    void add(int cell,float cost){
        if(size==cells.length){int capacity=Math.addExact(size,size/2);cells=Arrays.copyOf(cells,capacity);costs=Arrays.copyOf(costs,capacity);}
        int at=size++;
        while(at>0){int parent=(at-1)>>>1;if(compare(cost,cell,costs[parent],cells[parent])>=0)break;cells[at]=cells[parent];costs[at]=costs[parent];at=parent;}
        cells[at]=cell;costs[at]=cost;
    }
    int remove(){
        if(size==0)throw new NoSuchElementException();
        int result=cells[0],last=--size,cell=cells[last];float cost=costs[last];int at=0,half=size>>>1;
        while(at<half){int child=(at<<1)+1,right=child+1;if(right<size&&compare(costs[right],cells[right],costs[child],cells[child])<0)child=right;
            if(compare(cost,cell,costs[child],cells[child])<=0)break;cells[at]=cells[child];costs[at]=costs[child];at=child;}
        if(size>0){cells[at]=cell;costs[at]=cost;}return result;
    }
}
