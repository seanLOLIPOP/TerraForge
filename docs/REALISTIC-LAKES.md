# Irregular natural lakes

Select Lake / reservoir in Nodes. Set **Natural shoreline** and **Realistic
basin** to 1. Older graphs default to Realistic basin = 0 and retain the previous
algorithm. These controls work with manual or seeded random placement.

- **Bays and headlands** controls broad outline variation and domain warping.
- **Shoreline indentations** adds smaller coves; reduce it for smoother banks.
- **Lake elongation** stretches the footprint while preserving nominal area;
  **Lake direction** rotates that shape. Irregularity can still change its area.
- **Shallow shelf** sets the fractional nearshore band before the deep basin.
- **Basin depth variation** makes the deep bed uneven while respecting Basin depth.
- Existing radius, water level, terrain fit, bank slope, and outlet controls apply.

## Valley-following shapes and varied banks (v0.1.20)

With Natural shoreline and Realistic basin enabled:

- **Follow valleys and avoid ridges** expands the candidate footprint in low
  ground and contracts it uphill. Start at 0.85. The connected-footprint pass
  rejects disconnected water beyond barriers.
- **Maximum ridge cut** limits candidate carving into high ground, divided by
  the follow amount. The immediate seed basin (normalized radius <= 0.25) may
  still be carved so a manually placed lake can form. It is not a strict maximum
  cut-depth guarantee for the whole lake.
- **Varied lake banks** replaces the legacy broad bank smoothing with bounded
  blending. Gentle shores transition softly; steep faces and protected ridges
  stay intact. Slope and seeded deposits vary sand, gravel, rock, and damp soil.
- **Lake bank blend** sets that landward blend distance in blocks. Start at 80.

The new amounts default to zero, preserving v0.1.19 behavior. The older Follow
surrounding terrain control remains available; set it to zero when tuning the
new valley-following control independently. If either new amount is enabled,
the bounded bank path replaces legacy surrounding-terrain smoothing.

Bank deposits are carried as a transient channel through generation and stage
cache snapshots, sampled from global coordinates into regions, and reapplied
after Organic surface materials (including mountain zones). River, snow, ice,
and glacier classifications take priority. The final material channel is saved
normally; the transient deposit channel is regenerated during graph builds.

This is terrain-constrained procedural shaping, not catchment flooding or an
erosion simulation. It does not add new inlet/outlet algorithms or vegetation.
Changing lakes can reroute the existing downstream river generation.

ValleyLakeTest covers a synthetic valley with a blocking ridge, CPU/GPU parity,
bank variation, final material retention, cache copy isolation, and the supplied
City Map at 16384 scale plus a sampled region. TerrainLakeTest and
RealisticLakesTest retain coverage of the previous lake behavior. This does not
constitute a complete 1024-region world export test.

Start with Bays = 0.9, Indentations = 0.85, Elongation = 1.6. Shape is deterministic
for the map seed, node ID, and placement seed. Water stays flat. Only the connected
new footprint is retained so noisy edges do not create isolated new puddles;
islands within the lake are allowed. Existing water channels are retained.

CPU and GPU paths use matching shape and depth calculations; connected-footprint
cleanup, bank blending, and outlet routing remain CPU work. Auto/CPU/GPU fallback
continues to apply. Regional builds generate this footprint in the shared global
layout before sampling individual regions. At 16384 blocks that layout has
eight-block cells; features smaller than this may not survive.

Changing a lake can change subsequent river routes and lake outlets. This is a
procedural profile rather than a geological or hydraulic simulation. It does not
add reeds, docks, or new tree species.

Validated with RealisticLakesTest: varied outlines, shallow/deep profiles, fixed
water levels, immutable input, CPU/GPU parity, and the supplied City Map's five
lake nodes at 16384-block world scale. The City graph is saved separately with the
new lake controls enabled; it is not included in the public release.

The legacy generation corpus retains SHA-256
`7d6c61449d4e90be37e772176021144db0b5463c6fbe2cbb4d11a2a6c3e340e3`.
GpuGenerationTest and the packaged Windows launch/export smoke test pass.
EnvironmentTest's older "rivers above sea level" assertion fails both with this
build and the published v0.1.18 JAR; this existing river fixture issue is not
resolved by the lake change.
