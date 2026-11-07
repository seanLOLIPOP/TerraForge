# Converging tributaries (0.1.24)

The old extra-fork generator grew downstream and repeatedly split into comparable
arms that could reconnect. This produced triangular webs even when no segments
crossed. New generated mountain forks grow upstream from a confluence and are
emitted in downstream flow order. Growth cannot attach to a second channel.

One dominant continuation and an optional smaller side tributary replace equal
binary forks. Width and discharge decrease toward headwaters; branch lengths
shrink across generations. Existing terrain, spill-basin and shared-channel
clearance checks still apply. Original trunks, local island splits and separate
seasonal/lake ending options remain available.

The Rivers node has an optional **Extra tributaries on left half** control (0–2).
Zero leaves density uniform. Positive values add a supplemental pass only for
confluences with X below half the world width, with tighter source spacing.
Coordinates scale with map size; terrain and collision checks can limit how many
extra streams fit. This control does not invent rivers in unsuitable terrain.

For the user's supplied City map, a separate desktop graph enables this control
at 2. The original graph is preserved. Use seed 74839261, sea level 63 and map size
16384 to reproduce that terrain. Start a new Nodes → Build into map operation;
existing generated region files cannot change their river geometry in place.

Validation covers downhill water profiles, increasing downstream width,
branching without cycles, collision checks across three seeds, coarse visibility,
endings, tile seams, preserved slopes and existing river curves. The supplied
map is also generated at layout resolution, audited and rendered alongside a
fully generated 512-block region. This is a procedural tree model, not a full
hydrological simulation. Finite grids and height quantization still affect bends.

Supplied-map check: 92 tributary roots (62 on the left), 228 arms, compared
with 62 roots before the left density boost. No accidental centerline crossings
or tributary cycles were found. Windows packaged-launcher export smoke test passed.
