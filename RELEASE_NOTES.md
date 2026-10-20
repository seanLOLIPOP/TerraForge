# TerraForge v0.1.1 — Updated terrain and rivers

Updated from the supplied TerraForge-Ubuntu-fixed.zip.

- Hydraulic erosion and procedural ground cover.
- Mountain surface zones and more natural spurs.
- Connected watershed routing, broader river curves, branch-spacing and crossing checks, and more visible downstream forks.
- Updated beach, snow, material and tree generation behavior.
- Launcher uses bundled Java 21, or explicitly selects an installed Java 21, rather than automatically using Java 25.

Portable Ubuntu x86_64 ZIP and tar.gz include Java 21. Extract and run `./launch.sh` (or `bash launch.sh`). Source ZIP requires a Java 21 JDK to build. A graphical desktop and OpenGL drivers are required for GUI/GPU views.

Validation: Java 21 source/test compilation, 10 targeted terrain/river regression checks, headless four-chunk export, and bundled-runtime desktop startup check on Ubuntu. Existing 16K maps were not regenerated as part of this release.
