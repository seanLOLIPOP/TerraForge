# Rounded bends (0.1.27)

Smoothing previously happened before final loop/collision repair. Removing a
near-self loop could introduce a shortcut with a sharp corner afterward. The
final channel now receives an arc-length fairing pass after that repair and is
then rechecked for contacts/crossings. A final smoothing candidate is accepted
only if it avoids crossings and non-junction bank overlaps, so a second repair
cannot silently reintroduce the same sharp elbow. A terrain-checked cubic approach aligns
incoming tributaries with the receiving channel downstream, rather than
finishing with a sideways snap. Internal merge points may shift downstream;
ocean outlets stay fixed.

RiverBendFairing resamples at four-block spacing and applies three width-scaled
Gaussian passes. Endpoint correction holds the source and outlet in place.
Terrain fitting allows bounded bank adjustment (up to six blocks) while
protecting steep slopes and ridges. Fit envelopes prevent independent per-point clipping from introducing
fresh wiggles. Heights and discharge remain attached to their ordered samples;
no added random displacement is used. Short wiggles are suppressed while broad
bends remain. Terrain constraints can limit how much a bend can be rounded.

Upstream tributary arms use the same filter. Their old collision entries are
retired, and the smoothed geometry is checked and indexed before use. If it
cannot retain bank clearance or avoid crossings, the original arm is restored.
Wet-end indices use actual polyline length after resampling.

RiverBendFairingTest checks high-frequency turn reduction, retained broad bends,
a right-angle elbow, downstream-facing confluences, source/outlet positions,
deterministic output, downstream
water profiles, and a protected ridge. Existing collision, coarse visibility,
ocean-mouth, endings/seam, island split, flow and slope checks remain applicable.
A saved-map diagnostic renders a detailed region containing a tight remaining
bend. The build revision changes so old region geometry is not silently reused.

Final saved-map audit: zero proper crossings across wet channels. The detailed
region was rendered and inspected. Native Windows launch/export smoke test passed.
