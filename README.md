# BLACKOUT

A 3D third-person / first-person zombie survival game built with JavaFX.

- Java 17+, JavaFX 21, Maven
- Day/night cycle, blood moon, sleeping, zombie types (Walker, Runner, Tank, Smoker)

<<<<<<< HEAD
## Controls

| Key | Action |
|---|---|
| W A S D / Shift / C / Space | move / sprint / crouch / jump (double-tap Space near a ledge to climb) |
| E (or F) | interact: open / close door, sleep in a bed (night only). The prompt shows what E will do |
| R / T / 1-6 | reload / flashlight / select item |
| M | world map (click = waypoint, M or Esc closes) |
| F5 | quick save |
| Esc | pause / close map or settings |

Every key except Esc and 1-6 can be rebound in **Settings**. Settings and saves live in `~/.blackout/`.

=======
>>>>>>> ff27d6927653b9cba7010f8f2288388bf2484d2a
## Run

```bash
mvn javafx:run
```

<<<<<<< HEAD
## Test

```bash
mvn test
```

=======
>>>>>>> ff27d6927653b9cba7010f8f2288388bf2484d2a
## Build

```bash
mvn clean package
```

## Project layout

```
BLACKOUT/
├── pom.xml
├── docs/
│   └── UPDATES.txt                 feature roadmap / update notes
└── src/
    ├── main/
    │   ├── java/com/blackout/game/
    │   │   ├── Main.java           JavaFX Application: game loop, input, HUD, world build
    │   │   ├── core/
    │   │   │   ├── Constants.java  shared constants (GROUND_Y, zombie/state ids, RNG)
<<<<<<< HEAD
    │   │   │   ├── GameState.java  MENU / PLAYING / PAUSED / MAP / SETTINGS / GAME_OVER
    │   │   │   ├── Action.java + InputMap.java  rebindable key map
    │   │   │   ├── Settings.java   persistent user settings
    │   │   │   ├── FileUtil.java   data folder + atomic file writes
=======
>>>>>>> ff27d6927653b9cba7010f8f2288388bf2484d2a
    │   │   │   ├── Gfx.java        3D building blocks (box, sph, cylY/X/Z, strut, cable, plane, crossQuads ...)
    │   │   │   ├── Mats.java       cached realistic materials (grass, asphalt, brick, bark, paint ...)
    │   │   │   └── TextureFactory.java  procedural tileable textures (no image files needed)
    │   │   ├── entities/
    │   │   │   ├── Enemy.java      zombie model, stats, animation, blood-moon form
    │   │   │   ├── Rig.java        articulated character skeleton
    │   │   │   ├── RigFactory.java builds soldier and zombie rigs
    │   │   │   ├── WeaponType.java pistol / assault rifle / shotgun stats
    │   │   │   ├── Pickup.java
    │   │   │   └── Grenade.java
    │   │   ├── environment/
    │   │   │   ├── Building.java
    │   │   │   ├── BuildingDetail.java  porch, shutters, boards, chimney, gutter
    │   │   │   ├── BridgeFactory.java   steel-truss road bridge
    │   │   │   ├── TowerFactory.java    radio towers, monopoles, power pylons
    │   │   │   ├── TreeFactory.java     pine / oak / birch / dead trees with wind sway
    │   │   │   ├── VegetationFactory.java  grass chunks, bushes, debris
    │   │   │   ├── VehicleFactory.java  sedan / SUV / pickup / van (clean, wreck, burnt)
    │   │   │   ├── Obstacle.java
    │   │   │   ├── Car.java
    │   │   │   ├── Fire.java
    │   │   │   ├── DynLight.java
    │   │   │   └── Cull.java       distance-culling entry
    │   │   ├── effects/
    │   │   │   ├── Particle.java
    │   │   │   ├── Tracer.java
    │   │   │   ├── Explosion.java
    │   │   │   └── SmokeCloud.java
<<<<<<< HEAD
    │   │   ├── save/
    │   │   │   ├── SaveData.java   everything a save contains (plain data)
    │   │   │   └── SaveManager.java versioned, CRC-checked, crash-safe save file + backup
    │   │   ├── systems/
    │   │   │   ├── Interactable.java   anything the interact key can use (doors, beds, later loot / vehicles)
    │   │   │   ├── InteractionSystem.java  picks the best target in range and routes the key to it
    │   │   │   ├── ActionLock.java     one timed action at a time (weapon swap, door) with a contact moment
    │   │   │   └── ReloadPoses.java    per-weapon reload animation curves
    │   │   └── ui/
    │   │       ├── MainMenu.java   landing menu, confirm dialog, loading cover, animated fog
    │   │       ├── SettingsPanel.java  graphics, volume, sensitivity, FOV, key bindings
    │   │       ├── WorldMapView.java   M-key map with fog of war, places, waypoint
    │   │       └── UiTheme.java
=======
    │   │   ├── systems/            (reserved: spawning, AI, day/night ...)
    │   │   └── ui/                 (reserved: HUD, minimap, menus)
>>>>>>> ff27d6927653b9cba7010f8f2288388bf2484d2a
    │   └── resources/
    │       ├── audio/
    │       ├── models/
    │       ├── styles/
    │       └── textures/
<<<<<<< HEAD
    └── test/java/                  JUnit tests (save files, settings, key map)
=======
    └── test/java/
>>>>>>> ff27d6927653b9cba7010f8f2288388bf2484d2a
```

## Next refactor steps

<<<<<<< HEAD
See `docs/reference/` for the feature roadmap and `docs/UPDATES.txt` for what is done.

`Main.java` still holds most of the game logic (about 4,400 lines). The `systems/` and
=======
`Main.java` still holds the game logic (about 3,800 lines). The `systems/` and
>>>>>>> ff27d6927653b9cba7010f8f2288388bf2484d2a
`ui/` packages are ready for it to be split into e.g. `SpawnSystem`,
`DayNightSystem`, `CombatSystem`, `CameraController`, `Hud` and `Minimap`.
