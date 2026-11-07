# TerraForge v0.1.21 - Fix straight rivers

- Drainage-first rivers now carve the final shaped centerline in direct builds
  and coarse layouts, matching the geometry used for regional refinement.
- Final terrain-checked spline interpolation rounds drainage-first river corners.
- Local bend fitting prevents blocked points from shrinking a whole river reach.
- Natural channel curves now scales drainage-first meanders. Zero disables added
  meanders; new river nodes default to 1. Existing saved values are retained.
- Sources, junctions, and outlets stay anchored; terrain checks constrain bends.

# TerraForge v0.1.20 - Lakes that follow the landscape

- Valley-following lake outlines favor low ground and stop against high ridges.
- Maximum ridge cut limits carving outside the seed basin.
- Bounded, varied bank blending preserves steep shore faces and mixes sand,
  gravel, exposed rock, and damp-soil deposits according to slope and rainfall.
- Lake shore deposits survive the final material pass, cache snapshots, and
  sampling into regional builds. Snow, ice, glaciers, and river cells retain priority.
- New controls default off for compatibility with existing graphs.

# TerraForge v0.1.19 - Irregular natural lakes

- Opt-in non-round lake outlines: broad bays, headlands, and smaller shoreline
  indentations, plus elongation and direction controls.
- Shallow shelves transition to asymmetric deeper basins.
- Remove disconnected new puddles while preserving existing water channels.
- Matching CPU and GPU shape calculations; regional builds share the global layout.
- Existing graphs retain their lake algorithm until Realistic basin is enabled.

# TerraForge v0.1.18 - Natural shorelines

- Opt-in slope-based variable beaches, sand/gravel/rock choices, and shallow
  underwater shelves in Coast / beach.
- Fixed water levels and river masks are preserved. Regional builds use the
  shared global shoreline profile. Existing graphs keep the legacy behavior.
- Organic surface materials can enable matching natural beach classification.
  This optional classifier currently uses parallel CPU workers.
- See docs/NATURAL-SHORELINES.md for controls and limitations.

# TerraForge v0.1.14 — Faster GPU graph builds

- Compatible connected terrain nodes keep their intermediate maps on the GPU,
  including thermal erosion. CPU stages synchronize the inputs they require.
- Closed groups of noise/ridges, island/relief, remap/clamp/terrace/blend/mask,
  constant, and output nodes can run as one GPU pass during builds. Explicit
  float rounding and clamping preserve each node's mathematical boundary.
- Builds download final channels; live node previews still return every field.
- Shared branches remain separately cached and erosion preserves input maps.
- GPU working buffers are limited across active evaluations. Memory pressure
  materializes fields and uses the existing GPU or CPU fallback paths.
- If a GPU failure loses resident data, evaluation restarts on CPU. Cancellation
  drains queued work and releases the graph's buffers.
- Use Nodes > Generation > Auto. Region compression and disk writes stay on CPU.

# TerraForge v0.1.13 — Vendor-neutral GPU terrain generation

- Nodes adds Generation: Auto, GPU, and CPU, plus GPU info. Auto is recommended:
  it accelerates expensive supported stages while leaving cheap operations on CPU.
- OpenCL 1.2 GPU paths cover noise, ridges, island shaping, all mountain profiles,
  regional relief, standard Generate presets, thermal erosion, ocean shaping,
  climate, terrain detail, organic materials, mountain zones, and natural snow.
- GPU mode additionally includes constant/remap/clamp/terrace/blend/mask nodes,
  lake basin shaping, coast/wetland/sediment surface passes, legacy snow,
  glacier/freeze/rapids, and biome classification.
- Ordered routing, hydraulic erosion, neighborhood preparation, object placement,
  Minecraft region encoding, and disk writes continue to use the CPU.
- Compatible NVIDIA, AMD, and Intel OpenCL drivers are supported. Double precision
  is required; missing runtimes, unsupported devices, oversized jobs, or GPU errors
  fall back to CPU. Small maps stay on CPU. Intel hardware is not yet tested.
