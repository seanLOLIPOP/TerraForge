# TerraForge v0.1.0

Standalone terrain editor and Minecraft Java Edition 1.20.4 world exporter for Ubuntu.

## Download and run

Download `TerraForge-v0.1.0-Ubuntu-x86_64.tar.gz` from this repository's Releases page, extract it, and run `./launch.sh` inside the extracted folder. The portable download includes Java 21; Java installation is not required.

Built and tested on Ubuntu 24.04 x86_64. GPU views need working OpenGL drivers. The runtime still uses standard Linux desktop, graphics, audio and font libraries. Other Linux distributions are not verified. The source-only checkout requires a Java 21 JDK.

## Features

- Node-based terrain generation, sculpting, masks, material and biome layers.
- Maps up to 16,384 × 16,384 blocks, with organic mountain ranges, spurs, beaches and terrain-shaped lakes.
- Terrain-following river networks, narrow upper-mountain snowmelt streams, and branching channels.
- Gradual grass, stone and snow transitions; selectable imported tree variants.
- 2D, GPU relief, Minecraft-style and whole-map block previews, plus 4096 × 4096 image capture.
- Editable `.terra` projects, reusable `.tgraph` recipes and Minecraft Java 1.20.4 world export.
- Extended-height exports for peaks above vanilla's build limit, up to terrain Y=1024. These worlds include custom dimension data and require compatible Minecraft/server setup.

Large maps require substantial memory and generation time. The 16K workflow was tested with 32 GB RAM and an RTX 4090; this does not guarantee that every graph fits in that memory. GPU memory does not replace the Java heap used for generation.

## Build from source

Install a Java 21 JDK, then run:

```bash
bash ./build.sh
bash ./launch.sh
```

Bundled LWJGL JARs and their notices are under `lib/`. `build.sh` copies the converted tree catalog into the application JAR. Run `java -jar TerraForge.jar --version` to check the application version.

For the included large-island recipe, open `examples/Alpine-Streams-16384-Y1024.tgraph` in Nodes, select 16384 and build. The graph is included; multi-gigabyte generated worlds are not part of the app download.

## Release status and credits

This is an early desktop release. Back up projects before editing or exporting. See `RELEASE_NOTES.md` for release details and `THIRD_PARTY_NOTICES.md` for bundled dependencies and tree-library attribution. No new license to third-party content is granted by this repository.
