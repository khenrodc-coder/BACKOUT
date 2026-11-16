# Weekly Development Plan

## Phase 1 — Stabilize and structure
- Back up the project and create a known-good Git commit.
- Review existing game loop, player controller, camera, input, and scene setup.
- Introduce explicit game states without breaking pause/game-over behavior.

## Phase 2 — Menu and persistence
- Build the 3D landing menu with Continue, Start New Game, Settings, Exit.
- Add save-slot existence checks and a versioned save/load format.
- Verify new game and continue behavior.

## Phase 3 — Player feel
- Implement one animation at a time: idle/walk, run, crouch, jump/land, reload, door, climb.
- Add action-state validation and interaction prompts.
- Test in a small house before expanding the world.

## Phase 4 — Buildings and map
- Establish character-to-world scale.
- Upgrade one representative house to the new size and validate interior dimensions.
- Implement M-key map, player marker, points of interest, and close/pause behavior.

## Phase 5 — City prototype
- Build one complete district block with road, sidewalks, several buildings, props, and cars.
- Add chunk loading/unloading and profile performance before duplicating districts.

## Phase 6 — Survival and AI
- Add inventory and item pickup first, then hunger/thirst/medical/crafting.
- Add zombie sound perception, search behavior, and horde logic.
- Verify Blood Moon calendar behavior and persistence.

## Definition of done for each feature
- Compiles cleanly.
- Existing controls and systems still work.
- No major camera, collision, save, or input regression.
- Tested in the actual game scene, not only in isolation.
- Changes committed with a clear message.
