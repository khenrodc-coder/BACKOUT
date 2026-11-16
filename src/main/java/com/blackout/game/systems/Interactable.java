package com.blackout.game.systems;

/**
 * Anything the player can use with the interact key (doors, beds, later: loot containers, radios, vehicle doors).
 * New kinds of objects only implement this interface; the player controller never needs to know about them.
 */
public interface Interactable {

    /** Short verb phrase shown in the on-screen prompt, e.g. "Open door". */
    String prompt();

    double x();

    double z();

    /** Maximum distance (world units) at which the prompt appears and the object can be used. */
    double range();

    /** Higher priority wins when several objects overlap (a distance bonus, not an absolute override). */
    default int priority() {
        return 0;
    }

    /** False hides the prompt completely (wrong floor, player busy, ...). */
    boolean available();

    /** False shows the prompt but refuses to act (see {@link #disabledReason()}), e.g. "sleep (night only)". */
    default boolean enabled() {
        return true;
    }

    default String disabledReason() {
        return "NOT NOW";
    }

    void interact();
}
