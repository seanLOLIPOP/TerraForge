# Missing river forks in the overview (v0.1.23)

v0.1.22 generated recursive forks in the vector network, but carved their arms
only when resolution equaled world size. Large region builds use an eight-block
global grid, so the coarse layout lacked those channels. Unexplored Whole Map
areas and the initial overview could therefore look like the previous simple
trunks even though the report counted many forks. Seasonal tails and terminal
pools were separate passes and did not supply the missing parent arms.

The RIVERS pass now remembers the first new branch segment, generates recursive
and alpine branches, and rasterizes the added segments into the coarse terrain,
water, and river fields using RiverChannels' world-to-grid conversion. Detailed
regional rendering still uses the same vector geometry. Direct builds retain
their existing fork-carving path.

This fixes representation of the existing network; it does not replace the
drainage planner or invent additional primary river basins. The same graph and
seed should have the same vector branches. Very small channels remain limited
by the eight-block global resolution.

BranchOverviewTest generates forks on a controlled slope and samples them away
from the primary river. The published v0.1.22 JAR fails with 0/36 visible wet fork
samples; the fixed build passes with 36/36. The user's actual saved v0.1.22 build
graph, seed, and sea level are also used to verify the full 16384 layout. User
maps and diagnostic images are not included in the release.

In the actual saved build's 16384 layout, 3725/3728 sampled fork points are wet
after the downstream environmental passes. The three missed nearest-cell samples
are consistent with the coarse-grid sampling limit. The full-map preview was
visually checked against the user's v0.1.22 preview with the same graph and seed.

Build into map must be run with the new app. The editor build-folder revision
changes so old incomplete layout/overview files are not resumed. Command-line
resumable builds should use a new destination folder. Existing app windows and
already opened projects do not update themselves.

Previously documented baseline EnvironmentTest and DenseRiverBranchesTest
failures remain separate from this fix; see RIVER-BRANCH-ENDINGS.md.
