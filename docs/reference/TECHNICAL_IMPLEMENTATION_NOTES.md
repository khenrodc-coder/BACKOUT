# Technical Implementation Notes (JavaFX 3D)

## Preserve the current project
- Keep Java 21 and the existing Maven/JavaFX setup unless a separately planned migration is approved.
- Inspect current classes before changing them. Prefer small interfaces/controllers over replacing a large working `Main.java` all at once.
- Create a Git branch and commit a known-good baseline before major work.

## Suggested architecture boundaries
- `GameStateManager`: menu, playing, paused, map, settings, game over.
- `SaveManager`: versioned serialization and safe save writes.
- `WorldManager`: chunk/district lifecycle and persistent world state.
- `MapController`: map UI, markers, coordinates, discovered areas.
- `InteractionSystem`: range/line-of-sight checks and interactable targets.
- `PlayerAnimationController`: animation state and transition timing.
- `WeatherController`: weather state, lighting/audio changes.
- `ZombiePerception` / `ZombieAIController`: sensing and behavior states.
- `InventoryManager`: item stacks, equipment, capacity, quick slots.

These are proposed boundaries, not a requirement to create every class immediately. Match the existing codebase and avoid duplicate systems.

## JavaFX considerations
- Keep scene graph changes on the JavaFX Application Thread.
- Use `AnimationTimer` carefully; calculate delta time and cap unusually large frame deltas after pauses.
- Avoid allocating many new nodes/materials every frame.
- Reuse meshes, materials, and repeated props.
- Use chunk loading and distance-based detail to control scene graph size.
- Test collision and camera behavior at doorways, stairs, and chunk boundaries.
- For large worlds, prototype a small streamed district before building the entire city.

## Suggested implementation order
1. Game states and main menu.
2. Save/load.
3. Input and interaction architecture.
4. Animation transitions.
5. Map.
6. Reusable house/building modules.
7. Small chunk-streamed district.
8. Zombie perception/AI improvements.
9. Survival inventory systems.
10. Weather, vehicles, NPCs, and missions.
