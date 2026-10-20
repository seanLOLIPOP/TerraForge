# TerraForge — World Studio prototype, update 6

Standalone Java terrain generator and painter for Ubuntu, targeting Minecraft Java Edition 1.20.4.

## Start

Java 21 or newer is required. It is already installed on the development machine. No Python, external libraries or network connection are needed to run the app.

```sh
./launch.sh
```

Or run `java -Xmx2g -jar TerraForge.jar` from this folder.

## Imported tree library and Tree scatter node — update 6

Your supplied `treerepository10948005.schematic` is included as a selectable **Exsilits tree repository** library in the new **Tree scatter** node.

### Keep the same tree variety

1. Add **Tree scatter** from the Nodes list.
2. Connect your terrain/environment branch to its Terrain input, then connect Tree scatter to World output.
3. Select a **Tree group** and **Exact tree variant**. The small preview shows that selected source tree.
4. Place this node after terrain/water processing. Set density, spacing, elevation range and slope. Optionally connect a 0–1 mask to restrict placement.
5. Build into map and export. The chosen tree's blocks are placed into the exported chunks.

**Each node repeats one explicitly selected tree variant. It never randomly chooses different groups or variants.** The placement seed changes positions and optional quarter-turn rotations only. To use another variety, select it explicitly or use another Tree scatter node with a separate mask.

Try **Repository grove** from the graph template menu, or open `examples/Repository-grove.terra`. The `.tgraph` recipe is also included. `tree-groups.png` shows one representative from each group.

### How the supplied schematic was imported

- **370 original display-cell variants**, organized into **29 trunk/canopy material groups**; **343,774** tree blocks retained.
- The board has no usable species labels. Group names therefore describe actual block materials, such as Oak / oak leaves or Acacia / pink wool; they are not verified botanical species or assumed author categories.
- Variant names retain their original source cell numbers, coordinates and dimensions. The full inventory is in `assets/tree-library.json`.
- The display platform at Y10, commands, signs, controllers and tile entities are excluded from the imported library. The original schematic is retained separately in `assets/` as a reference; the app uses the converted tree pack, never its commands.
- Most overlapping silhouettes were separated by 3D connectivity. The physically touching spruce examples **Cell 087 and Cell 111** required a nearest-root boundary split; their original canopy boundary is approximate and those two variants are marked in the inventory.
- Legacy blocks are converted to modern Minecraft names and properties. Decorative wool, terracotta, glass and other source-tree materials are retained. Leaves are persistent to prevent imported canopies decaying. Quarter turns rotate directional properties and horizontal log axes.

### Placement and preview behavior

Spacing expands to fit the selected tree's footprint. Trees avoid water-covered roots, ice ground, steep/uneven footing, map edges, overlapping tree bounds and the Minecraft build ceiling. These filters can produce fewer trees than requested, particularly for large models. Density 0 disables new placements. A node can add up to 4,096 trees, with a graph-wide maximum of 8,192 placements.

The Surface view includes canopy footprints; the 3D relief view uses canopy heights rather than a full voxel tree renderer. Minecraft export contains the individual tree voxels, including portions crossing chunk boundaries, and updates canopy heightmaps. Spawn selection avoids tree bounds when possible. Export skips voxels buried by subsequent terrain edits; rebuild the scatter graph after major sculpting.

Tree placements and graph selections save in **project version 5**. Projects from versions 1–4 remain readable. Tree graphs and placed forests store the converted library fingerprint so a changed asset pack cannot silently substitute a different variety. The library is bundled in the Java app; Python is not needed to run it. The optional offline converter in `tools/import_tree_repository.py` uses nbtlib, NumPy and SciPy.

## Random lakes

New Lake nodes use **Random (seeded)** placement by default. The world seed, node ID and Placement seed offset determine the location; **New random location** changes the offset. Random placement leaves room for the lake's radius and banks inside the map. Water level, depth and overflow still use your selected values. Choose **Manual X / Z** to place it by coordinates. Existing saved Lake nodes keep their previous manual placement.

## Node-editor map size

Choose **Map size · blocks** in the node editor to build 128×128, 256×256, 512×512 or 1024×1024 maps. This control stays synchronized with the main World tab. Changing size leaves the current terrain intact until **Build into map**; the live preview uses the same selected resolution. The built size is stored with the project.

## Water, snow and environment nodes — update 5

The node library now contains **33 node types**. Environment processors carry terrain plus independent water, snow, ice, material, biome, temperature and rainfall channels.

### Try a complete example

- Open `examples/Frozen-highlands.terra` from the main app to inspect an already-built, editable world.
- Or click **Nodes**, choose **Coastal watershed**, **Frozen highlands** or **Wetland basin**, click **Load template**, then **Build into map**.
- Use the layer selector in either window to inspect **Surface, Water depth, Snow, Ice, Temperature, Rainfall and Biomes**.

These templates are also included as reusable `.tgraph` files in `examples/`.

### Environment processors

