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
