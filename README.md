# TerraForge v0.1.8

Standalone terrain editor and Minecraft Java Edition 1.20.4 world exporter for Ubuntu.

## Download and run

Download `TerraForge-v0.1.1-Ubuntu-x86_64.tar.gz` from this repository's Releases page, extract it, and run `./launch.sh` inside the extracted folder. The portable download includes Java 21; Java installation is not required.

Built and tested on Ubuntu 24.04 x86_64. GPU views need working OpenGL drivers. The runtime still uses standard Linux desktop, graphics, audio and font libraries. Other Linux distributions are not verified. The source-only checkout requires a Java 21 JDK.

## Updated in v0.1.1

This release incorporates the supplied updated app: hydraulic erosion, ground cover, mountain material zones, natural spurs, connected watershed routing and visible downstream river branches. The launcher explicitly selects Java 21 to avoid the Java 25/headless startup problem.

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

## 3D view controls

- **GPU 3D:** left drag orbits, right drag pans, wheel zooms.
- **Minecraft view and Whole map blocks:** click to capture the mouse, then WASD to fly, Space/Shift for up/down, Ctrl for fast. The mouse wheel sets fly speed. Esc releases the mouse.
- **All views:** `[` and `]` lower or raise mouse sensitivity in 10% steps. Sensitivity and fly speed are saved in `controls.properties` beside the app.

## Minecraft assets and compatibility

Minecraft game files and textures are not distributed. The Minecraft-style preview can read textures from a user's locally installed Minecraft 1.20.4 client. This project is not affiliated with Mojang or Microsoft.

## Release status and credits

This is an early desktop release. Back up projects before editing or exporting. See `RELEASE_NOTES.md` for release details and `THIRD_PARTY_NOTICES.md` for bundled dependencies and tree-library attribution. No new license to third-party content is granted by this repository.

## Downloads (v0.1.8)

Get the latest apps from [release v0.1.8](https://github.com/seanLOLIPOP/TerraForge/releases/tag/v0.1.8):

- **TerraForge-v0.1.8-Mac-AppleSilicon.zip**: Macs with M1, M2, M3 or M4
- **TerraForge-v0.1.8-Mac-Intel.zip**: Intel Macs
- **TerraForge-v0.1.8-Ubuntu-x86_64.tar.gz**: Ubuntu / Linux x64, Java included (extract and run `./launch.sh`)

The Mac apps are unsigned. After moving TerraForge.app to Applications, run `xattr -cr /Applications/TerraForge.app` once in Terminal. The source in this repository builds the same `TerraForge.jar` that ships in all three downloads. See [macOS packaging notes](packaging/macos/README.md).

Nodes → Build into map builds 8192 and 16384 maps region by region on disk by default ("Build region by region", next to Map size). Untick it to build in memory on computers with enough RAM. Maps too large for memory always use regions. The included `examples/City-Map-01-Natural-Mountains-Y1024.tgraph` is a 16384 island with a Y1024 summit.