- A single serialized GPU queue avoids competing region-worker contexts. GPU jobs
  use bounded staging/output batches and at most 256 MiB of working buffers,
  further limited to one quarter of the selected device's reported memory.
- GPU calculations may differ slightly from CPU floating-point results. Select
  CPU when exact reproduction of the CPU implementation is required.
- The Windows package includes the OpenCL Java bindings; the GPU driver supplies
  the OpenCL runtime. No CUDA toolkit or vendor-specific app package is needed.

# TerraForge v0.1.12 — Batched region encoding and buffered writes

- Nodes adds Region batch: Auto, 2, 4, 8, or 16 concurrent regions. Manual choices
  remain upper bounds within the existing CPU and memory limits.
- Regions encode 64 chunks per batch using a shared encoder pool, then commit
  payload data sequentially with a 1 MiB output buffer and one final header write.
- The default encoder pool is capped at eight threads to balance generation,
  allocation, and compression work. Advanced `terraforge.chunkThreads` overrides
  are capped at 16 and the CPU-thread limit.
- Shared sea-distance and slope fields are prepared before region jobs start.
- Status reports completed regions and the effective concurrent-region limit.
- Region files retain their chunk order, compression, and contents. Failed or
  cancelled batches drain their workers and remove incomplete temporary files.

# TerraForge v0.1.11 — Multicore map generation

- Nodes → Build into map uses a shared CPU pool for independent rows in noise,
  ridges, mountains, masks, materials, climate, lake shapes, snow, and slope maps.
- Thermal erosion computes flow in parallel and gathers incoming material in
  the original floating-point order, preserving terrain output exactly.
- Small previews stay sequential. One logical CPU is reserved for other work.
- Region generation removes the four-worker cap and automatically limits work
  by CPU count, Java heap headroom, and available system memory. Region jobs
  suppress nested row parallelism so the two pools do not multiply each other.
- Cancellation and failures drain CPU workers before generation state is reused.
- Ordered river routing and hydraulic erosion still have sequential stages;
  CPU utilization varies by node and Java warm-up affects the first build.

# TerraForge v0.1.10 — Progressive whole-map block detail

- Whole Map Blocks now retains 64-block detail tiles and builds only missing
  tiles as you move, nearest first, instead of rebuilding one large neighborhood.
- Default textured target increased from 768 to 1,024 blocks across, adjustable
  with `+`/`-` to 1,536. Complex scenes remain subject to the mesh memory budget.
- Loaded tiles remain visible while new tiles arrive; the whole-map overview
  fills remaining gaps. Nearby cached tiles are reused when backtracking.
- Texture uploads append newly encountered materials instead of rebuilding the
  whole atlas for each tile. Tile geometry is merged into at most two draw calls.
- GPU mesh buffers are capped at 288 MiB; farthest detail is discarded first.
- Region-built maps fetch individual source regions instead of constructing a
  full 1536-block window before showing any local detail.
- Includes the portable Windows x64 launcher and bundled Java 21 runtime.

# TerraForge v0.1.9 — Mouse look fix for Wayland

- Fixed: on Ubuntu desktops running Wayland (common with NVIDIA), the Minecraft view and Whole map blocks could receive no mouse movement after v0.1.8 switched on raw mouse input. Raw input is now skipped on Wayland, and anywhere it delivers no movement within 1.5 seconds of capturing the mouse, the app switches back to normal mouse input automatically.
- `-Dterraforge.noRawMouse=true` turns raw input off entirely.
- Includes all v0.1.8 changes (calmer, adjustable 3D controls).

# TerraForge v0.1.8 — Calmer 3D controls

