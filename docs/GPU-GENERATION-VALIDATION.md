# TerraForge v0.1.13 — GPU generation validation

## Backend and controls

Nodes > Generation offers Auto (recommended), GPU, or CPU. This is a session
setting and does not alter graph serialization. Auto offloads expensive supported
passes; GPU additionally includes cheap mapping/classification and water-shaping
passes. Maps below 512 cells per side stay on the CPU. Unsupported stages always
use the existing CPU implementation, including ordered hydraulic erosion, river
routing, neighborhood preparation, and object placement. Region compression and
saving remain on CPU.

The backend uses LWJGL OpenCL 3.3.6 and OpenCL C 1.2 with cl_khr_fp64. Compatible
NVIDIA, AMD, and Intel devices can use the same implementation; Intel hardware
has not been tested. A missing driver/runtime, unsupported device, too-large job,
or GPU error uses CPU fallback. Devices without double precision still run the
app on CPU. There is no CUDA toolkit dependency. macOS acceleration depends on
an available compatible OpenCL driver; no new macOS binary was built.

A single synchronized queue owns context, kernels, and buffers. This avoids
competing contexts and argument races between concurrent region workers. Source
nodes and mapped outputs read back in at most 1,048,576-float batches (4 MiB).
Mapping and erosion working buffers are limited to 256 MiB and one quarter of
the device's reported memory. Programs are cached with a 64-entry bound. Thermal
erosion retains its input/flow/amount on device across iterations. Other passes
currently read back at node boundaries; this transfer cost is why Auto keeps
cheap operations on CPU. GPU info describes the backend, not a promise that
every node or every part of a build uses it.

Advanced JVM options: -Dterraforge.gpu=auto|gpu|cpu and
-Dterraforge.gpuDevice=<case-insensitive device-name substring>. Default selection
uses the eligible GPU with the largest reported memory. A driver/kernel failure
disables the GPU backend for the process; restart to retry.

## Covered GPU passes

Noise, ridges, island shaping, all local mountain styles (including seeded spur
and natural ridge tables), regional relief, standard Generate presets, thermal
erosion, ocean shaping, climate, terrain detail, organic material classification,
mountain surface/biome zones, and natural snow are available in Auto. Shared
coarse fields, seeded ridge tables, and neighborhood preparations use CPU.

GPU mode also includes constants, terraces, remapping, clamping, blending,
height/slope masks, lake basin shaping, coast/wetland/sediment surface passes,
legacy snow, glacier/freeze/rapids, and biome classification. Ordered lake outlet
routing and surrounding terrain blending still use CPU. GPU mode can be slower
than Auto because it transfers additional maps.

## Measurements

Machine: Ryzen 9 8940HX (16 cores/32 threads), RTX 5070 Ti Laptop GPU (12 GiB),
NVIDIA driver 591.97, Java 21.0.13, 6 GiB Java heap. Same 2048x2048 Island
mountains graph, seed 4242, sea 62. Four evaluations per fresh JVM; first use
includes CPU JIT or GPU initialization/compiler costs. No cached map reuse.

| Mode | First evaluation | Warm evaluation 1 | Warm 2 | Warm 3 |
| --- | ---: | ---: | ---: | ---: |
| CPU | 2995.774 ms | 242.191 ms | 237.786 ms | 268.014 ms |
| Auto | 1220.650 ms | 175.844 ms | 166.344 ms | 171.972 ms |
| GPU | 1006.621 ms | 241.634 ms | 222.098 ms | 217.427 ms |

Warm median: Auto 171.972 ms versus CPU 242.191 ms, approximately 1.41x faster.
This measures terrain graph evaluation only, not Minecraft encoding or disk
export. Checksums matched. It is not a guarantee for other graphs or hardware.
Tests were also run on the installed AMD integrated GPU, reported as gfx1036;
compatibility tests passed, but no AMD performance claim is made.

## Correctness and recovery

- GpuGenerationTest: CPU/GPU comparison for positive, negative, and overflowing
  source seeds; all three mountain styles, coastal attenuation, island/relief,
  mappings and masks, thermal erosion, ocean, climate, detail, all standard
  presets, material/snow and mountain-zone fixtures, ice/biome/glacier/rapids,
  lake basin, coast flattening, sediment, and wetlands.
- Heights were identical in the tested source/mountain/standard-preset cases.
  Ocean and climate had small floating-point differences (roughly 0.000005 and
  0.00000006 respectively). Surface categories matched in the fixtures. These
  tests do not promise universal bitwise equivalence: math libraries and GPUs
  can differ near thresholds. CPU mode retains exact CPU behavior.
- GpuSafetyTest: in-flight cancellation drains work; cancelled GPU thermal
  erosion leaves caller data unchanged; concurrent callers match reference;
  later calls work after cancellation; shader failure switches to CPU.
- GpuFallbackTest: both a nonexistent device and missing OpenCL runtime produce
  identical CPU reference output without crashing generation.
- CpuGenerationTest: original bit-exact CPU thermal reference, CPU worker
  cancellation/failure propagation, and region CPU/memory limits pass.
- RegionBatchControlsTest: Auto/GPU/CPU properties, busy-state controls,
  region batch settings, and rendered toolbar verified. The generation selector
  has enough width to display its mode.

## Remaining validation limits

No Intel machine or macOS hardware was available. No new full 16384x16384 GPU
export or Minecraft gameplay validation was performed. Speed depends on graph,
resolution, driver, thermal state, transfer cost, and which stages stay on CPU.

Build folders include a GPU-generation format salt, selected backend mode, and
explicit device filter. Changing modes starts/resumes a separate build folder;
pre-GPU cached worlds are not silently reused as a new GPU build.

The extracted Windows archive was tested with its bundled Java 21 runtime:
GPU equivalence suite (including zoned natural snow), CPU generation regression,
region chunk equivalence/cancellation, manifest dependency loading without an
explicit lib wildcard, and the native EXE smoke export all passed.

- RegionExportSizeTest gpu-surfaces: complete 2048x2048 export with terrain detail,
  organic materials and natural snow used GPU passes, produced 16 regions, and
  resumed by rebuilding only a deleted region while preserving finished files.
