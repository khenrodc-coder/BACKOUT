package com.blackout.game.systems;

/**
 * One timed player action at a time (weapon switch, door interaction). While it is busy, other actions that would conflict
 * (firing, reloading, switching again, using another door) are refused. A "contact" moment fires part-way through, so the
 * effect (the door actually moving, the new weapon appearing) lines up with the animation.
 */
public final class ActionLock {

    public enum Type { NONE, SWAP, DOOR }

    private Type type = Type.NONE;
    private double duration, t, contactAt;
    private boolean contacted;
    private Runnable onContact, onEnd;

    public boolean busy() {
        return type != Type.NONE;
    }

    public Type type() {
        return type;
    }

    /** 0..1 progress of the running action, 0 when idle. */
    public double progress() {
        return busy() ? Math.min(1, t / duration) : 0;
    }

    /** Starts an action unless another is running. {@code contactFraction} is when {@code onContact} fires (0..1). */
    public boolean start(Type type, double duration, double contactFraction, Runnable onContact, Runnable onEnd) {
        if (busy() || type == Type.NONE || duration <= 0) {
            return false;
        }
        this.type = type;
        this.duration = duration;
        this.contactAt = Math.max(0, Math.min(1, contactFraction));
        this.t = 0;
        this.contacted = false;
        this.onContact = onContact;
        this.onEnd = onEnd;
        return true;
    }

    public void update(double dt) {
        if (!busy()) {
            return;
        }
        t += dt;
        if (!contacted && t >= contactAt * duration) {
            contacted = true;
            Runnable c = onContact;
            if (c != null) {
                c.run();                     // may call cancel() if the action turned out to be invalid
            }
        }
        if (busy() && t >= duration) {
            Runnable e = onEnd;
            reset();
            if (e != null) {
                e.run();
            }
        }
    }

    /** Aborts without firing any callbacks (death, menu, map, restart). */
    public void cancel() {
        reset();
    }

    private void reset() {
        type = Type.NONE;
        onContact = null;
        onEnd = null;
        t = 0;
        duration = 0;
        contacted = false;
    }
}