- Mouse controls in all 3D views (GPU 3D, Minecraft view, Whole map blocks and the editor's 3D) are half as sensitive by default.
- `[` and `]` lower or raise mouse sensitivity in 10% steps. In the Minecraft and Whole map views, the mouse wheel sets fly speed. Both settings are shown in the window title and remembered in `controls.properties` beside the app.
- Linux: captured mouse look uses raw mouse motion where available, so the cursor re-centring no longer inflates or jitters movement. Single oversized cursor jumps are ignored.
- Smooth-scrolling wheels and touchpads zoom one step per event instead of racing.
- Includes all v0.1.7 changes (Build region by region option).

# TerraForge v0.1.7 — Region builds on every computer

- New **Build region by region** option in Nodes, next to Map size. It is on by default for 8192 and 16384 maps, so they build region by region on disk on every computer. Before, computers with lots of RAM (such as Linux desktops) fell back to the old in-memory build. Untick it to build in memory. Maps too large for memory always use regions.
- Includes all v0.1.6 changes.

# TerraForge v0.1.6 — Whole map blocks on region maps, macOS fixes

## Whole map blocks view
- **Real blocks on region-built maps.** On maps built region by region (for example 16384 maps on 8 GB computers), the view now works in real block coordinates. The distant terrain comes from the map overview at true height. Within 1200 blocks of the ground, the real blocks around you are built from the region files, with trees and vanilla textures, exactly as they export. Flying further away loads the next area automatically.
- **Fixed the grey, blotchy terrain.** The close-up blocks were drawn at the wrong scale, as a 6 km patch up to Y1024 on top of the map.
- **Fixed glitchy distant terrain on macOS.** Distant terrain detail levels are now declared correctly for Apple's OpenGL driver.
- **Lower CPU and GPU use.** The view idles while the camera is still, so it no longer keeps a MacBook busy. The oversized block patch that was the main cause of the slowdown is gone.

## Other
- Maps you already built are reused. Upgrading does not force a rebuild.

## Also in this version (source now included for the v0.1.3-r2 and v0.1.4 Mac builds)
- macOS support: forward-compatible OpenGL contexts, Mac Minecraft folders, Mac LWJGL natives on the class path.
- Views of region-built maps scale heights correctly (no more 8x or 32x tall mountains).
- "Preview as 16384 map": small builds of large-map graphs are scaled previews with true proportions.

# TerraForge v0.1.3 — Portable region-backed editor

- Build large maps region by region on disk, with a lightweight editor overview.
- Load full-resolution Minecraft-view windows from the shared region layout on demand.
- Export already-built region maps by copying the world without editor overview files.
- Reuse completed regions when rebuilding the same graph and settings.
- Keep region builds, recovery and viewer cache beside the portable app, with a home-folder fallback for app data when needed.
- Retain bundled Java 21 and explicit Java 21 launcher selection.

# TerraForge v0.1.2 — Flexible low-memory export

- Select region export sizes from 2048 to 16384 blocks in the export dialog.
- Build 512 × 512 regions separately, with concurrency limited by available Java heap.
- Resume region exports without rebuilding completed regions.
- Record the selected map size, seed, sea level and region count in export metadata.
- Adapt river sampling to the selected region export size.
- Fix region worker cache locking to avoid export deadlocks.
- Retain the Java 21 desktop launcher fix and bundled runtime.

Portable Ubuntu x86_64 archives include Java 21. Extract and run ./launch.sh.

# TerraForge v0.1.1 — Updated terrain and rivers

Updated from the supplied TerraForge-Ubuntu-fixed.zip.

- Hydraulic erosion and procedural ground cover.
- Mountain surface zones and more natural spurs.
- Connected watershed routing, broader river curves, branch-spacing and crossing checks, and more visible downstream forks.
- Updated beach, snow, material and tree generation behavior.
- Launcher uses bundled Java 21, or explicitly selects an installed Java 21, rather than automatically using Java 25.

Portable Ubuntu x86_64 ZIP and tar.gz include Java 21. Extract and run `./launch.sh` (or `bash launch.sh`). Source ZIP requires a Java 21 JDK to build. A graphical desktop and OpenGL drivers are required for GUI/GPU views.

Validation: Java 21 source/test compilation, 10 targeted terrain/river regression checks, headless four-chunk export, and bundled-runtime desktop startup check on Ubuntu. Existing 16K maps were not regenerated as part of this release.
