# TerraForge v0.1.0

Initial numbered portable Ubuntu desktop release.

- Organic mountain ranges with wider foothills and longer branching spurs.
- Larger terrain-aware lake shorelines and material transitions.
- Terrain-following mountain rivers with broad curves, spaced sources and short downstream forks.
- Upper-mountain snowmelt streams preserved through region and full-map cleanup.
- Fixed false water-elevation drops on narrow diagonal channels that could carve deep cuts into mountains.
- GPU block and relief previews, 4K image capture and Minecraft 1.20.4 world export.
- Bundled Java 21 runtime in the Ubuntu x86_64 portable archive.

Validation: Java 21 compilation, headless export smoke test, narrow-channel water-profile regression, strict-slope routing, broad curves, mountain preservation, upper-stream generation and cleanup checks. Desktop/GPU views were exercised locally on Ubuntu with an RTX 4090. No clean-machine or other-distribution compatibility claim is made.

The main island recipe is included. Generated 16K world files are separate from the app release. Extended-height worlds need compatible dimension support.
