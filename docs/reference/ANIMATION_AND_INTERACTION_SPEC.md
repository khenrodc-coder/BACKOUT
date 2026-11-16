# Animation and Interaction Specification

## Animation states
- Idle: relaxed stance with subtle breathing and weapon-ready variation.
- Walk: grounded foot placement, alternating arm/leg motion, slight torso movement.
- Run/sprint: faster cadence, forward lean, stronger arm swing, weapon bob.
- Crouch: smooth height transition, lower center of mass, shorter stride.
- Jump/land: takeoff anticipation, airborne pose, landing compression and recovery.
- Climb/vault: detect valid ledge, move hands/body over it, then place feet and recover.
- Reload: weapon-specific sequence; magazine removal/insertion, bolt/slide action where applicable, return to ready.
- Door: approach, reach toward handle, rotate door around hinge, pass through, close only when appropriate.
- Weapon switch: lower current weapon, bring next weapon into position, prevent firing during transition.
- Hit/death: directional reaction, brief control response, clear state transition.

## Animation quality rules
- Use animation blending or interpolated pose transitions; avoid abrupt teleporting between poses.
- Match movement speed to animation playback speed.
- Foot contact should not slide excessively.
- Crouch and stand transitions should adjust collision height smoothly.
- Reload duration and sequence should differ by weapon.
- Door pivot must be at the hinge, not the center of the door.
- Use an action state/lock so reload, climb, door interaction, and weapon switching cannot conflict.
- Provide cancel/failure paths when the player moves away or the target becomes invalid.

## Interaction model
- Show a small contextual prompt when a valid object is in range and visible.
- E interacts with doors, beds, loot containers, pickups, radios, and vehicle entry points.
- Validate distance and line of sight before interaction.
- Use a consistent interaction interface so new interactable objects do not require unrelated player-controller rewrites.
