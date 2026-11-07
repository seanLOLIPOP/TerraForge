# v0.1.15 block encoding

16384-square worlds contain 1024 regions and 1,048,576 chunks. A Java Flight
Recorder profile of region export showed chunk encoding, boxed palette lookups,
and packed block-state serialization dominating CPU samples.

The encoder now uses a per-thread primitive section palette, preserving the
original first-seen ordering, and packs values a word at a time without integer
division and remainder for every value. Terrain generation and compression
settings are unchanged. The palette uses bounded reusable arrays (112 KiB per
encoding thread); parallel encoders do not share mutable palettes.

## Measurements

Same machine, Java 21, 6 GiB heap, seed 4242, Island mountains template.
These are local measurements, not a promise for every graph or full-world build.

| Test | v0.1.14 | v0.1.15 |
| --- | --- | --- |
| Complete 2048 world, 16 regions, one concurrent region, warmed runs | 3.495 / 3.556 s | 2.168 / 2.434 s |
| Real region (15,15) at 16384 map scale, warmed runs 4–7, median total | 0.309 s | 0.111 s |
| Same sampled region, median encoding and saving only | 0.276 s | 0.079 s |

The sampled large-map test evaluates the full 2048-cell layout, then repeatedly
builds and writes one 512-square region. It does not measure all 1024 regions.
Cold JVM timings varied substantially during compilation; warmed measurements
exclude those initial runs. First export of the complete 2048 world measured
9.859 versus 9.180 seconds, so the warmed speedup should not be applied to cold
startup. The full-map test improved about 34–38%; the warmed sampled-region test
improved about 2.8 times overall.

Reproduce with `tests/RegionBuildBenchmark.java` and
`tests/LargeMapRegionBenchmark.java`, using the corresponding release JAR and
`lib/*` on the classpath. Set `-Dterraforge.regionThreads=1` for the full-world
comparison to hold concurrency constant.

## Correctness

- `BlockEncodingTest`: reference ordered-map indices and palette order, up to
  4096 unique block IDs, repeated palette resets, and reference bit packing for
  widths 1 through 32 and partial words.
- Compared all 16 output region files of the complete world against v0.1.14:
  byte-identical after excluding only the region header timestamps.
- Compared the 16384-scale sampled region against v0.1.14: byte-identical under
  the same timestamp exclusion.
- Compared the Y1024 fixture with snow, water, trees and nonzero world offsets
  against v0.1.14: byte-identical under the same timestamp exclusion.
- `RegionBatchWriteTest`: concurrent encoding, reference output, cancellation
  drainage and partial-file cleanup.
- `RegionExportSizeTest gpu-surfaces`: GPU terrain detail, materials and snow,
  complete region export, settings, state restoration and resume.
