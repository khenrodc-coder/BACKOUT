# BLACKOUT

A 3D third-person / first-person zombie survival game built with JavaFX.

- Java 17+, JavaFX 21, Maven
- Day/night cycle, blood moon, sleeping, zombie types (Walker, Runner, Tank, Smoker)

## Run

```bash
mvn javafx:run
```

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
    │   │   │   ├── Gfx.java        3D building blocks (box, sph, cylY/X/Z, strut, cable, plane, crossQuads ...)
    │   │   │   ├── Mats.java       cached realistic materials (grass, asphalt, brick, bark, paint ...)
    │   │   │   └── TextureFactory.java  procedural tileable textures (no image files needed)
=======
    │   │   │   └── Gfx.java        mesh/material helpers (box, sph, cylY, mat, glow ...)
>>>>>>> 7ab94253a3fe49932fb5341cdbe322138aacef9c
    │   │   ├── entities/
    │   │   │   ├── Enemy.java      zombie model, stats, animation, blood-moon form
    │   │   │   ├── Rig.java        articulated character skeleton
    │   │   │   ├── RigFactory.java builds soldier and zombie rigs
    │   │   │   ├── WeaponType.java pistol / assault rifle / shotgun stats
    │   │   │   ├── Pickup.java
    │   │   │   └── Grenade.java
    │   │   ├── environment/
    │   │   │   ├── Building.java
<<<<<<< HEAD
    │   │   │   ├── BuildingDetail.java  porch, shutters, boards, chimney, gutter
    │   │   │   ├── BridgeFactory.java   steel-truss road bridge
    │   │   │   ├── TowerFactory.java    radio towers, monopoles, power pylons
    │   │   │   ├── TreeFactory.java     pine / oak / birch / dead trees with wind sway
    │   │   │   ├── VegetationFactory.java  grass chunks, bushes, debris
    │   │   │   ├── VehicleFactory.java  sedan / SUV / pickup / van (clean, wreck, burnt)
=======
>>>>>>> 7ab94253a3fe49932fb5341cdbe322138aacef9c
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
    │   │   ├── systems/            (reserved: spawning, AI, day/night ...)
    │   │   └── ui/                 (reserved: HUD, minimap, menus)
    │   └── resources/
    │       ├── audio/
    │       ├── models/
    │       ├── styles/
    │       └── textures/
    └── test/java/
```

## Next refactor steps

`Main.java` still holds the game logic (about 3,800 lines). The `systems/` and
`ui/` packages are ready for it to be split into e.g. `SpawnSystem`,
`DayNightSystem`, `CombatSystem`, `CameraController`, `Hud` and `Minimap`.
