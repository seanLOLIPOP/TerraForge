# TerraForge v0.1.15

Standalone terrain editor and Minecraft Java Edition 1.20.4 world exporter for Linux, macOS, and Windows.

v0.1.15 speeds up Minecraft chunk encoding with reusable primitive block palettes
and faster block-state packing. In local tests, warmed full-world export improved
34–38%; results depend on the graph and hardware. See
[encoding validation](docs/BLOCK-ENCODING-VALIDATION.md) for measurements and checks.

## Windows portable edition

[Published Windows releases](https://github.com/seanLOLIPOP/TerraForge/releases)

Extract `TerraForge-v0.1.15-Windows-x64.zip` completely and open `TerraForge.exe`.
The Windows x64 package includes Java 21 and Windows LWJGL natives. No Java install
or administrator rights are needed. GPU views require OpenGL 3.3 graphics drivers.
Minecraft saves default to `%APPDATA%\.minecraft\saves`. Run
`TerraForge-Console.cmd` if you need diagnostic output.

Build on Windows with a Java 21 JDK:

```powershell
.\build.ps1 -JdkHome 'C:\path\to\jdk-21'
.\packaging\windows\package.ps1 -JdkHome 'C:\path\to\jdk-21'
```

The portable ZIP and SHA-256 checksum are written to `dist/`. See
`packaging/windows/README-Windows.txt` for requirements and storage behavior.

Whole Map Blocks now streams and retains 64-block detail tiles as you fly. Its
default textured target spans 1,024 blocks across (previously 768); `+` and `-`
adjust it up to 1,536. Nearby tiles load first, and backtracking reuses cached
meshes. A 288 MiB mesh-buffer budget keeps distant detail from growing without
bound; complex terrain may show a smaller textured area. The complete map remains
visible as simplified terrain while detail arrives.

Nodes → Build into map now runs independent terrain, material, climate, snow,
and thermal-erosion calculations on multiple CPU threads. It reserves one logical
CPU for other work and keeps small previews sequential to avoid thread overhead.
Region builds are no longer capped at four workers: concurrency is bounded by CPU
count, Java heap, and currently free system memory. Ordered river routing and
hydraulic erosion still include sequential stages. Java warm-up can make the
first build slower than later builds. Seeds and generated channel values are
preserved. Advanced launch flags `-Dterraforge.cpuThreads=N` and
`-Dterraforge.regionThreads=N` can lower concurrency; region overrides still obey
CPU and memory limits.

For disk-backed builds, Nodes now has a **Region batch** control. Keep **Auto**
for CPU/RAM-based concurrency, or choose 2, 4, 8, or 16 regions as an upper bound.
Workers refill available slots as regions finish. Each region encodes 64 chunks
per batch on a shared encoder pool (8 workers by default), then writes the
compressed data sequentially through a buffer. Chunk order and region contents
are preserved. Partial files are removed if a batch fails or is cancelled.

## Download and run

For the existing Ubuntu package, download `TerraForge-v0.1.9-Ubuntu-x86_64.tar.gz` from release v0.1.9, extract it, and run `./launch.sh` inside the extracted folder. That package includes Java 21 and predates the v0.1.10/v0.1.11 updates.

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

## Earlier Mac and Linux downloads (v0.1.9)

The previous Mac and Linux packages remain available in [release v0.1.9](https://github.com/seanLOLIPOP/TerraForge/releases/tag/v0.1.9):

- **TerraForge-v0.1.9-Mac-AppleSilicon.zip**: Macs with M1, M2, M3 or M4
- **TerraForge-v0.1.9-Mac-Intel.zip**: Intel Macs
- **TerraForge-v0.1.9-Ubuntu-x86_64.tar.gz**: Ubuntu / Linux x64, Java included (extract and run `./launch.sh`)

The Mac apps are unsigned. After moving TerraForge.app to Applications, run `xattr -cr /Applications/TerraForge.app` once in Terminal. These older packages contain v0.1.9; the current source builds v0.1.11. Updated Mac and Linux packages have not been produced for v0.1.11. See [macOS packaging notes](packaging/macos/README.md).

Validation reports: [CPU generation](docs/CPU-GENERATION-VALIDATION.md) and
[whole-map streaming](docs/WHOLE-MAP-PERFORMANCE.md).

Nodes → Build into map builds 8192 and 16384 maps region by region on disk by default ("Build region by region", next to Map size). Untick it to build in memory on computers with enough RAM. Maps too large for memory always use regions. The included `examples/City-Map-01-Natural-Mountains-Y1024.tgraph` is a 16384 island with a Y1024 summit.

### GPU generation

In Nodes, use **Generation: Auto** for the recommended balance, **GPU** to include
all supported GPU passes, or **CPU** to force CPU generation. GPU info reports the
selected backend or fallback reason. Compatible OpenCL 1.2 drivers with double
precision are required for acceleration; the app still works using the CPU when
they are unavailable. NVIDIA and AMD hardware were tested; Intel is supported by
the same API but has not been hardware-tested. See [GPU validation](docs/GPU-GENERATION-VALIDATION.md)
for covered stages, measured results, limits, and advanced options.

GPU graph builds now keep compatible intermediate maps on the device and fuse
closed groups of scalar nodes. Only final channels are downloaded for builds;
node previews retain intermediate fields. See [GPU pipeline validation](docs/GPU-PIPELINE-VALIDATION.md)
for measured improvements and limitations.
