# River mouths (0.1.26)

River water is clamped to sea level as it approaches the coast. Both the coarse
rasterizer and detailed ribbon renderer previously skipped any reach whose water
was at sea level. A wide beach with terrain between sea level and sea level + 1
could therefore remain dry across the mouth despite a planned ocean outlet.

Both renderers now carve the bed at sea-level reaches. Detailed refinement skips
already submerged ocean cells, rather than also skipping dry cells whose rounded
block elevation equals sea level. Existing lake masks remain protected. The fix
uses the planned route; it does not turn seasonal or lake endings into ocean rivers.

RiverOceanMouthTest covers a long beach including exact sea-level and fractional
height cells, wet connectivity in coarse/direct/refined channels, adjacent region
consistency, shoreline/bank processing, and retained dry beach outside the channel.
The pre-fix 0.1.25 renderer fails the coarse-mouth fixture. An optional saved-map
case generates a detailed coastal region and verifies a wet path from an upstream
river point to offshore water using four-neighbor flood fill.

Existing generated worlds require a new build. The engine build-folder revision
is changed so old region folders are not silently resumed.
