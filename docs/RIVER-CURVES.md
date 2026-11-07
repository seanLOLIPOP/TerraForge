# Straight river fix (v0.1.21)

Drainage-first rivers previously carved the provisional route before final
RiverMorphology bends were applied. Regional refinement used the final geometry,
but direct builds and global layout channels could still appear straight.
Both now rasterize the final centerline. Drainage-first geometry also receives
the final spline interpolation that was previously skipped, with terrain and
map-bound checks before accepting each interpolated displacement. Legacy non-course river carving remains
on its existing path.

The terrain-fit envelope also repeatedly shrank whole stretches around blocked
points. It now finds local safe displacement limits and smooths those limits
without repeatedly multiplying a whole neighborhood down. Final offsets are
checked against terrain and map bounds. Sources and endpoints remain fixed.
Steep or confined reaches can still be relatively straight.

In Nodes > River network, use **Natural channel curves = 1 to 2** and rebuild.
The control now scales meanders for drainage-first rivers, in addition to its
existing broad-curve role. Zero disables the added meander displacement. New
nodes default to 1; existing graphs retain their saved value. The supplied
909.tgraph already had the maximum value 2; raising the setting was not the fix.

Existing generated regions must be regenerated with Build into map. Moving
through an old built map does not replace its already saved river channels.
The editor build-folder revision changes in this release, so Build into map
starts a fresh working map instead of resuming old straight-channel regions.
For explicit command-line resumable exports, choose a new destination folder.
Graph/dependency hashes include the new default parameter value for new nodes;
encoded-region reuse hashes the resulting channels. Stage caches restart with
the new application process.

RiverCurveRenderingTest reproduces a straight, sloping drainage route. The
published v0.1.20 JAR fails: zero- and maximum-strength direct channels have the
same nearly zero centroid span. The fixed build renders a continuous wet curve
with approximately 85 blocks of centroid variation; the layout fixture bends
as well. This is a geometric fixture, not a full-world performance benchmark.

Additional checks cover seeded bends, broad-turn barrier avoidance, tributary
joins, routing/catchment conservation, channel obstructions, regional ribbon
refinement, and the legacy generation corpus. The corpus does not include full
drainage-first rendered river worlds; those intentionally change with this fix.
The existing EnvironmentTest river assertion noted in REALISTIC-LAKES.md remains
a separate baseline issue.
