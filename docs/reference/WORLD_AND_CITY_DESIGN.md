# World and City Design

## City districts
1. Residential suburbs: detached houses, yards, garages, parked cars, fences.
2. Downtown: taller buildings, shops, offices, parking structures, plazas.
3. Medical district: hospital, clinic, pharmacy, ambulance bay.
4. Civic/security district: police station, fire station, government buildings.
5. Commercial district: supermarket, hardware store, gas station, restaurants.
6. Industrial district: warehouses, factories, loading yards, rail sidings.
7. Green areas: parks, sports fields, wooded outskirts, drainage channels.
8. Restricted zone: military checkpoint, research facility, underground access.

## Larger house proportions
- Measure the player character first and establish a world-unit scale.
- Increase house dimensions toward the requested 2x size, but preserve believable proportions.
- Door height should exceed the character height with clearance; width should accommodate the character and weapon.
- Keep stair width, ceiling height, furniture, and corridors consistent.
- Ensure collision meshes match visible geometry.
- Build interiors as separate reusable room modules.

## Natural prop placement
- Use varied placement rules rather than random scattering.
- Keep furniture aligned to walls and floors; preserve walkable paths.
- Add small imperfections: rotated chairs, open drawers, fallen objects, dust, stains, curtains, and personal items.
- Cars should sit on road/parking surfaces, with varied orientation and spacing.
- Avoid clipping, floating props, repeated identical arrangements, and blocked doorways.

## Large-world performance
- Divide the city into chunks/district cells.
- Load nearby chunks asynchronously where safe; do not create JavaFX scene graph nodes from background threads.
- Keep only nearby high-detail assets active; use simpler distant representations.
- Reuse meshes/materials and pool repeated objects.
- Use distance-based culling and level-of-detail representations.
- Keep persistent changes (looted containers, opened doors, destroyed barricades) in world-state data rather than relying on scene nodes remaining loaded.

## Map requirements
- M toggles the map; Escape closes it.
- Show player marker, orientation, discovered points of interest, and optional waypoint.
- Unexplored regions should remain hidden or muted until discovered.
- Do not allow movement/shooting while the map is open.
- Map coordinate conversion must match world coordinates and remain consistent across chunks.
