package com.blackout.game.core;

import javafx.scene.input.KeyCode;

/** Every rebindable gameplay action. Number keys 1-6 (weapon slots) and Esc are fixed. */
public enum Action {
    FORWARD("Move forward", KeyCode.W, null),
    BACK("Move backward", KeyCode.S, null),
    LEFT("Move left", KeyCode.A, null),
    RIGHT("Move right", KeyCode.D, null),
    SPRINT("Sprint", KeyCode.SHIFT, null),
    CROUCH("Crouch", KeyCode.C, null),
    JUMP("Jump / climb", KeyCode.SPACE, null),
    INTERACT("Interact (door / bed)", KeyCode.E, KeyCode.F),
    RELOAD("Reload", KeyCode.R, null),
    FLASHLIGHT("Flashlight", KeyCode.T, null),
    MAP("World map", KeyCode.M, null),
    QUICK_SAVE("Quick save", KeyCode.F5, null),
    MEDKIT("Use medkit", KeyCode.H, null);

    public final String label;
    public final KeyCode defaultPrimary, defaultSecondary;

    Action(String label, KeyCode primary, KeyCode secondary) {
        this.label = label;
        this.defaultPrimary = primary;
        this.defaultSecondary = secondary;
    }
}
