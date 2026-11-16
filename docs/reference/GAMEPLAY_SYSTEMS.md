# Gameplay Systems

## Survival
- Health, stamina, hunger, thirst, fatigue, and injury status.
- Food and water restore appropriate needs; expired/unsafe supplies can be a later feature.
- Medical items treat specific injuries; avoid making every item heal everything.
- Inventory with stack limits, item weight/capacity, equipment slots, and quick-use items.
- Loot containers with location-specific tables and respawn rules decided deliberately.
- Crafting and barricading depend on collected materials and player interaction.

## Zombies
- Types: walker, runner, brute, crawler, and optional special infected.
- Perception: sight, hearing, and optional close-range scent detection.
- AI states: idle/patrol, investigate, search, chase, attack, stagger, return/roam.
- Use last-known-player-position search rather than perfect tracking through walls.
- Sound events should carry position, radius, and intensity.
- Horde movement should be coordinated but not perfectly synchronized.

## World events
- Blood Moon follows the established calendar rule: every third week of each month.
- During Blood Moon, zombies become runners with dark bodies and red eyes.
- Make the event visible in sky/lighting/audio and communicate its start/end.
- Other events: supply drop, distant gunfire, horde migration, emergency radio signal, power outage.

## Vehicles
- Start with static wrecks and interaction/loot before implementing drivable vehicles.
- Drivable vehicles require input handling, collision, camera mode, entry/exit, fuel/damage, and chunk-boundary handling.
- Do not add driving until city streaming and collision are reliable.

## Weather and ambience
- Weather states: clear, cloudy, rain, fog, thunderstorm.
- Ambient audio varies by district and time of day.
- Rain can add wet-road appearance and sound; avoid expensive per-frame effects on every object.
