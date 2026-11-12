package com.blackout.game;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.effect.Bloom;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Sphere;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import javafx.util.Duration;

public class Main extends javafx.application.Application {

    // =====================================
    // TUNING SWITCHES
    // =====================================
    private static final boolean POST_FX = true;          // bloom + colour grading (turn off if FPS is low)
    private static final boolean WAVE_RESUPPLY = true;    // ammo + a little health after each wave
    private static final int MAX_PARTICLES = 320;
    private static final double GROUND_Y = 110;

    private static final Random RNG = new Random();

    // zombie types
    private static final int Z_WALKER = 0;
    private static final int Z_RUNNER = 1;
    private static final int Z_BRUTE = 2;

    // gun hold position (inside the soldier's upper body)
    private static final double GUN_X = 8;
    private static final double GUN_Y = -11;
    private static final double GUN_Z = 40;

    // =====================================
    // PLAYER & CAMERA STATE
    // =====================================
    private Group player;
    private Group weaponModel;
    private Rig playerRig;
    private final Rotate playerFacing = new Rotate(0, Rotate.Y_AXIS);
    private final Rotate weaponPitch = new Rotate(0, Rotate.X_AXIS);
    private Group muzzleFlash;
    private PointLight muzzleLight;
    private double muzzleLocalY = -9;
    private double muzzleLocalZ = 34;

    private boolean isAiming = false;
    private boolean sprintingNow = false;
    private static final double JUMP_FORCE = 380;
    private static final double GRAVITY = 950;
    private static final double PLAYER_GROUND_Y = 55;
    private double jumpVelocity = 0;
    private boolean isGrounded = true;
    private double playerYaw = 0;
    private double walkPhase = 0;
    private double moveBlend = 0;
    private double aimBlend = 0;
    private double weaponKick = 0;
    private double stepTimer = 0;
    private double strafeInput = 0;

    private double animationTime = 0;
    private double muzzleFlashTimer = 0;
    private double worldTime = 0;
    private double shake = 0;
    private double damageFlash = 0;
    private double currentFov = 32;
    private double camRoll = 0;

    private double health = 100;
    private double stamina = 100;

    private static final double WALK_SPEED = 190;
    private static final double SPRINT_SPEED = 320;
    private static final double WORLD_BOUNDARY = 550;
    private static final double PLAYER_RADIUS = 23;

    private double smoothedCamX = 0;
    private double smoothedCamY = 0;
    private double smoothedCamZ = 0;
    private double smoothedYaw = 0;
    private double smoothedPitch = -10.5;

    private boolean firstPerson = false;
    private Button viewButton;

    // =====================================
    // WEAPON SETTINGS
    // =====================================
    private enum WeaponType {
        PISTOL("PISTOL", 12, 48, 52, 1200, 250_000_000L, 1, 0.4),
        ASSAULT_RIFLE("ASSAULT RIFLE", 30, 120, 30, 1200, 95_000_000L, 1, 1.0),
        SHOTGUN("SHOTGUN", 6, 36, 20, 550, 750_000_000L, 8, 6.5);

        final String displayName;
        final int magazineSize;
        final int startingReserve;
        final double damage;
        final double range;
        final long cooldown;
        final int pellets;
        final double spread;

        WeaponType(String displayName, int magazineSize, int startingReserve, double damage, double range, long cooldown, int pellets, double spread) {
            this.displayName = displayName;
            this.magazineSize = magazineSize;
            this.startingReserve = startingReserve;
            this.damage = damage;
            this.range = range;
            this.cooldown = cooldown;
            this.pellets = pellets;
            this.spread = spread;
        }
    }

    private WeaponType currentWeapon = WeaponType.PISTOL;
    private final EnumMap<WeaponType, Integer> magazineAmmo = new EnumMap<>(WeaponType.class);
    private final EnumMap<WeaponType, Integer> reserveAmmo = new EnumMap<>(WeaponType.class);
    private int kills = 0;
    private long lastShotTime = 0;
    private double cameraRecoil = 0;
    private final double recoilRecovery = 12;
    private boolean firing = false;

    private Text hitMarker;
    private double hitMarkerTimer = 0;

    private void initializeWeaponAmmo() {
        for (WeaponType weapon : WeaponType.values()) {
            magazineAmmo.put(weapon, weapon.magazineSize);
            reserveAmmo.put(weapon, weapon.startingReserve);
        }
        currentWeapon = WeaponType.PISTOL;
    }

    private double kickFor(WeaponType w) {
        switch (w) {
            case PISTOL:
                return 0.8;
            case ASSAULT_RIFLE:
                return 0.5;
            default:
                return 1.0;
        }
    }

    private double[] armPose(WeaponType w) {
        // rightShoulder, rightElbow, rightYaw, leftShoulder, leftElbow, leftYaw
        switch (w) {
            case PISTOL:
                return new double[]{55, 38, -12, 55, 38, 32};
            case ASSAULT_RIFLE:
                return new double[]{38, 58, -8, 78, 6, 38};
            default:
                return new double[]{40, 55, -8, 76, 8, 36};
        }
    }

    // =====================================
    // CAMERA SETTINGS
    // =====================================
    private PerspectiveCamera camera;
    private double cameraYaw = 0;
    private double cameraPitch = -10.5;
    private double mouseX;
    private double mouseY;
    private static final double CAMERA_DISTANCE = 600;
    private Sphere sky;

    // =====================================
    // INPUT & COLLISION
    // =====================================
    private final Set<KeyCode> keys = new HashSet<>();
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Obstacle> obstacles = new ArrayList<>();
    private final List<Obstacle> sightBlockers = new ArrayList<>();

    // =====================================
    // WAVE SYSTEM
    // =====================================
    private int currentWave = 0;
    private double waveCountdown = 0;
    private boolean waitingForNextWave = false;
    private static final double WAVE_BREAK_DURATION = 5;

    // =====================================
    // GAME STATE & HUD
    // =====================================
    private boolean gameOver = false;
    private boolean paused = false;
    private StackPane gameOverOverlay;
    private StackPane pauseOverlay;
    private Text finalStatsText;

    private ProgressBar healthBar;
    private ProgressBar staminaBar;
    private Text healthText;
    private Text staminaText;
    private Text enemyText;
    private Text statusText;
    private Text ammoText;
    private Text killsText;
    private Text waveText;
    private Text weaponText;

    private Text banner;
    private FadeTransition bannerFade;
    private Region redFlash;
    private final Rectangle[] ch = new Rectangle[4];
    private double crossGap = 8;

    private Group world;

    // =====================================
    // LIVING-WORLD STATE (effects, lights, props)
    // =====================================
    private PhongMaterial bloodMat, sparkMat, brassMat, dustMat, emberMat, tracerMat, moteMat;
    private PhongMaterial matConcrete, matWood, matBurlap, matRust, matMetalDark, matGlassDark, matGlassLit, matAsphalt;

    private final List<Particle> particles = new ArrayList<>();
    private final List<Tracer> tracers = new ArrayList<>();
    private final List<PointLight> lampLights = new ArrayList<>();
    private final List<Double> lampPhase = new ArrayList<>();
    private final List<Fire> fires = new ArrayList<>();
    private final List<Sphere> beacons = new ArrayList<>();
    private final List<Rotate> beamRotates = new ArrayList<>();
    private final List<Box> motes = new ArrayList<>();
    private final List<double[]> moteVel = new ArrayList<>();

    private double hitDist;
    private boolean hitHead;

