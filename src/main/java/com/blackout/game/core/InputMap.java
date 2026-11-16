package com.blackout.game.core;

import java.util.EnumMap;
import java.util.Properties;
import java.util.Set;

import javafx.scene.input.KeyCode;

/** Central action to key mapping (two keys per action). All gameplay input goes through this. */
public final class InputMap {

    private final EnumMap<Action, KeyCode[]> binds = new EnumMap<>(Action.class);

    public InputMap() {
        resetDefaults();
    }

    public void resetDefaults() {
        for (Action a : Action.values()) {
            binds.put(a, new KeyCode[]{a.defaultPrimary, a.defaultSecondary});
        }
    }

    public KeyCode get(Action a, int slot) {
        return binds.get(a)[slot];
    }

    public boolean matches(Action a, KeyCode k) {
        KeyCode[] b = binds.get(a);
        return k != null && (b[0] == k || b[1] == k);
    }

    public boolean down(Action a, Set<KeyCode> pressed) {
        KeyCode[] b = binds.get(a);
        return (b[0] != null && pressed.contains(b[0])) || (b[1] != null && pressed.contains(b[1]));
    }

    /** Escape and the weapon slot keys cannot be rebound. */
    public static boolean isReserved(KeyCode k) {
        return k == KeyCode.ESCAPE || (k.ordinal() >= KeyCode.DIGIT1.ordinal() && k.ordinal() <= KeyCode.DIGIT6.ordinal());
    }

    /** Binds a key; if another action already used it, that binding is cleared. Returns false if the key is reserved. */
    public boolean rebind(Action a, int slot, KeyCode k) {
        if (k == null || isReserved(k)) {
            return false;
        }
        for (Action x : Action.values()) {
            KeyCode[] b = binds.get(x);
            for (int s = 0; s < 2; s++) {
                if (b[s] == k && !(x == a && s == slot)) {
                    b[s] = null;
                }
            }
        }
        binds.get(a)[slot] = k;
        return true;
    }

    public void clear(Action a, int slot) {
        binds.get(a)[slot] = null;
    }

    public String label(Action a, int slot) {
        KeyCode k = get(a, slot);
        return k == null ? "-" : k.getName();
    }

    public void write(Properties p) {
        for (Action a : Action.values()) {
            for (int s = 0; s < 2; s++) {
                KeyCode k = get(a, s);
                p.setProperty("bind." + a.name() + "." + s, k == null ? "NONE" : k.name());
            }
        }
    }

    public void read(Properties p) {
        for (Action a : Action.values()) {
            for (int s = 0; s < 2; s++) {
                String v = p.getProperty("bind." + a.name() + "." + s);
                if (v == null) {
                    continue;
                }
                if (v.equals("NONE")) {
                    binds.get(a)[s] = null;
                    continue;
                }
                try {
                    KeyCode k = KeyCode.valueOf(v);
                    if (!isReserved(k)) {
                        binds.get(a)[s] = k;
                    }
                } catch (IllegalArgumentException ignored) {
                    // unknown key name in the file: keep the default
                }
            }
        }
    }
}
