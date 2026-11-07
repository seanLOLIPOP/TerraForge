# Connected branches and natural endings (v0.1.22)

The intended network can contain tributaries that merge into a trunk, recursive
forks that divide downstream, and intentional split/rejoin channels around
islands. Accidental X crossings are different from those endpoint junctions.

Drainage-first centerlines now check the existing channel index before accepting
new geometry. A compatible intersection becomes an endpoint on the existing
river; the new path does not continue through it as an X. Incompatible crossings
stop short. Tight self-loops are erased while preserving the downstream outlet. This is geometric generation, not an unsteady
hydraulic simulation; the original catchment budget is not recalculated for
every local branch or endpoint edit.

Recursive fork generation always uses the shared clearance index. Side branches
check their paths against other channels and accepted siblings, as well as their
tip clearance. Intended roots and compatible confluences remain connected.

In River network, enable **Natural branch endings**:

- **Branch endings in lakes** sets the target share of eligible branch tips that
  receive a small terminal lake. Terrain slope, height, world edges, existing
  channels, other branches, and other terminal lakes can reject a site.
- **Terminal lake radius** sets the nominal size, varied by the seed.
- Remaining branches retain a longer wet reach before narrowing into a dry
  seasonal streambed. New natural side branches favor gentle downhill routes.
- Use Short fork groups, Fork branching levels, Organic branch length variety,
  and Tapered side branches to control the number and extent of branches.

The supplied map copy enables natural endings, a 50% target lake share, a
20-block nominal terminal radius, collision-aware recursive branching, and
24 or more side branches. Its split density is set to 2 to reduce crowding.
The original graph is unchanged. New ending controls default off for older maps.

Ending metadata survives water remaps. The same world-coordinate geometry is
rendered in the global layout and detailed regions; terminal water stays flat,
and seasonal beds rise above their water level before the tip. Not every primary
river is forced to end in a lake: ocean outlets and natural confluences remain.

Rebuild with the new app. A new editor build-folder revision avoids resuming
older channels. Command-line resumable exports should use a new destination.

RiverNetworkEndingsTest covers endpoint merging instead of crossing, connected
lake endings, seasonal dry ends, metadata remapping, and exact region-border
agreement. Other fixtures cover recursive forks, shared collision space, flow
metadata, previous curved channels, splits, and lake behavior. The supplied City
map is checked at 16384-block world scale plus a detailed terminal-lake region;
this is not a complete 1024-region export test.

City validation result: 127 recursive fork junctions, 19 terminal-lake branches,
82 seasonal-ending branches, and zero proper centerline crossings across the
main/fork geometry plus all wet side-branch geometry. Intended endpoint junctions
and split/rejoin loops are allowed. The audit is not a proof that every possible
graph has separated banks at every block or a fully solved hydraulic network.

Known baseline tests: DenseRiverBranchesTest expects four side branches but gets
three both here and in the published v0.1.21 JAR (its 320-block root spacing limits
the fixture). The older EnvironmentTest river assertion documented in
REALISTIC-LAKES.md also remains outside this change.