    @Override
    public void start(Stage stage) {
        initMaterials();
        initializeWeaponAmmo();
        world = new Group();

        createSky(world);
        createEnvironment(world);
        createPlayer(world);
        createLighting(world);
        createCamera();
        createMotes(world);
        createEnemies();

        StackPane root = new StackPane();
        root.setFocusTraversable(true);
        root.setStyle("-fx-background-color: black;");

        SubScene gameScene = new SubScene(world, 1280, 720, true, SceneAntialiasing.BALANCED);
        gameScene.setFill(Color.rgb(8, 11, 15));
        gameScene.setCamera(camera);
        gameScene.widthProperty().bind(root.widthProperty());
        gameScene.heightProperty().bind(root.heightProperty());

        if (POST_FX) {
            ColorAdjust grade = new ColorAdjust(0.0, 0.12, 0.03, 0.14);
            Bloom bloom = new Bloom(0.78);
            bloom.setInput(grade);
            gameScene.setEffect(bloom);
        }

        VBox hud = createHUD();
        Group crosshair = createCrosshair();

        viewButton = new Button("FPP");

        viewButton.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        viewButton.setPrefWidth(100);
        viewButton.setPrefHeight(38);

        viewButton.setStyle(
                "-fx-background-color: #d99b32;"
                + "-fx-text-fill: white;"
                + "-fx-background-radius: 6;"
                + "-fx-cursor: hand;"
        );

        viewButton.setOnAction(e -> toggleCameraMode());

        hitMarker = new Text("×");
        hitMarker.setFont(Font.font("Arial", FontWeight.BOLD, 36));
        hitMarker.setFill(Color.WHITE);
        hitMarker.setMouseTransparent(true);
        hitMarker.setVisible(false);

        Region vignette = new Region();
        vignette.setMouseTransparent(true);
        vignette.setStyle("-fx-background-color: radial-gradient(center 50% 50%, radius 75%, rgba(0,0,0,0) 45%, rgba(0,0,0,0.78) 100%);");

        redFlash = new Region();
        redFlash.setMouseTransparent(true);
        redFlash.setOpacity(0);
        redFlash.setStyle("-fx-background-color: radial-gradient(center 50% 50%, radius 80%, rgba(140,0,0,0) 30%, rgba(170,0,0,0.85) 100%);");

        banner = new Text("");
        banner.setFont(Font.font("Arial", FontWeight.BOLD, 54));
        banner.setFill(Color.GOLD);
        banner.setOpacity(0);
        banner.setMouseTransparent(true);
        banner.setEffect(new DropShadow(18, Color.BLACK));

        Region introFade = new Region();
        introFade.setStyle("-fx-background-color: black;");
        introFade.setMouseTransparent(true);

        root.getChildren().addAll(gameScene, vignette, redFlash, hud, crosshair, hitMarker, banner, introFade, viewButton);

        gameOverOverlay = createGameOverOverlay();
        root.getChildren().add(gameOverOverlay);
        pauseOverlay = createPauseOverlay();
        root.getChildren().add(pauseOverlay);

        StackPane.setAlignment(hitMarker, Pos.CENTER);
        StackPane.setAlignment(hud, Pos.TOP_LEFT);
        StackPane.setAlignment(crosshair, Pos.CENTER);
        StackPane.setAlignment(banner, Pos.TOP_CENTER);
        StackPane.setMargin(banner, new Insets(110, 0, 0, 0));
        StackPane.setAlignment(viewButton, Pos.TOP_RIGHT);
        StackPane.setMargin(viewButton, new Insets(20, 25, 0, 0));

        Scene scene = new Scene(root, 1280, 720);

        setupKeyboard(scene);
        setupMouse(gameScene);

        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                togglePause();
                event.consume();
            }
        });

        stage.setTitle("BLACKOUT: The Last Outpost [Cinematic Edition]");
        stage.setScene(scene);
        stage.setFullScreen(true);
        stage.setFullScreenExitHint("Press ESC to exit full screen");
        stage.setMinWidth(1280);
        stage.setMinHeight(720);
        stage.show();

        updateCamera(1.0);

        FadeTransition intro = new FadeTransition(Duration.seconds(2.8), introFade);
        intro.setFromValue(1);
        intro.setToValue(0);
        intro.setOnFinished(e -> introFade.setVisible(false));
        intro.play();

        showBanner("WAVE 1", Color.GOLD);
        root.requestFocus();
        startGameLoop();
    }

    // =====================================
    // STATIC BUILD HELPERS
    // =====================================
    private static final Map<Color, PhongMaterial> GLOW_CACHE = new HashMap<>();

    private static PhongMaterial mat(Color diffuse, Color specular, double power) {
        PhongMaterial m = new PhongMaterial();
        m.setDiffuseColor(diffuse);
        m.setSpecularColor(specular);
        m.setSpecularPower(power);
        return m;
    }

    private PhongMaterial advancedMaterial(Color diffuse, Color specular, double power) {
        return mat(diffuse, specular, power);
    }

    private static PhongMaterial glow(Color c) {
        return GLOW_CACHE.computeIfAbsent(c, col -> {
            PhongMaterial m = new PhongMaterial(Color.BLACK);
            WritableImage img = new WritableImage(2, 2);
            PixelWriter pw = img.getPixelWriter();
            for (int y = 0; y < 2; y++) {
                for (int x = 0; x < 2; x++) {
                    pw.setColor(x, y, col);
                }
            }
            m.setSelfIlluminationMap(img);
            m.setSpecularColor(Color.BLACK);
            return m;
        });
    }

    private static PhongMaterial texMat(Image img, Color spec, double power) {
        PhongMaterial m = new PhongMaterial(Color.WHITE);
        m.setDiffuseMap(img);
        m.setSpecularColor(spec);
        m.setSpecularPower(power);
        return m;
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static Image noiseTexture(int size, Color base, double variation, double speckle, Color speckleColor, int stripe) {
        WritableImage img = new WritableImage(size, size);
        PixelWriter pw = img.getPixelWriter();
        int cell = 16;
        int n = size / cell + 2;
        double[][] blot = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                blot[i][j] = RNG.nextDouble();
            }
        }
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double fx = x / (double) cell, fy = y / (double) cell;
                int ix = (int) fx, iy = (int) fy;
                double tx = fx - ix, ty = fy - iy;
                double b = blot[ix][iy] * (1 - tx) * (1 - ty) + blot[ix + 1][iy] * tx * (1 - ty)
                        + blot[ix][iy + 1] * (1 - tx) * ty + blot[ix + 1][iy + 1] * tx * ty;
                double k = 1 + (b - 0.5) * variation * 1.6 + (RNG.nextDouble() - 0.5) * variation;
                if (stripe > 0 && y % stripe < 2) {
                    k *= 0.55;
                }
                double r = base.getRed() * k, g = base.getGreen() * k, bl = base.getBlue() * k;
                if (speckle > 0 && RNG.nextDouble() < speckle) {
                    r = speckleColor.getRed();
                    g = speckleColor.getGreen();
                    bl = speckleColor.getBlue();
                }
                pw.setColor(x, y, Color.color(clamp01(r), clamp01(g), clamp01(bl)));
            }
        }
        return img;
    }

    private static Box box(double w, double h, double d, PhongMaterial m, double x, double y, double z) {
        Box b = new Box(w, h, d);
        b.setMaterial(m);
        b.setTranslateX(x);
        b.setTranslateY(y);
        b.setTranslateZ(z);
        return b;
    }

    /**
     * Cylinder lying along the Z axis.
     */
    private static Cylinder cyl(double r, double len, PhongMaterial m, double x, double y, double z) {
        Cylinder c = new Cylinder(r, len);
        c.setMaterial(m);
        c.setRotationAxis(Rotate.X_AXIS);
        c.setRotate(90);
        c.setTranslateX(x);
        c.setTranslateY(y);
        c.setTranslateZ(z);
        return c;
    }

    /**
     * Cylinder lying along the X axis.
     */
    private static Cylinder cylX(double r, double len, PhongMaterial m, double x, double y, double z) {
        Cylinder c = new Cylinder(r, len);
        c.setMaterial(m);
        c.setRotationAxis(Rotate.Z_AXIS);
        c.setRotate(90);
        c.setTranslateX(x);
        c.setTranslateY(y);
        c.setTranslateZ(z);
        return c;
    }

    private static Sphere sph(double r, PhongMaterial m, double x, double y, double z, double sx, double sy, double sz) {
        Sphere s = new Sphere(r, 24);
        s.setMaterial(m);
        s.setTranslateX(x);
        s.setTranslateY(y);
        s.setTranslateZ(z);
        s.setScaleX(sx);
        s.setScaleY(sy);
        s.setScaleZ(sz);
        return s;
    }

    private void initMaterials() {
        bloodMat = mat(Color.rgb(120, 8, 8), Color.rgb(220, 60, 60), 60);
        sparkMat = glow(Color.rgb(255, 220, 120));
        brassMat = mat(Color.rgb(200, 160, 60), Color.WHITE, 90);
        dustMat = mat(Color.rgb(110, 105, 95, 0.55), Color.BLACK, 1);
        emberMat = glow(Color.rgb(255, 120, 30));
        tracerMat = glow(Color.rgb(255, 235, 160));
        moteMat = glow(Color.rgb(95, 105, 125));

        matConcrete = texMat(noiseTexture(256, Color.rgb(112, 116, 110), 0.5, 0.02, Color.rgb(50, 52, 50), 0), Color.rgb(50, 55, 52), 24);
        matWood = texMat(noiseTexture(128, Color.rgb(120, 84, 48), 0.45, 0.0, Color.BLACK, 12), Color.rgb(70, 50, 30), 16);
        matBurlap = texMat(noiseTexture(128, Color.rgb(132, 120, 86), 0.6, 0.0, Color.BLACK, 0), Color.rgb(30, 28, 20), 6);
        matRust = texMat(noiseTexture(128, Color.rgb(98, 68, 48), 0.65, 0.06, Color.rgb(44, 30, 22), 0), Color.rgb(70, 55, 45), 20);
        matAsphalt = texMat(noiseTexture(256, Color.rgb(46, 48, 48), 0.35, 0.05, Color.rgb(90, 90, 90), 0), Color.rgb(40, 40, 44), 30);
        matMetalDark = mat(Color.rgb(48, 56, 52), Color.rgb(120, 130, 125), 56);
        matGlassDark = mat(Color.rgb(12, 18, 28), Color.rgb(150, 170, 210), 110);
        matGlassLit = glow(Color.rgb(255, 205, 110));
    }

    // =====================================
    // SKY
    // =====================================
    private void createSky(Group world) {
        int w = 1024, h = 512;
        WritableImage img = new WritableImage(w, h);
        PixelWriter pw = img.getPixelWriter();
        double horizonV = 0.52;
        for (int y = 0; y < h; y++) {
            double v = y / (double) (h - 1);
            for (int x = 0; x < w; x++) {
                double r, g, b;
                if (v < horizonV) {
                    double t = Math.pow(v / horizonV, 1.7);
                    r = 3 + (30 - 3) * t;
                    g = 5 + (44 - 5) * t;
                    b = 14 + (70 - 14) * t;
                    double cloud = Math.sin(x * 0.021 + y * 0.05) * Math.sin(x * 0.011 - y * 0.031) + Math.sin(x * 0.047 + 2) * 0.4;
                    if (cloud > 0.2) {
                        double c = (cloud - 0.2) * 14 * (1 - t * 0.5);
                        r += c;
                        g += c;
                        b += c * 1.3;
                    }
                } else {
                    double t = Math.min(1, (v - horizonV) / 0.08);
                    r = 30 + (9 - 30) * t;
                    g = 44 + (12 - 44) * t;
                    b = 70 + (12 - 70) * t;
                }
                pw.setColor(x, y, Color.rgb((int) Math.min(255, r), (int) Math.min(255, g), (int) Math.min(255, b)));
            }
        }
        for (int i = 0; i < 1500; i++) {
            int sx = RNG.nextInt(w), sy = RNG.nextInt((int) (h * 0.46));
            int br = 110 + RNG.nextInt(145);
            pw.setColor(sx, sy, Color.rgb(br, br, Math.min(255, br + 20)));
        }
        int mx = (int) (w * 0.62), my = (int) (h * 0.2);
        for (int y = my - 80; y <= my + 80; y++) {
            for (int x = mx - 80; x <= mx + 80; x++) {
                if (x < 0 || y < 0 || x >= w || y >= h) {
                    continue;
                }
                double d = Math.sqrt((x - mx) * (x - mx) + (y - my) * (y - my));
                Color old = img.getPixelReader().getColor(x, y);
                if (d < 22) {
                    pw.setColor(x, y, Color.rgb(235, 240, 255));
                } else if (d < 80) {
                    double a = Math.pow(1 - (d - 22) / 58.0, 2.2) * 0.55;
                    pw.setColor(x, y, Color.color(clamp01(old.getRed() + a * 0.6), clamp01(old.getGreen() + a * 0.7), clamp01(old.getBlue() + a * 0.9)));
                }
            }
        }
        PhongMaterial m = new PhongMaterial(Color.BLACK);
        m.setSelfIlluminationMap(img);
        m.setSpecularColor(Color.BLACK);
        sky = new Sphere(3000, 48);
        sky.setMaterial(m);
        sky.setCullFace(CullFace.NONE);
        sky.setMouseTransparent(true);
        world.getChildren().add(sky);
    }

    // =====================================
    // ENVIRONMENT
    // =====================================
    private void createEnvironment(Group world) {
        Box voidPlane = new Box(9000, 2, 9000);
        voidPlane.setTranslateY(112);
        voidPlane.setMaterial(mat(Color.rgb(9, 12, 12), Color.BLACK, 1));
        world.getChildren().add(voidPlane);

        Box ground = new Box(1200, 20, 1200);
        ground.setTranslateY(120);
        ground.setMaterial(texMat(noiseTexture(512, Color.rgb(46, 52, 40), 0.5, 0.035, Color.rgb(96, 84, 60), 0), Color.rgb(25, 30, 25), 8));
        world.getChildren().add(ground);

        Box road = new Box(180, 3, 1200);
        road.setTranslateY(108);
        road.setMaterial(matAsphalt);
        world.getChildren().add(road);

        for (double sx : new double[]{-93, 93}) {
            world.getChildren().add(box(6, 5, 1200, matConcrete, sx, 107, 0));
        }

        PhongMaterial paint = mat(Color.rgb(210, 195, 130), Color.WHITE, 64);
        for (int z = -520; z <= 520; z += 100) {
            world.getChildren().add(box(5, 2, 38, paint, 0, 105, z));
        }

        PhongMaterial puddle = mat(Color.rgb(8, 12, 18), Color.WHITE, 128);
        double[][] pud = {{-40, -180, 44, 26}, {35, 60, 36, 22}, {-30, 330, 50, 28}, {50, -380, 40, 24}};
        for (double[] p : pud) {
            world.getChildren().add(box(p[2], 0.8, p[3], puddle, p[0], 106.1, p[1]));
        }

        // Military buildings
        addBuilding(world, -180, 70, 100, 180, 140, 180);
        addBuilding(world, 180, 70, 180, 220, 140, 180);
        addBuilding(world, 0, 70, 300, 120, 140, 100);
        addBuilding(world, -380, 70, -250, 150, 140, 120);
        addBuilding(world, 350, 70, -300, 180, 140, 150);

        // Perimeter fences
        createFence(world, -500, 0, 1000, true);
        createFence(world, 500, 0, 1000, true);
        createFence(world, -500, 0, 1000, false);
        createFence(world, 500, 0, 1000, false);

        addBarrier(world, -300, 100, 120);
        addBarrier(world, 300, 100, 120);
        addBarrier(world, -300, -100, 120);
        addBarrier(world, 300, -100, 120);

        addSandbagStack(world, -270, 0, 3);
        addSandbagStack(world, 270, 0, 3);
        addSandbagStack(world, -80, 250, 4);
        addSandbagStack(world, 80, 250, 4);

        addCrate(world, -310, 90, -120);
        addCrate(world, -350, 90, -100);
        addCrate(world, 320, 90, 80);
        addCrate(world, 350, 90, 100);

        addBarrel(world, -250, 90, -300, true);
        addBarrel(world, -220, 90, -300, false);
        addBarrel(world, 280, 90, -350, true);

        addWatchTower(world, -460, 105, -460, 1);
        addWatchTower(world, 460, 105, 460, -1);

        addStreetLight(world, -130, 105, -200, false);
        addStreetLight(world, 130, 105, 200, true);
        addStreetLight(world, -130, 105, 400, false);
        addStreetLight(world, 130, 105, -400, false);

        addWreck(world, -48, -140, 14, false);
        addWreck(world, 50, 390, -20, false);
        addWreck(world, 140, -60, 90, true);

        // Ground clutter
        PhongMaterial rubble = mat(Color.rgb(70, 72, 68), Color.rgb(40, 40, 40), 10);
        PhongMaterial grass = mat(Color.rgb(70, 78, 44), Color.BLACK, 1);
        for (int i = 0; i < 45; i++) {
            double x = (RNG.nextDouble() - 0.5) * 960, z = (RNG.nextDouble() - 0.5) * 960;
            if (Math.abs(x) < 100) {
                continue;
            }
            double s = 3 + RNG.nextDouble() * 7;
            Box rb = box(s, s * 0.6, s * (0.7 + RNG.nextDouble() * 0.6), rubble, x, GROUND_Y - s * 0.3, z);
            rb.setRotationAxis(Rotate.Y_AXIS);
            rb.setRotate(RNG.nextDouble() * 90);
            world.getChildren().add(rb);
        }
        for (int i = 0; i < 70; i++) {
            double x = (RNG.nextDouble() - 0.5) * 980, z = (RNG.nextDouble() - 0.5) * 980;
            if (Math.abs(x) < 100) {
                continue;
            }
            double hh = 6 + RNG.nextDouble() * 10;
            world.getChildren().add(box(1.4, hh, 1.4, grass, x, GROUND_Y - hh / 2, z));
        }

        // Distant ruined skyline for depth
        PhongMaterial skyline = mat(Color.rgb(14, 18, 24), Color.rgb(30, 40, 55), 12);
        for (int i = 0; i < 38; i++) {
            double a = Math.PI * 2 * i / 38 + RNG.nextDouble() * 0.15;
            double rad = 1000 + RNG.nextDouble() * 350;
            double hh = 100 + RNG.nextDouble() * 330;
            world.getChildren().add(box(60 + RNG.nextDouble() * 120, hh, 60 + RNG.nextDouble() * 100, skyline,
                    Math.sin(a) * rad, GROUND_Y - hh / 2, Math.cos(a) * rad));
        }
    }

    private void addObstacle(double x, double z, double width, double depth) {
        obstacles.add(new Obstacle(x, z, width / 2, depth / 2));
    }

    private void addSightBlocker(double x, double z, double width, double depth) {
        sightBlockers.add(new Obstacle(x, z, width / 2, depth / 2));
    }

    private boolean collidesWithObstacle(double x, double z, double radius) {
        for (Obstacle obstacle : obstacles) {
            double closestX = Math.max(obstacle.x - obstacle.halfWidth, Math.min(x, obstacle.x + obstacle.halfWidth));
            double closestZ = Math.max(obstacle.z - obstacle.halfDepth, Math.min(z, obstacle.z + obstacle.halfDepth));
            double dx = x - closestX;
            double dz = z - closestZ;
            if (dx * dx + dz * dz < radius * radius) {
                return true;
            }
        }
        return false;
    }

    private void addBuilding(Group world, double x, double y, double z, double width, double height, double depth) {
        world.getChildren().add(box(width, height, depth, matConcrete, x, y, z));
        world.getChildren().add(box(width + 12, 10, depth + 12, matMetalDark, x, y - height / 2 - 5, z));
        world.getChildren().add(box(24, 10, 20, matMetalDark, x + width * 0.25, y - height / 2 - 15, z));

        double[] rows = {35, 80};
        int colsZ = Math.max(2, (int) ((width - 30) / 46) + 1);
        int colsX = Math.max(2, (int) ((depth - 30) / 46) + 1);
        for (double row : rows) {
            for (int c = 0; c < colsZ; c++) {
                double wx = x - (colsZ - 1) * 23 + c * 46;
                for (int side = -1; side <= 1; side += 2) {
                    PhongMaterial gm = RNG.nextDouble() < 0.22 ? matGlassLit : matGlassDark;
                    world.getChildren().add(box(14, 18, 1.2, gm, wx, row, z + side * (depth / 2 + 0.7)));
                }
            }
            for (int c = 0; c < colsX; c++) {
                double wz = z - (colsX - 1) * 23 + c * 46;
                for (int side = -1; side <= 1; side += 2) {
                    PhongMaterial gm = RNG.nextDouble() < 0.22 ? matGlassLit : matGlassDark;
                    world.getChildren().add(box(1.2, 18, 14, gm, x + side * (width / 2 + 0.7), row, wz));
                }
            }
        }

        // antenna + blinking red beacon
        world.getChildren().add(cylY(1.2, 40, matMetalDark, x - width * 0.3, y - height / 2 - 30, z - depth * 0.2));
        Sphere beacon = sph(2.6, glow(Color.rgb(255, 30, 20)), x - width * 0.3, y - height / 2 - 52, z - depth * 0.2, 1, 1, 1);
        world.getChildren().add(beacon);
        beacons.add(beacon);

        addObstacle(x, z, width + 12, depth + 12);
        addSightBlocker(x, z, width + 12, depth + 12);
    }

    private static Cylinder cylY(double r, double len, PhongMaterial m, double x, double y, double z) {
        Cylinder c = new Cylinder(r, len);
        c.setMaterial(m);
        c.setTranslateX(x);
        c.setTranslateY(y);
        c.setTranslateZ(z);
        return c;
    }

    private void createFence(Group world, double position, double start, double length, boolean vertical) {
        PhongMaterial mesh = new PhongMaterial(Color.rgb(150, 160, 160, 0.10));
        for (double offset = -length / 2; offset <= length / 2; offset += 80) {
            double a = start + offset;
            Box post = box(8, 115, 8, matMetalDark, vertical ? position : a, 52, vertical ? a : position);
            world.getChildren().add(post);
            if (offset + 80 <= length / 2 + 1) {
                double mid = a + 40;
                if (vertical) {
                    world.getChildren().add(box(2, 2.5, 80, matMetalDark, position, 12, mid));
                    world.getChildren().add(box(2, 2.5, 80, matMetalDark, position, 62, mid));
                    world.getChildren().add(box(1, 100, 80, mesh, position, 56, mid));
                } else {
                    world.getChildren().add(box(80, 2.5, 2, matMetalDark, mid, 12, position));
                    world.getChildren().add(box(80, 2.5, 2, matMetalDark, mid, 62, position));
                    world.getChildren().add(box(80, 100, 1, mesh, mid, 56, position));
                }
            }
        }
        if (vertical) {
            addObstacle(position, start, 8, length);
        } else {
            addObstacle(start, position, length, 8);
        }
    }

    private void addBarrier(Group world, double x, double z, double width) {
        world.getChildren().add(box(width, 48, 35, matConcrete, x, 85, z));
        world.getChildren().add(box(width - 6, 1.6, 8, glow(Color.rgb(255, 110, 20)), x, 60.6, z));
        addObstacle(x, z, width, 35);
        addSightBlocker(x, z, width, 35);
    }

    private void addSandbagStack(Group world, double x, double z, int count) {
        for (int i = 0; i < count; i++) {
            Box bag = box(42, 19, 23, matBurlap, x + (i % 2) * 8, 101 - (i / 2) * 17, z);
            bag.setRotationAxis(Rotate.Y_AXIS);
            bag.setRotate((RNG.nextDouble() - 0.5) * 8);
            world.getChildren().add(bag);
        }
        addObstacle(x + 4, z, 58, 23);
    }

    private void addCrate(Group world, double x, double y, double z) {
        world.getChildren().add(box(42, 42, 42, matWood, x, y, z));
        world.getChildren().add(box(44, 3, 44, matMetalDark, x, y - 20, z));
        world.getChildren().add(box(44, 3, 44, matMetalDark, x, y + 20, z));
        addObstacle(x, z, 46, 46);
    }

    private void addBarrel(Group world, double x, double y, double z, boolean burning) {
        world.getChildren().add(cylY(19, 48, matRust, x, y, z));
        world.getChildren().add(cylY(19.6, 3, matMetalDark, x, y - 10, z));
        world.getChildren().add(cylY(19.6, 3, matMetalDark, x, y + 10, z));
        addObstacle(x, z, 42, 42);
        if (burning) {
            Fire f = new Fire(x, y - 24, z);
            world.getChildren().addAll(f.flames, f.light);
            fires.add(f);
        }
    }

    private void addWatchTower(Group world, double x, double y, double z, int beamDir) {
        for (double offsetX : new double[]{-30, 30}) {
            for (double offsetZ : new double[]{-30, 30}) {
                world.getChildren().add(box(9, 170, 9, matMetalDark, x + offsetX, y - 85, z + offsetZ));
            }
        }
        world.getChildren().add(box(90, 12, 90, matMetalDark, x, y - 165, z));
        world.getChildren().add(box(96, 5, 96, matMetalDark, x, y - 215, z));
        for (double o : new double[]{-45, 45}) {
            world.getChildren().add(box(3, 40, 3, matMetalDark, x + o, y - 188, z + o));
            world.getChildren().add(box(3, 40, 3, matMetalDark, x + o, y - 188, z - o));
        }
        // rotating searchlight beam
        Group beam = new Group();
        beam.setTranslateX(x);
        beam.setTranslateY(y - 180);
        beam.setTranslateZ(z);
        Rotate yaw = new Rotate(beamDir > 0 ? 0 : 180, Rotate.Y_AXIS);
        beam.getTransforms().add(yaw);
        Group tilt = new Group();
        tilt.getTransforms().add(new Rotate(-9, Rotate.X_AXIS));
        tilt.getChildren().add(box(10, 10, 360, new PhongMaterial(Color.rgb(255, 245, 200, 0.09)), 0, 0, 190));
        beam.getChildren().add(tilt);
        beam.getChildren().add(sph(5, glow(Color.rgb(255, 245, 210)), 0, 0, 0, 1, 1, 1));
        world.getChildren().add(beam);
        beamRotates.add(yaw);

        addObstacle(x, z, 95, 95);
        addSightBlocker(x, z, 95, 95);
    }

    private void addStreetLight(Group world, double x, double y, double z, boolean broken) {
        world.getChildren().add(box(7, 170, 7, matMetalDark, x, y - 85, z));
        world.getChildren().add(box(35, 10, 25, glow(Color.rgb(255, 232, 160)), x, y - 170, z));
        world.getChildren().add(sph(15, new PhongMaterial(Color.rgb(255, 220, 140, 0.14)), x, y - 168, z, 1, 0.7, 1));

        PointLight lampLight = new PointLight(Color.rgb(255, 235, 170));
        lampLight.setTranslateX(x);
        lampLight.setTranslateY(y - 150);
        lampLight.setTranslateZ(z);
        lampLight.setMaxRange(320);
        world.getChildren().add(lampLight);
        lampLights.add(lampLight);
        lampPhase.add(broken ? -1.0 : RNG.nextDouble() * 6.28);
        addObstacle(x, z, 12, 12);
    }

    private void addWreck(Group world, double x, double z, double rotY, boolean turned) {
        Group car = new Group();
        car.setTranslateX(x);
        car.setTranslateY(GROUND_Y);
        car.setTranslateZ(z);
        car.getTransforms().add(new Rotate(rotY, Rotate.Y_AXIS));
        PhongMaterial tire = mat(Color.rgb(14, 14, 15), Color.rgb(40, 40, 40), 10);
        car.getChildren().addAll(
                box(56, 18, 118, matRust, 0, -17, 0),
                box(50, 16, 58, matRust, 0, -33, -6),
                box(44, 11, 1, matGlassDark, 0, -33, 23.6),
                box(44, 11, 1, matGlassDark, 0, -33, -35.6),
                box(8, 5, 2, glow(Color.rgb(120, 20, 18)), -18, -22, -59.5),
                box(8, 5, 2, glow(Color.rgb(120, 20, 18)), 18, -22, -59.5),
                cylX(10, 8, tire, -29, -9, 38), cylX(10, 8, tire, 29, -9, 38),
                cylX(10, 8, tire, -29, -9, -38), cylX(10, 8, tire, 29, -9, -38));
        world.getChildren().add(car);
        if (turned) {
            addObstacle(x, z, 124, 62);
        } else {
            addObstacle(x, z, 66, 126);
        }
    }

    // =====================================
    // RIG (humanoid skeleton used by player & zombies)
    // =====================================
    private static class Rig {

        final Group root = new Group();
        final Group upper = new Group();
        final Group headGroup = new Group();
        final Group jawGroup = new Group();
        final Rotate lean = new Rotate(0, 0, 12, 0, Rotate.X_AXIS);
        final Rotate twist = new Rotate(0, 0, 12, 0, Rotate.Z_AXIS);
        final Rotate lHipR = new Rotate(0, Rotate.X_AXIS);
        final Rotate rHipR = new Rotate(0, Rotate.X_AXIS);
        final Rotate lKneeR = new Rotate(0, Rotate.X_AXIS);
        final Rotate rKneeR = new Rotate(0, Rotate.X_AXIS);
        final Rotate lShR = new Rotate(0, Rotate.X_AXIS);
        final Rotate rShR = new Rotate(0, Rotate.X_AXIS);
        final Rotate lElbR = new Rotate(0, Rotate.X_AXIS);
        final Rotate rElbR = new Rotate(0, Rotate.X_AXIS);
        final Rotate lShYaw = new Rotate(0, Rotate.Y_AXIS);
        final Rotate rShYaw = new Rotate(0, Rotate.Y_AXIS);
        final Rotate headX = new Rotate(0, 0, -31, 0, Rotate.X_AXIS);
        final Rotate headZ = new Rotate(0, 0, -31, 0, Rotate.Z_AXIS);
        final Rotate jaw = new Rotate(0, 0, -40, 6, Rotate.X_AXIS);

        Rig() {
            upper.getTransforms().addAll(twist, lean);
            headGroup.getTransforms().addAll(headZ, headX);
            jawGroup.getTransforms().add(jaw);
        }
    }

    private static Group limb(Rotate yaw, Rotate pitch, Rotate bend, double x, double y, double w, double d,
            double upperLen, double lowerLen, PhongMaterial mUp, PhongMaterial mLow, PhongMaterial mEnd,
            double endH, double endD, double endZ) {
        Group g = new Group();
        g.setTranslateX(x);
        g.setTranslateY(y);
        if (yaw != null) {
            g.getTransforms().add(yaw);
        }
        g.getTransforms().add(pitch);
        g.getChildren().add(box(w, upperLen, d, mUp, 0, upperLen / 2, 0));
        Group low = new Group();
        low.setTranslateY(upperLen);
        low.getTransforms().add(bend);
        low.getChildren().add(sph(w * 0.5, mUp, 0, 0, 0, 1, 1, 1));
        low.getChildren().add(box(w * 0.88, lowerLen, d * 0.9, mLow, 0, lowerLen / 2, 0));
        low.getChildren().add(box(w * 0.95, endH, endD, mEnd, 0, lowerLen - endH / 2 + 1, endZ));
        g.getChildren().add(low);
        return g;
    }

    private static Rig buildSoldier() {
        Rig r = new Rig();
        PhongMaterial cloth = mat(Color.rgb(52, 64, 56), Color.rgb(50, 60, 55), 8);
        PhongMaterial pants = mat(Color.rgb(44, 52, 48), Color.rgb(40, 48, 44), 8);
        PhongMaterial vest = mat(Color.rgb(30, 34, 37), Color.rgb(90, 98, 105), 28);
        PhongMaterial pad = mat(Color.rgb(22, 24, 26), Color.rgb(110, 115, 120), 40);
        PhongMaterial glove = mat(Color.rgb(16, 17, 19), Color.rgb(70, 70, 75), 30);
        PhongMaterial boot = mat(Color.rgb(24, 21, 18), Color.rgb(80, 75, 70), 40);
        PhongMaterial mask = mat(Color.rgb(26, 28, 30), Color.rgb(50, 50, 55), 10);
        PhongMaterial helmet = mat(Color.rgb(58, 70, 56), Color.rgb(150, 160, 145), 52);
        PhongMaterial lens = glow(Color.rgb(70, 210, 255));
        PhongMaterial accent = glow(Color.rgb(255, 150, 30));

        Group up = r.upper;
        up.getChildren().addAll(
                box(36, 40, 21, cloth, 0, -9, 0),
                box(38, 30, 24, vest, 0, -11, 0.5),
                box(26, 22, 3, pad, 0, -13, 13),
                box(7, 2, 0.6, accent, 0, -21, 14.7),
                box(8, 8, 5, pad, -11, 2, 12),
                box(8, 8, 5, pad, 11, 2, 12),
                box(38, 4, 23, pad, 0, 4, 0),
                box(28, 34, 13, pad, 0, -12, -17),
                cylX(6, 28, cloth, 0, -32, -18),
                box(10, 6, 10, mask, 0, -31, 0),
                sph(8.5, pad, -25, -27, 0, 1, 0.8, 1),
                sph(8.5, pad, 25, -27, 0, 1, 0.8, 1));

        r.headGroup.getChildren().addAll(
                box(21, 24, 21, mask, 0, -45, 0),
                box(19.5, 5, 2.5, lens, 0, -45, 10.8),
                sph(13, helmet, 0, -52, -0.5, 1.12, 0.85, 1.18),
                box(3, 6, 4, pad, -11.5, -46, 2),
                box(3, 6, 4, pad, 11.5, -46, 2),
                box(2, 2, 2, accent, 11.5, -50, 3));
        up.getChildren().add(r.headGroup);

        up.getChildren().add(limb(r.lShYaw, r.lShR, r.lElbR, -24, -27, 11, 12, 24, 22, cloth, cloth, glove, 9, 10, 0));
        up.getChildren().add(limb(r.rShYaw, r.rShR, r.rElbR, 24, -27, 11, 12, 24, 22, cloth, cloth, glove, 9, 10, 0));

        Group lLeg = limb(null, r.lHipR, r.lKneeR, -9, 12, 15, 17, 22, 20, pants, pants, boot, 9, 22, 4);
        Group rLeg = limb(null, r.rHipR, r.rKneeR, 9, 12, 15, 17, 22, 20, pants, pants, boot, 9, 22, 4);
        r.root.getChildren().addAll(box(34, 10, 20, pants, 0, 11, 0), r.upper, lLeg, rLeg);
        return r;
    }

    private static Rig buildZombie(int type) {
        Rig r = new Rig();
        Color skinC = type == Z_RUNNER ? Color.rgb(138, 150, 112)
                : type == Z_BRUTE ? Color.rgb(108, 122, 98)
                        : Color.rgb(122 + RNG.nextInt(16), 140 + RNG.nextInt(12), 106 + RNG.nextInt(10));
        Color[] shirts = {Color.rgb(78, 84, 70), Color.rgb(96, 74, 64), Color.rgb(62, 70, 88), Color.rgb(84, 66, 66), Color.rgb(58, 62, 56)};
        PhongMaterial skin = mat(skinC, Color.rgb(30, 36, 30), 6);
        PhongMaterial shirt = mat(shirts[RNG.nextInt(shirts.length)], Color.rgb(25, 25, 25), 4);
        PhongMaterial pants = mat(Color.rgb(46 + RNG.nextInt(14), 44, 42), Color.rgb(20, 20, 20), 4);
        PhongMaterial blood = mat(Color.rgb(96, 10, 10), Color.rgb(200, 50, 50), 48);
        PhongMaterial flesh = mat(Color.rgb(130, 45, 52), Color.rgb(200, 110, 110), 30);
        PhongMaterial dark = mat(Color.rgb(16, 14, 13), Color.rgb(20, 20, 20), 4);
        PhongMaterial bone = mat(Color.rgb(205, 196, 165), Color.rgb(120, 120, 100), 20);
        PhongMaterial eye = glow(type == Z_RUNNER ? Color.rgb(255, 40, 30) : type == Z_BRUTE ? Color.rgb(255, 140, 30) : Color.rgb(210, 255, 70));

        Group up = r.upper;
        up.getChildren().addAll(
                box(34, 40, 20, shirt, 0, -9, 0),
                box(11, 7, 21, shirt, -9, 11, 0),
                box(9, 5, 21, shirt, 10, 10, 0),
                box(15, 19, 1, blood, -6, -12, 10.4),
                box(9, 11, 1.2, flesh, -6, -11, 10.6),
                box(11, 1.4, 1.6, bone, -6, -15, 11.1),
                box(11, 1.4, 1.6, bone, -6, -11.4, 11.1),
                box(11, 1.4, 1.6, bone, -6, -7.8, 11.1),
                box(8, 6, 1, blood, 10, -22, 10.4),
                box(12, 16, 1, blood, 5, -10, -10.4),
                box(9, 7, 9, skin, 0, -31, 0),
                sph(7, shirt, -22, -27, 0, 1, 1, 1),
                sph(7, shirt, 22, -27, 0, 1, 1, 1));
        if (type == Z_BRUTE) {
            up.getChildren().addAll(sph(10, flesh, 8, -20, -14, 1, 1, 1), sph(6, blood, -12, -30, -8, 1, 1, 1),
                    sph(5, flesh, 14, -32, 4, 1, 1, 1));
        }

        r.headGroup.getChildren().addAll(
                box(21, 16, 21, skin, 0, -49, 0),
                box(19, 8, 19, skin, 0, -41, 0),
                box(5.5, 4.5, 1, dark, -5, -48, 10.4),
                box(5.5, 4.5, 1, dark, 5, -48, 10.4),
                box(3.6, 2.8, 0.8, eye, -5, -48, 10.95),
                box(3.6, 2.8, 0.8, eye, 5, -48, 10.95),
                box(3, 5, 3, flesh, 0, -44, 11),
                box(14, 4, 14, dark, -2, -58.5, -2),
                box(8, 3, 8, dark, 6, -57, 4),
                box(6, 1, 6, blood, 4, -57.6, 3));
        for (int i = 0; i < 4; i++) {
            r.headGroup.getChildren().add(box(3, 3, 1, bone, -4.5 + 3 * i, -38.5, 9.6));
        }
        r.jawGroup.getChildren().addAll(
                box(17, 6, 17, skin, 0, -35.5, 1.5),
                box(8, 1.5, 8, flesh, 0, -38.5, 2.5),
                box(2.5, 2.5, 1, bone, -4, -38, 9.4),
                box(2.5, 2.5, 1, bone, 0, -38, 9.4),
                box(2.5, 2.5, 1, bone, 4, -38, 9.4));
        r.headGroup.getChildren().add(r.jawGroup);
        up.getChildren().add(r.headGroup);

        up.getChildren().add(limb(r.lShYaw, r.lShR, r.lElbR, -23, -27, 10.5, 11, 24, 22, shirt, skin, skin, 8, 9, 0));
        up.getChildren().add(limb(r.rShYaw, r.rShR, r.rElbR, 23, -27, 10.5, 11, 24, 22, shirt, skin, skin, 8, 9, 0));

        Group lLeg = limb(null, r.lHipR, r.lKneeR, -9, 12, 15, 16, 22, 20, pants, pants, dark, 9, 21, 4);
        Group rLeg = limb(null, r.rHipR, r.rKneeR, 9, 12, 15, 16, 22, 20, pants, blood, dark, 9, 21, 4);
        r.root.getChildren().addAll(box(32, 10, 19, pants, 0, 11, 0), r.upper, lLeg, rLeg);
        return r;
    }

    // =====================================
    // PLAYER & WEAPON MODELING
    // =====================================
    private void createPlayer(Group world) {
        player = new Group();
        player.setTranslateY(55);
        player.getTransforms().add(playerFacing);
        playerRig = buildSoldier();
        player.getChildren().add(playerRig.root);

        weaponModel = createWeaponModel();
        playerRig.upper.getChildren().add(weaponModel);
        world.getChildren().add(player);
    }

    private Group createWeaponModel() {
        Group model = new Group();
        model.setTranslateX(GUN_X);
        model.setTranslateY(GUN_Y);
        model.setTranslateZ(GUN_Z);
        model.getTransforms().add(weaponPitch);

        PhongMaterial gunmetal = mat(Color.rgb(34, 37, 40), Color.rgb(200, 210, 220), 90);
        PhongMaterial dark = mat(Color.rgb(12, 13, 15), Color.rgb(90, 90, 100), 50);
        PhongMaterial polymer = mat(Color.rgb(26, 26, 28), Color.rgb(60, 60, 60), 20);
        PhongMaterial chrome = mat(Color.rgb(176, 182, 190), Color.WHITE, 120);
        PhongMaterial gold = mat(Color.rgb(196, 152, 58), Color.rgb(255, 230, 150), 100);
        PhongMaterial wood = mat(Color.rgb(112, 70, 38), Color.rgb(160, 120, 80), 30);
        PhongMaterial green = glow(Color.rgb(80, 255, 120));
        PhongMaterial orange = glow(Color.rgb(255, 130, 20));
        PhongMaterial red = glow(Color.rgb(255, 40, 30));
        PhongMaterial cyan = glow(Color.rgb(70, 210, 255));

        double flashScale;
        if (currentWeapon == WeaponType.PISTOL) {
            Box grip = box(6, 17, 9, polymer, 0, 7, -1);
            grip.setRotationAxis(Rotate.X_AXIS);
            grip.setRotate(-12);
            model.getChildren().addAll(
                    box(6.5, 7, 31, chrome, 0, -9, 14),
                    box(6, 5, 28, dark, 0, -3.5, 13),
                    box(6.6, 1.2, 20, gold, 0, -12.7, 15),
                    cyl(2.0, 5, gold, 0, -9, 31.5),
                    box(1.6, 1.6, 9, dark, 0, 0.5, 6),
                    box(1.4, 4, 1.2, dark, 0, 2, 4),
                    box(1.6, 2, 1.6, green, -2.2, -13.4, -0.5),
                    box(1.6, 2, 1.6, green, 2.2, -13.4, -0.5),
                    box(1.8, 2.4, 1.6, green, 0, -13.6, 28.5),
                    box(0.8, 1, 14, cyan, 3.4, -4.5, 13),
                    grip);
            for (int i = 0; i < 4; i++) {
                model.getChildren().add(box(6.9, 5, 0.8, dark, 0, -9, -0.5 - i * 2.2));
            }
            muzzleLocalY = -9;
            muzzleLocalZ = 34;
            flashScale = 0.9;
        } else if (currentWeapon == WeaponType.ASSAULT_RIFLE) {
            Box grip = box(6, 14, 8, polymer, 0, 7, -2);
            grip.setRotationAxis(Rotate.X_AXIS);
            grip.setRotate(-15);
            Box mag = box(6, 20, 9, dark, 0, 10, 12);
            mag.setRotationAxis(Rotate.X_AXIS);
            mag.setRotate(10);
            model.getChildren().addAll(
                    box(8, 10, 36, gunmetal, 0, -6, 8),
                    box(7, 8, 26, dark, 0, -5, 33),
                    box(3, 2, 24, gunmetal, 0, -10.5, 33),
                    cyl(1.6, 22, dark, 0, -5, 52),
                    cyl(2.5, 6, gunmetal, 0, -5, 66),
                    box(6, 12, 22, polymer, 0, -4, -20),
                    box(6.4, 4, 14, polymer, 0, -9.5, -17),
                    box(5.5, 6, 18, dark, 0, -14, 9),
                    box(4, 4, 0.8, red, 0, -14, 18.3),
                    box(1.4, 3, 1.4, gunmetal, 0, -10, 52),
                    box(0.8, 1, 14, orange, 4.1, -6, 10),
                    box(0.8, 1, 14, orange, -4.1, -6, 10),
                    box(0.8, 4, 8, cyan, 3.2, 2, 12),
                    grip, mag);
            muzzleLocalY = -5;
            muzzleLocalZ = 69;
            flashScale = 1.25;
        } else {
            model.getChildren().addAll(
                    box(7, 10, 26, gunmetal, 0, -6, 4),
                    cyl(2.6, 52, dark, 0, -8, 40),
                    cyl(2.2, 42, gunmetal, 0, -2.5, 36),
                    box(8, 6, 18, wood, 0, -1, 28),
                    box(6, 13, 26, wood, 0, -3, -22),
                    box(6, 14, 8, polymer, 0, 7, -2),
                    box(2.2, 4, 6, red, 4.2, -6, 4),
                    box(2.2, 4, 6, red, 4.2, -6, 11),
                    box(2.2, 4, 6, red, 4.2, -6, 18),
                    box(1.6, 2, 1.6, green, 0, -11.6, 64),
                    cyl(3.0, 4, dark, 0, -8, 67));
            muzzleLocalY = -8;
            muzzleLocalZ = 70;
            flashScale = 1.7;
        }

        muzzleFlash = new Group();
        PhongMaterial core = glow(Color.rgb(255, 250, 215));
        PhongMaterial outer = glow(Color.rgb(255, 150, 40));
        muzzleFlash.getChildren().addAll(
                sph(3.6, core, 0, 0, 0, 1, 1, 1.6),
                box(1, 12, 18, outer, 0, 0, 9),
                box(12, 1, 18, outer, 0, 0, 9),
                box(0.6, 6, 28, core, 0, 0, 14));
        muzzleLight = new PointLight(Color.rgb(255, 170, 80));
        muzzleLight.setMaxRange(520);
        muzzleLight.setTranslateZ(10);
        muzzleLight.setLightOn(false);
        muzzleFlash.getChildren().add(muzzleLight);
        muzzleFlash.setTranslateY(muzzleLocalY);
        muzzleFlash.setTranslateZ(muzzleLocalZ + 2);
        muzzleFlash.setScaleX(flashScale);
        muzzleFlash.setScaleY(flashScale);
        muzzleFlash.setScaleZ(flashScale);
        muzzleFlash.setVisible(false);
        model.getChildren().add(muzzleFlash);
        return model;
    }

    private void createLighting(Group world) {
        AmbientLight ambient = new AmbientLight(Color.rgb(62, 74, 102));
        PointLight moon = new PointLight(Color.rgb(150, 175, 235));
        moon.setTranslateX(300);
        moon.setTranslateY(-900);
        moon.setTranslateZ(-700);
        moon.setMaxRange(3500);
        world.getChildren().addAll(ambient, moon);
    }

    private void createCamera() {
        camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(4000);
        camera.setFieldOfView(32);
    }

