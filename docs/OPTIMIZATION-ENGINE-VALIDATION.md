# v0.1.17 shared optimization engine

All changes use shared generation/export paths. No map filename is special-cased.
Unsupported devices, sizes and operations retain CPU fallbacks. Full builds and
legacy erosion/distance algorithms remain the defaults.

## Implemented changes

| Requested work | Implementation and practical scope |
| --- | --- |
| Vectorized CPU noise | Java 21 Vector API hashes/interpolates multiple samples per instruction in graph Noise/Ridges source nodes. Optional **CPU SIMD**; scalar fallback when the module is unavailable. Off by default after the local benchmark was slower. |
| Compiled graph sections | Extends the existing GPU fusion to groups above up to three external dependency boundaries, preserving explicit float boundaries, shared branches, and environmental channels. Water remapping remains an explicit boundary. |
| GPU erosion | Existing exact thermal erosion plus optional parallel heightfield erosion for water-erosion nodes. The new algorithm differs from ordered CPU droplets. |
| GPU river/water distance | Optional jump-flood Euclidean distance fields for sea distance and tree/water-margin distance. Routing stays on CPU; these distances differ from legacy chamfer scans. |
| Repeated solid/empty sections | Bounded cache of serialized uniform-section payloads keyed by section height, block and exact encoded biome content. |
| Fewer copies | Safe final-output aliases, existing resident GPU fields, bounded stage snapshots rather than recomputing upstream stages, and reusable scratch arrays. Cache snapshots deliberately copy mutable arrays for isolation. |
| Requested areas only | **Explore first** builds nine regions near spawn. Missing overview areas use the coarse layout; block windows generate and persist regions as requested. **Finish whole map** and export complete missing output. |
| Affected-region rebuilds | Content fingerprints include all block-affecting arrays, settings, seed, coordinates, trees, cover, compression and algorithm mode. Identical encoded regions are reused across graph edits. Tiles still generate to establish whether their output changed; global changes conservatively affect all regions whose content changes. |
| Completed-stage cache | Dependency-keyed hydraulic/thermal erosion, rivers, lakes and climate snapshots, including size/seed/sea/backend/algorithm context. Editor positions and unrelated graph nodes do not invalidate upstream results. Session cache bounded to at most 384 MiB and 10% of heap; full-field previews do not skip required node fields. |
| Tree collision indexing | Spatially indexed bounding rectangles replace scans of every existing tree. Placement order and random draws are unchanged. |
| Sparse tree overlays | Allocate only 16-high sections touched by trees; keep direct column-top summaries for heightmaps. Dense compatibility access remains available. |
| Palette/NBT caches | Bounded immutable block-state tags and serialized uniform sections with exact biome-key equality. |
| Compression choices | Level 1 default, optional 0 or 3. Reusable per-encoder compressors; default normal exports also use the selected level. |
| Adaptive workers | Region admission rechecks CPU/heap/system-memory bounds between jobs, grows or shrinks within the configured maximum, and drains on failure/cancel. Single-region terrain work can parallelize. |
| Faster previews | Publish 64, 256 and up to 512-square previews progressively, at the selected world scale. Scaled previews omit individual tree scatter; full builds retain it. |
| GPU rock layers | Optional 64-depth batches for 512-square export tiles, shared by region-mode maps and eligible 512-square direct exports. Skip low/mostly wet tiles. All other sizes retain CPU column caching. Temporary output is released after encoding. |
| Reusable buffers | Per-thread 4096-block scratch, bounded NBT/compression streams, and persistent reusable compressors. Returned bytes are independent copies. |
| Overlap work and saving | A bounded prefetch pipeline generates/compresses the next 64 chunks while the previous batch writes. At most two batches are live; worker failure/cancel drains the producer. Concurrent region workers also overlap terrain and encoding. Direct whole-world exports on complete 512-region grids use the same pipeline, with correct global coordinates; smaller/partial grids retain their compatible writer. |
| River preparation | Cache local 3x3 minimum heights once per route instead of repeating the scan for each candidate edge, in addition to the earlier primitive queue/noise reuse. Stage caching avoids repeated upstream routing after downstream edits. |
| Nearby first | Stable distance ordering around spawn for full region builds and initial exploration areas. Finishing an exploration map locks one region at a time so an already open block view can request nearby areas between jobs. |

The new algorithm options are explicit because results can change. Their build
folders are separate from legacy builds. Default legacy build-folder keys stay
compatible with v0.1.16; completed region output can resume. GPU/algorithm context
is captured for an exploration map and propagated to its region workers.

