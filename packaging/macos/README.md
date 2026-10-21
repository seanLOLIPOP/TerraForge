# Supplied macOS distributions

The v0.1.3 release also provides the supplied TerraForge-Mac-Intel.zip and TerraForge-Mac-AppleSilicon.zip unchanged. They include Java 21 and architecture-specific LWJGL native libraries. Choose the archive matching your Mac processor.

These archives contain compiled applications, not Java source. Their internal bundle version is 0.1.0; their Java binaries have not been established as identical to the v0.1.3 source. They are unsigned. Startup and GPU rendering have not been tested on macOS in this Ubuntu environment.

The packaging directory preserves their supplied launcher and Info.plist files. The existing repository Java source and Linux downloads remain v0.1.3.

## Mac revision 2

Updated supplied compiled applications are available as TerraForge-Mac-Intel-r2.zip and TerraForge-Mac-AppleSilicon-r2.zip on release v0.1.3. These supersede the original Mac downloads. Changes are present in the export dialog, graph editor, TerraForge core, GPU terrain viewer and whole-map block view binaries. Java source was not supplied, so source correspondence and specific behavior changes are unverified. Bundle metadata still reports 0.1.0. ZIP integrity passed for both archives. macOS startup and GPU rendering were not tested on this Ubuntu host.
