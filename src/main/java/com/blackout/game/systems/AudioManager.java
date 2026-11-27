package com.blackout.game.systems;

import com.blackout.game.entities.WeaponType;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

/**
 * Handles all audio: sound effects, music, ambient sounds, 3D positional audio.
 * New system for Phase 1 architecture refactoring.
 */
public final class AudioManager {

    private final Map<String, AudioClip> soundEffects = new HashMap<>();
    private final Map<String, MediaPlayer> musicTracks = new HashMap<>();
    private MediaPlayer currentMusic;
    private double masterVolume = 1.0;
    private double sfxVolume = 1.0;
    private double musicVolume = 0.5;
    private final Random rng = new Random();

    // Sound categories for volume control
    public enum SoundCategory { MASTER, SFX, MUSIC, AMBIENT, UI, WEAPON, ZOMBIE, ENVIRONMENT, PLAYER }

    public AudioManager() {
        loadSounds();
    }

    private void loadSounds() {
        // Weapon sounds
        loadSfx("pistol_shot", "sounds/weapons/pistol_shot.wav");
        loadSfx("rifle_shot", "sounds/weapons/rifle_shot.wav");
        loadSfx("shotgun_shot", "sounds/weapons/shotgun_shot.wav");
        loadSfx("reload_pistol", "sounds/weapons/reload_pistol.wav");
        loadSfx("reload_rifle", "sounds/weapons/reload_rifle.wav");
        loadSfx("reload_shotgun", "sounds/weapons/reload_shotgun.wav");
        loadSfx("dry_fire", "sounds/weapons/dry_fire.wav");
        loadSfx("weapon_swap", "sounds/weapons/weapon_swap.wav");

        // Zombie sounds
        loadSfx("zombie_walker_growl", "sounds/zombies/walker_growl.wav");
        loadSfx("zombie_runner_scream", "sounds/zombies/runner_scream.wav");
        loadSfx("zombie_tank_roar", "sounds/zombies/tank_roar.wav");
        loadSfx("zombie_smoker_hiss", "sounds/zombies/smoker_hiss.wav");
        loadSfx("zombie_death", "sounds/zombies/death.wav");
        loadSfx("zombie_hit", "sounds/zombies/hit.wav");
        loadSfx("zombie_footstep", "sounds/zombies/footstep.wav");

        // Player sounds
        loadSfx("footstep_grass", "sounds/player/footstep_grass.wav");
        loadSfx("footstep_concrete", "sounds/player/footstep_concrete.wav");
        loadSfx("footstep_wood", "sounds/player/footstep_wood.wav");
        loadSfx("footstep_water", "sounds/player/footstep_water.wav");
        loadSfx("jump", "sounds/player/jump.wav");
        loadSfx("land", "sounds/player/land.wav");
        loadSfx("crouch", "sounds/player/crouch.wav");
        loadSfx("player_hurt", "sounds/player/hurt.wav");
        loadSfx("player_death", "sounds/player/death.wav");
        loadSfx("heal", "sounds/player/heal.wav");
        loadSfx("pickup", "sounds/player/pickup.wav");

        // Environment sounds
        loadSfx("door_open", "sounds/environment/door_open.wav");
        loadSfx("door_close", "sounds/environment/door_close.wav");
        loadSfx("door_break", "sounds/environment/door_break.wav");
        loadSfx("glass_break", "sounds/environment/glass_break.wav");
        loadSfx("explosion", "sounds/environment/explosion.wav");
        loadSfx("fire_crackle", "sounds/environment/fire_crackle.wav");
        loadSfx("wind", "sounds/environment/wind.wav");
        loadSfx("rain_light", "sounds/environment/rain_light.wav");
        loadSfx("rain_heavy", "sounds/environment/rain_heavy.wav");
        loadSfx("thunder", "sounds/environment/thunder.wav");

        // UI sounds
        loadSfx("menu_select", "sounds/ui/menu_select.wav");
        loadSfx("menu_back", "sounds/ui/menu_back.wav");
        loadSfx("menu_confirm", "sounds/ui/menu_confirm.wav");
        loadSfx("notification", "sounds/ui/notification.wav");
        loadSfx("error", "sounds/ui/error.wav");

        // Music
        loadMusic("menu_theme", "music/menu_theme.mp3");
        loadMusic("gameplay_calm", "music/gameplay_calm.mp3");
        loadMusic("gameplay_tension", "music/gameplay_tension.mp3");
        loadMusic("combat", "music/combat.mp3");
        loadMusic("blood_moon", "music/blood_moon.mp3");
        loadMusic("game_over", "music/game_over.mp3");
    }

    
private void loadSfx(String name, String path) {
    try {
        URL resource = AudioManager.class.getResource("/" + path);

        if (resource == null) {
            System.err.println(
                "[AUDIO WARNING] Missing sound: /" + path
            );
            return;
        }

        AudioClip clip = new AudioClip(resource.toExternalForm());
        soundEffects.put(name, clip);

    } catch (Exception e) {
        System.err.println(
            "[AUDIO ERROR] Failed to load " + name + ": " + e.getMessage()
        );
    }
}

private void loadMusic(String name, String path) {
    try {
        URL resource = AudioManager.class.getResource("/" + path);

        if (resource == null) {
            System.err.println(
                "[MUSIC WARNING] Missing music: /" + path
            );
            return;
        }

        Media media = new Media(resource.toExternalForm());

        MediaPlayer player = new MediaPlayer(media);
        player.setCycleCount(MediaPlayer.INDEFINITE);
        player.setVolume(musicVolume * masterVolume);

        musicTracks.put(name, player);

    } catch (Exception e) {
        System.err.println(
            "[MUSIC ERROR] Failed to load " + name + ": " + e.getMessage()
        );
    }
}


