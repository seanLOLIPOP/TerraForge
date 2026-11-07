# Natural shorelines

In Nodes, select **Coast / beach** and set **Natural shoreline** to 1.
Existing graphs default to 0 and retain their previous coastal algorithm.

- Shore width sets the base beach width. Natural beach width variation changes it
  along the coast using seeded world coordinates; steep slopes narrow the band.
- Shore / cliff rise controls the beach profile. Steep rocky faces remain intact.
- Underwater shelf width and depth control a shallow nearshore bed with a smooth
  blend into the existing seabed. Shelf shaping never raises ground above water.
- Coastal material roughness increases gravel pockets. Shore type selects auto,
  sand, gravel, or rock. Auto uses slope and coherent noise.
- Ocean beaches only excludes elevated lakes. River-mask cells are preserved.

The natural profile replaces the legacy Coast flattening controls while enabled.
It does not change water levels, generate deltas, or simulate waves/erosion.
Regional builds shape the shared global layout, then sample it in world
coordinates; they do not restart shelf-distance calculations at region borders.
The global layout uses eight-block cells, so very narrow shelves need a smaller
direct build for more detail. No land seeds means no shelf on all-water maps.

For a final Organic surface materials node, enable **Slope-based natural shores**
as well. Beach width variation and Dune transition remain available there. This
classification currently uses the parallel CPU path; other eligible graph stages
retain their GPU support.

Validation: NaturalShorelinesTest covers finite profiles, fixed water levels,
river preservation, and all-water maps. The supplied City Map graph was loaded,
saved as a separate enabled copy, and evaluated at its 16384-block world scale;
adjacent regions were generated successfully. This is not a complete 1024-region
export or proof that every user graph has identical shoreline quality.
