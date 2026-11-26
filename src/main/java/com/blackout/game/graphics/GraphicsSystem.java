package com.blackout.game.graphics;

import com.blackout.game.core.Constants;
import com.blackout.game.core.Gfx;
import com.blackout.game.systems.WeatherSystem;
import com.blackout.game.systems.DayNightSystem;
import com.blackout.game.entities.Enemy;
import com.blackout.game.environment.Building;
import com.blackout.game.environment.Car;
import com.blackout.game.environment.TreeFactory;

import java.util.List;

import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.paint.Color;

/**
 * Master graphics system that integrates all visual enhancements:
 * - Shadow mapping
 * - Volumetric fog
 * - Sky atmosphere
 * - Blood moon effects
 * - Enhanced fire
 * - Muzzle flash
 * - Impact effects
 */
public final class GraphicsSystem {

    private final Group world;
    private final Scene scene;

    // Sub-systems
    private final ShadowMap shadowMap;
    private final VolumetricFog volumetricFog;
    private final SkyAtmosphere skyAtmosphere;
    private final BloodMoonEffects bloodMoonEffects;
    private final EnhancedFire enhancedFire;
    private final MuzzleFlashEnhancement muzzleFlash;
    private final ImpactEffects impactEffects;

    // Settings
    private boolean shadowsEnabled = true;
    private boolean fogEnabled = true;
    private boolean skyEnabled = true;
    private boolean bloodMoonEnabled = true;
    private boolean fireEnabled = true;
    private boolean muzzleFlashEnabled = true;
    private boolean impactsEnabled = true;

    // Quality settings
    public enum Quality { LOW, MEDIUM, HIGH, ULTRA }
    private Quality quality = Quality.HIGH;

    public GraphicsSystem(Group world, Scene scene,
            List<Building> buildings, List<Enemy> enemies,
            List<Car> cars, List<TreeFactory.Tree> trees,
            WeatherSystem weather, DayNightSystem dayNight) {
        this.world = world;
        this.scene = scene;

        // Initialize sub-systems
        this.shadowMap = new ShadowMap(world, Constants.RNG, buildings, enemies, cars, trees);
        this.volumetricFog = new VolumetricFog(world);
        this.skyAtmosphere = new SkyAtmosphere(world, dayNight);
        this.bloodMoonEffects = new BloodMoonEffects(world);
        this.enhancedFire = new EnhancedFire(world);
        this.muzzleFlash = new MuzzleFlashEnhancement(world);
        this.impactEffects = new ImpactEffects(world);
    }

    public void update(double dt, double playerX, double playerY, double playerZ,
            double cameraX, double cameraY, double cameraZ,
            double sunX, double sunY, double sunZ,
            List<Enemy> enemies,
            WeatherSystem weather, DayNightSystem dayNight) {

        // Update shadow map
        if (shadowsEnabled) {
            shadowMap.update(dt, playerX, playerZ, sunX, sunY, sunZ);
        }

        // Update volumetric fog
        if (fogEnabled) {
            volumetricFog.update(dt, playerX, playerZ, cameraY, weather, dayNight);
        }

        // Update sky atmosphere
        if (skyEnabled) {
            skyAtmosphere.update(dt, cameraX, cameraY, cameraZ);
        }

        // Update blood moon effects
        if (bloodMoonEnabled) {
            bloodMoonEffects.update(dt, playerX, playerY, playerZ, enemies, dayNight != null && dayNight.isBloodMoon());
            bloodMoonEffects.applyToScene(scene);
        }

        // Update enhanced fire
        if (fireEnabled) {
            enhancedFire.update(dt);
        }

        // Update muzzle flashes
        if (muzzleFlashEnabled) {
            muzzleFlash.update(dt);
        }

        // Update impact effects
        if (impactsEnabled) {
            impactEffects.update(dt);
        }
    }

    // ===================== PUBLIC API =====================

    public void triggerMuzzleFlash(com.blackout.game.entities.WeaponType weapon,
            double x, double y, double z, double yaw, double pitch) {
        if (muzzleFlashEnabled) {
            muzzleFlash.triggerFlash(weapon, x, y, z, yaw, pitch, null);
        }
    }

    public void createImpact(ImpactEffects.ImpactType type, double x, double y, double z,
            double nx, double ny, double nz, double[] rayDir) {
        if (impactsEnabled) {
            impactEffects.createImpact(type, x, y, z, nx, ny, nz, rayDir);
        }
    }

    public EnhancedFire.FireInstance createFire(double x, double y, double z, double scale, boolean smoke) {
        if (fireEnabled) {
            return enhancedFire.createFire(x, y, z, scale, smoke);
        }
        return null;
    }

    // ===================== QUALITY SETTINGS =====================

    public void setQuality(Quality quality) {
        this.quality = quality;
        applyQualitySettings();
    }

    public Quality getQuality() {
        return quality;
    }

    private void applyQualitySettings() {
        switch (quality) {
            case LOW -> {
                shadowsEnabled = false;
                fogEnabled = false;
                skyEnabled = true; // Keep sky but simpler
                bloodMoonEnabled = true;
                fireEnabled = true;
                muzzleFlashEnabled = true;
                impactsEnabled = false;
            }
            case MEDIUM -> {
                shadowsEnabled = true;
                fogEnabled = true;
                skyEnabled = true;
                bloodMoonEnabled = true;
                fireEnabled = true;
                muzzleFlashEnabled = true;
                impactsEnabled = true;
            }
            case HIGH -> {
                shadowsEnabled = true;
                fogEnabled = true;
                skyEnabled = true;
                bloodMoonEnabled = true;
                fireEnabled = true;
                muzzleFlashEnabled = true;
                impactsEnabled = true;
            }
            case ULTRA -> {
                shadowsEnabled = true;
                fogEnabled = true;
                skyEnabled = true;
                bloodMoonEnabled = true;
                fireEnabled = true;
                muzzleFlashEnabled = true;
                impactsEnabled = true;
            }
        }
    }

    public void setShadowsEnabled(boolean enabled) {
        shadowsEnabled = enabled;
        shadowMap.setEnabled(enabled);
    }

    public void setFogEnabled(boolean enabled) {
        fogEnabled = enabled;
        volumetricFog.setEnabled(enabled);
    }

    public void setSkyEnabled(boolean enabled) {
        skyEnabled = enabled;
    }

    public void setBloodMoonEnabled(boolean enabled) {
        bloodMoonEnabled = enabled;
        bloodMoonEffects.setActive(enabled && (skyAtmosphere.isBloodMoon()));
    }

    public void setFireEnabled(boolean enabled) {
        fireEnabled = enabled;
    }

    public void setMuzzleFlashEnabled(boolean enabled) {
        muzzleFlashEnabled = enabled;
    }

    public void setImpactsEnabled(boolean enabled) {
        impactsEnabled = enabled;
    }

    // ===================== DISPOSAL =====================

    public void dispose() {
        shadowMap.clear();
        volumetricFog.dispose();
        bloodMoonEffects.dispose();
        enhancedFire.update(0); // Clean up
        muzzleFlash.clear();
        impactEffects.clear();
    }
}