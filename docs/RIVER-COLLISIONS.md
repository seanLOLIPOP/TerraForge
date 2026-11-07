# River bank collisions (0.1.25)

The previous crossing audit tested centerlines. It did not prove the water bodies
were separate: channels could touch at their banks without an X intersection.
A broad source-area exemption also hid unrelated channels near child junctions.

Main-channel fitting now merges compatible channels on bank contact from the
start of the incoming route. Incompatible water levels stop before contact.
Near-self contacts erase the enclosed detour while retaining the downstream
continuation. Layout cell size contributes to clearance during fitting.

Extra upstream tributaries and natural side branches leave at least a 16-block
bank margin (or two coarse cells, whichever is larger) from unrelated channels.
The local junction exemption applies only to channel owners actually connected
to that junction. Spatial lookups inspect neighboring buckets for the complete
clearance footprint instead of only the point's own bucket.

These rules apply to drainage-first rivers on all maps. Intentional island
splits and real tributary confluences remain supported. Collision avoidance can
shorten or reject a tributary where insufficient room remains. Old generated
regions require a new build; the build-folder revision is changed accordingly.

Validation:
- RiverBankCollisionTest reproduces bank-only contacts, early contacts,
  incompatible levels, near-self loops, coarse footprints, bucket boundaries,
  unrelated source-area neighbors, and separated rivers.
- The early bank-contact test fails against the published 0.1.24 JAR.
- NaturalTributaryTest checks bank separation outside junctions, downstream
  widths/water, branching, no cycles and no crossings across three seeds.
- Existing endings/seam, curve rendering, coarse visibility, shared clearance,
  island split, flow and slope-preservation checks are retained.
- The user's saved 16384 map is generated at seed 74839261 and sea level 63;
  its overview is inspected with a separate color-coded geometry diagnostic.

Final supplied-map audit: 29,473 channel and tail segments, zero proper crossings.
Windows native launcher/export smoke test passed; packaged JAR matches the tested JAR.
