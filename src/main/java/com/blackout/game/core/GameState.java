package com.blackout.game.core;

/** High-level state of the running game. Input and updates are routed by this. */
public enum GameState {
    /** Landing screen over the cinematic world flyover. */
    MENU,
    /** Normal gameplay. */
    PLAYING,
    /** Pause menu. */
    PAUSED,
    /** World map is open (gameplay input is suspended). */
    MAP,
    /** Settings screen (opened from the main menu or the pause menu). */
    SETTINGS,
    /** The player died. */
    GAME_OVER
}
