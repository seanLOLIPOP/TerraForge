# TerraForge v0.1.10 — whole-map streaming

The Windows portable build replaces movement-triggered full-neighborhood rebuilds
in Whole Map Blocks with cached 64x64 block tiles. Missing tiles build nearest
first on the background worker; already uploaded tiles remain visible. A GPU
coverage texture hides simplified terrain only where exact tiles have arrived.
Eviction restores the overview underneath those tiles.

The default textured target is 1024 blocks across, up from 768; +/- adjusts it up
to 1536 blocks across. The target is a square centered near the camera, clipped
to the map bounds. The GPU mesh-buffer budget is 288 MiB. Complex terrain can
reduce actual detailed coverage below the requested target. Simplified terrain
continues to cover the whole map.

Textures are appended to a reusable atlas. Each tile merges opaque and transparent
geometry into at most two draw calls. Artificial underground bottom faces are
omitted above the real world floor. Region-built maps fetch individual cached
512-block source regions rather than requiring a 1536-block window first.

## Measured comparison

Same generated 512x512 island, seed 2024, relief 170, 256-block detail span,
camera X=192 -> 256 -> 192 with Z=256. CPU mesh work only; no FPS claim.

| Work | Original v0.1.9 mesh | Retained tiles |
|---|---:|---:|
| Initial mesh | 1.750 s | 1.299 s |
| Move and return | 2.502 s | 0.191 s |
| Total | 4.253 s | 1.490 s |

These timings are one local benchmark run, not a guarantee for other maps or
machines. The GPU integration test independently verified 16 initial tiles,
4 new tiles for a 64-block move, and zero rebuilds on backtracking, with existing
GPU buffer identities retained.

## Validation

The final ZIP was extracted and tested with its bundled Java runtime:

- WholeMapStreamingTest: tile planning, retained buffers, bounded upload bytes,
  eviction under a deliberately small budget, overview coverage restoration,
  and correct absolute coordinates across four synthetic cached source regions.
- MinecraftViewTest: vanilla models, all 133 imported tree block states in four
  rotations, water transparency, snow layers, slabs, and terrain elevations.
- WholeMapBlocksTest, WholeMapPeakTest, ViewReplacementTest, EasyExportTest.
- Actual whole-map OpenGL render and 4096x4096 capture on an NVIDIA RTX 5070 Ti
  Laptop GPU. Output: whole-stream-final.png.
- Portable EXE smoke export and final ZIP checksum verification.
- git diff --check.

Minecraft assets used for validation came from the user's installed 1.20.4
client and are not included in the package. The full 16K terrain-generation
workflow and other graphics vendors were not retested. The previously reported
upstream EnvironmentTest river-fixture failure is outside this rendering change.


Package SHA-256:
f44caf4bf6d9946cfe2287b6d5ced04856fdc0381345de70f21d0f4dd90040d1
