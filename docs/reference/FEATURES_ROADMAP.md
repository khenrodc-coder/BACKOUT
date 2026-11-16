# BLACKOUT Feature Roadmap

## Project direction
A single-player, open-world zombie survival game set in 2099. The world should feel explorable, dangerous, reactive, and believable. Keep the current JavaFX 3D / Java 21 project unless a deliberate engine migration is separately approved.

## Priority 0 — Preserve the current game
- Keep existing weapons, enemy types, wave logic, health/stamina, pause/game-over, FPP/TPP, jump, collision, and Blood Moon work.
- Create a backup/working Git branch before major changes.
- Add features incrementally; compile and test after each stage.

## Priority 1 — Core experience
1. **3D landing/main menu**
   - Continue Game, Start New Game, Settings, Exit.
   - Continue is disabled when no valid save exists.
   - Start New Game confirms before replacing/starting a new run.
   - Animated 3D scene: ruined city, drifting fog, subtle wind, distant zombie silhouettes, cinematic camera.
   - Settings: graphics quality, audio volume, mouse sensitivity, key bindings, fullscreen, FOV where supported.
2. **Save/load**
   - Save world date/time, player transform, health/stamina, inventory, equipped weapon/ammo, discovered map markers, and relevant world changes.
   - Use versioned save data and safe writes (temporary file then replace).
   - Validate save data; handle missing/corrupt files gracefully.
3. **Player animation and interaction**
   - Walk, run, crouch, jump, land, climb/vault, open/close door, reload, weapon switch, aim, fire, hit reaction.
   - Blend transitions; prevent conflicting actions and animation snapping.
4. **World map**
   - Press M to open; M or Escape closes.
   - Show player position, heading, explored areas, discovered locations, and waypoint.
   - Pause gameplay input while map is open; retain mouse/camera state correctly.

## Priority 2 — World scale and realism
5. **Larger buildings**
   - Target roughly 2x the current house footprint/volume only after checking the player scale and interior layout.
   - Door opening height/width must fit the player rig with clearance.
   - Keep consistent real-world scale; do not scale only the exterior while leaving tiny interiors.
   - Natural interior arrangement: furniture against walls where appropriate, clear paths, clutter variation, believable object scale.
6. **Large city**
   - Districts: residential, downtown/commercial, hospital, police station, supermarket, industrial, school, gas station, park, outskirts.
   - Add roads, intersections, sidewalks, parking, streetlights, signs, drainage, fences, debris, and landmarks.
   - Use modular blocks and reusable building kits.
   - Stream world chunks around the player; unload distant chunks and retain important state.
7. **Natural props and vehicles**
   - Vary car placement, orientation, damage, rust, dirt, and wheel condition.
   - Avoid identical repeated object layouts; use deterministic seeded variation.
   - Keep doors, furniture, cars, and props aligned with floors and collision geometry.

## Priority 3 — Survival and world simulation
8. Inventory, item pickup, containers, loot tables, equipment slots.
9. Hunger, thirst, fatigue, medicine, injuries, and stamina recovery.
10. Crafting and barricading, introduced after inventory and interaction systems.
11. Safehouse discovery/upgrades and sleeping.
12. Dynamic weather: rain, fog, wind, storms, wet-ground look, and ambience.
13. Vehicle entry/driving only after world streaming, collision, and camera handling are stable.
14. Missions, radio broadcasts, survivor NPCs, and dynamic supply drops.

## Priority 4 — Zombie AI
- Hearing: gunshots, sprinting, doors, glass, and loud interactions.
- Sight: line of sight, distance, lighting, and obstruction.
- Search: investigate last known position and search nearby rooms.
- Door interaction/breaking where appropriate.
- Horde grouping and migration; avoid every zombie following the exact same path.
- Keep walker, runner, brute, and crawler roles distinct.
- Blood Moon: every third week of each month; runners, dark bodies, red eyes, stronger aggression. Ensure the event uses the existing in-game calendar and save/load state.

## Acceptance criteria
- No existing core feature silently disappears.
- No player can pass through a closed solid door or get trapped by an opened one.
- Player can enter/exit buildings without camera or collision glitches.
- Map opens/closes reliably and does not allow movement while open.
- Save/load restores the same run state.
- Large-world performance remains stable through chunk loading and object reuse.