| Node | What it generates |
|---|---|
| Ocean / seabed | Continental shelves, deeper basins, procedural trenches and underwater hills. World sea level sets the ocean surface. |
| Coast / beach | Shores shaped within a configurable distance of water, with sand, rocky patches and optional cliff-like rises. |
| River network | Seeded headwaters routed through a priority-flood drainage network, carved beds, tributary joins and local water levels above the ocean. The source count is a maximum; limited high ground can produce fewer sources. |
| Lake / reservoir | A positioned basin, local water elevation and raised banks; optional overflow carves a drainage outlet. Existing low terrain can make a lake deeper than its requested carving depth. |
| Waterfall / rapids | Stepped water elevations and rough stone beds on an existing river channel. Place after River network. |
| Climate | Normalized temperature and rainfall maps with regional noise, altitude cooling and a simple terrain-shadow term. Terrain input is optional. |
| Snow coverage | Elevation transitions, climate, slope limits and patchy snow edges. Exports 1–8 snow layers above the terrain. |
| Glacier | Procedural packed-ice fill over cold, elevated, gentle terrain, with adjustable thickness. It changes terrain elevation and retains an ice-depth layer. |
| Freeze water | Ice up to five blocks thick on water below a temperature threshold. |
| Wetlands / marsh | Shallow, rainy shoreline areas with irregular pools, mud and swamp biome hints; deep water is preserved. |
| Sediment / riverbank | Sand, gravel and mud near water, restricted by slope. Applies to both riverbanks and other shorelines. |
| Biome distribution | Uses water, river, freezing, snow, mud, temperature and rainfall to assign plains, ocean, river, snowy plains, swamp, desert, forest, frozen ocean or frozen river. |

Climate inputs are optional on Snow, Glacier, Freeze, Wetlands and Biome distribution. Their fallback is deterministic; a connected Climate node gives you explicit temperature/rainfall control. Temperature/rainfall values are generation maps, not Celsius measurements or custom Minecraft weather settings. A forest biome does not automatically place trees; connect Tree scatter to add the selected source-tree variety.

### Channels and World output

Most environment nodes have a **green terrain output that carries all accumulated environment layers**. A simple chain into World output's Terrain port therefore builds the whole environment without extra wires.

For separate branches, use **Water level map, Snow mask, Ice mask, River mask, Material map, Biome map, Temperature mask and Rainfall mask** extractor nodes. World output now has six typed inputs:

| Port | Accepted data | If left disconnected |
|---|---|---|
| Terrain | Terrain/environment bundle; required | Build reports a missing input. |
| Water | Blue water-level map | Inherits the terrain branch's water levels, otherwise global sea level. |
| Snow | Purple 0–1 mask | Inherits the terrain branch's coverage, otherwise legacy altitude rules. |
| Ice | Purple 0–1 mask | Inherits ice thickness; an explicit mask creates up to one block of water-surface ice. |
| Material | Yellow material map | Inherits procedural materials; painted surfaces still override them. |
| Biome | Pink biome map | Inherits assigned biomes, otherwise automatic terrain rules. |

Orange ports carry Climate data. Water and terrain use block elevations; masks use 0–1. Explicit World output connections override the corresponding inherited layer. Combine keeps environment channels from Terrain A while combining heights; use extractor/output connections to take layers from another branch.

Local water levels allow lakes and rivers above sea level. Snow is skipped underwater, ice stays within the water column, and glacier ice fills its configured depth. Exported biomes are sampled on Minecraft's 4×4 horizontal grid and stored in each vertical biome section. Water, snow and ice are included in world heightmaps and spawn elevation.

### Scope and compatibility

These are working procedural generators, not time-based fluid, sediment or glacier physics. Waterfalls are carved stepped channels with water faces; the app does not simulate moving water or foam. Glaciers use elevation/climate/slope rules, not an ice-flow solver. Climate and shoreline rules are approximations. Preview and Build use the same selected map resolution. Larger maps take more time and memory to preview. Hydraulic erosion, general vegetation generation, buildings, custom biome definitions and Gaea file import remain outside this release.

Changing a node affects the preview and saved graph draft. **Build replaces the current terrain and paint**, so save manual edits first. Painting after Build changes the baked map without recomputing its environmental masks; rebuild when you need those masks regenerated. Snow and ice ports represent independent overrides and do not automatically recompute a separately connected biome map.

New projects use **format version 5**, storing environmental channels, tree placements and the graph. Project versions 1–4 and pre-environment `.tgraph` recipes still load. New graph files use a self-describing schema with named parameters and port counts; older app releases cannot read the new files.

## Node editor

Click **Nodes** in the main toolbar to open the procedural terrain workspace. The World/Terrain controls and painting tools remain available in the main window.

### Build a terrain graph

