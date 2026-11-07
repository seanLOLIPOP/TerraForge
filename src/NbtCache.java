import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

/** Immutable palette tags and bounded, serialized uniform-section payloads. */
final class NbtCache {
    private record Key(int y,int block,ByteBuffer biomes){}
    private static final Map<Integer,Map<String,TerraForge.Tag>> BLOCKS=new LinkedHashMap<>();
    private static final Map<Key,byte[]> SECTIONS=new LinkedHashMap<>(16,.75f,true);
    static synchronized Map<String,TerraForge.Tag> block(int id){
        var cached=BLOCKS.get(id);if(cached!=null)return cached;
        Map<String,TerraForge.Tag> tag=id>=GroundCover.BASE?GroundCover.stateTag(id):id>=1000000?TerraForge.tags("Name",TerraForge.str("minecraft:"+RiverBanks.EXTRA_BLOCKS[id-1000000])):id>=22?TreeLibrary.stateTag(id):TerraForge.tags("Name",TerraForge.str("minecraft:"+(id==21?"grass_block":id>=13?"snow":TerraForge.blocks[id])));
        if(id==5||id==21)tag.put("Properties",TerraForge.compound(TerraForge.tags("snowy",TerraForge.str(id==21?"true":"false"))));
        if(id==7)tag.put("Properties",TerraForge.compound(TerraForge.tags("level",TerraForge.str("0"))));
        if(id>=13&&id<=20)tag.put("Properties",TerraForge.compound(TerraForge.tags("layers",TerraForge.str(Integer.toString(id-12)))));
        tag=Collections.unmodifiableMap(tag);if(BLOCKS.size()>=4096)BLOCKS.remove(BLOCKS.keySet().iterator().next());BLOCKS.put(id,tag);return tag;
    }
    static byte[] encode(Map<String,TerraForge.Tag> tags)throws IOException{var bytes=new ByteArrayOutputStream();TerraForge.payload(new DataOutputStream(bytes),TerraForge.compound(tags));return bytes.toByteArray();}
    static synchronized byte[] uniform(int y,int id,Map<String,TerraForge.Tag> biomes,byte[] encodedBiomes)throws IOException{
        var key=new Key(y,id,ByteBuffer.wrap(encodedBiomes).asReadOnlyBuffer());var cached=SECTIONS.get(key);if(cached!=null)return cached;
        var tags=TerraForge.tags("Y",TerraForge.b(y),"block_states",TerraForge.compound(TerraForge.tags("palette",TerraForge.list(10,List.of(block(id))))),"biomes",TerraForge.compound(biomes));
        byte[] payload=encode(tags);if(SECTIONS.size()>=4096)SECTIONS.remove(SECTIONS.keySet().iterator().next());SECTIONS.put(key,payload);return payload;
    }
}