// =====================================
// CAMERA MODE TOGGLE
// =====================================
    private void toggleCameraMode() {
        if (paused || gameOver) {
            return;
        }

        firstPerson = !firstPerson;

        if (firstPerson) {
            viewButton.setText("TPP");
        } else {
            viewButton.setText("FPP");
        }

        // Hide the player model in first-person mode.
        playerRig.root.setVisible(!firstPerson);
    }

    private void createMotes(Group world) {
        for (int i = 0; i < 70; i++) {
            Box m = new Box(0.9, 0.9, 0.9);
            m.setMaterial(moteMat);
            m.setTranslateX((RNG.nextDouble() - 0.5) * 700);
            m.setTranslateY(-150 + RNG.nextDouble() * 250);
            m.setTranslateZ((RNG.nextDouble() - 0.5) * 700);
            world.getChildren().add(m);
            motes.add(m);
            moteVel.add(new double[]{(RNG.nextDouble() - 0.3) * 14, (RNG.nextDouble() - 0.4) * 5, (RNG.nextDouble() - 0.5) * 10});
        }
    }

    private void updateCamera(double deltaTime) {
        double targetYaw = cameraYaw;
        double targetPitch = cameraPitch + cameraRecoil;

        smoothedYaw += (targetYaw - smoothedYaw) * Math.min(1.0, deltaTime * 20);
        smoothedPitch += (targetPitch - smoothedPitch) * Math.min(1.0, deltaTime * 20);

        double radians = Math.toRadians(smoothedYaw);

        double targetCamX;
        double targetCamY;
        double targetCamZ;

        if (firstPerson) {

            // First-person camera positioned near the soldier's head.
            targetCamX = player.getTranslateX();
            targetCamY = player.getTranslateY() - 42;
            targetCamZ = player.getTranslateZ() + 12;

        } else {

            // Original third-person camera.
            double currentDistance = isAiming
                    ? CAMERA_DISTANCE * 0.6
                    : CAMERA_DISTANCE;

            double offsetX = -Math.sin(radians) * currentDistance;
            double offsetZ = -Math.cos(radians) * currentDistance;

            double shoulderOffset = isAiming ? 28 : 0;

            targetCamX = player.getTranslateX()
                    + offsetX
                    + (Math.cos(radians) * shoulderOffset);

            targetCamY = player.getTranslateY()
                    - (isAiming ? 150 : 175)
                    + Math.sin(walkPhase * 2) * 1.6 * moveBlend;

            targetCamZ = player.getTranslateZ()
                    + offsetZ
                    - (Math.sin(radians) * shoulderOffset);
        }

        double lerpFactor = Math.min(1.0, deltaTime * 16);
        smoothedCamX += (targetCamX - smoothedCamX) * lerpFactor;
        smoothedCamY += (targetCamY - smoothedCamY) * lerpFactor;
        smoothedCamZ += (targetCamZ - smoothedCamZ) * lerpFactor;

        double sx = shake > 0 ? (RNG.nextDouble() - 0.5) * shake : 0;
        double sy = shake > 0 ? (RNG.nextDouble() - 0.5) * shake : 0;
        shake = Math.max(0, shake - deltaTime * 20);

        camera.setTranslateX(smoothedCamX + sx);
        camera.setTranslateY(smoothedCamY + sy);
        camera.setTranslateZ(smoothedCamZ);

        double fovTarget = firstPerson
                ? (isAiming ? 55 : 75)
                : (isAiming ? 24 : (sprintingNow ? 37 : 32));
        currentFov += (fovTarget - currentFov) * Math.min(1.0, deltaTime * 6);
        camera.setFieldOfView(currentFov);

        camRoll += (-strafeInput * 1.8 - camRoll) * Math.min(1.0, deltaTime * 6);

        camera.getTransforms().setAll(
                new Rotate(smoothedYaw, Rotate.Y_AXIS),
                new Rotate(smoothedPitch, Rotate.X_AXIS),
                new Rotate(camRoll, Rotate.Z_AXIS));

        if (sky != null) {
            sky.setTranslateX(camera.getTranslateX());
            sky.setTranslateY(camera.getTranslateY());
            sky.setTranslateZ(camera.getTranslateZ());
        }
    }

    // =====================================
    // PLAYER ANIMATION
    // =====================================
    private void updatePlayerAnimation(double dt, boolean moving, boolean sprinting, double walkDir) {
        animationTime += dt;
        cameraRecoil = Math.max(0, cameraRecoil - recoilRecovery * dt);
        weaponKick = Math.max(0, weaponKick - dt * 9);

        if (hitMarkerTimer > 0) {
            hitMarkerTimer -= dt;
            if (hitMarkerTimer <= 0 && hitMarker != null) {
                hitMarker.setVisible(false);
            }
        }

        moveBlend += ((moving ? 1 : 0) - moveBlend) * Math.min(1, dt * 10);
        aimBlend += ((isAiming ? 1 : 0) - aimBlend) * Math.min(1, dt * 10);
        if (moving) {
            walkPhase += dt * (sprinting ? 13 : 9.5);
        }

        Rig r = playerRig;
        double amp = (sprinting ? 40 : 27) * moveBlend * walkDir;
        double sw = Math.sin(walkPhase) * amp;
        r.lHipR.setAngle(sw);
        r.rHipR.setAngle(-sw);
        double kneeAmp = (sprinting ? 60 : 42) * moveBlend;
        r.lKneeR.setAngle(-Math.max(0, Math.cos(walkPhase)) * kneeAmp);
        r.rKneeR.setAngle(-Math.max(0, -Math.cos(walkPhase)) * kneeAmp);

        double breath = Math.sin(animationTime * 2.2);
        double lean = (sprinting ? -10 : -2) * moveBlend + weaponKick * 4 + breath * 0.5 - aimBlend;
        r.lean.setAngle(lean);
        r.twist.setAngle(Math.sin(walkPhase) * 2.5 * moveBlend);
        r.headX.setAngle(-lean * 0.6 + Math.sin(animationTime * 1.1) * 0.8);

        double[] p = armPose(currentWeapon);
        double kick = weaponKick * 6;
        double aimUp = aimBlend * 6;
        r.rShR.setAngle(p[0] + kick + aimUp + breath * 0.8);
        r.rElbR.setAngle(p[1] - kick * 0.4);
        r.rShYaw.setAngle(p[2]);
        r.lShR.setAngle(p[3] + kick * 0.8 + aimUp + breath * 0.8);
        r.lElbR.setAngle(p[4]);
        r.lShYaw.setAngle(p[5]);

        if (weaponModel != null) {
            double bobX = Math.sin(walkPhase * 2) * 0.9 * moveBlend;
            double bobY = Math.abs(Math.cos(walkPhase)) * 1.6 * moveBlend;
            double back = currentWeapon == WeaponType.SHOTGUN ? 8 : 5;
            weaponModel.setTranslateX(GUN_X + bobX - aimBlend * 2);
            weaponModel.setTranslateY(GUN_Y + breath * 0.6 + bobY - weaponKick * 1.4);
            weaponModel.setTranslateZ(GUN_Z - weaponKick * back);
            weaponPitch.setAngle(weaponKick * (currentWeapon == WeaponType.SHOTGUN ? 16 : 9));
        }

        if (moving) {
            stepTimer -= dt;
            if (stepTimer <= 0) {
                stepTimer = sprinting ? 0.17 : 0.3;
                emit(dustMat, 4, player.getTranslateX() + (RNG.nextDouble() - 0.5) * 10, 107, player.getTranslateZ() + (RNG.nextDouble() - 0.5) * 10,
                        (RNG.nextDouble() - 0.5) * 14, -14, (RNG.nextDouble() - 0.5) * 14, 0.6, 0, false, false);
            }
        }

        if (muzzleFlashTimer > 0) {
            muzzleFlashTimer -= dt;
            if (muzzleFlashTimer <= 0) {
                muzzleFlash.setVisible(false);
                muzzleLight.setLightOn(false);
            }
        }
    }

    // =====================================
    // PARTICLES / TRACERS / EFFECT UPDATES
    // =====================================
    private void emit(PhongMaterial m, double size, double x, double y, double z, double vx, double vy, double vz,
            double life, double gravity, boolean bounce, boolean blood) {
        if (particles.size() >= MAX_PARTICLES) {
            Particle old = particles.remove(0);
            world.getChildren().remove(old.node);
        }
        Particle p = new Particle();
        p.node = new Box(size, size, size * (0.8 + RNG.nextDouble() * 0.8));
        p.node.setMaterial(m);
        p.node.setTranslateX(x);
        p.node.setTranslateY(y);
        p.node.setTranslateZ(z);
        p.node.setMouseTransparent(true);
        p.vx = vx;
        p.vy = vy;
        p.vz = vz;
        p.life = life;
        p.fade = Math.min(0.4, life * 0.5);
        p.gravity = gravity;
        p.bounce = bounce;
        p.blood = blood;
        p.moving = true;
        particles.add(p);
        world.getChildren().add(p.node);
    }

    private void burst(PhongMaterial m, double x, double y, double z, int count, double speed, double size, double life, double gravity, boolean blood, double[] dir) {
        for (int i = 0; i < count; i++) {
            double vx = (RNG.nextDouble() - 0.5) * speed, vy = (RNG.nextDouble() - 0.7) * speed, vz = (RNG.nextDouble() - 0.5) * speed;
            if (dir != null) {
                vx += dir[0] * speed * 0.5;
                vy += dir[1] * speed * 0.5;
                vz += dir[2] * speed * 0.5;
            }
            emit(m, size * (0.6 + RNG.nextDouble() * 0.8), x, y, z, vx, vy, vz, life * (0.6 + RNG.nextDouble() * 0.8), gravity, false, blood);
        }
    }

    private void spawnTracer(double sx, double sy, double sz, double ex, double ey, double ez) {
        double dx = ex - sx, dy = ey - sy, dz = ez - sz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 2) {
            return;
        }
        Box b = new Box(0.9, 0.9, len);
        b.setMaterial(tracerMat);
        b.setMouseTransparent(true);
        b.setTranslateX((sx + ex) / 2);
        b.setTranslateY((sy + ey) / 2);
        b.setTranslateZ((sz + ez) / 2);
        double yaw = Math.toDegrees(Math.atan2(dx, dz));
        double pitch = Math.toDegrees(Math.asin(-dy / len));
        b.getTransforms().setAll(new Rotate(yaw, Rotate.Y_AXIS), new Rotate(pitch, Rotate.X_AXIS));
        Tracer t = new Tracer();
        t.node = b;
        t.life = 0.07;
        tracers.add(t);
        world.getChildren().add(b);
    }

    private void updateEffects(double dt) {
        worldTime += dt;

        // particles
        for (Iterator<Particle> it = particles.iterator(); it.hasNext();) {
            Particle p = it.next();
            p.life -= dt;
            if (p.life <= 0) {
                world.getChildren().remove(p.node);
                it.remove();
                continue;
            }
            if (p.moving) {
                p.vy += p.gravity * dt;
                double x = p.node.getTranslateX() + p.vx * dt;
                double y = p.node.getTranslateY() + p.vy * dt;
                double z = p.node.getTranslateZ() + p.vz * dt;
                if (y >= 108.5 && p.vy > 0) {
                    if (p.bounce && Math.abs(p.vy) > 40) {
                        y = 108.5;
                        p.vy = -p.vy * 0.35;
                        p.vx *= 0.55;
                        p.vz *= 0.55;
                    } else {
                        y = p.blood ? 109.6 : 108.5;
                        p.moving = false;
                        if (p.blood) {
                            p.flat = true;
                            p.life = Math.max(p.life, 7);
                            p.fade = 1.2;
                        }
                    }
                }
                p.node.setTranslateX(x);
                p.node.setTranslateY(y);
                p.node.setTranslateZ(z);
            }
            double s = Math.min(1, p.life / p.fade);
            p.node.setScaleX(s);
            p.node.setScaleZ(s);
            p.node.setScaleY(p.flat ? 0.2 * s : s);
        }

        // tracers
        for (Iterator<Tracer> it = tracers.iterator(); it.hasNext();) {
            Tracer t = it.next();
            t.life -= dt;
            if (t.life <= 0) {
                world.getChildren().remove(t.node);
                it.remove();
            } else {
                double s = t.life / 0.07;
                t.node.setScaleX(s);
                t.node.setScaleY(s);
            }
        }

        // lamps flicker
        for (int i = 0; i < lampLights.size(); i++) {
            double ph = lampPhase.get(i);
            double f;
            if (ph < 0) {
                f = RNG.nextDouble() < 0.12 ? 0.08 : 0.55 + 0.4 * RNG.nextDouble();
            } else {
                f = 0.88 + 0.1 * Math.sin(worldTime * 7 + ph) + (RNG.nextDouble() - 0.5) * 0.05;
                if (RNG.nextDouble() < 0.004) {
                    f *= 0.3;
                }
            }
            lampLights.get(i).setColor(Color.color(clamp01(1.0 * f), clamp01(0.92 * f), clamp01(0.67 * f)));
        }

        // fires
        for (Fire f : fires) {
            f.phase += dt * 14;
            for (int i = 0; i < f.tongues.length; i++) {
                double k = 0.75 + 0.45 * Math.abs(Math.sin(f.phase + i * 2.1)) + RNG.nextDouble() * 0.15;
                f.tongues[i].setScaleY(k);
                f.tongues[i].setScaleX(0.85 + 0.3 * RNG.nextDouble());
                f.tongues[i].setTranslateY(-f.heights[i] * k / 2);
            }
            double fl = 0.78 + 0.22 * Math.sin(f.phase * 0.9) + (RNG.nextDouble() - 0.5) * 0.25;
            f.light.setColor(Color.color(clamp01(fl), clamp01(0.5 * fl), clamp01(0.15 * fl)));
            f.emberTimer -= dt;
            if (f.emberTimer <= 0) {
                f.emberTimer = 0.1;
                emit(emberMat, 1.4, f.x + (RNG.nextDouble() - 0.5) * 14, f.y - 10, f.z + (RNG.nextDouble() - 0.5) * 14,
                        (RNG.nextDouble() - 0.5) * 24, -(35 + RNG.nextDouble() * 40), (RNG.nextDouble() - 0.5) * 24, 1.3, -10, false, false);
            }
        }

        // beacons & searchlights
        boolean beaconOn = (worldTime % 1.6) < 0.22;
        for (Sphere s : beacons) {
            s.setVisible(beaconOn);
        }
        for (int i = 0; i < beamRotates.size(); i++) {
            Rotate r = beamRotates.get(i);
            r.setAngle(r.getAngle() + dt * (i % 2 == 0 ? 22 : -22));
        }

        // drifting ash / dust
        double px = player.getTranslateX(), pz = player.getTranslateZ();
        for (int i = 0; i < motes.size(); i++) {
            Box m = motes.get(i);
            double[] v = moteVel.get(i);
            double x = m.getTranslateX() + v[0] * dt, y = m.getTranslateY() + v[1] * dt, z = m.getTranslateZ() + v[2] * dt;
            if (Math.abs(x - px) > 380 || Math.abs(z - pz) > 380 || y > 104 || y < -170) {
                x = px + (RNG.nextDouble() - 0.5) * 700;
                z = pz + (RNG.nextDouble() - 0.5) * 700;
                y = -150 + RNG.nextDouble() * 250;
            }
            m.setTranslateX(x);
            m.setTranslateY(y);
            m.setTranslateZ(z);
        }

        // screen overlays
        damageFlash = Math.max(0, damageFlash - dt * 1.8);
        double lowHealth = health < 35 && health > 0 ? (0.18 + 0.14 * Math.sin(worldTime * 5)) * (1 - health / 35) * 2 : 0;
        redFlash.setOpacity(clamp01(damageFlash + lowHealth));
        updateCrosshair(dt);
    }

    // =====================================
    // ENEMIES
    // =====================================
    private void createEnemies() {
        startWave(1);
    }

    private void startWave(int wave) {
        currentWave = wave;
        waitingForNextWave = false;
        waveCountdown = 0;

        enemies.removeIf(e -> {
            if (!e.alive) {
                world.getChildren().remove(e.model);
                return true;
            }
            return false;
        });

        int enemyCount = 2 + (currentWave * 2);
        for (int i = 0; i < enemyCount; i++) {
            int type = Z_WALKER;
            if (currentWave >= 2 && i % 3 == 2) {
                type = Z_RUNNER;
            }
            if (currentWave >= 3 && i % 5 == 4) {
                type = Z_BRUTE;
            }
            double angle = (Math.PI * 2 * i) / enemyCount;
            double rad = 420;
            double sx = Math.sin(angle) * rad, sz = Math.cos(angle) * rad;
            int tries = 0;
            while (collidesWithObstacle(sx, sz, 30) && tries < 12) {
                rad += (tries % 2 == 0 ? 1 : -2) * 30;
                sx = Math.sin(angle) * rad;
                sz = Math.cos(angle) * rad;
                tries++;
            }
            spawnEnemy(world, sx, sz, type);
        }

        if (waveText != null) {
            waveText.setText("WAVE: " + currentWave);
        }
        if (statusText != null) {
            statusText.setText("INCOMING INFECTED!");
            statusText.setFill(Color.ORANGERED);
        }
        if (banner != null) {
            showBanner("WAVE " + currentWave, Color.GOLD);
        }
    }

    private void spawnEnemy(Group world, double x, double z, int type) {
        Enemy enemy = new Enemy(x, z, type);
        enemies.add(enemy);
        world.getChildren().add(enemy.model);
    }

    private void updateEnemies(double dt) {
        double baseSpeed = 70 + (currentWave - 1) * 7;
        double baseDmg = 12 + (currentWave - 1) * 2;
        double px = player.getTranslateX(), pz = player.getTranslateZ();

        for (Iterator<Enemy> it = enemies.iterator(); it.hasNext();) {
            Enemy e = it.next();
            if (!e.alive) {
                e.deathTime += dt;
                double fp = Math.min(1, e.deathTime / 0.55);
                e.fall.setAngle(fp * fp * (3 - 2 * fp) * 88);
                double sink = e.deathTime > 4.5 ? (e.deathTime - 4.5) * 12 : 0;
                e.model.setTranslateY(GROUND_Y - 9 * fp + sink);
                if (e.deathTime > 6.5) {
                    world.getChildren().remove(e.model);
                    it.remove();
                }
                continue;
            }

            if (e.spawnTimer > 0) {
                e.spawnTimer -= dt;
                e.model.setTranslateY(GROUND_Y + 62 * Math.max(0, e.spawnTimer / 1.2));
                e.animate(dt, false, false);
                continue;
            }
            e.model.setTranslateY(GROUND_Y);
            if (e.stagger > 0) {
                e.stagger -= dt;
            }

            double ex = e.model.getTranslateX(), ez = e.model.getTranslateZ();
            double dx = px - ex, dz = pz - ez;
            double distance = Math.sqrt(dx * dx + dz * dz);

            double target = Math.toDegrees(Math.atan2(dx, dz));
            double diff = target - e.yaw;
            while (diff > 180) {
                diff -= 360;
            }
            while (diff < -180) {
                diff += 360;
            }
            e.yaw += diff * Math.min(1, dt * 7);
            e.facing.setAngle(e.yaw);

            double stop = 50 * e.scale;
            double speed = baseSpeed * e.speedMul * (e.stagger > 0 ? 0.15 : 1);
            boolean moving = false, attacking = false;
            if (distance > stop && distance > 0.001) {
                double nx = ex + (dx / distance) * speed * dt;
                double nz = ez + (dz / distance) * speed * dt;
                double rad = 20 * e.scale;
                if (!collidesWithObstacle(nx, ez, rad)) {
                    e.model.setTranslateX(nx);
                }
                if (!collidesWithObstacle(e.model.getTranslateX(), nz, rad)) {
                    e.model.setTranslateZ(nz);
                }
                moving = true;
            } else {
                attacking = true;
                e.attackTimer -= dt;
                if (e.attackTimer <= 0) {
                    damagePlayer(baseDmg * e.dmgMul);
                    e.attackTimer = e.type == Z_RUNNER ? 0.8 : 1.1;
                }
            }
            e.animate(dt, moving, attacking);
        }

        // keep zombies from stacking
        for (int i = 0; i < enemies.size(); i++) {
            Enemy a = enemies.get(i);
            if (!a.alive || a.spawnTimer > 0) {
                continue;
            }
            for (int j = i + 1; j < enemies.size(); j++) {
                Enemy b = enemies.get(j);
                if (!b.alive || b.spawnTimer > 0) {
                    continue;
                }
                double dx = b.model.getTranslateX() - a.model.getTranslateX();
                double dz = b.model.getTranslateZ() - a.model.getTranslateZ();
                double d = Math.sqrt(dx * dx + dz * dz);
                double min = 36 * (a.scale + b.scale) / 2;
                if (d < min && d > 0.01) {
                    double push = (min - d) * 0.5;
                    a.model.setTranslateX(a.model.getTranslateX() - dx / d * push);
                    a.model.setTranslateZ(a.model.getTranslateZ() - dz / d * push);
                    b.model.setTranslateX(b.model.getTranslateX() + dx / d * push);
                    b.model.setTranslateZ(b.model.getTranslateZ() + dz / d * push);
                }
            }
        }
    }

    private void damagePlayer(double amount) {
        if (health <= 0 || gameOver) {
            return;
        }
        health = Math.max(0, health - amount);
        damageFlash = Math.min(0.85, damageFlash + 0.55);
        shake = Math.max(shake, 7);
        updateHUD();
        if (health <= 0) {
            showGameOver();
        }
    }

    private void showGameOver() {
        gameOver = true;
        paused = false;
        keys.clear();
        firing = false;
        pauseOverlay.setVisible(false);
        finalStatsText.setText("FINAL STATISTICS\n\nWAVE REACHED: " + currentWave + "\nTOTAL KILLS: " + kills);
        gameOverOverlay.setVisible(true);
        gameOverOverlay.toFront();
    }

    private void restartGame() {
        keys.clear();
        health = 100;
        stamina = 100;
        initializeWeaponAmmo();
        kills = 0;
        firing = false;
        isAiming = false;
        lastShotTime = 0;
        cameraRecoil = 0;
        animationTime = 0;
        muzzleFlashTimer = 0;
        hitMarkerTimer = 0;
        weaponKick = 0;
        shake = 0;
        damageFlash = 0;
        playerYaw = 0;
        playerFacing.setAngle(0);

        for (Particle p : particles) {
            world.getChildren().remove(p.node);
        }
        particles.clear();
        for (Tracer t : tracers) {
            world.getChildren().remove(t.node);
        }
        tracers.clear();

        // rebuild weapon model for the pistol
        if (weaponModel != null) {
            playerRig.upper.getChildren().remove(weaponModel);
        }
        weaponModel = createWeaponModel();
        playerRig.upper.getChildren().add(weaponModel);

        muzzleFlash.setVisible(false);
        muzzleLight.setLightOn(false);
        hitMarker.setVisible(false);

        player.setTranslateX(0);
        player.setTranslateY(55);
        player.setTranslateZ(0);
        jumpVelocity = 0;
        isGrounded = true;
        cameraYaw = 0;
        cameraPitch = -10.5;
        currentWave = 0;
        waitingForNextWave = false;

        for (Enemy e : enemies) {
            world.getChildren().remove(e.model);
        }
        enemies.clear();

        gameOver = false;
        paused = false;
        gameOverOverlay.setVisible(false);
        pauseOverlay.setVisible(false);

        statusText.setText("STATUS: SURVIVING");
        statusText.setFill(Color.LIGHTGREEN);
        waveText.setText("WAVE: 1");

        updateCamera(1.0);
        updateHUD();
        startWave(1);
    }

    // =====================================
    // SHOOTING
    // =====================================
    private double[] rayDir(double yawDeg, double pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        return new double[]{Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)};
    }

    private double[] muzzleWorld() {
        double fx = Math.sin(Math.toRadians(playerYaw)), fz = Math.cos(Math.toRadians(playerYaw));
        double rx = fz, rz = -fx;
        double fd = GUN_Z + muzzleLocalZ + 4;
        return new double[]{
            player.getTranslateX() + fx * fd + rx * GUN_X,
            player.getTranslateY() + GUN_Y + muzzleLocalY,
            player.getTranslateZ() + fz * fd + rz * GUN_X};
    }

    private void shoot() {
        if (paused || gameOver || health <= 0 || waitingForNextWave) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastShotTime < currentWeapon.cooldown) {
            return;
        }

        int curAmmo = magazineAmmo.get(currentWeapon);
        if (curAmmo <= 0) {
            statusText.setText("RELOAD YOUR WEAPON!");
            statusText.setFill(Color.ORANGE);
            return;
        }

        lastShotTime = now;
        magazineAmmo.put(currentWeapon, curAmmo - 1);
        weaponKick = kickFor(currentWeapon);
        cameraRecoil = Math.min(cameraRecoil + (currentWeapon == WeaponType.SHOTGUN ? 4 : 1.8), 9);
        shake = Math.max(shake, currentWeapon == WeaponType.SHOTGUN ? 5 : 1.8);

        muzzleFlash.setVisible(true);
        muzzleFlash.setRotationAxis(Rotate.Z_AXIS);
        muzzleFlash.setRotate(RNG.nextDouble() * 90);
        muzzleLight.setLightOn(true);
        muzzleFlashTimer = 0.06;

        double[] mw = muzzleWorld();
        double fx = Math.sin(Math.toRadians(playerYaw)), fz = Math.cos(Math.toRadians(playerYaw));
        // muzzle sparks
        for (int i = 0; i < 3; i++) {
            emit(sparkMat, 1.2, mw[0], mw[1], mw[2], fx * (140 + RNG.nextDouble() * 140) + (RNG.nextDouble() - 0.5) * 60,
                    (RNG.nextDouble() - 0.5) * 60, fz * (140 + RNG.nextDouble() * 140) + (RNG.nextDouble() - 0.5) * 60, 0.18, 300, false, false);
        }
        // eject brass
        double ex = player.getTranslateX() + fx * (GUN_Z + 6) + fz * (GUN_X + 6);
        double ey = player.getTranslateY() + GUN_Y - 4;
        double ez = player.getTranslateZ() + fz * (GUN_Z + 6) - fx * (GUN_X + 6);
        emit(brassMat, 1.5, ex, ey, ez, fz * (70 + RNG.nextDouble() * 40), -(80 + RNG.nextDouble() * 50), -fx * (70 + RNG.nextDouble() * 40), 3, 520, true, false);

        double cx = camera.getTranslateX(), cy = camera.getTranslateY(), cz = camera.getTranslateZ();
        boolean hit = false, elim = false, headshot = false;
        for (int i = 0; i < currentWeapon.pellets; i++) {
            double sYaw = (Math.random() * 2 - 1) * currentWeapon.spread * (isAiming ? 0.4 : 1.0);
            double sPitch = (Math.random() * 2 - 1) * currentWeapon.spread * (isAiming ? 0.4 : 1.0);
            double[] dir = rayDir(cameraYaw + sYaw, cameraPitch + sPitch);
            Enemy target = findTarget(dir, currentWeapon.range);
            boolean head = hitHead;
            double dist = target != null ? hitDist : currentWeapon.range * 0.8;
            boolean groundHit = false;
            if (target == null && dir[1] > 0.001) {
                double tg = (108 - cy) / dir[1];
                if (tg > 0 && tg < dist) {
                    dist = tg;
                    groundHit = true;
                }
            }
            double hx = cx + dir[0] * dist, hy = cy + dir[1] * dist, hz = cz + dir[2] * dist;
            if (i < 3) {
                spawnTracer(mw[0], mw[1], mw[2], hx, hy, hz);
            }
            if (groundHit && i < 3) {
                burst(sparkMat, hx, 107, hz, 5, 150, 1.1, 0.35, 400, false, null);
                burst(dustMat, hx, 107, hz, 3, 40, 4, 0.7, 0, false, null);
            }
            if (target != null && target.alive) {
                double dmg = currentWeapon.damage * (head ? 2.0 : 1.0);
                target.health -= dmg;
                target.stagger = 0.22;
                if (head) {
                    headshot = true;
                }
                burst(bloodMat, hx, hy, hz, head ? 9 : 5, 200, 2.2, 1.0, 500, true, dir);
                if (target.health <= 0) {
                    target.alive = false;
                    kills++;
                    elim = true;
                    burst(bloodMat, hx, hy, hz, 14, 260, 2.6, 1.2, 500, true, dir);
                }
                hit = true;
            }
        }

        if (hit) {
            hitMarker.setFill(elim ? Color.ORANGERED : headshot ? Color.GOLD : Color.WHITE);
            hitMarker.setVisible(true);
            hitMarkerTimer = 0.2;
            statusText.setText(elim ? (headshot ? "HEADSHOT KILL!" : "INFECTED ELIMINATED") : (headshot ? "HEADSHOT!" : "TARGET HIT"));
            statusText.setFill(headshot ? Color.GOLD : Color.LIGHTGREEN);
        } else {
            hitMarker.setVisible(false);
        }
        updateHUD();
    }

    private static double raySphere(double ox, double oy, double oz, double[] d, double sx, double sy, double sz, double r) {
        double lx = sx - ox, ly = sy - oy, lz = sz - oz;
        double t = lx * d[0] + ly * d[1] + lz * d[2];
        if (t <= 0) {
            return -1;
        }
        double perp2 = lx * lx + ly * ly + lz * lz - t * t;
        if (perp2 > r * r) {
            return -1;
        }
        return Math.max(0.1, t - Math.sqrt(r * r - perp2));
    }

    private Enemy findTarget(double[] dir, double range) {
        double cx = camera.getTranslateX(), cy = camera.getTranslateY(), cz = camera.getTranslateZ();
        Enemy best = null;
        double bestT = range;
        boolean bestHead = false;
        for (Enemy e : enemies) {
            if (!e.alive || e.spawnTimer > 0) {
                continue;
            }
            double s = e.scale;
            double ex = e.model.getTranslateX(), ez = e.model.getTranslateZ();
            double th = raySphere(cx, cy, cz, dir, ex, GROUND_Y - 100 * s, ez, 16 * s);
            double tc = raySphere(cx, cy, cz, dir, ex, GROUND_Y - 62 * s, ez, 30 * s);
            double tl = raySphere(cx, cy, cz, dir, ex, GROUND_Y - 32 * s, ez, 24 * s);
            double tb = tc < 0 ? tl : (tl < 0 ? tc : Math.min(tc, tl));
            double t;
            boolean head;
            if (th >= 0 && (tb < 0 || th <= tb + 25 * s)) {
                t = th;
                head = true;
            } else if (tb >= 0) {
                t = tb;
                head = false;
            } else {
                continue;
            }
            if (t < bestT) {
                bestT = t;
                best = e;
                bestHead = head;
            }
        }
        hitDist = bestT;
        hitHead = bestHead;
        return best;
    }

    private void reload() {
        if (paused || gameOver || health <= 0) {
            return;
        }
        int curMag = magazineAmmo.get(currentWeapon);
        int reserve = reserveAmmo.get(currentWeapon);
        if (curMag >= currentWeapon.magazineSize || reserve <= 0) {
            return;
        }

        int needed = currentWeapon.magazineSize - curMag;
        int reloadAmt = Math.min(needed, reserve);
        magazineAmmo.put(currentWeapon, curMag + reloadAmt);
        reserveAmmo.put(currentWeapon, reserve - reloadAmt);
        statusText.setText("WEAPON RELOADED");
        statusText.setFill(Color.LIGHTGREEN);
        weaponKick = 0.6;
        updateHUD();
    }

    private void switchWeapon(WeaponType weapon) {
        if (paused || gameOver || currentWeapon == weapon) {
            return;
        }
        currentWeapon = weapon;
        lastShotTime = 0;
        firing = false;

        if (player != null && weaponModel != null) {
            playerRig.upper.getChildren().remove(weaponModel);
            weaponModel = createWeaponModel();
            playerRig.upper.getChildren().add(weaponModel);
        }
        statusText.setText("EQUIPPED: " + currentWeapon.displayName);
        statusText.setFill(Color.GOLD);
        updateHUD();
    }

    // =====================================
    // INPUT
    // =====================================
    private void setupMouse(SubScene gameScene) {
        gameScene.setOnMousePressed(e -> {
            mouseX = e.getSceneX();
            mouseY = e.getSceneY();
            if (e.getButton() == MouseButton.PRIMARY && !paused && !gameOver) {
                firing = true;
                shoot();
            }
            if (e.getButton() == MouseButton.SECONDARY && !paused && !gameOver) {
                isAiming = true;
            }
        });
        gameScene.setOnMouseReleased(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                firing = false;
            }
            if (e.getButton() == MouseButton.SECONDARY) {
                isAiming = false;
            }
        });
        gameScene.setOnMouseDragged(e -> {
            if (paused || gameOver) {
                return;
            }
            cameraYaw += (e.getSceneX() - mouseX) * 0.22;
            cameraPitch -= (e.getSceneY() - mouseY) * 0.22;
            cameraPitch = Math.max(-50, Math.min(50, cameraPitch));
            mouseX = e.getSceneX();
            mouseY = e.getSceneY();
        });
    }

    private void setupKeyboard(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {

            keys.add(e.getCode());

            if (e.getCode() == KeyCode.SPACE) {
                if (!paused && !gameOver && isGrounded) {
                    jumpVelocity = -JUMP_FORCE;
                    isGrounded = false;
                }
            } else if (e.getCode() == KeyCode.R) {
                reload();
            } else if (e.getCode() == KeyCode.DIGIT1) {
                switchWeapon(WeaponType.PISTOL);
            } else if (e.getCode() == KeyCode.DIGIT2) {
                switchWeapon(WeaponType.ASSAULT_RIFLE);
            } else if (e.getCode() == KeyCode.DIGIT3) {
                switchWeapon(WeaponType.SHOTGUN);
            }
        });

        scene.addEventFilter(KeyEvent.KEY_RELEASED, e -> {
            keys.remove(e.getCode());
        });
    }

    private void updatePlayer(double deltaTime) {
        double mx = 0, mz = 0;
        if (keys.contains(KeyCode.W)) {
            mz += 1;
        }
        if (keys.contains(KeyCode.S)) {
            mz -= 1;
        }
        if (keys.contains(KeyCode.A)) {
            mx -= 1;
        }
        if (keys.contains(KeyCode.D)) {
            mx += 1;
        }
        strafeInput = mx;

        boolean moving = mx != 0 || mz != 0;
        boolean sprinting = keys.contains(KeyCode.SHIFT) && moving && stamina > 0;
        sprintingNow = sprinting;
        double speed = sprinting ? SPRINT_SPEED : WALK_SPEED;

        if (sprinting) {
            stamina -= 30 * deltaTime;
        } else {
            stamina += 22 * deltaTime;
        }
        stamina = Math.max(0, Math.min(100, stamina));

        // body smoothly turns to face where the camera looks (over-the-shoulder style)
        double diff = cameraYaw - playerYaw;
        while (diff > 180) {
            diff -= 360;
        }
        while (diff < -180) {
            diff += 360;
        }
        playerYaw += diff * Math.min(1, deltaTime * 14);
        playerFacing.setAngle(playerYaw);

        if (moving) {
            double len = Math.sqrt(mx * mx + mz * mz);
            mx /= len;
            mz /= len;
            double rad = Math.toRadians(cameraYaw);

            double fx = Math.sin(rad), fz = Math.cos(rad);
            double rx = Math.cos(rad), rz = -Math.sin(rad);

            double finX = fx * mz + rx * mx;
            double finZ = fz * mz + rz * mx;

            double oldX = player.getTranslateX();
            double oldZ = player.getTranslateZ();
            double newX = Math.max(-WORLD_BOUNDARY, Math.min(WORLD_BOUNDARY, oldX + finX * speed * deltaTime));
            double newZ = Math.max(-WORLD_BOUNDARY, Math.min(WORLD_BOUNDARY, oldZ + finZ * speed * deltaTime));

            if (!collidesWithObstacle(newX, oldZ, PLAYER_RADIUS)) {
                player.setTranslateX(newX);
            }
            if (!collidesWithObstacle(player.getTranslateX(), newZ, PLAYER_RADIUS)) {
                player.setTranslateZ(newZ);
            }
        }

        updatePlayerAnimation(deltaTime, moving, sprinting, mz < 0 ? -1 : 1);
        updateHUD();

        updateJump(deltaTime);
    }

    private void updateJump(double deltaTime) {

        if (isGrounded) {
            return;
        }

        // Apply gravity.
        jumpVelocity += GRAVITY * deltaTime;

        // Move the player vertically.
        double newY = player.getTranslateY() + jumpVelocity * deltaTime;

        // Check if the player has landed.
        if (newY >= PLAYER_GROUND_Y) {
            newY = PLAYER_GROUND_Y;
            jumpVelocity = 0;
            isGrounded = true;
        }

        player.setTranslateY(newY);
    }

    private void updateWaveSystem(double deltaTime) {
        if (waitingForNextWave) {
            waveCountdown -= deltaTime;
            if (waveCountdown <= 0) {
                startWave(currentWave + 1);
            } else {
                waveText.setText("NEXT WAVE: " + (int) Math.ceil(waveCountdown));
            }
            return;
        }

        long aliveEnemies = enemies.stream().filter(e -> e.alive).count();
        if (aliveEnemies == 0) {
            waitingForNextWave = true;
            waveCountdown = WAVE_BREAK_DURATION;
            waveText.setText("WAVE " + currentWave + " CLEARED!");
            statusText.setText("AREA SECURED");
            statusText.setFill(Color.LIGHTGREEN);
            showBanner("AREA SECURED", Color.LIGHTGREEN);
            if (WAVE_RESUPPLY) {
                for (WeaponType w : WeaponType.values()) {
                    reserveAmmo.put(w, reserveAmmo.get(w) + w.magazineSize);
                }
                health = Math.min(100, health + 15);
                statusText.setText("AREA SECURED  +AMMO  +HEALTH");
            }
        }
    }

    // =====================================
    // UI & OVERLAYS
    // =====================================
    private void showBanner(String text, Color color) {
        if (banner == null) {
            return;
        }
        banner.setText(text);
        banner.setFill(color);
        if (bannerFade != null) {
            bannerFade.stop();
        }
        banner.setOpacity(1);
        bannerFade = new FadeTransition(Duration.seconds(2.6), banner);
        bannerFade.setDelay(Duration.seconds(0.8));
        bannerFade.setFromValue(1);
        bannerFade.setToValue(0);
        bannerFade.play();
    }

    private Group createCrosshair() {
        Group g = new Group();
        for (int i = 0; i < 4; i++) {
            Rectangle rc = new Rectangle(i < 2 ? 2 : 9, i < 2 ? 9 : 2);
            rc.setFill(Color.rgb(255, 255, 255, 0.9));
            rc.setStroke(Color.rgb(0, 0, 0, 0.5));
            rc.setStrokeWidth(0.5);
            ch[i] = rc;
            g.getChildren().add(rc);
        }
        g.getChildren().add(new Circle(1.3, Color.rgb(255, 80, 60, 0.9)));
        g.setMouseTransparent(true);
        updateCrosshair(1);
        return g;
    }

    private void updateCrosshair(double dt) {
        double target = 7 + moveBlend * 7 + weaponKick * 10 - (isAiming ? 3 : 0);
        crossGap += (target - crossGap) * Math.min(1, dt * 14);
        ch[0].setX(-1);
        ch[0].setY(-(crossGap + 9));
        ch[1].setX(-1);
        ch[1].setY(crossGap);
        ch[2].setX(-(crossGap + 9));
        ch[2].setY(-1);
        ch[3].setX(crossGap);
        ch[3].setY(-1);
    }

    private VBox createHUD() {
        Text title = new Text("BLACKOUT");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 26));
        title.setFill(Color.WHITE);

        Text subtitle = new Text("CINEMATIC EDITION");
        subtitle.setFill(Color.GOLD);
        subtitle.setFont(Font.font("Arial", FontWeight.BOLD, 10));

        healthText = new Text("HEALTH 100 / 100");
        healthText.setFill(Color.WHITE);
        healthBar = new ProgressBar(1);
        healthBar.setPrefWidth(230);
        healthBar.setStyle("-fx-accent: #e74c3c;");

        staminaText = new Text("STAMINA 100 / 100");
        staminaText.setFill(Color.WHITE);
        staminaBar = new ProgressBar(1);
        staminaBar.setPrefWidth(230);
        staminaBar.setStyle("-fx-accent: #2ecc71;");

        enemyText = new Text("INFECTED: 0");
        enemyText.setFill(Color.ORANGERED);
        weaponText = new Text("WEAPON: PISTOL");
        weaponText.setFill(Color.GOLD);
        weaponText.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        ammoText = new Text("AMMO: 12 / 48");
        ammoText.setFill(Color.WHITE);
        killsText = new Text("KILLS: 0");
        killsText.setFill(Color.LIGHTGREEN);
        waveText = new Text("WAVE: 1");
        waveText.setFill(Color.GOLD);
        waveText.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        statusText = new Text("STATUS: SURVIVING");
        statusText.setFill(Color.LIGHTGREEN);

        VBox hud = new VBox(8, title, subtitle, healthText, healthBar, staminaText, staminaBar, enemyText, weaponText, ammoText, killsText, waveText, statusText);
        hud.setPadding(new Insets(20));
        hud.setMaxWidth(270);
        hud.setMaxHeight(Region.USE_PREF_SIZE);
        hud.setStyle("-fx-background-color: rgba(12, 16, 22, 0.72); -fx-background-radius: 10; -fx-border-color: rgba(255,255,255,0.1); -fx-border-radius: 10;");
        hud.setMouseTransparent(true);
        return hud;
    }

    private void updateHUD() {
        if (healthBar == null) {
            return;
        }
        healthBar.setProgress(health / 100);
        staminaBar.setProgress(stamina / 100);
        healthText.setText("HEALTH " + (int) health + " / 100");
        staminaText.setText("STAMINA " + (int) stamina + " / 100");
        weaponText.setText("WEAPON: " + currentWeapon.displayName);
        ammoText.setText("AMMO: " + magazineAmmo.get(currentWeapon) + " / " + reserveAmmo.get(currentWeapon));
        killsText.setText("KILLS: " + kills);
        enemyText.setText("INFECTED: " + enemies.stream().filter(e -> e.alive).count());
    }

    private StackPane createPauseOverlay() {
        Text title = new Text("GAME PAUSED");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 42));
        title.setFill(Color.WHITE);
        Button resumeBtn = new Button("RESUME");
        resumeBtn.setOnAction(e -> resumeGame());
        Button restartBtn = new Button("RESTART");
        restartBtn.setOnAction(e -> restartGame());

        for (Button b : List.of(resumeBtn, restartBtn)) {
            b.setFont(Font.font("Arial", FontWeight.BOLD, 15));
            b.setPrefWidth(220);
            b.setPrefHeight(44);
            b.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: white; -fx-background-radius: 6; -fx-cursor: hand;");
        }

        VBox panel = new VBox(20, title, resumeBtn, restartBtn);
        panel.setAlignment(Pos.CENTER);
        StackPane overlay = new StackPane(panel);
        overlay.setStyle("-fx-background-color: rgba(8, 11, 15, 0.92);");
        overlay.setVisible(false);
        return overlay;
    }

    private StackPane createGameOverOverlay() {
        Text title = new Text("YOU DIED");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 46));
        title.setFill(Color.RED);
        title.setEffect(new DropShadow(20, Color.rgb(160, 0, 0)));
        finalStatsText = new Text();
        finalStatsText.setFont(Font.font("Arial", 16));
        finalStatsText.setFill(Color.WHITE);
        Button restartBtn = new Button("TRY AGAIN");
        restartBtn.setOnAction(e -> restartGame());
        restartBtn.setFont(Font.font("Arial", FontWeight.BOLD, 15));
        restartBtn.setPrefWidth(220);
        restartBtn.setPrefHeight(44);
        restartBtn.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-background-radius: 6; -fx-cursor: hand;");

        VBox panel = new VBox(20, title, finalStatsText, restartBtn);
        panel.setAlignment(Pos.CENTER);
        StackPane overlay = new StackPane(panel);
        overlay.setStyle("-fx-background-color: rgba(8, 11, 15, 0.94);");
        overlay.setVisible(false);
        return overlay;
    }

    private void togglePause() {
        if (gameOver) {
            return;
        }
        if (paused) {
            resumeGame();
        } else {
            pauseGame();
        }
    }

    private void pauseGame() {
        paused = true;
        keys.clear();
        firing = false;
        pauseOverlay.setVisible(true);
        pauseOverlay.toFront();
    }

    private void resumeGame() {
        paused = false;
        keys.clear();
        pauseOverlay.setVisible(false);
    }

    private void startGameLoop() {
        new AnimationTimer() {
            private long lastTime = 0;

            @Override
            public void handle(long now) {
                if (lastTime == 0) {
                    lastTime = now;
                    return;
                }
                double deltaTime = Math.min((now - lastTime) / 1_000_000_000.0, 0.05);
                lastTime = now;

                if (paused) {
                    return;
                }

                if (firing && currentWeapon == WeaponType.ASSAULT_RIFLE) {
                    shoot();
                }

                if (health > 0 && !gameOver) {
                    updatePlayer(deltaTime);
                    updateEnemies(deltaTime);
                    updateWaveSystem(deltaTime);
                } else {
                    updatePlayerAnimation(deltaTime, false, false, 1);
                }

                updateEffects(deltaTime);
                updateCamera(deltaTime);
            }
        }.start();
    }

    // =====================================
    // HELPER CLASSES
    // =====================================
    private static class Obstacle {

        final double x, z, halfWidth, halfDepth;

        Obstacle(double x, double z, double halfWidth, double halfDepth) {
            this.x = x;
            this.z = z;
            this.halfWidth = halfWidth;
            this.halfDepth = halfDepth;
        }
    }

    private static class Particle {

        Box node;
        double vx, vy, vz, life, fade, gravity;
        boolean bounce, blood, moving, flat;
    }

    private static class Tracer {

        Box node;
        double life;
    }

    private static class Fire {

        final Group flames = new Group();
        final PointLight light;
        final Box[] tongues = new Box[3];
        final double[] heights = {16, 11, 8};
        final double x, y, z;
        double phase = RNG.nextDouble() * 6;
        double emberTimer = 0;

        Fire(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            flames.setTranslateX(x);
            flames.setTranslateY(y);
            flames.setTranslateZ(z);
            PhongMaterial[] fm = {glow(Color.rgb(255, 90, 20)), glow(Color.rgb(255, 170, 40)), glow(Color.rgb(255, 235, 140))};
            double[] widths = {13, 9, 5};
            for (int i = 0; i < 3; i++) {
                tongues[i] = new Box(widths[i], heights[i], widths[i]);
                tongues[i].setMaterial(fm[i]);
                tongues[i].setTranslateY(-heights[i] / 2);
                flames.getChildren().add(tongues[i]);
            }
            light = new PointLight(Color.rgb(255, 130, 40));
            light.setTranslateX(x);
            light.setTranslateY(y - 20);
            light.setTranslateZ(z);
            light.setMaxRange(300);
        }
    }

    private static class Enemy {

        final Group model = new Group();
        final Rotate facing = new Rotate(0, Rotate.Y_AXIS);
        final Rotate fall = new Rotate(0, Rotate.X_AXIS);
        final Rig rig;
        final int type;
        final double scale, speedMul, dmgMul, limp, phaseOff;

        boolean alive = true;
        double health;
        double attackTimer = 1.0;
        double animationTime = 0;
        double walkPhase = 0;
        double spawnTimer = 1.2;
        double stagger = 0;
        double deathTime = 0;
        double yaw = 0;

        Enemy(double x, double z, int type) {
            this.type = type;
            scale = type == Z_BRUTE ? 1.3 : type == Z_RUNNER ? 0.95 : 0.97 + RNG.nextDouble() * 0.1;
            health = type == Z_BRUTE ? 300 : type == Z_RUNNER ? 60 : 100;
            speedMul = type == Z_RUNNER ? 1.9 : type == Z_BRUTE ? 0.65 : 0.85 + RNG.nextDouble() * 0.3;
            dmgMul = type == Z_BRUTE ? 1.8 : 1.0;
            limp = 0.4 + RNG.nextDouble() * 0.5;
            phaseOff = RNG.nextDouble() * 6.28;

            rig = buildZombie(type);
            Group inner = new Group(rig.root);
            inner.setTranslateY(-55);
            model.getChildren().add(inner);
            model.getTransforms().addAll(facing, fall, new Scale(scale * (type == Z_RUNNER ? 0.92 : 1.0), scale, scale));
            model.setTranslateX(x);
            model.setTranslateY(GROUND_Y + 62);
            model.setTranslateZ(z);
        }

        void animate(double dt, boolean moving, boolean attack) {
            animationTime += dt;
            double sp = type == Z_RUNNER ? 12 : type == Z_BRUTE ? 5.5 : 7.5;
            if (moving) {
                walkPhase += dt * sp;
            }
            double p = walkPhase + phaseOff;
            double amp = type == Z_RUNNER ? 38 : 24;
            double sw = Math.sin(p) * amp;
            double blend = moving ? 1 : 0.25;
            rig.lHipR.setAngle(sw * blend);
            rig.rHipR.setAngle(-sw * limp * blend);
            rig.lKneeR.setAngle(-Math.max(0, Math.cos(p)) * 35 * blend);
            rig.rKneeR.setAngle(-Math.max(0, -Math.cos(p)) * 20 * limp * blend);

            double t = animationTime + phaseOff;
            if (type == Z_RUNNER && moving) {
                rig.lShR.setAngle(55 - Math.sin(p) * 40);
                rig.rShR.setAngle(55 + Math.sin(p) * 40);
                rig.lElbR.setAngle(50);
                rig.rElbR.setAngle(50);
            } else if (attack) {
                rig.lShR.setAngle(95 + Math.sin(t * 11) * 20);
                rig.rShR.setAngle(95 - Math.sin(t * 11) * 20);
                rig.lElbR.setAngle(10);
                rig.rElbR.setAngle(10);
            } else {
                rig.lShR.setAngle(78 + Math.sin(t * 1.7) * 7);
                rig.rShR.setAngle(84 + Math.sin(t * 1.4) * 8);
                rig.lElbR.setAngle(14 + Math.sin(t * 2.1) * 6);
                rig.rElbR.setAngle(22 + Math.sin(t * 1.9) * 6);
            }
            rig.lShYaw.setAngle(3);
            rig.rShYaw.setAngle(-3);

            double lean0 = type == Z_RUNNER ? -28 : type == Z_BRUTE ? -10 : -14;
            rig.lean.setAngle(lean0 + (stagger > 0 ? stagger / 0.22 * 28 : 0));
            rig.twist.setAngle(Math.sin(p) * 5);
            rig.headX.setAngle(-8 + Math.sin(t * 1.3) * 5);
            rig.headZ.setAngle(Math.sin(t * 0.9) * 9);

            double open = attack ? 28 * Math.abs(Math.sin(t * 11)) : 10 + Math.abs(Math.sin(t * 2.2)) * 10;
            rig.jaw.setAngle(-open);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
