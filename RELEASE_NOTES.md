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
