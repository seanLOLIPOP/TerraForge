# TerraForge v0.1.12 — region batches

Nodes now offers Region batch: Auto, 2, 4, 8, or 16. The selection is an upper
bound on concurrent 512x512 regions, subject to existing CPU, Java heap, and
available system memory limits. Auto is the default. Slots refill as regions
finish rather than waiting for a whole group to complete. The setting applies
to the current app session and does not change the saved graph recipe.

Each region encodes 64 chunks per batch on a shared encoder pool (eight workers
by default), separate from terrain-row workers. Compressed chunks are committed
in their original order through a 1 MiB output buffer. The region header is
written once at the end, removing thousands of random seeks and small writes.
Only one compressed chunk batch per active region is retained. Shared sea/slope
fields are prepared before region workers start, avoiding first-use cache waits.

Advanced `terraforge.chunkThreads` overrides are bounded to 16 and to the CPU
limit. The default eight encoder workers performed better on this machine than
trying to saturate all logical CPUs with encoding and allocation at once.

## Complete-map measurement

Same Ryzen 9 8940HX, Java 21, 6 GiB Java heap, four concurrent regions, default
island graph, seed 4242, sea level 62. Each run built a fresh 2048x2048 world
(16 regions / 16,384 chunks). Two maps were built in the same JVM for each version;
no existing region files were reused and no competing agent workload ran.

| Build | v0.1.11 | Batched build |
|---|---:|---:|
| First fresh map | 14.575 s | 13.711 s |
| Second fresh map, warmed JVM | 8.547 s | 4.111 s |

The second map was approximately 2.1x faster in this paired run. First-build
performance was similar; Java compilation and machine load remain significant.
These measurements do not guarantee the same speedup on other maps or hardware.

## Validation

- RegionBatchWriteTest compared all 1,024 chunks in a region to the old serial
  writer, including offsets, compressed bytes and padding. Files were identical
  after excluding only their timestamp header. The fixture includes Y1024 terrain,
  snow, elevated water, a rotated imported tree, and nonzero world coordinates.
- The same test verified cancellation drains encoding work and removes partial
  files, and that chunk batches use multiple encoders inside a region worker.
- RegionBatchControlsTest verified Auto/manual selection, runtime limits,
  region-only enablement, restoration after busy state, and rendered the toolbar.
- RegionEditorBuildTest built a full 2048 map through the desktop editor, verified
  all 16 region files, compared full-resolution windows to source tiles, copied
  the finished world, and resumed without rebuilding completed regions.
- Packaged-runtime checks cover region export/resume, batch output equivalence,
  CPU generation regression, and native Windows EXE smoke export.

Full 16K builds and Minecraft gameplay were not rerun. The known upstream
EnvironmentTest river-fixture failure remains outside this change.
