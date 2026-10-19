"""Offline converter for the supplied legacy showcase board (not a runtime dependency).
Requires nbtlib, numpy and scipy. Commands/entities/signs are ignored as data.
"""
import collections, gzip, hashlib, json, pathlib, struct, sys
import nbtlib
import numpy as np
from scipy import ndimage
source=pathlib.Path(sys.argv[1]);dest=pathlib.Path(sys.argv[2]);dest.mkdir(parents=True,exist_ok=True)
n=nbtlib.load(source);height,length,width=(int(n[k]) for k in ('Height','Length','Width'))
a=np.asarray(n['Blocks'],dtype=np.uint8).reshape(height,length,width);meta=np.asarray(n['Data'],dtype=np.uint8).reshape(a.shape)
# This board has white showcase pads at y=10; trunks start at y=11.
ground=10;cells,ncells=ndimage.label((a[ground]==35)&(meta[ground]==0));boxes=ndimage.find_objects(cells)
allowed={5,17,18,30,35,40,85,86,89,95,103,113,125,126,127,159,160,161,162,164,179,182,188,190,191}
active=np.isin(a[ground+1:],list(allowed));projection=active.any(axis=0);components,ncomponents=ndimage.label(projection,structure=np.ones((3,3)))
roots=np.isin(a[ground+1],[17,162,5,85,113,125,126,188,190,191]);anchors={};component_cells=collections.defaultdict(set)
for cell,(zz,xx) in enumerate(boxes,1):
 z,x=np.where(roots[zz,xx]);
 if not len(x):continue
 # Most trunks stand on green markers inside their white display pad.
 x=x+xx.start;z=z+zz.start;anchors[cell]=(int(np.median(x)),int(np.median(z)))
 for label in np.unique(components[z,x]):
  if label:component_cells[int(label)].add(cell)
assigned=np.zeros((length,width),dtype=np.int32);ambiguous=[]
for label,cell_set in component_cells.items():
 z,x=np.where(components==label);group=sorted(cell_set)
 if len(group)==1:assigned[z,x]=group[0]
 else:
  ambiguous.extend(group);dist=np.array([(x-anchors[c][0])**2+(z-anchors[c][1])**2 for c in group]);assigned[z,x]=np.array(group)[dist.argmin(axis=0)]
# Detached canopy fragments belong to the nearest already-rooted silhouette.
unknown=projection&(assigned==0)
if unknown.any():
 nearest=ndimage.distance_transform_edt(assigned==0,return_distances=False,return_indices=True);assigned[unknown]=assigned[tuple(nearest[:,unknown])]
# Resolve overlapping 2D canopies in 3D before resorting to a root-boundary split.
gy,gz,gx=np.where(active);owners=assigned[gz,gx].copy();unresolved=[]
for label,cell_set in component_cells.items():
 if len(cell_set)<2:continue
 zz,xx=np.where(components==label);z0,z1,x0,x1=zz.min(),zz.max()+1,xx.min(),xx.max()+1
 local=active[:,z0:z1,x0:x1]&(components[z0:z1,x0:x1]==label)[None,:,:]
 labels3d,count3d=ndimage.label(local,structure=np.ones((3,3,3)));root_map=collections.defaultdict(set)
 for cell in cell_set:
  zslice,xslice=boxes[cell-1];rz,rx=np.where(roots[zslice,xslice]);rz=rz+zslice.start;rx=rx+xslice.start
  for z,x in zip(rz,rx):
   if z0<=z<z1 and x0<=x<x1 and components[z,x]==label:
    k=int(labels3d[0,z-z0,x-x0])
    if k:root_map[k].add(cell)
 owner3d=np.zeros(labels3d.shape,dtype=np.int32)
 for k,cells3d in root_map.items():
  yy,zz,xx=np.where(labels3d==k);choices=sorted(cells3d)
  if len(choices)==1:owner3d[yy,zz,xx]=choices[0]
  else:
   unresolved.extend(choices);distance=np.array([(xx+x0-anchors[c][0])**2+(zz+z0-anchors[c][1])**2 for c in choices]);owner3d[yy,zz,xx]=np.array(choices)[distance.argmin(axis=0)]
 unknown=local&(owner3d==0)
 if unknown.any():
  nearest=ndimage.distance_transform_edt(owner3d==0,return_distances=False,return_indices=True);owner3d[unknown]=owner3d[tuple(nearest[:,unknown])]
 selection=components[gz,gx]==label;owners[selection]=owner3d[gy[selection],gz[selection]-z0,gx[selection]-x0]
ambiguous=unresolved
colors=['white','orange','magenta','light_blue','yellow','lime','pink','gray','light_gray','cyan','purple','blue','brown','green','red','black']
species=['oak','spruce','birch','jungle','acacia','dark_oak']
def state(b,d):
 p={}
 if b in (17,162):
  wood=species[(d&3) if b==17 else 4+(d&1)];axis={0:'y',4:'x',8:'z',12:'y'}[d&12];name=wood+('_wood' if d&12==12 else '_log');p={'axis':axis}
 elif b in (18,161):name=species[(d&3) if b==18 else 4+(d&1)]+'_leaves';p={'distance':'7','persistent':'true','waterlogged':'false'}
 elif b==5:name=species[d&7]+'_planks'
 elif b==35:name=colors[d&15]+'_wool'
 elif b==159:name=colors[d&15]+'_terracotta'
 elif b==95:name=colors[d&15]+'_stained_glass'
 elif b in (85,113,188,190,191,160):
  name={85:'oak_fence',113:'nether_brick_fence',188:'spruce_fence',190:'jungle_fence',191:'dark_oak_fence',160:colors[d&15]+'_stained_glass_pane'}[b];p={key:'false' for key in ['north','east','south','west','waterlogged']}
 elif b in (125,126):name=species[d&7]+'_slab';p={'type':'double' if b==125 else 'top' if d&8 else 'bottom','waterlogged':'false'}
 elif b==164:name='dark_oak_stairs';p={'facing':['east','west','south','north'][d&3],'half':'top' if d&4 else 'bottom','shape':'straight','waterlogged':'false'}
 elif b==127:name='cocoa';p={'age':str((d>>2)&3),'facing':['south','west','north','east'][d&3]}
 elif b==86:name='carved_pumpkin';p={'facing':['south','west','north','east'][d&3]}
 elif b==179:name='smooth_red_sandstone'
 elif b==182:name='red_sandstone_slab';p={'type':'top' if d&8 else 'bottom','waterlogged':'false'}
 else:name={30:'cobweb',40:'red_mushroom',89:'glowstone',103:'melon'}[b]
 return 'minecraft:'+name,p