The encoded-output cache is bounded to 1 GiB by default and lives in
`encoded-regions` in the portable data area. A bounded index of original region
files also permits reuse after payload eviction, so an entire previous 1024-region
map can remain reusable without retaining another full copy in that cache.
Original files are checked against stored size and timestamp-normalized SHA-256
before reuse; missing or modified originals fall back to generation/encoding.
Reference metadata is pruned toward 8192 entries periodically. Stage caching persists only during
the process. There is no claim that every optimization helps every graph or that
changing global mountains or rivers can always be confined to a small region.

## Measurements

Local Java 21, 6 GiB heap, Ryzen 9 8940HX, NVIDIA RTX 5070 Ti Laptop GPU. These are
fixtures and sampled stages, not complete 16384-world timings.

| Test | Result |
| --- | --- |
| User City Map layout, 16384 scale, seed 4242 | Cold 41.948 s; repeated cached evaluations 3.361 / 3.599 s, two stage hits each, identical heights. Another cold validation run measured 39.331 s. First preparation remains around 40 s. |
| Rock-heavy 512-square region, warmed full encoding/saving including GPU preparation | CPU runs 0.332 / 0.235 / 0.228 s; GPU 0.142 / 0.136 / 0.138 s. Median 0.235 to 0.138 s, about 41% faster. Complete region bytes match apart from timestamps. |
| CPU source-noise fixture, 1024 square | Warm scalar roughly 12–13 ms; SIMD roughly 18–50 ms in this run. SIMD is optional and off by default. |
| 64 representative chunks, compression level 0 | 0.102–0.123 s, 1,609,982 compressed payload bytes. |
| Same chunks, compression level 1 | 0.108–0.131 s, 236,632 bytes. Default retained. |
| Same chunks, compression level 3 | 0.130–0.131 s, 220,067 bytes. |

Compression timing includes chunk encoding but not disk writes. Uncompressed
payloads were about 6.8 times larger; sector padding also affects final disk use.
Do not generalize the GPU fixture's speedup to a full map with different terrain.

## Validation

- Legacy `GenerationCompatibilityCorpus` retains SHA-256
  `7d6c61449d4e90be37e772176021144db0b5463c6fbe2cbb4d11a2a6c3e340e3`:
  96 chunks across 24 varied fixtures, eight routing fixtures, ten tree passes.
- Four user-map regions (ocean, central area, highest peak and another sample)
  match previous saved region bytes excluding timestamps at 16384 scale.
- `OptimizationFeaturesTest`: exact SIMD samples including tails/extreme seeds,
  scalar/SIMD graph equivalence, stage hits, input invalidation, snapshot isolation.
- `FastGpuAlgorithmsTest`: GPU distances, all-dry map, copied/bounded erosion,
  rock-layer samples and released device buffers. Passed on NVIDIA and AMD gfx1036.
- `GpuPipelineTest`: fusion, transfers, full previews, shared branches, CPU
  boundaries, cancellation and concurrency, on NVIDIA and AMD.
- `GpuGenerationTest`, `GpuGraphIntegrationTest`, `GpuPipelineFailureTest`,
  `GpuSafetyTest`, and no-device `GpuFallbackTest` passed.
- `RegionReuseTest`: exact region reuse, content/mode/coordinate invalidation,
  references after payload eviction, modified-original rejection, captured worker
  settings, and changing worker caps. `RegionBatchWriteTest`: byte-identical output, parallel
  encoding, cancellation cleanup and producer drainage.
- `ExplorationBuildTest`: initial nine of sixteen regions, on-demand tenth,
  finish all sixteen, complete export and restored global build state.
- `FullWorldBatchTest`: a direct 1024-square export preserves global chunk/noise
  coordinates and trees crossing region boundaries across all four region files.
- CPU generation, tree library, region export/resume and toolbar-render tests
  passed. The new controls were visually checked in the rendered node editor.

Reproduction tools: `GraphCacheBenchmark`, `RockLayersBenchmark`,
`CompressionBenchmark`, `NoiseBenchmark`. Pass
`--add-modules jdk.incubator.vector` when running SIMD tests from a Java command
line; native Windows and console launchers include it. The implementation follows
the [Java 21 Vector API](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.incubator.vector/jdk/incubator/vector/package-summary.html).

The user's graph file is not included in the source or Windows package.
