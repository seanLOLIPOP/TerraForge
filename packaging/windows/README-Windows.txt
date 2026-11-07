TerraForge 0.1.14 - Windows x64 portable edition

Extract the entire ZIP to a writable folder, then open TerraForge.exe.
Java 21 is included. No Java installation or administrator access is required.
Keep the app, runtime, and examples folders beside TerraForge.exe.
Do not run directly inside the ZIP. To move the app, move the whole folder.

Requires Windows 10/11 x64 and a graphics driver supporting OpenGL 3.3 for GPU
previews. Start with a small map; large 8K/16K maps need substantial RAM and disk.
The bundled launcher allows Java to use up to 70 percent of system RAM.
Region building is available for large maps.

Build into map now uses multiple CPU threads for independent terrain, erosion,
climate, material, and snow calculations. On a 32-thread CPU it can use up to
31 workers for these stages. Region builds choose their worker count from CPU,
heap, and available system RAM. Small previews and ordered river/hydraulic
operations can still use fewer cores. The first build includes Java warm-up;
later builds generally run faster. No CPU setting is required.

For large disk-backed builds, choose Nodes > Region batch > Auto (recommended).
You can also request 2, 4, 8, or 16 regions at once; CPU/RAM limits still apply.
Each region encodes 64-chunk batches using a shared pool, then writes buffered
data in order. Available region slots refill as soon as another region finishes.
The batch selector is enabled only for region builds. The status line shows the
effective region batch limit.

Whole Map Blocks retains detailed tiles while you move. The default textured
target spans 1,024 blocks across; + and - adjust it up to 1,536 blocks across.
Nearby tiles load first and the simplified overview fills unloaded areas.
Detailed mesh buffers are limited to 288 MiB, so complex terrain can reduce
the textured area. R refreshes the detail cache once its current job finishes.

Minecraft Java 1.20.4 saves default to %APPDATA%\.minecraft\saves.
Use Browse in the export dialog for another launcher or game instance.
Minecraft textures are loaded from your own installed 1.20.4 client, if present;
Minecraft game files are not bundled.

Projects, autosave recovery, controls, and region-build data remain portable in
this folder. If it is read-only, app data falls back to %USERPROFILE%\.terraforge.
Use a normal writable folder rather than Program Files.

For diagnostic output, run TerraForge-Console.cmd. It also accepts --version
or --smoke-test followed by a new destination folder.
This portable build is unsigned.
See FEATURE_GUIDE.md for node controls and THIRD_PARTY_NOTICES.md for credits.

BUILD FROM SOURCE
Use a Windows x64 Java 21 JDK (Eclipse Temurin recommended). In PowerShell:
  .\build.ps1 -JdkHome 'C:\path\to\jdk-21'
  .\packaging\windows\package.ps1 -JdkHome 'C:\path\to\jdk-21'
The package is written to dist and includes a SHA-256 checksum.

GPU generation: Nodes > Generation > Auto (recommended), GPU, or CPU.
GPU info shows the selected backend. GPU acceleration requires an OpenCL 1.2
driver with double precision; unsupported devices automatically use the CPU.
No CUDA toolkit is required. First use includes shader compilation.

Version 0.1.14 keeps connected terrain stages on the GPU and combines compatible
node passes during builds. Use Generation: Auto for the recommended balance.
CPU-only stages and saving still use the CPU. First use compiles GPU programs.
