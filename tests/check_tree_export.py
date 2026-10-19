"""Independent tree voxel, leaf-state and heightmap check; requires nbtlib."""
import io,json,pathlib,sys,zlib
import nbtlib
root=pathlib.Path(sys.argv[1]);raw=(root/'region/r.0.0.mca').read_bytes();expected=json.loads(pathlib.Path(str(root)+'.expected.json').read_text());chunks={}
def unpack(values,index,bits):
 per=64//bits
 return (int(values[index//per])>>((index%per)*bits))&((1<<bits)-1)
def chunk(x,z):
 key=x//16,z//16
 if key not in chunks:
  slot=key[1]*32+key[0];offset=(int.from_bytes(raw[slot*4:slot*4+4],'big')>>8)*4096;length=int.from_bytes(raw[offset:offset+4],'big');chunks[key]=nbtlib.File.parse(io.BytesIO(zlib.decompress(raw[offset+5:offset+4+length])))
 return chunks[key]
def block(x,y,z):
 s=next(s for s in chunk(x,z)['sections']if int(s['Y'])==y//16)['block_states'];pal=s['palette'];i=(y%16)*256+(z%16)*16+x%16
 return pal[unpack(s['data'],i,max(4,(len(pal)-1).bit_length()))]if len(pal)>1 else pal[0]
heights={}
for x,y,z,name in expected:
 state=block(x,y,z);assert str(state['Name'])==name,(x,y,z,name,state)
 if name.endswith('_leaves'):assert str(state['Properties']['persistent'])=='true'
 heights[x,z]=max(y,heights.get((x,z),80))
for (x,z),height in heights.items():
 c=chunk(x,z);actual=unpack(c['Heightmaps']['WORLD_SURFACE'],(z%16)*16+x%16,9)-65;assert actual==height,(x,z,height,actual)
for c in chunks.values():
 for section in c['sections']:
  for state in section['block_states']['palette']:
   assert 'command' not in str(state['Name']) and 'sign' not in str(state['Name'])
assert len(chunks)==4
print(f'PASS: all {len(expected)} original tree blocks preserved across four chunks, persistent foliage, canopy heightmaps, no controller blocks')