    // ===================== PLAYBACK =====================
    public void playSfx(String name) {
        playSfx(name, 1.0, 1.0, false);
    }

    public void playSfx(String name, double volume, double pitch, boolean loop) {
        AudioClip clip = soundEffects.get(name);
        if (clip != null) {
            clip.setVolume(volume * sfxVolume * masterVolume);
            clip.setRate(pitch);
            clip.setCycleCount(loop ? AudioClip.INDEFINITE : 1);
            clip.play();
        }
    }

    public void playSfx3D(String name, double x, double y, double z, double listenerX, double listenerY, double listenerZ) {
        AudioClip clip = soundEffects.get(name);
        if (clip != null) {
            double dx = x - listenerX;
            double dy = y - listenerY;
            double dz = z - listenerZ;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double maxDist = 1000;
            double volume = Math.max(0, 1 - dist / maxDist);
            // Pan based on angle
            double angle = Math.atan2(dx, dz);
            double pan = Math.sin(angle); // -1 to 1
            clip.setVolume(volume * sfxVolume * masterVolume);
            clip.setPan(pan);
            clip.play();
        }
    }

    public void playMusic(String name) {
        if (currentMusic != null) {
            currentMusic.stop();
        }
        MediaPlayer player = musicTracks.get(name);
        if (player != null) {
            currentMusic = player;
            player.setVolume(musicVolume * masterVolume);
            player.play();
        }
    }

    public void crossfadeMusic(String name, double duration) {
        MediaPlayer newPlayer = musicTracks.get(name);
        if (newPlayer == null || newPlayer == currentMusic) return;

        if (currentMusic != null) {
            // Fade out current
            new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(javafx.util.Duration.seconds(duration),
                            new javafx.animation.KeyValue(currentMusic.volumeProperty(), 0))
            ).play();
        }
        newPlayer.setVolume(0);
        newPlayer.play();
        // Fade in new
        new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(duration),
                        new javafx.animation.KeyValue(newPlayer.volumeProperty(), musicVolume * masterVolume))
        ).play();
        currentMusic = newPlayer;
    }

    public void stopMusic() {
        if (currentMusic != null) {
            currentMusic.stop();
            currentMusic = null;
        }
    }

    public void pauseMusic() {
        if (currentMusic != null) currentMusic.pause();
    }

    public void resumeMusic() {
        if (currentMusic != null) currentMusic.play();
    }

    // ===================== VOLUME CONTROL =====================
    public void setVolume(SoundCategory category, double volume) {
        volume = Math.max(0, Math.min(1, volume));
        switch (category) {
            case MASTER -> {
                masterVolume = volume;
                updateAllVolumes();
            }
            case SFX -> sfxVolume = volume;
            case MUSIC -> {
                musicVolume = volume;
                if (currentMusic != null) currentMusic.setVolume(musicVolume * masterVolume);
            }
        }
    }

    public double getVolume(SoundCategory category) {
        return switch (category) {
            case MASTER -> masterVolume;
            case SFX -> sfxVolume;
            case MUSIC -> musicVolume;
            default -> 1.0;
        };
    }

    private void updateAllVolumes() {
        if (currentMusic != null) currentMusic.setVolume(musicVolume * masterVolume);
        // SFX volumes applied at play time
    }

    // ===================== CONVENIENCE METHODS =====================
    public void playWeaponShot(WeaponType weapon) {
        switch (weapon) {
            case PISTOL -> playSfx("pistol_shot", 0.8, 0.9 + rng.nextDouble() * 0.2, false);
            case ASSAULT_RIFLE -> playSfx("rifle_shot", 0.9, 0.95 + rng.nextDouble() * 0.1, false);
            case SHOTGUN -> playSfx("shotgun_shot", 1.0, 0.9 + rng.nextDouble() * 0.15, false);
        }
    }

    public void playReload(WeaponType weapon) {
        switch (weapon) {
            case PISTOL -> playSfx("reload_pistol", 0.6, 1.0, false);
            case ASSAULT_RIFLE -> playSfx("reload_rifle", 0.6, 1.0, false);
            case SHOTGUN -> playSfx("reload_shotgun", 0.6, 1.0, false);
        }
    }

    public void playZombieSound(int zombieType, String action) {
        // zombieType: 0=walker, 1=runner, 2=tank, 3=smoker
        String prefix = switch (zombieType) {
            case 0 -> "zombie_walker";
            case 1 -> "zombie_runner";
            case 2 -> "zombie_tank";
            case 3 -> "zombie_smoker";
            default -> "zombie_walker";
        };
        playSfx(prefix + "_" + action, 0.7, 0.9 + rng.nextDouble() * 0.2, false);
    }

    public void playFootstep(String surface, boolean sprinting) {
        String sound = "footstep_" + surface;
        double pitch = sprinting ? 1.3 : 1.0;
        playSfx(sound, 0.4, pitch, false);
    }

    public void playBloodMoonStart() {
        playSfx("thunder", 0.8, 0.8, false);
        crossfadeMusic("blood_moon", 3.0);
    }

    public void playBloodMoonEnd() {
        crossfadeMusic("gameplay_calm", 5.0);
    }

    public void playGameOver() {
        stopMusic();
        playSfx("player_death", 1.0, 1.0, false);
        playMusic("game_over");
    }
}