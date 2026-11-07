# TerraForge v0.1.11 — multicore generation

Windows x64 portable build. Target: Nodes → Build into map on the user's
AMD Ryzen 9 8940HX, 16 cores / 32 logical CPUs, approximately 15.2 GiB system RAM.

## Changes

- A shared, bounded CPU pool runs independent output rows using up to 31 workers
  on this machine. Maps smaller than 512x512 stay sequential to avoid overhead.
- Noise, ridges, mountains, blends, masks, main terrain generation, terrain detail,
  organic materials, climate, lake rasterization, snow, slope fields, separable
  smoothing, and mountain surface classification use parallel rows.
- Thermal erosion computes flow and gathers contributions in the original
  floating-point order. Flow buffers are reused across erosion passes.
- Region builds remove the fixed four-worker limit. CPU, heap headroom, and free
  physical memory bound concurrency; explicit overrides cannot bypass those
  bounds. Region jobs suppress nested row pools.
- Failure/cancellation drains workers before shared generation state is restored.
- The portable runtime now includes jdk.management for system memory metrics.

## Measured generation benchmarks

Same graph, seed 42, sea level 62, same map size, same machine. Times include
graph evaluation but exclude hashing and disk export. Baseline is packaged v0.1.10;
the new results are from the final v0.1.11 ZIP's bundled runtime. Each benchmark
runs four times in a fresh JVM. No other agent benchmark ran concurrently.

| Workload | v0.1.10 | v0.1.11 |
|---|---:|---:|
| Default graph, 1024, first build | 1.374 s | 2.504 s |
| Default graph, 1024, fourth build | 0.285 s | 0.073 s |
| City map graph, 512, first build | 9.182 s | 10.814 s |
| City map graph, 512, fourth build | 3.836 s | 1.828 s |

Repeated generation improved by about 3.9x and 2.1x in these examples. Cold
first builds were slower: Java compilation and pool warm-up remain significant.
These are local measurements, not promises for other maps or machines. Ordered
river routing, hydraulic erosion, and other dependent stages remain sequential,
so CPU use varies across a graph. Process CPU measurements include Java compiler
and GC work as well as terrain work; no claim is made that every stage uses 31
cores or that higher CPU use alone guarantees a faster build.

## Output equivalence

SHA-256 digests of generated height, water, snow, ice, glacier, rainfall,
temperature, material, and biome channels matched v0.1.10 for:

- Default island graph at 1024.
- City-Map-01-Natural-Mountains-Y1024 at 512.
- Main Generate at 512.
- Coastal watershed, frozen highlands, wetland basin, and mountain rocks at 512.

The default graph hash is
`b03bd8dfe958e14c65d9b27f9cd909e1c2e0306826eb7421bac9137d774eec6e`.
The city graph hash is
`ef848e821a18e2e4258d19b76e3de39e0c14a63b8cf4677f5e434bec89ed114f`.

## Validation

- CpuGenerationTest: bit-exact reference erosion at 32, 256, and 512; 31 observed
  worker threads; interrupted work drains; exceptions propagate; memory/CPU
  limits apply; parallel regions do not multiply nested pools; failed region
  work drains before returning.
- OrganicMaterialsTest, NaturalSnowTest, MountainZonesTest, TerrainDetailTest.
- TerrainGraphTest, EnvironmentDesktopTest, TerraForgeTest, including desktop
  node builds, project saving/loading, graph editing, and undo/redo.
- RegionExportSizeTest: complete 2048 map with 16 region files, metadata and
  state restoration, then resume that rebuilds only a removed region. Automatic
  selection was seven region workers on the first run versus the old cap of four;
  subsequent selection varies with available memory.
- Final portable runtime: CPU tests, generation benchmarks, full region-export
  and resume test, whole-map streaming regression, native EXE smoke export,
  archive SHA-256 verification, and git diff --check.

Full 16K builds and Minecraft gameplay were not rerun. The earlier upstream
EnvironmentTest river-fixture failure is still outstanding.

Package SHA-256:
`9610156ec0346f4016233459c17231759e8cef34f9d41cfb43c562ed3a4f8700`