palette=[];palette_index={};trees=[]
for cell,(ax,az) in anchors.items():
 selection=owners==cell;yy=gy[selection];absz=gz[selection];absx=gx[selection]
 if not len(yy):continue
 xmin,xmax,zmin,zmax=int(absx.min()),int(absx.max()),int(absz.min()),int(absz.max());x=absx-xmin;z=absz-zmin;bid=a[yy+ground+1,absz,absx];dat=meta[yy+ground+1,absz,absx]
 if len(yy)<4:continue
 counts=collections.Counter();woods=collections.Counter()
 for b,d in zip(bid.tolist(),dat.tolist()):
  if b in (17,162):woods[species[(d&3) if b==17 else 4+(d&1)]]+=1
  if b in (18,161,35,159,95,160):counts[state(b,d)[0].removeprefix('minecraft:')]+=1
 wood=woods.most_common(1)[0][0] if woods else 'decorative';foliage=counts.most_common(1)[0][0] if counts else 'bare branches';group=wood.replace('_',' ').title()+' / '+foliage.replace('_',' ')
 voxel={tuple(k):int(v)for k,v in zip(np.column_stack((x+xmin,yy,z+zmin)).tolist(),bid.tolist())};blocks=[]
 for vx,vy,vz,b,d in zip((x+xmin).tolist(),yy.tolist(),(z+zmin).tolist(),bid.tolist(),dat.tolist()):
  name,props=state(b,d)
  if b in (85,113,188,190,191,160):
   for side,dx,dz in [('north',0,-1),('east',1,0),('south',0,1),('west',-1,0)]:
    neighbor=voxel.get((vx+dx,vy,vz+dz),0)
    if neighbor in ({95,160}if b==160 else {5,17,35,85,86,103,113,159,162,188,190,191}):props[side]='true'
  key=(name,tuple(sorted(props.items())))
  if key not in palette_index:palette_index[key]=len(palette);palette.append(key)
  blocks.append((vx-ax,vy,vz-az,palette_index[key]))
 trees.append(dict(cell=cell,group=group,x=ax,z=az,width=xmax-xmin+1,length=zmax-zmin+1,height=int(yy.max())+1,blocks=blocks,shared_boundary=cell in ambiguous))
groups=sorted({t['group']for t in trees},key=lambda s:('Oak / oak leaves'!=s,s));group_id={g:i for i,g in enumerate(groups)}
trees.sort(key=lambda t:(group_id[t['group']],t['height'],t['width']*t['length'],t['cell']))
out=bytearray()
def utf(s):
 b=s.encode();out.extend(struct.pack('>H',len(b)));out.extend(b)
def integer(v):out.extend(struct.pack('>i',v))
utf('TERRATREES1');utf(source.name);utf(hashlib.sha256(source.read_bytes()).hexdigest());integer(len(palette))
for name,props in palette:
 utf(name);integer(len(props))
 for k,v in props:utf(k);utf(v)
integer(len(groups))
for group in groups:utf(group)
integer(len(trees));manifest=[]
for idx,t in enumerate(trees):
 name='Cell %03d'%t['cell'];utf(name);integer(group_id[t['group']]);integer(t['cell']);out.extend(struct.pack('>hhhhh',t['x'],t['z'],t['width'],t['height'],t['length']));integer(len(t['blocks']))
 for x,y,z,state_id in t['blocks']:out.extend(struct.pack('>hhhH',x,y,z,state_id))
 manifest.append(dict(id=idx,name=name,group=t['group'],source_x=t['x'],source_z=t['z'],width=t['width'],height=t['height'],length=t['length'],blocks=len(t['blocks']),shared_canopy_split=t['shared_boundary']))
(dest/'tree-library.trees').write_bytes(gzip.compress(out,mtime=0));(dest/'tree-library.json').write_text(json.dumps(dict(library_sha256=hashlib.sha256((dest/'tree-library.trees').read_bytes()).hexdigest(),source=source.name,sha256=hashlib.sha256(source.read_bytes()).hexdigest(),ground_removed_y=ground,groups=groups,trees=manifest,notes='Groups describe dominant trunk/canopy block materials, not verified botanical names. Each original display cell remains a selectable variant. Overlapping silhouettes were separated by 3D connectivity; physically touching trees use nearest-root boundaries where marked. Entities, commands, signs, ground and controller blocks were not imported.'),indent=2))
print('Imported',len(trees),'source-cell variants in',len(groups),'material groups;',len(palette),'block states;',sum(len(t['blocks'])for t in trees),'blocks; shared-boundary cells',len(set(ambiguous)))
