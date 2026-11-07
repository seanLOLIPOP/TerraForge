# TerraForge v0.1.14 — GPU graph pipeline validation

## What changed

Graph builds now retain compatible intermediate maps in OpenCL buffers instead
of downloading and uploading each node. Thermal erosion copies its input on the
device, preserving shared branches, and keeps height/flow/amount resident across
iterations. CPU-only stages materialize and invalidate their required inputs;
other cached GPU fields can remain on the device. Builds download final channels
and return only the target in their fields/environments maps. Live node previews
use the original evaluate API and materialize every intermediate field.

Closed groups of scalar terrain nodes can be compiled into one kernel: noise,
ridges, constants, island shaping, regional relief, terraces, remapping, clamping,
height masks, blends, and an output without auxiliary inputs. Every intermediate
value explicitly rounds to float and clamps at the original node boundary.
Groups with external consumers of an internal value stay separately cached.
Slope masks and thermal erosion are separate passes because they read neighbors.
Mountain coastal-distance calculations and environmental/routing processors
synchronize the data their CPU preparation needs.

Build into map, graph export, and the region exporter's shared layout use the
build API. Standard Generate presets retain their existing v0.1.13 path. Region
encoding and file writes remain on CPU. Region-local environmental passes keep
the GPU support introduced in v0.1.13; this optimization primarily targets the
terrain graph and shared layout, not every step of a Minecraft export.

Use Nodes > Generation > Auto. Compatible connected cheap nodes now remain on
GPU in Auto because their inputs are already there. GPU mode still includes
additional surface/water passes that may cost more to transfer than they save.

## Measured graph improvement

Same RTX 5070 Ti Laptop GPU (12 GiB), Ryzen 9 8940HX (16 cores/32 threads), Java
21.0.13, 6 GiB Java heap, NVIDIA 591.97. Same 2048x2048 Island mountains graph,
seed 4242, sea level 62. Four evaluations per fresh JVM, no map cache reuse;
GPU driver compiler caches already existed. Baseline uses the same binary with
-Dterraforge.gpuPipeline=false to select the pre-pipeline path. Both use Auto.

| Path | First evaluation | Warm 1 | Warm 2 | Warm 3 |
| --- | ---: | ---: | ---: | ---: |
| Previous GPU path | 1152.822 ms | 179.852 ms | 183.614 ms | 165.054 ms |
| Resident + fused path | 580.293 ms | 60.143 ms | 55.526 ms | 56.925 ms |

Warm medians: 179.852 ms versus 56.925 ms, approximately 3.16x faster. This is
terrain graph evaluation, not total export time or an across-all-graphs promise.
First-use compiler, JVM, driver, and thermal conditions affect timing.

Instrumented host/device data per evaluation:

| Path | Uploaded | Downloaded | GPU jobs | Fused nodes |
| --- | ---: | ---: | ---: | ---: |
| Previous GPU path | 67,108,928 bytes | 67,108,864 bytes | 4 | 0 |
| Resident + fused path | 152 bytes | 16,777,216 bytes | 3 | 4 |

Total transferred data falls from roughly 128 MiB to 16 MiB (about 8x less).
The optimized upload is just parameters. Full-array comparison tests verify the
output, not only the benchmark's sampled checksum.

## Resource and failure behavior

All active graph sessions share the backend's 256 MiB working-buffer cap, also
bounded to one quarter of reported device memory. Temporary parameter/erosion
buffers count toward the cap. A graph releases its buffers on success, failure,
or cancellation; no resident map cache survives evaluation. On memory pressure,
fields are materialized and the existing GPU or CPU path is used. Very small
budgets may increase transfers and reduce speed rather than improving it.

If a GPU failure loses resident values, evaluation is restarted on CPU under a
thread-local override. This avoids using uninitialized host placeholders and
avoids changing another evaluation's requested mode. The backend still disables
itself after driver/kernel errors as in v0.1.13. Cancellation drains queued work
before buffers are released. Kernel cache hits refresh their eviction order.

Advanced diagnostics: -Dterraforge.gpuPipeline=false disables residency/fusion;
-Dterraforge.gpuBudgetMiB=8 exercises memory fallback. The budget option is clamped
to 8-256 MiB and never increases the normal device-memory limit. The existing
GPU mode and device-name-filter options remain available. GPU CPU-only fallback
requirements and cross-vendor limitations are described in
[the v0.1.13 validation](GPU-GENERATION-VALIDATION.md).

## Verification

- GpuPipelineTest compares complete arrays against the pre-pipeline GPU path for
  island, masked, and terraced graphs, plus float-rounding/clipping extremes,
  shared branches across erosion, a CPU hydraulic boundary, all preview fields,
  cancellation, subsequent reuse, and concurrent evaluations. Successful runs
  leave zero tracked resident bytes. Full 2048 builds test multiple dispatches.
- The same pipeline and real-map integration tests pass on NVIDIA and the
  installed AMD integrated GPU (gfx1036). Intel hardware remains untested.
- GpuGraphIntegrationTest evaluates the supplied Island-mountains.tgraph at
  512 cells, with 16384-block scale, and matches heights, water, snow, ice,
  temperature, rain, river, glacier, materials, biomes, and object placements.
- The pipeline suite also passes with an 8 MiB buffer budget, exercising
  materialization and fallback instead of requiring the fast path to fit.
- GpuPipelineFailureTest injects a shader compiler failure after a fused result
  exists only on GPU, and verifies exact CPU restart output and buffer cleanup.
- Existing GpuGenerationTest, GpuSafetyTest, missing-runtime GpuFallbackTest,
  and CpuGenerationTest pass with the new evaluator.

Bit-exact comparisons cover these fixtures on the tested devices. They do not
make a universal promise about floating-point math across all GPU drivers.
No new 16384x16384 full world export, Intel hardware test, or Minecraft gameplay
validation was performed. CPU-dependent graphs and disk-heavy exports can see
much smaller improvements than the graph benchmark.

## Windows package checks

The extracted v0.1.14 portable archive passed GpuPipelineTest (including the
full 2048 comparison), GpuPipelineFailureTest, and GpuGenerationTest using its
bundled Java runtime and manifest dependency paths. A complete 2048x2048 world
with GPU surface passes exported all 16 regions and resumed by rebuilding only
the missing region. The native EXE smoke export produced level.dat. The package
JAR matched the current build and the archive checksum was verified.