1. Start with the connected **Island mountains** graph, or load **Terraced ranges** / **Masked mountains** from the template menu.
2. Choose a node type and click **Add node**. Drag a node header to move it.
3. Drag from a node's output dot on the right to an input dot on the left. Alternatively, click output and then input. Green ports carry terrain/environment data; purple ports carry masks. Water, material, biome and climate ports have their own colors.
4. Select a node to edit its parameters. The main graph preview normally shows **World output**; enable **Preview selected node** to inspect a branch or mask.
5. Feed two terrain branches into **Combine**, optionally connecting a height or slope mask to its third input. Choose Mix, Add, Maximum, Minimum, Subtract or Multiply.
6. Connect the final processing node to **World output**. Click **Build into map** to evaluate at the World tab's size, seed and sea level. The Terrain tab's automatic surface rules are applied to the built map. Landscape shape, relief and other procedural options are controlled by graph nodes during a graph build.
7. Return to the main window to inspect the built terrain in 2D/3D, paint it, save, or export a Minecraft world.

Graph edits do **not** change existing terrain until Build. Building replaces the terrain and painted materials and clears terrain undo history, so save important manual edits first. Terrain brushes do not become graph nodes. The graph and baked terrain are saved together, allowing projects to retain manual edits alongside a procedural recipe.

### Available nodes

| Node | Function |
|---|---|
| Noise | Fractal terrain source with frequency, detail, roughness, amplitude and seed offset. |
| Ridges | Ridged mountain source with frequency, sharpness, amplitude and seed offset. |
| Constant | A flat terrain at a chosen elevation. |
| Island | Elliptical coastal falloff with extent, aspect, rotation and falloff depth. |
| Thermal erosion | Moves material downhill over the selected number of iterations. |
| Terraces | Blends the input toward stepped elevation bands. |
| Height remap | Multiplies elevation relative to sea level and adds an offset. |
| Clamp | Limits terrain to an elevation interval. |
| Combine | Mixes two terrain branches using six modes and an optional mask. |
| Height mask | Creates a smooth black-to-white mask from elevation thresholds. |
| Slope mask | Creates a mask from neighboring elevation differences. |
| World output | Defines the branch used for full-resolution builds. |

Mask values range from 0 to 1. On Combine, black keeps terrain A, while white allows the selected operation at its Strength. Add and Subtract use elevations relative to sea level; Multiply scales the product of those relative elevations by 1/100. Height outputs are limited to Y −60 through 300.

### Graph controls and storage

- Right/middle drag to pan; mouse wheel to zoom; **F** or **Fit** to frame all nodes.
- Click an input dot without an active wire, or its inspector connection button, to disconnect it. **Esc** cancels a pending wire.
- **Delete** removes the selected node. Graph **Undo/Redo** and Ctrl+Z / Ctrl+Y have a separate history from terrain painting.
- **Save graph / Open graph** store reusable `.tgraph` recipes. Example files are included in `examples/`. Standalone graph files use the current world's size, seed and sea level when built.
- Edited graph drafts are included in project saves and automatic recovery. Incomplete drafts can be saved; previews/builds explain missing inputs. Cycles and incompatible port types are rejected.
- Graph previews run in the background at the selected map size and cancel obsolete evaluations. Preview and Build evaluate the same resolution; terrain graph previews and the main editor can show surface or environmental layers. Mask and climate nodes have their own previews.
- Up to 64 nodes are supported, with a heightfield and environmental output layers. Gaea project files, hydraulic erosion outputs, arbitrary RGB texture graphs and graph groups are not supported.

