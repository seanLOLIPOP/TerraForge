"""Optional independent NBT verification: pip install nbtlib, then pass fixture world path."""
import io, pathlib, struct, sys, zlib
import nbtlib
root=pathlib.Path(sys.argv[1]);data=(root/'region/r.0.0.mca').read_bytes()
chunks={}
for z in range(2):
 for x in range(2):
  index=z*32+x;loc=int.from_bytes(data[index*4:index*4+4],'big');offset=(loc>>8)*4096
  length=int.from_bytes(data[offset:offset+4],'big');assert data[offset+4]==2
  chunks[x,z]=nbtlib.File.parse(io.BytesIO(zlib.decompress(data[offset+5:offset+4+length])))
def unpack(values,index,bits):
 per=64//bits
 return (int(values[index//per])>>((index%per)*bits))&((1<<bits)-1)
def block(x,y,z):
 section=next(s for s in chunks[x//16,z//16]['sections'] if int(s['Y'])==y//16)['block_states'];palette=section['palette']
 index=(y%16)*256+(z%16)*16+x%16
 entry=palette[unpack(section['data'],index,max(4,(len(palette)-1).bit_length()))] if len(palette)>1 else palette[0]
 return str(entry['Name']),dict(entry.get('Properties',{}))
for pos,name in [((4,110,4),'ice'),((4,109,4),'ice'),((4,108,4),'water'),((12,110,4),'water'),((12,111,4),'air'),((20,101,4),'snow'),((20,100,4),'grass_block'),((8,120,24),'packed_ice'),((8,109,24),'packed_ice'),((8,108,24),'stone'),((20,75,24),'mud'),((28,75,24),'gravel')]:
 actual,_=block(*pos);assert actual=='minecraft:'+name,(pos,actual,name)
assert str(block(20,101,4)[1]['layers'])=='4'
assert str(block(20,100,4)[1]['snowy'])=='true'
for (cx,cz),chunk in chunks.items():
 for z in range(16):
  for x in range(16):
   wx=cx*16+x;wz=cz*16+z
   expected=110 if wx<16 and wz<16 else 101 if wz<16 else 120 if wx<16 else 75
   actual=unpack(chunk['Heightmaps']['WORLD_SURFACE'],z*16+x,9)-65
   assert actual==expected,(wx,wz,actual,expected)
 for section in chunk['sections']:
  states=section['block_states'];pal=states['palette']
  if len(pal)>1:
   for i in range(4096):assert unpack(states['data'],i,max(4,(len(pal)-1).bit_length()))<len(pal)
  biomes=section['biomes'];pal=biomes['palette']
  for y in range(4):
   for z in range(4):
    for x in range(4):
     wx=cx*16+x*4+2;wz=cz*16+z*4+2
     expected=('frozen_ocean' if wx<8 else 'river') if wz<16 and wx<16 else 'snowy_plains' if wz<16 or wx<16 else 'swamp' if wx<24 else 'forest'
     index=y*16+z*4+x
     actual=str(pal[unpack(biomes['data'],index,max(1,(len(pal)-1).bit_length()))]) if len(pal)>1 else str(pal[0])
     assert actual=='minecraft:'+expected,(wx,wz,actual,expected)
print('PASS: local water level 110, two-layer ice, four-layer snow, snowy grass, deep glacier ice, mud/gravel, all heightmaps, all block states and every biome quart')
