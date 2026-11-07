import java.util.*;

public class BlockEncodingTest {
    public static void main(String[] args){
        var random=new Random(4242);
        for(int run=0;run<100;run++){
            var expected=new LinkedHashMap<Integer,Integer>();var actual=BlockPalette.begin();
            for(int i=0;i<4096;i++){
                int id=run==0?i:run==1?1000000+i:random.nextInt(run%2==0?22:2000000);
                int index=expected.computeIfAbsent(id,k->expected.size());
                if(actual.index(id)!=index)throw new AssertionError("palette index");
            }
            if(actual.size!=expected.size())throw new AssertionError("palette size");
            int index=0;for(int id:expected.keySet())if(actual.ids[index++]!=id)throw new AssertionError("palette order");
        }
        for(int bits=1;bits<=32;bits++)for(int length:new int[]{0,1,64,256,4096}){
            int[] values=new int[length];for(int i=0;i<length;i++)values[i]=random.nextInt()&(-1>>>(32-bits));
            int per=64/bits;long[] expected=new long[(length+per-1)/per];
            for(int i=0;i<length;i++)expected[i/per]|=(long)values[i]<<((i%per)*bits);
            if(!Arrays.equals(expected,TerraForge.pack(values,bits)))throw new AssertionError("packed bits "+bits);
        }
        System.out.println("PASS: primitive palette matches ordered map through 4096 unique blocks; packing matches reference for every bit width");
    }
}