New projects use format version 5; version 1–4 projects still open. Older app releases cannot read new version 5 projects. The graph UI follows the general source → process → output workflow described in [Gaea's graph documentation](https://docs.gaea.app/ui/interface/graph/index.html); TerraForge uses its own implementation and file format.

## What's new in update 3

- **World presets:** Balanced island, Alpine ranges, Archipelago, Mesa plateaus, and Rolling plains.
- **Terrain tab:** advanced generation settings for mountains, terrain detail, island outlines, ocean depth, terraces, plateaus, erosion and natural surface materials.
- **Automatic surfaces:** consistent snow, sand and rocky-slope rules in both preview and Minecraft export. Painted surfaces override those rules.
- **Saved generation recipes:** project files retain seed, landscape, relief and all advanced settings. Opening a project restores its controls without regenerating your edits.
- Previous editor tools remain available: continuous sculpting, undo/redo, zoom/pan, orbitable relief view, project recovery and erosion.

### Advanced terrain controls

| Control | Effect |
|---|---|
| Feature frequency | Higher values produce more, smaller terrain features across the map. |
| Detail octaves | Adds fine detail layers; more layers increase generation time. |
| Roughness | Sets the strength of small details relative to broad terrain forms. |
| Ridge strength | Adds ridged noise for connected mountain ranges. |
| Domain warp | Distorts terrain and coastlines to reduce regular shapes. |
| Island extent | Changes how far island land extends from the map center. |
| Island aspect | Stretches or compresses the island outline; 100 is round. |
| Island rotation | Rotates the outline; most apparent on elongated islands. |
| Maximum ocean depth | Compresses the generated seabed toward this depth below sea level, before erosion. |
| Plateau strength | Flattens upper terrain toward a common elevation. |
| Terrace step | Sets terrace spacing in vertical blocks; 0 disables terraces. |
| Generation erosion | Automatically applies thermal erosion after generating terrain; 0 disables it. |
| Beach height | Automatically uses sand below sea level plus this height. This is a vertical band, not horizontal beach width. |
| Snow starts at Y | Natural surfaces at or above this height become snow. |
| Automatic rocky slopes | Uses stone when neighboring heights differ by at least the selected slope threshold. |

Island extent, aspect and rotation affect **Island** and **Archipelago** layouts. The other settings apply to all layouts. Generation settings affect the **next generated map**; changing a slider or preset does not alter the current world. Press **Generate** in the header or sidebar to create the new map. Changes made to controls after generation are pending settings; saving retains the recipe that generated the current map.

The generator has changed in this update, so a seed from an older release can produce a different newly generated landscape. Existing saved heights and painted materials are preserved. Old projects retain their previous automatic material rules. This update also preserves advanced settings in the current version 5 project format.

## Workflow

1. In **World**, choose a preset, width, seed, sea level and relief. Optionally adjust the **Terrain** tab, then generate.
2. Optionally apply thermal erosion. More iterations soften steep slopes further.
3. In **Paint**, sculpt or paint the terrain. Flatten samples the height at the beginning of each stroke.
4. Switch to **3D relief** to inspect, then return to 2D to paint.
5. Save an editable `.terra` project. The previous prototype's projects remain compatible.
6. Export to a parent directory and enter a NEW folder name. Existing world folders are never overwritten.
7. Move the exported folder into your Minecraft launcher's `saves` directory and open with **Java 1.20.4**. Standard Ubuntu installations often use `~/.minecraft/saves`; launcher paths vary.

Sea level controls apply to newly generated worlds; opening a project restores its stored sea level. Changing generation controls does not modify the current terrain until you generate again.

### Controls

| Action | Control |
|---|---|
| Paint | Left drag in 2D |
| Zoom | Mouse wheel, or + / − toolbar buttons |
| Pan | Right or middle drag |
| Orbit relief | Left drag in 3D |
| Fit map | F or Fit button |
| Brush radius | [ and ] |
| Undo / redo | Ctrl+Z / Ctrl+Y |
| Save / open | Ctrl+S / Ctrl+O |

Typing fields keep their own editing shortcuts. Toolbar buttons are always available when no background operation is running.

### Recovery

While changes are pending, the app writes `recovery.terra` in its working directory every 60 seconds, except during a stroke or another operation. The launcher uses the app folder as the working directory, so keep it writable. On restart, the app offers to restore that file. A restored project is unsaved: choose Save to give it a permanent name. Saving, explicitly discarding a project, or opening/generating its replacement clears the old recovery file. Recovery may lose changes from the most recent interval. Run one instance per app folder.

## Export details and limits

Worlds use creative mode with commands enabled. Spawn is selected near the center on land when possible. Custom terrain occupies x/z 0 through width−1. Outside that square Minecraft generates standard terrain; boundary seams are possible.

This remains an early prototype. There are no generated ores, caves or buildings, no dedicated biome brush, and no hydraulic erosion or time-based water-flow simulation. The painting River brush still cuts sea-level channels; the River network node builds routed channels with local water levels. Biome output now supports multiple biomes per chunk on a 4×4 horizontal grid. The orbit view is a surface relief mesh, not a full block renderer.

**Minecraft gameplay compatibility remains unverified.** File-structure validation passed, but a world still needs to be opened and checked in Minecraft 1.20.4 before relying on exports.

## Validation

- Compiled for Java 21 with Eclipse ECJ.
- Launched and visually inspected both the 2D studio and 3D relief view.
- Java regression checks cover deterministic generation, erosion mass conservation and slope reduction, brush locality, river depth, height limits, material reset, project round trips, corrupt-file rejection, undo/redo, cursor-anchored zoom, continuous interpolated strokes, and preview rendering/cache reuse.
- Additional tests verify advanced controls affect terrain, all presets are deterministic, island extent affects land coverage, natural surface rules respect paint overrides, generation recipes round-trip and legacy projects load.
- Node tests cover deterministic graph evaluation, masks, combine modes, cycles and wire rollback, port typing, preview cancellation, incomplete drafts, graph/project serialization, mouse wiring, node undo/redo and full-resolution map builds. An independent decoder also verifies every heightmap column in a graph-built test export.
- Independent checks decode exported top blocks to verify beaches, snow, rocky slopes and painted overrides.
- Environment tests cover all twelve processors, normalized/bounded outputs, drainage termination, independent output ports, graph upgrades, persistent environment layers and desktop integration.
- An independent NBT decoder verifies local water at Y110, two-block ice, four-layer snow, snowy grass state, twelve-block glacier ice, mud/gravel, every heightmap column and every biome quart in an export fixture.
- Tree tests verify source catalog/group counts, fixed-variant scattering, deterministic positions, group isolation, density masks, underwater exclusion, quarter-turn axes, cross-chunk voxel preservation, canopy heightmaps, library fingerprints, project persistence and group/variant UI controls.
- Independent NBT validation checks level metadata, region offsets, chunk compression, all 24 sections, nested NBT lists and palette index validity in a four-chunk export.

## Build and test

With a Java 21 JDK:

```sh
./build.sh
mkdir -p test-classes
javac --release 21 -cp classes -d test-classes tests/*.java
java -cp classes:test-classes TerraForgeTest --no-ui
java -cp classes:test-classes AdvancedTerrainTest --no-ui
java -cp classes:test-classes TerrainGraphTest --no-ui
java -cp classes:test-classes EnvironmentTest
java -cp classes:test-classes TreeLibraryTest
java -cp classes:test-classes RandomLakeTest
```

Omit `--no-ui` from the original test commands to also run desktop interaction checks. Run `java -cp classes:test-classes EnvironmentDesktopTest` for the environment build/save/open/view tests. Use a writable working directory without an existing recovery file for the desktop tests. Test output is printed to the terminal.

Exporter smoke check (destination must not already exist):

```sh
java -jar TerraForge.jar --smoke-test /path/to/a/new/test-world
```

### Optional independent environment export check

The Java app has no Python dependency. To run the additional independent format test, use a separate Python environment with `nbtlib` installed:

```sh
java -cp classes:test-classes EnvironmentExportFixture /path/to/new/fixture-world
python3 tests/check_environment_export.py /path/to/new/fixture-world
```

The destination must not exist. The fixture is a deliberately artificial 32×32 test world.

### Optional independent tree export check

```sh
java -cp classes:test-classes TreeLibraryTest /path/to/new/tree-fixture
python3 tests/check_tree_export.py /path/to/new/tree-fixture
```

With Python nbtlib installed, this independently checks every imported block of a rotated source tree across four chunks, persistent leaves, canopy heightmaps, and controller exclusion. `TreeDesktopTest` additionally exercises group/variant selection, builds and project restoration on a desktop.

River randomization: select a River network node and click Randomize rivers. This changes its River seed offset, refreshes the preview, and supports Undo/Redo. The saved seed reproduces the same river network; upstream terrain and the world seed stay unchanged. Build into map applies the result.

Mountain lake banks: Lake nodes reshape high surrounding terrain using distance from the shoreline and blend the banks into the mountain. Surrounding slope sets rise per horizontal block (default 0.5); lower values widen the transition. Existing lake graphs receive this correction when rebuilt.

Natural flat coastal patches: select Coast / beach and increase Extra coastal flattening (0 disables it). Flattened coast fraction controls how much of the coast is affected, Flat stretch length controls patch scale, and Flat coastal reach controls inland extent. Randomize flat patches enables flattening and rerolls the saved seed. Patches have smooth irregular edges, gentle terrain variation and a gradual inland transition. Build into map applies them.

Flat coastal patches now flatten the full selected inland reach, then reshape surrounding high terrain over a wider transition based on hill height. Increase Extra coastal flattening to 1 for the strongest flattening.

Coastal flattening sensitivity: strength uses a stronger curve (0.5 gives 75% of the full effect), strength and coverage adjust in 0.01 steps, and randomizing disabled patches starts at 0.5. Full strength remains available at 1.

Flatter coastal cores: inland rise and rolling variation are reduced, giving broad, nearly level ground. Irregular boundaries and smooth transitions remain.

Coastal patch surrounds: terrain slopes now extend around the sides and inland edges of all flattened patches. Surrounding transition slope controls the rise per block (default 0.5); lower values reshape a wider area for gentler banks. Rebuild existing graphs to apply the correction.

Mountain stone & rocks node: connect after terrain/lake/coast and before snow/trees. Adds exposed stone on slopes and seeded embedded stone outcrops. Controls: elevation limits, stone slope threshold/coverage, rock spacing/density/radius/height, saved seed and Randomize rocks. Optional density mask excludes black areas. Outcrops use terrain height geometry (no overhangs). Preview and export share the same stone surface. Example: examples/Mountain-rocks.tgraph.

Easy export: click Export world in the main window or node editor, enter a name and click Export now. Destination defaults to ~/.minecraft/saves; Browse selects another launcher instance. Current map and painted edits export by default. Optionally check Build latest node graph before export to build/export in one job, without replacing the current painted map. Existing worlds cannot be overwritten. The completion dialog can open the exported world folder.

Minecraft generation fixes: river water follows actual channel terrain instead of priority-flood fill elevations; river intersections limit excess elevated depth; coast flattening reshapes river channels and lowers their water with the ground. New worlds generate surrounding ocean at the map sea level. Export has a default-on Blend map edges into surrounding ocean option, which reshapes the outer band in the export copy and omits trees in that band; the editable source map is preserved. Existing Minecraft saves must be re-exported under a new name to receive these fixes.

Large map sizes: World and Nodes now offer 2048, 4096, 8192 and 16384 (maximum). Project dimensions accept up to 16384. Maps above 2048 pause automatic node previews; Build generates the full selected dimensions. Main display samples the full extent into at most 2048 display pixels; tree markers are simplified at large sizes. Large-map painting retains only one undo snapshot. launch.sh allows Java to use up to 70% of available RAM. A working-memory estimate rejects oversized graph builds before allocating arrays. Full 16384 generation/export has not been validated; complex graphs, particularly drainage, can require more than 32 GB RAM. Chunk export remains sequential at full terrain resolution.

Custom Y512 export: node height ranges now extend to 512. High maps export a bundled Java 1.20.4 data pack defining an overworld from -64 to 575, 40 sections per chunk and 10-bit heightmaps. Normal-height worlds retain their existing 384-block dimension. Do not disable the custom data pack in an extended-height world.

Region-based preset builder: LargeWorldBuilder exports the California 16384/Y512 preset using a shared 2048 environmental layout, sampled at full region resolution; rocks and trees are generated per region. It uses four export workers and does not hold the full 16384 raster in RAM. Run with Java 21: java -Xmx6g -cp TerraForge.jar LargeWorldBuilder /path/to/new-world. It is a dedicated preset, not a general replacement for the full-resolution node graph evaluator.

Graph-driven 16K export: open examples/California-Graph-16384-Y512.tgraph, select 16384, seed 2024 and sea 62, then Export world with Build latest node graph before export checked. This uses the region exporter and writes the input graph plus export-settings.json into the new world. It evaluates global terrain/environment at 2048 then samples the regional terrain; rock/tree settings apply per region. The graph used for California Graph 16384 - Y512 was validated to reach Y512. Height remap has an optional Scale elevated water setting so elevated lakes/rivers transform with height.

16K build routing fix: Build into map now becomes Build & export large map at size 16384. It opens regional export with graph generation enabled instead of attempting a full in-memory graph. No discard confirmation is needed because the editable map is preserved. Large-map preview labels now explain the paused preview. Restart the app after updating to get this behavior. Regional export receives the chosen world seed and sea level.

Build inside the editor (current behavior): Build into map at 16384 uses region assembly and loads full 16384 terrain/environment arrays into the editor. It does not open an export dialog. Global environmental layout is evaluated at 2048, then sampled into the full map; rocks/trees are regional at block resolution. This was built and displayed successfully with a 16 GB Java heap on the 32 GB machine. The display samples at 2048 pixels. Save projects manually above 2048; automatic recovery is skipped at those sizes to avoid large background writes. Saving large maps does not duplicate the full map, and tree persistence supports regional totals. Export world is a separate action.

Enhanced terrain recipe: examples/California-Features-16384-Y512.tgraph has broader mountain ridges, roughly 68% land in the global layout, three large lakes, wider river channels, denser rock outcrops, and expanded forest placement. It was built at 16384 inside the editor with 143661 trees. The large-map 3D preview uses labeled 4x vertical exaggeration; actual terrain remains capped at Y512. Project saving/reading is buffered and rebuilding releases the old map after discard confirmation to avoid holding two huge maps in RAM.

Island recipe: examples/California-Island-16384-Y512.tgraph reshapes the enhanced terrain into one main island with offshore islets. Global layout verification found ocean along every boundary and 95.7% of land in the largest connected component. Size 16384, seed 2024, sea 62, peak Y512. Three lake sites are selected within contained inland terrain.


## GPU 3D preview and 4K renders

Click **GPU 3D / 4K** in the terrain toolbar. This opens a separate OpenGL view of the currently built map. It uses LWJGL 3.3.6 and bundled Linux x86-64 native libraries; keep `lib/` beside `TerraForge.jar`. No downloads are needed at launch. OpenGL 3.3 or newer is required. The basic Swing 3D view remains available.

- Left drag: orbit and tilt. Right drag: pan. Wheel: zoom.
- **F**: fit the whole map. **H**: switch between actual 1× height and 4× relief. The title shows the current height scale.
- **F11**: fullscreen. **P**: choose where to save a true **4096 × 4096 PNG**. **Esc**: close the GPU window.
- Close and reopen the viewer after editing or rebuilding to upload a fresh snapshot. The viewer does not change the terrain or export settings.

The GPU holds the full terrain and water elevations (about 2 GiB for a 16384² map), plus a 4096² surface-color texture and lightweight instanced tree shapes. The terrain grid has about 2.1 million triangles interactively and 8.4 million for 4K capture. Zooming refines geometry around the camera focus while retaining a coarser surrounding mesh. Near terrain can resolve the block-level height data. Lighting uses terrain normals and an approximate terrain shadow, with depth-colored water and 4× multisample antialiasing. These are rasterized previews, not ray-traced Minecraft screenshots. Tree shapes illustrate placement and size; Minecraft export still uses the actual library schematics.

Validated on the NVIDIA GeForce RTX 4090 with driver 595.99.02: the full 16384² map, over 54,000 tree instances, and a 4096 × 4096 render. Terrain generation remains CPU-based; this update moves the detailed 3D preview to the GPU.

Square render update: PNG export is now 4096 × 4096 pixels. The camera widens its vertical framing for square output to retain the full map at Fit.

Twin Peaks Island preset: use seed 20261016, size 16384 and sea 62. Island now has an Irregular coastline control; old graphs retain their original outline at zero. Mountain stone & rocks includes a grass-to-stone height band. Regional builds respect the final Clamp ceiling, including local rocks and snow caps.

Wildshore Island: use size 16384, seed 74839261 and sea 62. River network now offers Natural channel curves and Minimum source elevation. Lake nodes offer Natural outlet curves. These controls default to zero curves for compatibility. Curved paths follow drainage corridors, with terrain-constrained offsets and softened banks; snow transition supports bands up to 160 blocks.

Local mountain nodes now include Range length, Range direction and Connected ridge relief. Defaults preserve existing round mountains; the Wildshore Ranges preset uses elongated ridgelines with secondary summits.

Local mountain nodes include Gentle coastal foothills, measured in blocks from the shoreline. This tapers added mountain relief near the sea and leaves inland peaks intact. Wildshore Coastal Ranges uses 2000 blocks and longer ridges extending into the northern lowlands.

### Natural riverbanks
Use **Natural riverbanks** after terrain detail/rocks and before trees. It contains elevated river water, grades banks into surrounding terrain, and mixes gravel, coarse dirt, mud, stone, and small sand pockets. Enable **oceanOnly** on Coast/beach to restrict beaches to sea-level water; set tree **waterMargin** to keep trunks off banks. Re-export existing worlds to apply these changes to Minecraft chunks.

### Minecraft block view
Click **Minecraft view** above the map. The first-person GPU view uses the same integer block/material/water/snow rules as Minecraft export and renders the placed schematic trees using their vanilla block models. It reads textures and models from your locally installed Minecraft Java 1.20.4 client JAR; if it cannot find it, choose the client JAR when prompted. Minecraft resources are not included in this app.

- Click the view to capture the mouse. Move with **WASD**, **Space** up, **Left Shift** down, and **Left Ctrl** for faster flight.
- **G** goes to X/Z map coordinates; **R** refreshes the blocks after edits.
- **+ / -** changes the local view distance from 4 to 32 chunks (default: 24 chunks / 384 blocks in each direction). Numpad + and - work too. Blocks load around your position as you fly across a large map.
- **F1** hides the crosshair; **F11** toggles fullscreen; **P** saves a 4096 × 4096 PNG.
- **Esc** releases the mouse; press it again to close the view.

Height is always 1:1 in this view. The existing **GPU 3D / 4K** button remains the whole-map overview. Close one GPU view before opening the other. Block positions and source textures follow the exported map; lighting, biome tint, fluid animation and shader/resource-pack effects can differ from Minecraft itself. This is a fly-through preview, without player collision or world simulation.

River valleys: Natural riverbanks now exposes **Valley blending distance** (32–384 blocks). Wider reaches soften high river sides into surrounding grassland. Natural river curves use smooth sideways displacement along the drainage path to avoid tight noise-generated hooks.

### Variable natural rivers
In **River network**, adjust **Follow terrain valleys**, **Natural width variation**, and **Unequal bank widths**. Terrain following favors lower ground and penalizes routes that cross raised ridges. Width variation adds gradual constrictions and wider pools; confined terrain narrows the channel. Each bank uses its own smooth variation profile. In **Natural riverbanks**, **Bank width and valley variation** changes the widths of bank materials and valley transitions in world coordinates. The existing river/bank seeds keep these patterns repeatable. The supplied Variable Natural Rivers graph enables these features while retaining the soft valley and ground-ledge fixes.

Organic river outlines in 16K maps: river paths are retained as continuous cubic curves and rasterized directly at block resolution in each region, instead of enlarging the coarse global wet/dry mask. This rounds the banks and bends while keeping variable channel widths, seeded banks, lake footprints and water remapping. The same method is used by Build into map and region graph export. Rebuild old graphs or use the supplied Organic Rivers map to apply the new outlines.

S-bend rivers: full-resolution river curves now form broad, seeded S-meanders with continuous tangents and gentle endpoint fades. Natural riverbanks scales gravel, mud and coarse-dirt deposits with the bank-width control, with irregular patches blending into grass. The supplied S-Bend Rivers graph uses 40-block bank transitions and 224-block valley blending. Rebuild and re-export to update an existing Minecraft world.

Clean Riverbanks update: the supplied graph narrows the sediment transition from 40 to 18 blocks, retaining the S-bends and broad valley smoothing. Valley materials are repainted at block resolution, including the grassy outer transition, to remove inherited coarse square patches.

Seven Block Rivers graph: 16 headwater sources, nominal width 7 blocks, 3-block channel depth, full terrain-valley following and modest width variation. Vector channel width retains its world-block size independently of the coarse routing grid. Lakes and confluences may be wider.

Narrow-river bends retain broad meanders independently of channel width. Displacement is reduced where the sampled terrain rises outside the drainage corridor, preserving the narrow channel while following lower land.

Tributaries terminate at existing wet rivers and join their continuous centerlines, avoiding duplicate parallel downstream paths.

### Natural river courses
The Natural River Courses graph enables staged channel geometry and routing around enclosed depressions. Upper reaches have narrower rocky channels, tighter curves and steeper valley sides; middle reaches widen with estimated contributing drainage area and develop stronger meanders; lower reaches have gentler, broader valleys, finer bank deposits, small levee relief and widened estuary mouths. Suitable low floodplains can receive shallow horseshoe oxbow ponds. Lake outlet nodes expose the same downstream-course option. These are terrain-generation rules, not a fluid or sediment-transport simulation. Minecraft provides its own water behavior.

River network controls: **Natural upper / middle / lower courses**, **Route around enclosed dips**. Natural riverbanks: **Course-shaped valleys and deposits**. The supplied graph retains a nominal 7-block middle-channel setting, with narrower headwaters and wider downstream reaches. Existing graphs retain their previous behavior until these options are enabled. Rebuild and re-export to apply the new terrain.

References: [USGS stream gradient and erosion/deposition zones](https://pubs.usgs.gov/sir/2006/5029/), [NPS meanders, cut banks, point bars and oxbows](https://www.nps.gov/articles/meandering-stream.htm), [Whitebox depression handling and hydrologic routing](https://www.whiteboxgeo.com/manuals/qgis/hydrology-depressions-storage.html). The routing implementation penalizes enclosed dips and uphill crossings; it does not use or redistribute Whitebox software.

### Terrain-driven river structure (replacement course system)
The Terrain Driven Rivers graph builds drainage connectivity before detailed geometry. It calculates a spill surface without artificial height increments, excludes enclosed non-lake depressions deeper than 1.5 blocks, routes to outlets with strong uphill/ridge penalties, favors connected local downhill flow, and accumulates contributing catchment area. Tributaries join selected drainage cells rather than stopping at nearby painted water masks.

Course shaping uses valley-constrained smoothing and iterative curvature-driven bank retreat; independent sine-wave turns are disabled. Channel widths/depths respond to catchment and course position. Outer bends can deepen while inner bends receive sediment. Suitable wider, gentle lowland channels can retain sediment islands with two wet arms that reconnect downstream. Oxbow generation now cuts off actual returning meanders with a shallow, passable neck and preserves the abandoned path; it no longer places unrelated horseshoe ponds. These are procedural terrain/erosion approximations rather than a time-resolved hydraulic simulation.

Enable **Drainage-first river structure** and **Route around enclosed dips** in River network, **Natural downstream outlet course** in lakes, and **Course-shaped valleys and deposits** in riverbanks. The supplied graph enables these options, retains nominal 7-block middle reaches, and preserves the island, mountains, lakes, beaches, snow and tree settings. Rebuild and re-export old graphs to apply the replacement routing system.

New river, lake outlet and riverbank nodes enable the replacement model by default. Older graph files with no course options retain their legacy behavior; the supplied Terrain Driven Rivers graph explicitly enables the new model.

### River flow, splits and rejoins
River generation now accumulates normalized runoff over physical cell area, combines flow at drainage confluences, and carries discharge through vector shaping and water-height remapping. **Runoff amount** on River network scales water supply. Rainfall is used when present upstream; otherwise runoff is uniform. Width, depth and bank migration respond to discharge. This is generation-time flow accounting; Minecraft controls its own fluid updates and animation.

Suitable sediment islands receive unequal branch shares from relative open-channel conveyance using width, depth, gradient and bed roughness. The share and branch dimensions are relaxed together; outgoing shares sum to incoming discharge, and the arms recombine downstream. A blocked arm receives no discharge. Branch profiles are applied in block space with deterministic world coordinates in normal full-resolution builds and 16K regional builds. No dense 16384-square discharge array is allocated.

The budget uses continuous normalized discharges; Minecraft rounds the resulting geometry to blocks. It is a simplified steady-flow approximation, not a calibrated hydraulic or unsteady sediment-transport simulation. The model uses [Manning-style relative conveyance](https://wwwrcamnl.wr.usgs.gov/sws/fieldmethods/Indirects/rantz.htm). Numerical water conservation does not imply a full sediment-conservation model.

Region world exports include **river-flow-report.txt** with total runoff and each split's incoming flow, shares, dimensions and rejoined flow. The supplied Flow Split Rivers graph retains the latest editor settings and enables the new model. Rebuild and re-export existing Minecraft worlds to apply the channel changes.
