# v0.1.16 shared generation optimizations

These changes live in shared code. They do not detect a map name, modify a user's
graph, lower quality, remove trees, or require a new generation setting.

- Minecraft chunk encoding lazily computes horizontal rock warp and regional
  noise once per column, then reuses the exact doubles for every block above it.
- Conservative column bounds bypass detailed block rules for plain underground
  stone/deepslate and empty sky. Bedrock, surface layers, strata, water, snow,
  glacier depth, tree overlays and ground cover retain their original rules.
- Tree passes reuse water-distance fields only when a freshly checked wet-cell
  mask is identical. In-place height/water edits invalidate the cache when they
  change this mask. The cache is transient and is not written into projects.
- River routing uses a primitive priority heap ordered by cost and then cell
  index, and computes each candidate cell's routing noise at most once per route.
  It preserves the previous float costs, tie order, random seeds and paths.
- A memory-limited single-region batch can use the existing bounded terrain CPU
  pool. Multiple concurrent regions retain serial inner terrain loops to avoid
  multiplying their worker counts. Chunk encoders retain their separate limit.

This release adds CPU optimizations. Existing OpenCL stages and CPU fallback
remain available; it does not introduce GPU block encoding or GPU river routing.
The routing-noise cache costs eight bytes per layout cell during routing (32 MiB
at 2048 square). Tree distance reuse retains one float per terrain cell plus a
wet-cell bit mask. Caches belong to a route, chunk or environment, not a static
collection of previously built worlds.

## Measurements on the user's City Map graph

Java 21, 6 GiB heap, seed 4242, sea 62, map size 16384, region (15,15), NVIDIA
RTX 5070 Ti Laptop GPU in Auto mode. Baseline was v0.1.15. Each fresh process
evaluated the full layout once and generated/wrote the sampled region eight
times. The values below are medians of warmed runs 4 through 7.

| Stage | v0.1.15 | v0.1.16 |
| --- | --- | --- |
| Tile generation | 0.386 s | 0.366 s |
| Chunk encoding and saving | 0.343 s | 0.219 s |
| Total sampled region | 0.734 s | 0.582 s |

The sampled region improved about 21% overall; its encoding/saving improved about
36%. This is not a timing for the full 1024-region world. Layout preparation
remained about 40 seconds (40.622 versus 39.673); no substantial layout speedup is
claimed. Cold sampled-region runs were 4.147 and 4.974 seconds respectively:
warm-up and compilation matter, and the warmed improvement is not a cold-start
claim. Features absent from a map cannot benefit from their corresponding cache.

A separate complete 2048-square Island mountains export, held at one concurrent
region, measured warmed runs of 2.081/2.143 seconds before and 1.962/1.660 seconds
after. This simpler map also benefits, with a smaller and variable improvement.

Use `LargeMapRegionBenchmark <graph-path> <region-x> <region-z> <runs>` with old
and new JARs to reproduce a sampled-region comparison. No graph path or contents
from the user's Desktop are included in this repository or the Windows package.

## Correctness and checks

- `GenerationReuseTest`: cached column blocks equal the original block rules for
  all 21 materials, negative and extended heights, varied seeds/world coordinates,
  water, snow, ice and glacier depths. Routing heap order matches the previous
  priority queue over 100,000 mixed insert/remove operations. Water-distance
  reuse and invalidation, and parallel rows in single-region batches, are checked.
- `GenerationCompatibilityCorpus` run against old and new JARs: the same SHA-256
  `7d6c61449d4e90be37e772176021144db0b5463c6fbe2cbb4d11a2a6c3e340e3` for 96
  encoded chunks across 24 world fixtures, eight routing fixtures, and ten chained
  tree passes. Includes extreme seeds, negative world offsets, flat/underwater/
  steep/Y1024/glacial terrain, finite slope constraints and lake routing.
- All eight City Map sampled region files match v0.1.15 byte for byte after
  excluding the region header timestamps.
- `CpuGenerationTest`, `RegionBatchWriteTest`, `RiverCourseTest`,
  `RiverRoutingSeedTest`, `RiverGentleRoutingTest`, `TreeLibraryTest`, and
  `GpuGraphIntegrationTest` pass.
- `RegionExportSizeTest gpu-surfaces` passes complete 16-region export, GPU
  terrain detail/materials/snow, settings, state restoration and missing-region
  resume.
