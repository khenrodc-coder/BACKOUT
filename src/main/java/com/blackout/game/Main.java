package com.blackout.game;

import com.blackout.game.core.Action;
import com.blackout.game.core.Dims;
import com.blackout.game.core.GameState;
import com.blackout.game.core.Gfx;
import com.blackout.game.core.Mats;
import com.blackout.game.core.Settings;
import com.blackout.game.effects.*;
import com.blackout.game.entities.*;
import com.blackout.game.environment.*;
import com.blackout.game.save.SaveData;
import com.blackout.game.save.SaveManager;
import com.blackout.game.systems.ActionLock;
import com.blackout.game.systems.Interactable;
import com.blackout.game.systems.InteractionSystem;
import com.blackout.game.systems.ReloadPoses;
import com.blackout.game.systems.CombatSystem;
import com.blackout.game.systems.ZombieAISystem;
import com.blackout.game.systems.SurvivalSystem;
import com.blackout.game.systems.DayNightSystem;
import com.blackout.game.systems.WeatherSystem;
import com.blackout.game.systems.InventorySystem;
import com.blackout.game.systems.SpawnSystem;
import com.blackout.game.systems.AudioManager;
import com.blackout.game.systems.CameraController;
import com.blackout.game.systems.AnimationController;
import com.blackout.game.world.ChunkManager;
import com.blackout.game.world.DistrictPlan;
import com.blackout.game.world.WorldState;
import com.blackout.game.ui.MainMenu;
import com.blackout.game.ui.SettingsPanel;
import com.blackout.game.ui.WorldMapView;

import static com.blackout.game.core.Constants.*;
import static com.blackout.game.core.Gfx.*;
import static com.blackout.game.entities.RigFactory.*;

import java.util.ArrayList;
import java.util.Comparator;
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
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.effect.Bloom;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Sphere;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import javafx.util.Duration;

public class Main extends javafx.application.Application {

    // ===================== SWITCHES / CONSTANTS =====================
    private static final boolean POST_FX = true;            // bloom + grading (disable if FPS is low)
    private static final int MAX_PARTICLES = 380;
    private static final int TREE_COUNT = 220;
    private static final double VIEW_BUILD = 1300;          // draw distance for buildings / cars
    private static final double VIEW_SMALL = 900;           // draw distance for trees / small props
    private static final double WORLD_BOUNDARY = 1480;          // west, east and south edge
    private static final double WORLD_MAX_Z = 3320;              // the downtown district extends the map to the north

    private static double clampX(double x) {
        return Math.max(-WORLD_BOUNDARY, Math.min(WORLD_BOUNDARY, x));
    }

    private static double clampZ(double z) {
        return Math.max(-WORLD_BOUNDARY, Math.min(WORLD_MAX_Z, z));
    }
    private static final double WALL_T = 6;
    private static final double DOOR_W = 50;
    private static final double CELL = 160;
    private static final double JUMP_FORCE = 380;
    private static final double GRAVITY = 950;
    private static final double WALK_SPEED = 190;
    private static final double SPRINT_SPEED = 320;
    private static final double CROUCH_SPEED = 95;
    private static final double PLAYER_RADIUS = 16;
    private static final double CAMERA_DISTANCE = 600;
    private static final double GUN_X = 8, GUN_Y = -11, GUN_Z = 40;
    private static final double LAKE_X = -880, LAKE_Z = -330, LAKE_RX = 250, LAKE_RZ = 170;
    private static final double RIVER_Z = -900;
    // ===================== PLAYER STATE =====================
    private Group player, weaponModel, muzzleFlash;
    private Rig playerRig;
    private final Rotate playerFacing = new Rotate(0, Rotate.Y_AXIS);
    private final Rotate weaponPitch = new Rotate(0, Rotate.X_AXIS);
    private PointLight muzzleLight, torchLight;
    private double muzzleLocalY = -9, muzzleLocalZ = 34;

    private boolean isAiming, sprintingNow, crouched, climbing, firstPerson, torchOn, throwing, threw, usingMed, firing;
    private boolean grounded = true;
    private double feetY = GROUND_Y, velY, playerYaw, walkPhase, climbPhase, moveBlend, aimBlend, crouchBlend, airBlend,
            landBlend, climbBlend, sprintBlend, swapBlend, weaponKick, stepTimer, strafeInput, animationTime,
            muzzleFlashTimer, worldTime, shake, damageFlash, currentFov = 32, camRoll, insideBlend, insideH, lastSpaceTime = -10,
            climbT, climbSX, climbSY, climbSZ, climbTX, climbTY, climbTZ, reloadTimer, reloadDur = 1, throwT, medT, choke,
            health = 100, stamina = 100, survivalTime, threat, spawnClock = 3, lightTick, cullTick, hitDist, hitMarkerTimer,
            cameraRecoil, smoothedCamX, smoothedCamY, smoothedCamZ, smoothedYaw, smoothedPitch = -10.5,
            cameraYaw, cameraPitch = -10.5, mouseX, mouseY, crossGap = 8;
    private final double recoilRecovery = 12;
    private boolean hitHead;
    private int heldItem, prevHeld, medkits = 1, grenades = 2, kills, lastThreatInt, mapTick, lastSlotSel = -1;
    private String deathCause = "";
    private double gameMinutes = 7 * 60 + 30, bloodBlend, sleepT, sleepBlend, daylightNow, lastSkyMin = -999, lastSkyBlood = -1, supY = GROUND_Y;
    private boolean bloodMoon, sleeping;
    private int sleepPhase, lastClockMin = -1;
    private double[] sleepBed;
    private final List<double[]> beds = new ArrayList<>();
    private final Rotate sleepRot = new Rotate(0, 0, 55, 0, Rotate.X_AXIS);
    private PointLight celestial;
    private AmbientLight ambientLight;
    private PhongMaterial skyMat, voidMat;
    private Cylinder playerShadow;
    private Text dateText, clockText, periodText, promptText, sleepText;
    private Region bloodTint, sleepOverlay;

    private WeaponType currentWeapon = WeaponType.PISTOL;
    private final EnumMap<WeaponType, Integer> magazineAmmo = new EnumMap<>(WeaponType.class);
    private final EnumMap<WeaponType, Integer> reserveAmmo = new EnumMap<>(WeaponType.class);
    private long lastShotTime;

    private void initializeWeaponAmmo() {
        for (WeaponType w : WeaponType.values()) {
            magazineAmmo.put(w, w.magazineSize);
            reserveAmmo.put(w, w.startingReserve);
        }
        currentWeapon = WeaponType.PISTOL;
    }

    private double kickFor(WeaponType w) {
        return w == WeaponType.PISTOL ? 0.8 : w == WeaponType.ASSAULT_RIFLE ? 0.5 : 1.0;
    }

    private double reloadTimeFor(WeaponType w) {
        return w == WeaponType.PISTOL ? 0.9 : w == WeaponType.ASSAULT_RIFLE ? 1.6 : 2.0;
    }

    /**
     * rightShoulder, rightElbow, rightYaw, leftShoulder, leftElbow, leftYaw
     */
    private double[] armPose() {
        if (heldItem == 1) {
            return new double[]{40, 60, -8, 18, 25, 6};
        }
        if (heldItem == 2) {
            return new double[]{50, 65, -14, 50, 65, 22};
        }
        switch (currentWeapon) {
            case PISTOL:
                return new double[]{55, 38, -12, 55, 38, 32};
            case ASSAULT_RIFLE:
                return new double[]{38, 58, -8, 78, 6, 38};
            default:
                return new double[]{40, 55, -8, 76, 8, 36};
        }
    }

    // ===================== WORLD STATE =====================
    private Group world;
    private PerspectiveCamera camera;
    private Sphere sky;
    private final Set<KeyCode> keys = new HashSet<>();
    private boolean gameOver, paused;

    // ---- game state, menu, save/load, map, settings (3rd update)
    private enum Mode { MENU, NEW, LOAD }

    private Stage stage;
    private StackPane rootPane;
    private GameState state = GameState.MENU;
    private GameState settingsReturn = GameState.MENU;
    private long worldSeed;
    private AnimationTimer loop;
    private final Settings settings = Settings.get();
    private final SaveManager saves = SaveManager.standard();
    private MainMenu menu;
    private SettingsPanel settingsPanel;
    private WorldMapView worldMap;
    private SubScene gameScene;
    private Bloom bloom;
    private Region introFade;
    private Text pauseMsg;
    private final List<Node> hudNodes = new ArrayList<>();
    private final List<Object[]> poiDefs = new ArrayList<>();
    private double autosaveClock, exploreClock, menuTime;

    // ---- interaction + action transitions (4th stage)
    /** How close the player must be to a bed (prompt and sleeping use the same number). */
    private static final double BED_RANGE = 100;
    private final ActionLock lock = new ActionLock();
    private final InteractionSystem interactions = new InteractionSystem();
    private double reachBlend, hitReact, hitSide;

    private final Map<Long, List<Obstacle>> grid = new HashMap<>();
    private final List<Building> buildings = new ArrayList<>();
    private final List<Car> cars = new ArrayList<>();
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Pickup> pickups = new ArrayList<>();
    private final List<Grenade> grenadeList = new ArrayList<>();
    private final List<Explosion> explosions = new ArrayList<>();
    private final List<SmokeCloud> smokeClouds = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<Tracer> tracers = new ArrayList<>();
    private final List<DynLight> lights = new ArrayList<>();
    private final List<Fire> fires = new ArrayList<>();
    private final List<Sphere> beacons = new ArrayList<>();
    private final List<Rotate> beamRotates = new ArrayList<>();
    private final List<Box> motes = new ArrayList<>();
    private final List<double[]> moteVel = new ArrayList<>();
    private final List<Cull> culls = new ArrayList<>();
    private final List<double[]> roadRects = new ArrayList<>();
    private final List<TreeFactory.Tree> trees = new ArrayList<>();
    private final List<VegetationFactory.Chunk> grassChunks = new ArrayList<>();

    // ---- system classes (Phase 1 refactoring)
    private CombatSystem combatSystem;
    private ZombieAISystem zombieAISystem;
    private SurvivalSystem survivalSystem;
    private DayNightSystem dayNightSystem;
    private WeatherSystem weatherSystem;
    private InventorySystem inventorySystem;
    private SpawnSystem spawnSystem;
    private AudioManager audioManager;
    private CameraController cameraController;
    private AnimationController animationController;

    // ---- streamed downtown district (5th stage): chunks load and unload around the player, changes live in WorldState
    private final WorldState worldState = new WorldState();
    private ChunkManager<DistrictBuilder.Content> chunks;
    private int staticBuildingCount;
    private final Map<Building, Interactable> streamedDoors = new HashMap<>();

    // materials
    private PhongMaterial bloodMat, sparkMat, brassMat, dustMat, emberMat, tracerMat, moteMat, smokeMat, matConcrete, matWood,
            matBurlap, matRust, matMetalDark, matGlassDark, matGlassLit, matAsphalt, pine1, pine2, trunkMat, rockMat, snowMat,
            waterMat, sandMat, floorMat, couchMat, bedMat;
    // ===================== UI =====================
    private ProgressBar healthBar, staminaBar;
    private Text healthText, staminaText, enemyText, statusText, ammoText, killsText, threatText, timeText, weaponText,
            banner, hitMarker, finalStatsText;
    private FadeTransition bannerFade;
    private Region redFlash, gasOverlay;
    private final Rectangle[] ch = new Rectangle[4];
    private final StackPane[] slotPanes = new StackPane[6];
    private final Text[] slotCount = new Text[6];
    private StackPane gameOverOverlay, pauseOverlay;
    private Canvas miniMap;
    private GraphicsContext mgc;
    private Button viewButton;

    @Override
    public void start(Stage stage) {
        // if a valid save exists the world behind the menu is the saved world, so Continue is instant
        SaveManager.LoadResult lr = saves.load();
        long seed = lr.status() == SaveManager.Status.OK ? lr.data().seed : new Random().nextLong();
        beginSession(stage, seed, Mode.MENU, null);
    }

    /** Builds the whole game (world, UI, loop) for one world seed. A new Main instance is used per world. */
    private void beginSession(Stage st, long seed, Mode mode, SaveData save) {
        this.stage = st;
        this.worldSeed = seed;
        setState(GameState.MENU);
        reseed(seed);                       // same seed -> identical map
        initMaterials();
        Mats.init();
        initializeWeaponAmmo();
        world = new Group();
        createLighting(world);
        createSky(world);
        Mats.warmUp();
        createWorld(world);
        staticBuildingCount = buildings.size();
        chunks = new ChunkManager<>(DistrictPlan.CHUNK, 560, 780, 1, DistrictPlan::exists, new ChunkManager.Loader<DistrictBuilder.Content>() {
            @Override
            public DistrictBuilder.Content load(int cx, int cz) {
                return loadChunk(cx, cz);
            }

            @Override
            public void unload(int cx, int cz, DistrictBuilder.Content c) {
                unloadChunk(c);
            }
        });
        createPlayer(world);
        createCamera();
        createMotes(world);

        // Initialize system classes
        combatSystem = new CombatSystem(world, RNG, particles, tracers, explosions, grenadeList, smokeClouds, lights, hitMarker);
        combatSystem.setMuzzleFlash(muzzleFlash, muzzleLight, muzzleLocalY, muzzleLocalZ);

        zombieAISystem = new ZombieAISystem(world, RNG, enemies, buildings, cars, roadRects,
                smokeClouds, particles,
                e -> {},
                dmg -> damagePlayer(dmg, "Torn apart by the infected"),
                range -> alertZombies(range),
                () -> effThreat(),
                () -> bloodMoon,
                () -> player.getTranslateX(),
                () -> player.getTranslateZ(),
                () -> feetY,
                () -> crouched,
                () -> sprintingNow,
                () -> grounded);
        zombieAISystem.setStaticBuildingCount(staticBuildingCount);

        survivalSystem = new SurvivalSystem(RNG);

        dayNightSystem = new DayNightSystem(sky, celestial, ambientLight, skyMat, voidMat, RNG);

        weatherSystem = new WeatherSystem(world, RNG, particles, lights);

        inventorySystem = new InventorySystem();

        spawnSystem = new SpawnSystem(world, RNG, enemies, buildings, cars, roadRects, pickups,
                e -> {}, // onEnemySpawned
                p -> {}); // onPickupSpawned

        audioManager = new AudioManager();

        cameraController = new CameraController(camera, settings);

        animationController = new AnimationController(playerRig, weaponModel, muzzleFlash, weaponPitch,
                () -> feetY,
                () -> player.getTranslateX(),
                () -> player.getTranslateZ(),
                p -> emit(p.material, 1, p.x, p.y, p.z, p.vx, p.vy, p.vz, p.life, p.gravity, p.bounce, p.blood, 0),
                () -> grounded,
                () -> cameraPitch,
                () -> strafeInput,
                amt -> shake = Math.max(shake, amt));

        registerInteractables();
        if (mode != Mode.LOAD) {
            spawnInitialZombies();
        }
        if (mode == Mode.MENU) {
            spawnMenuZombies();
        }

        StackPane root = new StackPane();
        rootPane = root;
        root.setFocusTraversable(true);
        root.setStyle("-fx-background-color: black;");

        gameScene = new SubScene(world, 1280, 720, true,
                settings.quality == Settings.Quality.LOW ? SceneAntialiasing.DISABLED : SceneAntialiasing.BALANCED);
        gameScene.setFill(Color.rgb(8, 11, 15));
        gameScene.setCamera(camera);
        gameScene.widthProperty().bind(root.widthProperty());
        gameScene.heightProperty().bind(root.heightProperty());
        bloom = new Bloom(0.78);
        bloom.setInput(new ColorAdjust(0.0, 0.12, 0.03, 0.14));
        gameScene.setEffect(POST_FX && settings.quality.bloom ? bloom : null);

        VBox hud = createHUD();
        Group crosshair = createCrosshair();
        HBox hotbar = createHotbar();
        createMinimap();

        viewButton = new Button("FPP");
        viewButton.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        viewButton.setPrefWidth(100);
        viewButton.setPrefHeight(34);
        viewButton.setFocusTraversable(false);
        viewButton.setStyle("-fx-background-color: #d99b32; -fx-text-fill: white; -fx-background-radius: 6; -fx-cursor: hand;");
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
        gasOverlay = new Region();
        gasOverlay.setMouseTransparent(true);
        gasOverlay.setOpacity(0);
        gasOverlay.setStyle("-fx-background-color: radial-gradient(center 50% 50%, radius 90%, rgba(70,120,40,0.25) 10%, rgba(60,100,30,0.85) 100%);");

        banner = new Text("");
        banner.setFont(Font.font("Arial", FontWeight.BOLD, 50));
        banner.setFill(Color.GOLD);
        banner.setOpacity(0);
        banner.setMouseTransparent(true);
        banner.setEffect(new DropShadow(18, Color.BLACK));

        introFade = new Region();
        introFade.setStyle("-fx-background-color: black;");
        introFade.setMouseTransparent(true);

        bloodTint = new Region();
        bloodTint.setMouseTransparent(true);
        bloodTint.setOpacity(0);
        bloodTint.setStyle("-fx-background-color: radial-gradient(center 50% 50%, radius 80%, rgba(150,0,10,0.30), rgba(110,0,8,0.85));");
        sleepOverlay = new Region();
        sleepOverlay.setMouseTransparent(true);
        sleepOverlay.setOpacity(0);
        sleepOverlay.setStyle("-fx-background-color: black;");
        sleepText = new Text("Zzz...");
        sleepText.setFont(Font.font("Arial", FontWeight.BOLD, 46));
        sleepText.setFill(Color.GOLD);
        sleepText.setVisible(false);
        sleepText.setMouseTransparent(true);
        promptText = new Text("");
        promptText.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        promptText.setFill(Color.WHITE);
        promptText.setEffect(new DropShadow(10, Color.BLACK));
        promptText.setVisible(false);
        promptText.setMouseTransparent(true);
        dateText = new Text("JANUARY 1, 2099");
        dateText.setFont(Font.font("Arial", FontWeight.BOLD, 15));
        dateText.setFill(Color.WHITE);
        clockText = new Text("07:30 AM");
        clockText.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        clockText.setFill(Color.GOLD);
        periodText = new Text("MORNING");
        periodText.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        periodText.setFill(Color.GOLD);
        VBox timeBox = new VBox(3, dateText, clockText, periodText);
        timeBox.setPadding(new Insets(10, 16, 10, 16));
        timeBox.setMaxWidth(Region.USE_PREF_SIZE);
        timeBox.setMaxHeight(Region.USE_PREF_SIZE);
        timeBox.setMouseTransparent(true);
        timeBox.setStyle("-fx-background-color: rgba(12, 16, 22, 0.72); -fx-background-radius: 10; -fx-border-color: rgba(255,255,255,0.1); -fx-border-radius: 10;");
        root.getChildren().addAll(gameScene, vignette, gasOverlay, redFlash, bloodTint, hud, crosshair, hitMarker, miniMap, viewButton,
                hotbar, timeBox, promptText, banner, sleepOverlay, sleepText, introFade);
        StackPane.setAlignment(timeBox, Pos.BOTTOM_LEFT);
        StackPane.setMargin(timeBox, new Insets(0, 0, 18, 18));
        StackPane.setAlignment(promptText, Pos.BOTTOM_CENTER);
        StackPane.setMargin(promptText, new Insets(0, 0, 100, 0));
        gameOverOverlay = createGameOverOverlay();
        pauseOverlay = createPauseOverlay();
        root.getChildren().addAll(gameOverOverlay, pauseOverlay);

        worldMap = new WorldMapView(roadRects, buildings, new double[]{LAKE_X, LAKE_Z, LAKE_RX, LAKE_RZ}, RIVER_Z, 100, worldSeed);
        for (Object[] p : poiDefs) {
            worldMap.addPoi((String) p[0], (Double) p[1], (Double) p[2]);
        }
        settingsPanel = new SettingsPanel(settings, this::applySettings, this::closeSettings);
        menu = new MainMenu(new MainMenu.Actions() {
            @Override
            public void onContinue() {
                menuContinue();
            }

            @Override
            public void onNewGame() {
                menuNewGame();
            }

            @Override
            public void onSettings() {
                openSettings(GameState.MENU);
            }

            @Override
            public void onExit() {
                Platform.exit();
            }
        });
        root.getChildren().addAll(worldMap, menu, settingsPanel);
        hudNodes.addAll(List.of(hud, crosshair, hitMarker, miniMap, viewButton, hotbar, timeBox, promptText, banner));

        StackPane.setAlignment(hud, Pos.TOP_LEFT);
        StackPane.setAlignment(hitMarker, Pos.CENTER);
        StackPane.setAlignment(crosshair, Pos.CENTER);
        StackPane.setAlignment(banner, Pos.TOP_CENTER);
        StackPane.setMargin(banner, new Insets(110, 0, 0, 0));
        StackPane.setAlignment(miniMap, Pos.TOP_RIGHT);
        StackPane.setMargin(miniMap, new Insets(18, 18, 0, 0));
        StackPane.setAlignment(viewButton, Pos.TOP_RIGHT);
        StackPane.setMargin(viewButton, new Insets(218, 22, 0, 0));
        StackPane.setAlignment(hotbar, Pos.BOTTOM_CENTER);
        StackPane.setMargin(hotbar, new Insets(0, 0, 16, 0));

        Scene scene = new Scene(root, 1280, 720);
        setupKeyboard(scene);
        setupMouse(gameScene);
        scene.setOnScroll(e -> {
            if (e.getDeltaY() != 0) {
                cycleWeapon(e.getDeltaY() > 0 ? -1 : 1);
            }
        });
        scene.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            if (settingsPanel.isCapturing()) {      // the settings screen is waiting for a key to bind
                settingsPanel.offerKey(ev.getCode());
                ev.consume();
                return;
            }
            if (ev.getCode() == KeyCode.ESCAPE) {
                handleEscape();
                ev.consume();
            }
        });

        stage.setTitle("BLACKOUT: The Last Outpost [Open World]");
        stage.setScene(scene);
        stage.setFullScreen(settings.fullscreen);
        stage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);   // Esc pauses; fullscreen is a setting
        stage.setFullScreenExitHint("Press ESC to pause");
        stage.setMinWidth(1280);
        stage.setMinHeight(720);
        stage.show();

        updateCulling();
        updateCamera(1.0);
        root.requestFocus();
        startGameLoop();
        if (mode == Mode.MENU) {
            enterMenu();
        } else {
            if (mode == Mode.LOAD && save != null) {
                applySave(save);
            }
            beginRun(mode == Mode.NEW);
        }
    }

    // ===================== BUILD HELPERS =====================
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

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double dist(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2, dz = z1 - z2;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static Image noiseTexture(int size, Color base, double variation, double speckle, Color spk, int stripe) {
        WritableImage img = new WritableImage(size, size);
        PixelWriter pw = img.getPixelWriter();
        int cell = 16, n = size / cell + 2;
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
                    r = spk.getRed();
                    g = spk.getGreen();
                    bl = spk.getBlue();
                }
                pw.setColor(x, y, Color.color(clamp01(r), clamp01(g), clamp01(bl)));
            }
        }
        return img;
    }

    private static TriangleMesh makeCone(int sides) {
        TriangleMesh m = new TriangleMesh();
        m.getPoints().addAll(0f, -1f, 0f, 0f, 0f, 0f);
        for (int i = 0; i < sides; i++) {
            double a = Math.PI * 2 * i / sides;
            m.getPoints().addAll((float) Math.cos(a), 0f, (float) Math.sin(a));
        }
        m.getTexCoords().addAll(0f, 0f);
        for (int i = 0; i < sides; i++) {
            int p = 2 + i, q = 2 + (i + 1) % sides;
            m.getFaces().addAll(0, 0, q, 0, p, 0);
            m.getFaces().addAll(1, 0, p, 0, q, 0);
        }
        return m;
    }

    private static final TriangleMesh CONE8 = makeCone(8);
    private static final TriangleMesh CONE7 = makeCone(7);

    private void initMaterials() {
        bloodMat = mat(Color.rgb(120, 8, 8), Color.rgb(220, 60, 60), 60);
        sparkMat = glow(Color.rgb(255, 220, 120));
        brassMat = mat(Color.rgb(200, 160, 60), Color.WHITE, 90);
        dustMat = mat(Color.rgb(110, 105, 95, 0.55), Color.BLACK, 1);
        smokeMat = mat(Color.rgb(75, 100, 62, 0.35), Color.BLACK, 1);
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
        pine1 = mat(Color.rgb(22, 48, 30), Color.rgb(10, 20, 10), 6);
        pine2 = mat(Color.rgb(30, 62, 38), Color.rgb(10, 20, 10), 6);
        trunkMat = mat(Color.rgb(60, 42, 28), Color.BLACK, 1);
        rockMat = mat(Color.rgb(62, 66, 72), Color.rgb(20, 20, 22), 8);
        snowMat = mat(Color.rgb(205, 215, 232), Color.rgb(120, 130, 150), 30);
        waterMat = mat(Color.rgb(18, 52, 80, 0.85), Color.WHITE, 110);
        sandMat = mat(Color.rgb(110, 98, 70), Color.BLACK, 1);
        floorMat = mat(Color.rgb(70, 52, 38), Color.rgb(30, 25, 20), 10);
        couchMat = mat(Color.rgb(70, 40, 40), Color.BLACK, 1);
        bedMat = mat(Color.rgb(60, 70, 96), Color.BLACK, 1);
    }

    private void createLighting(Group w) {
        celestial = new PointLight(Color.rgb(150, 175, 235));
        celestial.setTranslateX(300);
        celestial.setTranslateY(-900);
        celestial.setTranslateZ(-700);
        celestial.setMaxRange(3500);
        ambientLight = new AmbientLight(Color.rgb(62, 74, 102));
        w.getChildren().addAll(celestial, ambientLight);
    }

    private void createSky(Group w) {
        skyMat = new PhongMaterial(Color.BLACK);
        skyMat.setSpecularColor(Color.BLACK);
        sky = new Sphere(3000, 48);
        sky.setMaterial(skyMat);
        sky.setCullFace(CullFace.NONE);
        sky.setMouseTransparent(true);
        w.getChildren().add(sky);
        refreshSky(true);
    }

    // ===================== CLOCK / CALENDAR / DAY-NIGHT / BLOOD MOON =====================
    private static final double TIME_SCALE = 2.0;   // game minutes per real second (1 day = 12 real minutes)
    private static final String[] MONTHS = {"JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY", "AUGUST",
        "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"};
    private static boolean leap(int y) {
        return y % 4 == 0 && (y % 100 != 0 || y % 400 == 0);
    }

    private static int daysIn(int y, int m) {
        int[] d = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        return m == 1 && leap(y) ? 29 : d[m];
    }

    /**
     * Returns {year, monthIndex, dayOfMonth} for a day index counted from
     * January 1, 2099.
     */
    private static int[] dateFromDay(long day) {
        int y = 2099, m = 0;
        long d = day;
        while (d >= daysIn(y, m)) {
            d -= daysIn(y, m);
            if (++m == 12) {
                m = 0;
                y++;
            }
        }
        return new int[]{y, m, (int) d + 1};
    }

    private static String dateString(long day) {
        int[] d = dateFromDay(day);
        return MONTHS[d[1]] + " " + d[2] + ", " + d[0];
    }

    private double hourOfDay() {
        return (gameMinutes % 1440) / 60.0;
    }

    private boolean isNightNow() {
        double h = hourOfDay();
        return h >= 20 || h < 6;
    }

    /**
     * Blood Moon: nights of the third week (days 15-21) of every in-game month.
     */
    private boolean isBloodMoonTime() {
        if (!isNightNow()) {
            return false;
        }
        long day = (long) gameMinutes / 1440;
        long nightDay = hourOfDay() < 6 ? day - 1 : day;
        if (nightDay < 0) {
            return false;
        }
        return (dateFromDay(nightDay)[2] - 1) / 7 == 2;
    }

    private double effThreat() {
        return threat + (bloodMoon ? 3 : 0);
    }

    private static double smooth(double t) {
        t = clamp01(t);
        return t * t * (3 - 2 * t);
    }

    private static double daylightAt(double h) {
        if (h < 5) {
            return 0;
        }
        if (h < 7.5) {
            return smooth((h - 5) / 2.5);
        }
        if (h < 17) {
            return 1;
        }
        if (h < 19.5) {
            return 1 - smooth((h - 17) / 2.5);
        }
        return 0;
    }

    private static Color mixC(Color a, Color b, double t) {
        t = clamp01(t);
        return Color.color(a.getRed() + (b.getRed() - a.getRed()) * t, a.getGreen() + (b.getGreen() - a.getGreen()) * t,
                a.getBlue() + (b.getBlue() - a.getBlue()) * t);
    }

    private static Color voidColor(double d) {
        return mixC(Color.rgb(9, 12, 12), Color.rgb(62, 80, 54), d);
    }

    private void updateClock(double dt) {
        if (!sleeping) {
            gameMinutes += dt * TIME_SCALE;
        }
        boolean bm = isBloodMoonTime();
        if (bm != bloodMoon) {
            bloodMoon = bm;
            for (Enemy e : enemies) {
                e.setBlood(bm);
            }
            showBanner(bm ? "THE BLOOD MOON RISES" : "THE BLOOD MOON FADES", bm ? Color.CRIMSON : Color.LIGHTGRAY);
            status(bm ? "BLOOD MOON: THE DEAD ARE RUNNING!" : "THE BLOOD MOON HAS ENDED", bm ? Color.CRIMSON : Color.LIGHTGRAY);
        }
        double target = bloodMoon ? 1 : 0;
        bloodBlend += (target - bloodBlend) * Math.min(1, dt * 0.8);
        if (Math.abs(bloodBlend - target) < 0.02) {
            bloodBlend = target;
        }
        updateLighting();
        if (Math.abs(gameMinutes - lastSkyMin) >= 10 || Math.abs(bloodBlend - lastSkyBlood) > 0.1
                || (bloodBlend == target && lastSkyBlood != target)) {
            refreshSky(false);
        }
        int mi = (int) gameMinutes;
        if (mi != lastClockMin && dateText != null) {
            lastClockMin = mi;
            int mod = mi % 1440, hr = mod / 60, mn = mod % 60;
            dateText.setText(dateString(mi / 1440));
            clockText.setText(String.format("%02d:%02d %s", hr % 12 == 0 ? 12 : hr % 12, mn, hr >= 12 ? "PM" : "AM"));
        }
        if (periodText != null) {
            double h = hourOfDay();
            String p = h >= 6 && h < 12 ? "MORNING" : h >= 12 && h < 17 ? "AFTERNOON" : h >= 17 && h < 20 ? "EVENING" : "NIGHT";
            periodText.setText(bloodMoon ? p + "  -  BLOOD MOON" : p);
            periodText.setFill(bloodMoon ? Color.CRIMSON : h >= 6 && h < 17 ? Color.GOLD : h >= 17 && h < 20 ? Color.ORANGE : Color.LIGHTSKYBLUE);
        }
    }

    private void updateLighting() {
        double h = hourOfDay(), d = daylightAt(h), warm = 4 * d * (1 - d), bb = bloodBlend;
        daylightNow = d;
        Color moonC = mixC(Color.rgb(150, 175, 235), Color.rgb(215, 36, 34), bb);
        Color sunC = mixC(Color.rgb(235, 228, 205), Color.rgb(255, 150, 80), warm);
        celestial.setColor(mixC(moonC, sunC, d));
        Color ambN = mixC(Color.rgb(62, 74, 102), Color.rgb(122, 34, 40), bb);
        Color ambD = mixC(Color.rgb(112, 120, 134), Color.rgb(150, 112, 92), warm * 0.6);
        ambientLight.setColor(mixC(ambN, ambD, d));
        double f = d > 0.25 ? clamp01((h - 6) / 13.0) : clamp01(((h - 19 + 24) % 24) / 11.0);
        celestial.setTranslateX((f - 0.5) * 2600);
        celestial.setTranslateY(-Math.sin(Math.PI * f) * 1500 - 100);
        celestial.setTranslateZ(-700);
        if (voidMat != null) {
            voidMat.setDiffuseColor(voidColor(d));
        }
        SHADOW_MAT.setDiffuseColor(Color.rgb(0, 0, 0, 0.15 + 0.45 * d));
    }

    private static int argb(double r, double g, double b) {
        return 0xFF000000 | ((int) Math.max(0, Math.min(255, r)) << 16) | ((int) Math.max(0, Math.min(255, g)) << 8) | (int) Math.max(0, Math.min(255, b));
    }

    private void drawDisc(WritableImage img, double cx, double cy, double rad, double glowRad, Color core, Color glw, double strength) {
        PixelWriter pw = img.getPixelWriter();
        int w = (int) img.getWidth(), hh = (int) img.getHeight();
        for (int y = (int) (cy - glowRad); y <= cy + glowRad; y++) {
            for (int x = (int) (cx - glowRad); x <= cx + glowRad; x++) {
                if (x < 0 || y < 0 || x >= w || y >= hh) {
                    continue;
                }
                double dd = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
                if (dd < rad) {
                    pw.setArgb(x, y, argb(core.getRed() * 255, core.getGreen() * 255, core.getBlue() * 255));
                } else if (dd < glowRad) {
                    double a = Math.pow(1 - (dd - rad) / (glowRad - rad), 2.2) * strength;
                    int o = img.getPixelReader().getArgb(x, y);
                    pw.setArgb(x, y, argb(((o >> 16) & 255) + glw.getRed() * 255 * a, ((o >> 8) & 255) + glw.getGreen() * 255 * a, (o & 255) + glw.getBlue() * 255 * a));
                }
            }
        }
    }

    private void refreshSky(boolean force) {
        double h = hourOfDay(), d = daylightAt(h), warm = 4 * d * (1 - d), bb = bloodBlend;
        int w = 512, hgt = 256;
        double hv = 0.52;
        WritableImage img = new WritableImage(w, hgt);
        PixelWriter pw = img.getPixelWriter();
        Color top = mixC(mixC(Color.rgb(3, 5, 14), Color.rgb(40, 3, 7), bb), Color.rgb(58, 118, 200), d);
        Color hor = mixC(mixC(mixC(Color.rgb(30, 44, 70), Color.rgb(105, 16, 16), bb), Color.rgb(165, 205, 235), d), Color.rgb(255, 140, 70), warm * 0.75);
        Color vc = voidColor(d);
        for (int y = 0; y < hgt; y++) {
            double v = y / (double) (hgt - 1);
            for (int x = 0; x < w; x++) {
                double r, g, b;
                if (v < hv) {
                    double t = Math.pow(v / hv, 1.7);
                    r = (top.getRed() * (1 - t) + hor.getRed() * t) * 255;
                    g = (top.getGreen() * (1 - t) + hor.getGreen() * t) * 255;
                    b = (top.getBlue() * (1 - t) + hor.getBlue() * t) * 255;
                    double cl = Math.sin(x * 0.042 + y * 0.1) * Math.sin(x * 0.022 - y * 0.062) + Math.sin(x * 0.094 + 2) * 0.4;
                    if (cl > 0.2) {
                        double c = (cl - 0.2) * 14 * (1 - t * 0.5) * (0.4 + 1.4 * d);
                        r += c;
                        g += c;
                        b += c * 1.2;
                    }
                } else {
                    double t = Math.min(1, (v - hv) / 0.08);
                    r = (hor.getRed() * (1 - t) + vc.getRed() * t) * 255;
                    g = (hor.getGreen() * (1 - t) + vc.getGreen() * t) * 255;
                    b = (hor.getBlue() * (1 - t) + vc.getBlue() * t) * 255;
                }
                pw.setArgb(x, y, argb(r, g, b));
            }
        }
        double night = 1 - d;
        if (night > 0.05) {
            int n = (int) (700 * night);
            for (int i = 0; i < n; i++) {
                double br = (110 + RNG.nextInt(145)) * night;
                pw.setArgb(RNG.nextInt(w), RNG.nextInt((int) (hgt * 0.46)), argb(br, br * (1 - bb * 0.5), br * (1 - bb * 0.6) + 10));
            }
        }
        double fs = (h - 6) / 13.0;
        if (fs >= 0 && fs <= 1) {
            drawDisc(img, (0.25 + 0.5 * fs) * w, (hv - Math.sin(Math.PI * fs) * 0.40) * hgt, 10, 46, Color.rgb(255, 248, 220), Color.rgb(255, 200, 110), 0.7);
        }
        double fm = ((h - 19 + 24) % 24) / 11.0;
        if (fm >= 0 && fm <= 1 && d < 0.6) {
            double vis = 1 - d / 0.6;
            Color core = mixC(Color.rgb(235, 240, 255), Color.rgb(150, 12, 18), bb);
            Color glw = mixC(Color.rgb(150, 175, 235), Color.rgb(255, 20, 20), bb);
            drawDisc(img, (0.25 + 0.5 * fm) * w, (hv - Math.sin(Math.PI * fm) * 0.40) * hgt, 11 + bb * 2, 40 + bb * 24, core, glw, (0.5 + 0.4 * bb) * vis);
        }
        skyMat.setSelfIlluminationMap(img);
        lastSkyMin = gameMinutes;
        lastSkyBlood = bloodBlend;
    }

    // ===================== SLEEPING =====================
    private double[] nearestBed() {
        if (Math.abs(feetY - GROUND_Y) > 45) {
            return null;
        }
        double px = player.getTranslateX(), pz = player.getTranslateZ(), bd = BED_RANGE;
        double[] best = null;
        for (double[] b : beds) {
            double d = dist(px, pz, b[0], b[1]);
            if (d < bd) {
                bd = d;
                best = b;
            }
        }
        return best;
    }

    private void trySleep() {
        if (sleeping || paused || gameOver || climbing || lock.busy()) {
            return;
        }
        double[] bed = nearestBed();
        if (bed == null) {
            return;
        }
        if (!isNightNow()) {
            status("YOU CAN ONLY SLEEP AT NIGHT", Color.ORANGE);
            return;
        }
        double px = player.getTranslateX(), pz = player.getTranslateZ();
        for (Enemy e : enemies) {
            if (e.alive && e.state == S_CHASE && e.hiddenTimer <= 0 && dist(px, pz, e.model.getTranslateX(), e.model.getTranslateZ()) < 250) {
                status("TOO DANGEROUS TO SLEEP - ZOMBIES NEARBY", Color.ORANGERED);
                return;
            }
        }
        cancelActions();
        isAiming = false;
        crouched = false;
        keys.clear();
        sleeping = true;
        sleepPhase = 0;
        sleepT = 0;
        sleepBed = bed;
    }

    private void updateSleep(double dt) {
        sleepT += dt;
        double target = 0;
        if (sleepPhase == 0) {
            sleepOverlay.setOpacity(Math.min(1, sleepT / 1.4));
            target = sleepT > 0.5 ? 1 : 0;
            if (sleepT >= 1.4) {
                player.setTranslateX(sleepBed[0]);
                player.setTranslateZ(sleepBed[1]);
                playerYaw = 0;
                playerFacing.setAngle(0);
                feetY = supportY(sleepBed[0], sleepBed[1], feetY);
                grounded = true;
                velY = 0;
                long day = (long) gameMinutes / 1440;
                gameMinutes = (gameMinutes % 1440 >= 360 ? day + 1 : day) * 1440 + 360;
                health = Math.min(100, health + 35);
                stamina = 100;
                choke = 0;
                smokeClouds.clear();
                for (Enemy e : enemies) {
                    e.alert = 0;
                }
                sleepPhase = 1;
                sleepT = 0;
                sleepText.setVisible(true);
            }
            target = 1;
        } else if (sleepPhase == 1) {
            target = 1;
            sleepOverlay.setOpacity(1);
            if (sleepT >= 1.2) {
                sleepPhase = 2;
                sleepT = 0;
                sleepText.setVisible(false);
                showBanner("GOOD MORNING - " + dateString((long) gameMinutes / 1440), Color.GOLD);
            }
        } else {
            target = sleepT < 0.6 ? 1 : 0;
            sleepOverlay.setOpacity(1 - Math.min(1, sleepT / 1.6));
            if (sleepT >= 1.6) {
                sleeping = false;
                sleepOverlay.setOpacity(0);
                status("YOU WOKE UP RESTED", Color.LIGHTGREEN);
            }
        }
        sleepBlend += (target - sleepBlend) * Math.min(1, dt * 4);
        sleepRot.setAngle(90 * sleepBlend);
        player.setTranslateY(feetY - 55 - 8 * sleepBlend);
        if (!sleeping) {
            sleepRot.setAngle(0);
            sleepBlend = 0;
            player.setTranslateY(feetY - 55);
        }
    }

    // ===================== COLLISION GRID =====================
    private static long cellKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    private Obstacle addObs(double x, double z, double w, double d, double hAbove, boolean zombiePass) {
        Obstacle o = new Obstacle(x, z, w / 2, d / 2, GROUND_Y - hAbove, zombiePass);
        int x0 = (int) Math.floor((x - w / 2) / CELL), x1 = (int) Math.floor((x + w / 2) / CELL);
        int z0 = (int) Math.floor((z - d / 2) / CELL), z1 = (int) Math.floor((z + d / 2) / CELL);
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                grid.computeIfAbsent(cellKey(cx, cz), k -> new ArrayList<>()).add(o);
            }
        }
        return o;
    }

    private boolean blocked(double x, double z, double r, double fy, boolean isPlayer) {
        int x0 = (int) Math.floor((x - r) / CELL), x1 = (int) Math.floor((x + r) / CELL);
        int z0 = (int) Math.floor((z - r) / CELL), z1 = (int) Math.floor((z + r) / CELL);
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                List<Obstacle> l = grid.get(cellKey(cx, cz));
                if (l == null) {
                    continue;
                }
                for (Obstacle o : l) {
                    if (!o.active) {
                        continue;
                    }
                    if (isPlayer) {
                        if (fy <= o.top + 12) {
                            continue;
                        }
                    } else if (o.zombiePass) {
                        continue;
                    }
                    double qx = Math.max(o.x - o.halfWidth, Math.min(x, o.x + o.halfWidth));
                    double qz = Math.max(o.z - o.halfDepth, Math.min(z, o.z + o.halfDepth));
                    double dx = x - qx, dz = z - qz;
                    if (dx * dx + dz * dz < r * r) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private double supportY(double x, double z, double fy) {
        double best = GROUND_Y;
        List<Obstacle> l = grid.get(cellKey((int) Math.floor(x / CELL), (int) Math.floor(z / CELL)));
        if (l != null) {
            for (Obstacle o : l) {
                if (o.active && o.top > GROUND_Y - 500 && Math.abs(x - o.x) <= o.halfWidth && Math.abs(z - o.z) <= o.halfDepth
                        && o.top >= fy - 14 && o.top < best) {
                    best = o.top;
                }
            }
        }
        return best;
    }

    private Obstacle obstacleAt(double x, double z) {
        Obstacle best = null;
        List<Obstacle> l = grid.get(cellKey((int) Math.floor(x / CELL), (int) Math.floor(z / CELL)));
        if (l != null) {
            for (Obstacle o : l) {
                if (o.active && o.top > GROUND_Y - 500 && Math.abs(x - o.x) <= o.halfWidth && Math.abs(z - o.z) <= o.halfDepth
                        && (best == null || o.top < best.top)) {
                    best = o;
                }
            }
        }
        return best;
    }

    private Building buildingAt(double x, double z) {
        for (Building b : buildings) {
            if (b.contains(x, z)) {
                return b;
            }
        }
        return null;
    }

    private void registerCull(Node n, double x, double z, double view) {
        Cull c = new Cull();
        c.node = n;
        c.x = x;
        c.z = z;
        c.view = view;
        culls.add(c);
    }

    private void updateCulling() {
        double px = player.getTranslateX(), pz = player.getTranslateZ();
        for (Cull c : culls) {
            boolean v = dist(px, pz, c.x, c.z) < c.view * settings.quality.viewScale;
            if (c.node.isVisible() != v) {
                c.node.setVisible(v);
            }
        }
    }

    private DynLight addDynLight(double x, double y, double z, int kind, Color base, double range) {
        PointLight pl = new PointLight(base);
        pl.setTranslateX(x);
        pl.setTranslateY(y);
        pl.setTranslateZ(z);
        pl.setMaxRange(range);
        pl.setLightOn(false);
        DynLight d = new DynLight(pl, x, z, kind, base);
        lights.add(d);
        world.getChildren().add(pl);
        return d;
    }

    private boolean onRoad(double x, double z, double m) {
        for (double[] r : roadRects) {
            if (Math.abs(x - r[0]) < r[2] / 2 + m && Math.abs(z - r[1]) < r[3] / 2 + m) {
                return true;
            }
        }
        return false;
    }

    private boolean inLake(double x, double z, double m) {
        double a = (x - LAKE_X) / (LAKE_RX + m), b = (z - LAKE_Z) / (LAKE_RZ + m);
        return a * a + b * b < 1;
    }

    // ===================== WORLD =====================
    private void createWorld(Group w) {
        Box voidPlane = new Box(9000, 2, 9000);
        voidPlane.setTranslateY(112);
        voidMat = mat(Color.rgb(9, 12, 12), Color.BLACK, 1);
        voidPlane.setMaterial(voidMat);
        w.getChildren().add(voidPlane);
        // tiled grass (three texture variants so the ground does not look uniform)
        PhongMaterial[] grass = {Mats.GRASS_A, Mats.GRASS_B, Mats.GRASS_C};
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 9; j++) {                    // nine rows: the last three lie under the downtown district
                w.getChildren().add(Gfx.plane(-1500 + i * 600, -1500 + j * 600, 600, 600, 60, GROUND_Y - 0.1, grass[(i * 3 + j * 5 + (i ^ j)) % 3]));
            }
        }
        addRoad(0, 0, 160, 3300);
        addRoad(-865, 700, 1570, 140);
        addRoad(865, 700, 1570, 140);
        addRoad(600, 1210, 90, 880);
        addRoad(-600, 1210, 90, 880);
        addRoad(0, 2450, 160, 1600);                                          // the avenue continues north into downtown
        for (int k = DistrictPlan.MIN_CX; k <= DistrictPlan.MAX_CX + 1; k++) {
            if (k != 0) {
                addRoad(k * DistrictPlan.CHUNK, 2400, 64, 1664);
            }
        }
        for (int k = DistrictPlan.MIN_CZ; k <= DistrictPlan.MAX_CZ + 1; k++) {
            addRoad(0, k * DistrictPlan.CHUNK, 2464, 64);
        }
        PhongMaterial paint = mat(Color.rgb(210, 195, 130), Color.WHITE, 64);
        for (int z = -1450; z <= 1450; z += 120) {
            if (Math.abs(z - 700) > 110) {
                w.getChildren().add(box(5, 2, 38, paint, 0, 105, z));
            }
        }
        for (int x = -1450; x <= 1450; x += 120) {
            if (Math.abs(x) > 120 && Math.abs(Math.abs(x) - 600) > 70) {
                w.getChildren().add(box(38, 2, 5, paint, x, 105, 700));
            }
        }
        createRiver();
        createLake();
        createMountains();
        createOutpost();
        createTown();
        createSuburbs();
        createForest();
        createCars();
        createStreetLights();
        createRuins();
        createRadioTowers();
        createGrass();
        createDebris();
        poiDefs.add(new Object[]{"Last Outpost", 0.0, 0.0});
        poiDefs.add(new Object[]{"Old Town", 0.0, 700.0});
        poiDefs.add(new Object[]{"River Bridge", 0.0, RIVER_Z});
        poiDefs.add(new Object[]{"Footbridge", 700.0, RIVER_Z});
        poiDefs.add(new Object[]{"Lake", LAKE_X, LAKE_Z});
        poiDefs.addAll(DistrictPlan.landmarks(worldSeed));
        // mountains along the east and west edges of downtown (made last so the older world content is unchanged)
        for (int i = 0; i < 12; i++) {
            double off = 1750 + RNG.nextDouble() * 450, t = 1750 + RNG.nextDouble() * 1800;
            addMountain(i % 2 == 0 ? -off : off, t, 260 + RNG.nextDouble() * 230, 300 + RNG.nextDouble() * 430);
        }
    }

    private void addRoad(double cx, double cz, double w, double d) {
        world.getChildren().add(box(w, 3, d, matAsphalt, cx, 108.5, cz));
        world.getChildren().add(Gfx.plane(cx, cz, w, d, 64, 106.5, Mats.ASPHALT));
        roadRects.add(new double[]{cx, cz, w, d});
    }

    private void createRiver() {
        world.getChildren().add(box(3400, 1, 100, waterMat, 0, 109.7, RIVER_Z));
        double[][] seg = {{-1700, -95}, {95, 680}, {720, 1700}};
        for (double[] s : seg) {
            addObs((s[0] + s[1]) / 2, RIVER_Z, s[1] - s[0], 100, 1500, false);
        }
        for (double sx : new double[]{-93, 93}) {
            addObs(sx, RIVER_Z, 3, 112, 20, false);
        }
        Group bridge = BridgeFactory.build(0, RIVER_Z, 190, 112, GROUND_Y, 105.5, true, RNG);
        world.getChildren().add(bridge);
        registerCull(bridge, 0, RIVER_Z, 1500);
        world.getChildren().add(box(40, 3, 112, matWood, 700, 107, RIVER_Z));
        for (double sx : new double[]{680, 720}) {
            world.getChildren().add(box(2, 8, 112, matWood, sx, 102, RIVER_Z));
        }
    }

    private void createLake() {
        Cylinder sand = cylY(1, 1, sandMat, LAKE_X, 109.8, LAKE_Z, 64);
        sand.setScaleX(LAKE_RX + 18);
        sand.setScaleZ(LAKE_RZ + 18);
        Cylinder water = cylY(1, 1, waterMat, LAKE_X, 109.4, LAKE_Z, 64);
        water.setScaleX(LAKE_RX);
        water.setScaleZ(LAKE_RZ);
        world.getChildren().addAll(sand, water);
        int n = 7;
        double sl = LAKE_RZ * 2 / n;
        for (int k = 0; k < n; k++) {
            double zc = -LAKE_RZ + sl * (k + 0.5);
            double hx = LAKE_RX * Math.sqrt(Math.max(0.02, 1 - (zc / LAKE_RZ) * (zc / LAKE_RZ)));
            addObs(LAKE_X, LAKE_Z + zc, hx * 2, sl + 1, 1500, false);
        }
        world.getChildren().add(box(16, 2, 70, matWood, LAKE_X + LAKE_RX + 20, 107, LAKE_Z));
        for (int i = 0; i < 10; i++) {
            double a = RNG.nextDouble() * 6.28, rr = 1.05 + RNG.nextDouble() * 0.15;
            double s = 6 + RNG.nextDouble() * 12;
            world.getChildren().add(sph(s, rockMat, LAKE_X + Math.cos(a) * (LAKE_RX + 30) * rr, GROUND_Y - s * 0.3,
                    LAKE_Z + Math.sin(a) * (LAKE_RZ + 30) * rr, 1, 0.7, 1));
        }
    }

    private void createMountains() {
        for (int i = 0; i < 24; i++) {
            int side = i % 4;
            double t = RNG.nextDouble() * 3400 - 1700, off = 1750 + RNG.nextDouble() * 450;
            double x = side == 0 ? t : side == 1 ? t : side == 2 ? -off : off;
            double z = side == 0 ? -off : side == 1 ? off + (WORLD_MAX_Z - WORLD_BOUNDARY) : t;   // the north range sits beyond downtown
            addMountain(x, z, 260 + RNG.nextDouble() * 230, 300 + RNG.nextDouble() * 430);
        }
    }

    private void addMountain(double x, double z, double rx, double h) {
        MeshView mv = new MeshView(i2() ? CONE8 : CONE7);
        mv.setMaterial(rockMat);
        mv.setCullFace(CullFace.NONE);
        mv.getTransforms().addAll(new Rotate(RNG.nextDouble() * 90, Rotate.Y_AXIS), new Scale(rx, h, rx * 0.85));
        mv.setTranslateX(x);
        mv.setTranslateY(GROUND_Y + 6);
        mv.setTranslateZ(z);
        world.getChildren().add(mv);
        if (h > 420) {
            MeshView cap = new MeshView(CONE8);
            cap.setMaterial(snowMat);
            cap.setCullFace(CullFace.NONE);
            cap.getTransforms().addAll(new Rotate(0, Rotate.Y_AXIS), new Scale(rx * 0.41, h * 0.4, rx * 0.35));
            cap.setTranslateX(x);
            cap.setTranslateY(GROUND_Y + 6 - h * 0.6);
            cap.setTranslateZ(z);
            world.getChildren().add(cap);
        }
    }

    private boolean i2() {
        return RNG.nextBoolean();
    }

    // ===================== OUTPOST =====================
    private void createOutpost() {
        addHouse(-190, 110, 170, 170, 100, 1, false, true, Color.rgb(96, 100, 92));
        addHouse(210, 190, 200, 170, 100, -1, false, true, Color.rgb(90, 96, 90));
        addHouse(-150, 340, 110, 90, 90, 1, false, true, Color.rgb(100, 98, 88));
        addHouse(-380, -250, 150, 120, 100, 1, false, true, Color.rgb(88, 94, 98));
        addHouse(350, -300, 180, 150, 100, -1, false, true, Color.rgb(92, 92, 86));

        fenceSeg(-300, -500, 400, true);
        fenceSeg(300, -500, 400, true);
        fenceSeg(-300, 500, 400, true);
        fenceSeg(300, 500, 400, true);
        fenceSeg(-500, 0, 1000, false);
        fenceSeg(500, 0, 1000, false);

        for (double[] p : new double[][]{{-300, 100}, {300, 100}, {-300, -100}, {300, -100}}) {
            addBarrier(p[0], p[1], 120);
        }
        addSandbags(-270, 0, 3);
        addSandbags(270, 0, 3);
        addSandbags(-80, 250, 4);
        addSandbags(80, 250, 4);
        double[][] crates = {{-310, -120}, {-350, -100}, {320, 80}, {350, 100}, {-60, -200}, {60, -230}};
        for (double[] c : crates) {
            addCrate(c[0], c[1]);
        }
        addBarrel(-250, -300, true);
        addBarrel(-220, -300, false);
        addBarrel(280, -350, true);
        addBarrel(150, 420, false);
        addTower(-460, -460, 1);
        addTower(460, 460, -1);
        addContainer(-420, 120, false);
        addContainer(420, -120, true);
        addContainer(-430, 400, true);
        addWreck(-48, -140, 14, false);
        addWreck(140, -60, 90, true);
    }

    private void fenceSeg(double ax, double az, double len, boolean alongX) {
        Group g = new Group();
        PhongMaterial mesh = new PhongMaterial(Color.rgb(150, 160, 160, 0.10));
        int posts = Math.max(2, (int) (len / 80) + 1);
        for (int i = 0; i < posts; i++) {
            double o = -len / 2 + i * len / (posts - 1);
            g.getChildren().add(box(8, 115, 8, matMetalDark, alongX ? ax + o : ax, 52, alongX ? az : az + o));
        }
        g.getChildren().add(box(alongX ? len : 2, 2.5, alongX ? 2 : len, matMetalDark, ax, 12, az));
        g.getChildren().add(box(alongX ? len : 2, 2.5, alongX ? 2 : len, matMetalDark, ax, 62, az));
        g.getChildren().add(box(alongX ? len : 1, 100, alongX ? 1 : len, mesh, ax, 56, az));
        world.getChildren().add(g);
        registerCull(g, ax, az, VIEW_BUILD + len / 2);
        addObs(ax, az, alongX ? len : 8, alongX ? 8 : len, 110, false);
    }

    private void addBarrier(double x, double z, double w) {
        Group g = new Group();
        g.getChildren().add(box(w, 48, 35, matConcrete, x, 85, z));
        g.getChildren().add(box(w - 6, 1.6, 8, glow(Color.rgb(255, 110, 20)), x, 60.6, z));
        world.getChildren().add(g);
        registerCull(g, x, z, VIEW_SMALL);
        addObs(x, z, w, 35, 48, false);
    }

    private void addSandbags(double x, double z, int count) {
        Group g = new Group();
        for (int i = 0; i < count; i++) {
            Box bag = box(42, 19, 23, matBurlap, x + (i % 2) * 8, 101 - (i / 2) * 17, z);
            bag.setRotationAxis(Rotate.Y_AXIS);
            bag.setRotate((RNG.nextDouble() - 0.5) * 8);
            g.getChildren().add(bag);
        }
        world.getChildren().add(g);
        registerCull(g, x, z, VIEW_SMALL);
        addObs(x + 4, z, 58, 23, 19 + (count > 2 ? 17 : 0), false);
    }

    private void addCrate(double x, double z) {
        Group g = new Group();
        g.getChildren().addAll(box(42, 42, 42, matWood, x, 89, z), box(44, 3, 44, matMetalDark, x, 69, z), box(44, 3, 44, matMetalDark, x, 109, z));
        world.getChildren().add(g);
        registerCull(g, x, z, VIEW_SMALL);
        addObs(x, z, 46, 46, 42, false);
    }

    private void addBarrel(double x, double z, boolean burning) {
        Group g = new Group();
        g.getChildren().addAll(cylY(19, 48, matRust, x, 86, z), cylY(19.6, 3, matMetalDark, x, 76, z), cylY(19.6, 3, matMetalDark, x, 96, z));
        world.getChildren().add(g);
        registerCull(g, x, z, VIEW_SMALL);
        addObs(x, z, 40, 40, 48, false);
        if (burning) {
            addFire(x, 62, z, 1.0, false);
        }
    }

    private void addContainer(double x, double z, boolean turned) {
        Group g = new Group();
        PhongMaterial m = mat(RNG.nextBoolean() ? Color.rgb(120, 52, 40) : Color.rgb(40, 76, 96), Color.rgb(80, 80, 80), 30);
        double lw = turned ? 120 : 50, ld = turned ? 50 : 120;
        g.getChildren().add(box(lw, 50, ld, m, x, GROUND_Y - 25, z));
        g.getChildren().add(box(lw + 2, 3, ld + 2, matMetalDark, x, GROUND_Y - 51, z));
        world.getChildren().add(g);
        registerCull(g, x, z, VIEW_BUILD);
        addObs(x, z, lw, ld, 52, false);
    }

    private void addTower(double x, double z, int dir) {
        Group g = new Group();
        for (double ox : new double[]{-30, 30}) {
            for (double oz : new double[]{-30, 30}) {
                g.getChildren().add(box(9, 170, 9, matMetalDark, x + ox, 25, z + oz));
            }
        }
        g.getChildren().add(box(90, 12, 90, matMetalDark, x, -60, z));
        g.getChildren().add(box(96, 5, 96, matMetalDark, x, -110, z));
        Group beam = new Group();
        beam.setTranslateX(x);
        beam.setTranslateY(-75);
        beam.setTranslateZ(z);
        Rotate yaw = new Rotate(dir > 0 ? 0 : 180, Rotate.Y_AXIS);
        beam.getTransforms().add(yaw);
        Group tilt = new Group();
        tilt.getTransforms().add(new Rotate(-9, Rotate.X_AXIS));
        tilt.getChildren().add(box(10, 10, 360, new PhongMaterial(Color.rgb(255, 245, 200, 0.09)), 0, 0, 190));
        beam.getChildren().addAll(tilt, sph(5, glow(Color.rgb(255, 245, 210)), 0, 0, 0, 1, 1, 1));
        g.getChildren().add(beam);
        beamRotates.add(yaw);
        world.getChildren().add(g);
        registerCull(g, x, z, 1500);
        addObs(x, z, 95, 95, 215, false);
    }

    private void addWreck(double x, double z, double rotY, boolean turned) {
        addCarInternal(x, z, rotY, turned, true);
    }

    private Fire addFire(double x, double y, double z, double scale, boolean smoke) {
        Fire f = new Fire(x, y, z, scale, smoke);
        f.dl = addDynLight(x, y - 20, z, 2, Color.rgb(255, 130, 40), 320);
        fires.add(f);
        world.getChildren().add(f.flames);
        registerCull(f.flames, x, z, 1000);
        return f;
    }

    // ===================== HOUSES =====================
    private Building addHouse(double bx, double bz, double w, double d, double h, int front, boolean burning, boolean flat, Color col) {
        if (!flat) {
            h = Dims.HOUSE_WALL_H;               // houses follow the player-scale dimensions, whatever the caller passed
        }
        Building b = new Building(bx, bz, w, d, h, front);
        b.burning = burning;
        buildings.add(b);
        PhongMaterial wall = burning ? mat(Color.rgb(34, 30, 28), Color.rgb(20, 20, 20), 6) : mat(col, Color.rgb(40, 40, 40), 10);
        double T = flat ? WALL_T : Dims.HOUSE_WALL_T;
        double fz = bz + front * (d / 2 - T / 2);
        HouseBuilder.Result built = null;
        if (flat) {
            double bkz = bz - front * (d / 2 - T / 2);
            double[][] fOps = w >= 160 ? new double[][]{{-(w / 2 - 34), 26, 1}, {0, DOOR_W, 0}, {w / 2 - 34, 26, 1}} : new double[][]{{0, DOOR_W, 0}};
            buildWall(b, bx, fz, w, true, h, fOps, wall);
            buildWall(b, bx, bkz, w, true, h, w >= 80 ? new double[][]{{0, 26, 1}} : new double[0][], wall);
            double[][] sOps = d >= 90 ? new double[][]{{0, 26, 1}} : new double[0][];
            buildWall(b, bx - (w / 2 - T / 2), bz, d - 2 * T, false, h, sOps, wall);
            buildWall(b, bx + (w / 2 - T / 2), bz, d - 2 * T, false, h, sOps, wall);
            addDoor(b, bx, fz, front);
            b.group.getChildren().add(box(w - 2 * T, 1.2, d - 2 * T, floorMat, bx, GROUND_Y - 0.6, bz));
            if (!burning) {
                b.group.getChildren().add(sph(3, glow(Color.rgb(255, 214, 140)), bx, GROUND_Y - h + 10, bz, 1, 1, 1));
                b.group.getChildren().add(sph(2.5, glow(Color.rgb(255, 214, 140)), bx, GROUND_Y - h - 1, b.doorZ + front * 6, 1, 1, 1));
            }
        } else {
            // real house: rooms, doorways, windows and furniture come from a generated floor plan (see HouseLayout / HouseBuilder)
            HouseLayout layout = HouseLayout.generate(w, d, RNG, burning);
            built = HouseBuilder.build(b, layout, wall, burning, this::addObs, RNG);
            beds.addAll(built.beds);
            if (!burning) {
                b.group.getChildren().add(sph(2.5, glow(Color.rgb(255, 214, 140)), b.doorX, GROUND_Y - Dims.HOUSE_DOOR_H - 8, b.doorZ + front * 8, 1, 1, 1));
            }
        }
        double rise = 0;
        if (flat) {
            b.roof.getChildren().add(box(w + 8, 6, d + 8, matMetalDark, bx, GROUND_Y - h - 3, bz));
            b.roof.getChildren().add(box(24, 10, 20, matMetalDark, bx + w * 0.25, GROUND_Y - h - 11, bz));
            Sphere beacon = sph(2.6, glow(Color.rgb(255, 30, 20)), bx - w * 0.3, GROUND_Y - h - 30, bz - d * 0.2, 1, 1, 1);
            b.roof.getChildren().addAll(cylY(1.2, 40, matMetalDark, bx - w * 0.3, GROUND_Y - h - 20, bz - d * 0.2), beacon);
            beacons.add(beacon);
        } else {
            double ang = 26, run = d / 2 + 6, slabLen = run / Math.cos(Math.toRadians(ang));
            rise = (d / 2) * Math.tan(Math.toRadians(ang));
            Color[] rc = {Color.rgb(90, 40, 32), Color.rgb(56, 60, 66), Color.rgb(70, 54, 40), Color.rgb(48, 70, 58)};
            PhongMaterial rm = burning ? mat(Color.rgb(24, 22, 20), Color.BLACK, 1) : mat(rc[RNG.nextInt(rc.length)], Color.rgb(30, 30, 30), 8);
            for (int s = -1; s <= 1; s += 2) {
                Box slab = box(w + 12, 3, slabLen, rm, bx, GROUND_Y - h - rise / 2 - 2, bz + s * (d / 4 + 3));
                slab.setRotationAxis(Rotate.X_AXIS);
                slab.setRotate(-s * ang);
                b.roof.getChildren().add(slab);
            }
            for (int i = 0; i < 3; i++) {
                for (int sx = -1; sx <= 1; sx += 2) {
                    double sh = rise / 3;
                    b.roof.getChildren().add(box(T, sh, d * (1 - (i + 0.5) / 3), wall, bx + sx * (w / 2 - T / 2), GROUND_Y - h - sh * (i + 0.5), bz));
                }
            }
        }
        b.group.getChildren().add(b.roof);
        if (!flat) {
            addHouseDetails(b, bx, bz, fz, w, d, h, front, rise);
            b.spawn.addAll(built.spawns);                         // free floor points chosen by the layout
            int found = 0;
            for (double[] l : built.loot) {                       // supplies lie next to furniture
                if (found < 2 && RNG.nextDouble() < 0.7) {
                    createPickup(RNG.nextInt(3), l[0], l[1]);
                    found++;
                }
            }
        } else {
            addFurniture(b);
            for (int i = 0; i < 2; i++) {
                for (int a = 0; a < 8; a++) {
                    double sx = bx + (RNG.nextDouble() - 0.5) * (w - 2 * T - 30), sz = bz + (RNG.nextDouble() - 0.5) * (d - 2 * T - 30);
                    if (Math.abs(sz - b.doorZ) > 50) {
                        b.spawn.add(new double[]{sx, sz});
                        break;
                    }
                }
            }
            if (RNG.nextDouble() < 0.5 && !b.spawn.isEmpty()) {
                double[] p = b.spawn.get(0);
                createPickup(RNG.nextInt(3), p[0] + 14, p[1] + 10);
            }
        }
        world.getChildren().add(b.group);
        registerCull(b.group, bx, bz, VIEW_BUILD);
        if (burning) {
            addFire(bx - w / 4, GROUND_Y - h - rise * 0.4, bz, 3.2, true);
            addFire(bx + w / 5, GROUND_Y - h - rise * 0.4, bz + 6, 2.6, true);
        }
        return b;
    }

    /** Porch, shutters / boarded windows, gutters and chimney from BuildingDetail. */
    private void addHouseDetails(Building b, double bx, double bz, double fz, double w, double d, double h, int front, double rise) {
        boolean ruined = b.burning;
        if (!ruined && RNG.nextDouble() < 0.7) {
            BuildingDetail.porch(b.group, b.doorX, b.doorZ, front, 190, GROUND_Y + 8, RNG);   // roof clears the 132-high door
        }
        double winH = Dims.WINDOW_HEAD - Dims.WINDOW_SILL, winY = GROUND_Y - Dims.WINDOW_SILL - winH / 2;
        for (HouseLayout.Opening o : b.layout.openings) {
            if (o.door || o.side != HouseLayout.Side.FRONT) {
                continue;
            }
            double wx = bx + o.center;
            if (ruined || RNG.nextDouble() < 0.3) {
                BuildingDetail.boards(b.group, wx, winY, fz + 5 * front - 3.5, true, o.width, winH, RNG);
            } else {
                BuildingDetail.shutters(b.group, wx, winY, fz + 5 * front - 4, true, o.width, winH, RNG);
            }
        }
        for (int s = -1; s <= 1; s += 2) {
            BuildingDetail.gutter(b.roof, bx, bz + s * (d / 2 + 5), w, GROUND_Y - h + 1);
        }
        if (RNG.nextDouble() < 0.65) {
            BuildingDetail.chimney(b.roof, bx + w * 0.25, bz + d / 6, GROUND_Y - h - rise * 0.67, 1.7);
        }
    }

    private void buildWall(Building b, double cx, double cz, double len, boolean alongX, double h, double[][] ops, PhongMaterial wall) {
        double cur = -len / 2;
        for (double[] op : ops) {
            double a = op[0] - op[1] / 2, c = op[0] + op[1] / 2;
            seg(b, cx, cz, alongX, cur, a, 0, h, wall, true);
            if (op[2] == 0) {
                seg(b, cx, cz, alongX, a, c, 52, h - 52, wall, false);
            } else {
                seg(b, cx, cz, alongX, a, c, 0, 38, wall, true);
                seg(b, cx, cz, alongX, a, c, 68, h - 68, wall, false);
                double px = alongX ? cx + op[0] : cx, pz = alongX ? cz : cz + op[0];
                b.group.getChildren().add(box(alongX ? op[1] + 6 : WALL_T + 5, 3, alongX ? WALL_T + 5 : op[1] + 6, matMetalDark, px, GROUND_Y - 39.5, pz));
                if (b.burning || RNG.nextDouble() < 0.28) {
                    b.group.getChildren().add(box(alongX ? op[1] - 2 : 1, 28, alongX ? 1 : op[1] - 2,
                            b.burning ? glow(Color.rgb(255, 110, 30)) : matGlassLit, px, GROUND_Y - 53, pz));
                }
            }
            cur = c;
        }
        seg(b, cx, cz, alongX, cur, len / 2, 0, h, wall, true);
    }

    private void seg(Building b, double cx, double cz, boolean alongX, double from, double to, double yBot, double hh, PhongMaterial m, boolean collide) {
        double L = to - from;
        if (L < 0.5 || hh < 0.5) {
            return;
        }
        double mid = (from + to) / 2, px = alongX ? cx + mid : cx, pz = alongX ? cz : cz + mid;
        double bw = alongX ? L : WALL_T, bd = alongX ? WALL_T : L;
        b.group.getChildren().add(box(bw, hh, bd, m, px, GROUND_Y - yBot - hh / 2, pz));
        if (collide) {
            addObs(px, pz, bw, bd, yBot + hh, false);
        }
    }

    private void addDoor(Building b, double bx, double fz, int front) {
        b.doorX = bx;
        b.doorZ = fz;
        b.outX = bx;
        b.outZ = fz + front * 30;
        b.inX = bx;
        b.inZ = fz - front * 30;
        b.doorObs = addObs(bx, fz, DOOR_W, WALL_T, 52, false);
        Group g = new Group();
        g.setTranslateX(bx - DOOR_W / 2 + 1);
        g.setTranslateY(GROUND_Y);
        g.setTranslateZ(fz);
        b.doorRot = new Rotate(0, Rotate.Y_AXIS);
        g.getTransforms().add(b.doorRot);
        g.getChildren().add(box(DOOR_W - 2, 50, 2.5, matWood, (DOOR_W - 2) / 2, -25, 0));
        g.getChildren().add(box(3, 3, 5, matMetalDark, DOOR_W - 8, -24, 0));
        b.group.getChildren().add(g);
    }

    private void addFurniture(Building b) {
        int n = 2 + RNG.nextInt(3);
        for (int i = 0; i < n; i++) {
            int kind = i == 0 ? 1 : RNG.nextInt(5);
            double fw = kind == 0 ? 26 : kind == 1 ? 24 : kind == 2 ? 34 : kind == 3 ? 40 : 20;
            double fd = kind == 0 ? 18 : kind == 1 ? 46 : kind == 2 ? 9 : kind == 3 ? 16 : 20;
            double fh = kind == 0 ? 16 : kind == 1 ? 14 : kind == 2 ? 44 : kind == 3 ? 16 : 20;
            for (int a = 0; a < 8; a++) {
                double x = b.x + (RNG.nextDouble() - 0.5) * (b.w - 2 * WALL_T - fw - 8);
                double z = b.z + (RNG.nextDouble() - 0.5) * (b.d - 2 * WALL_T - fd - 8);
                if (Math.abs(x - b.x) < 40 + fw / 2 && Math.abs(z - b.doorZ) < 60 + fd / 2) {
                    continue;
                }
                PhongMaterial m = kind == 3 ? couchMat : kind == 1 ? bedMat : matWood;
                b.group.getChildren().add(box(fw, fh, fd, m, x, GROUND_Y - fh / 2 - 1, z));
                if (kind == 1) {
                    b.group.getChildren().add(box(18, 4, 10, matBurlap, x, GROUND_Y - fh - 3, z - fd / 2 + 8));
                }
                addObs(x, z, fw, fd, fh, true);
                if (kind == 1) {
                    beds.add(new double[]{x, z});
                }
                break;
            }
        }
    }

    // ===================== TOWN / SUBURBS / FOREST =====================
    /** Footprint of a house: about 4.6-6.8 m wide and 4.8-5.5 m deep, so every room fits real furniture at player scale. */
    private double[] houseSize() {
        return new double[]{300 + RNG.nextInt(141), 310 + RNG.nextInt(51)};
    }

    /**
     * True when a house of this footprint can stand here: inside the playable area, clear of every other building, the river
     * (including a lane for its door), the lake and, optionally, the roads. {@code margin} is the free space kept all round.
     */
    private boolean houseSpotFree(double x, double z, double w, double d, double margin, boolean checkRoads) {
        if (Math.abs(x) + w / 2 > 1330 || Math.abs(z) + d / 2 > 1330) {
            return false;
        }
        if (Math.abs(x) - w / 2 < 560 && Math.abs(z) - d / 2 < 560) {      // the fenced outpost
            return false;
        }
        for (Building b : buildings) {
            if (Math.abs(x - b.x) < (w + b.w) / 2 + margin && Math.abs(z - b.z) < (d + b.d) / 2 + margin) {
                return false;
            }
        }
        if (Math.abs(z - RIVER_Z) < d / 2 + 50 + 40) {            // the river itself plus a bank (doors are turned away from it)
            return false;
        }
        for (double fx : new double[]{-0.5, 0, 0.5}) {
            for (double fz : new double[]{-0.5, 0, 0.5}) {
                if (inLake(x + fx * w, z + fz * d, margin + 40)) {
                    return false;
                }
            }
        }
        if (checkRoads) {
            for (double[] r : roadRects) {
                if (Math.abs(x - r[0]) < (w + r[2]) / 2 + margin && Math.abs(z - r[1]) < (d + r[3]) / 2 + margin) {
                    return false;
                }
            }
        }
        return true;
    }

    private void createTown() {
        Color[] cols = {Color.rgb(150, 138, 118), Color.rgb(110, 86, 78), Color.rgb(96, 110, 120), Color.rgb(128, 128, 112), Color.rgb(90, 104, 88)};
        // x ranges a house must not touch: the gap at the outpost gate and the two roads that run south from the main street
        double[][] bands = {{-125, 125}, {-660, -540}, {540, 660}};
        for (int side = -1; side <= 1; side += 2) {
            double cursor = -1200;
            while (cursor < 1150) {
                double[] sz = houseSize();
                double w = sz[0], d = sz[1], px = cursor + w / 2;
                double[] hit = null;
                for (double[] bnd : bands) {
                    if (px - w / 2 < bnd[1] && px + w / 2 > bnd[0]) {
                        hit = bnd;
                        break;
                    }
                }
                if (hit != null) {
                    cursor = hit[1] + 6;
                    continue;
                }
                if (px + w / 2 > 1250) {
                    break;
                }
                boolean skip = RNG.nextDouble() < 0.1 || (side == -1 && Math.abs(px) < 600);
                double pz = 700 + side * (70 + 22 + d / 2);
                if (!skip && houseSpotFree(px, pz, w, d, 30, false)) {
                    addHouse(px, pz, w, d, 84, -side, RNG.nextDouble() < 0.22, false, cols[RNG.nextInt(cols.length)]);
                }
                cursor = px + w / 2 + 28 + RNG.nextInt(50);
            }
        }
        for (int s = -1; s <= 1; s += 2) {
            for (int attempt = 0; attempt < 25; attempt++) {
                double[] sz = houseSize();
                double px = s * (600 + 65 + sz[0] / 2 + 15), pz = 1000 + RNG.nextInt(260);
                if (houseSpotFree(px, pz, sz[0], sz[1], 40, true)) {
                    addHouse(px, pz, sz[0], sz[1], 84, -1, RNG.nextDouble() < 0.3, false, cols[RNG.nextInt(cols.length)]);
                    break;
                }
            }
        }
    }

    private void createSuburbs() {
        Color[] cols = {Color.rgb(140, 128, 110), Color.rgb(104, 82, 74), Color.rgb(100, 112, 104), Color.rgb(120, 120, 104)};
        int placed = 0;
        for (int t = 0; t < 3000 && placed < 16; t++) {
            double x = (RNG.nextDouble() - 0.5) * 2400, z = (RNG.nextDouble() - 0.5) * 2400;
            if ((Math.abs(x) < 600 && Math.abs(z) < 600) || (z > 480 && z < 940)) {
                continue;
            }
            double[] sz = houseSize();
            if (!houseSpotFree(x, z, sz[0], sz[1], 55, true)) {
                continue;
            }
            boolean nearRiver = Math.abs(z - RIVER_Z) < 450;
            int front = nearRiver ? (z > RIVER_Z ? 1 : -1) : (RNG.nextBoolean() ? 1 : -1);      // door faces away from the river
            addHouse(x, z, sz[0], sz[1], 84, front, RNG.nextDouble() < 0.18, false, cols[RNG.nextInt(cols.length)]);
            placed++;
        }
    }

    private void createForest() {
        int placed = 0;
        for (int t = 0; t < 1500 && placed < TREE_COUNT; t++) {
            double x = (RNG.nextDouble() - 0.5) * 2900, z = (RNG.nextDouble() - 0.5) * 2900;
            if ((Math.abs(x) < 540 && Math.abs(z) < 540) || onRoad(x, z, 40) || inLake(x, z, 40) || Math.abs(z - RIVER_Z) < 90) {
                continue;
            }
            boolean ok = true;
            for (Building b : buildings) {
                if (Math.abs(x - b.x) < b.w / 2 + 45 && Math.abs(z - b.z) < b.d / 2 + 45) {
                    ok = false;
                    break;
                }
            }
            if (!ok || blocked(x, z, 28, 0, false)) {
                continue;
            }
            addTree(x, z, 0.8 + RNG.nextDouble() * 0.9);
            placed++;
        }
    }

    private void addTree(double x, double z, double s) {
        double r = RNG.nextDouble();
        int species = r < 0.45 ? TreeFactory.PINE : r < 0.7 ? TreeFactory.OAK : r < 0.85 ? TreeFactory.BIRCH : TreeFactory.DEAD;
        TreeFactory.Tree t = TreeFactory.create(species, s, x, z, GROUND_Y, RNG);
        trees.add(t);
        world.getChildren().add(t.node);
        registerCull(t.node, x, z, VIEW_SMALL);
        addObs(x, z, 10 * s, 10 * s, 60, false);
    }

    /** Radio / cellular towers and a line of high-voltage pylons (all from TowerFactory). */
    private void createRadioTowers() {
        int placed = 0;
        for (int t = 0; t < 600 && placed < 3; t++) {
            double x = (RNG.nextDouble() - 0.5) * 2600, z = (RNG.nextDouble() - 0.5) * 2600;
            if ((Math.abs(x) < 700 && Math.abs(z) < 900) || onRoad(x, z, 120) || inLake(x, z, 100) || Math.abs(z - RIVER_Z) < 150
                    || blocked(x, z, 80, 0, false)) {
                continue;
            }
            boolean near = false;
            for (Building b : buildings) {
                if (dist(x, z, b.x, b.z) < 340) {
                    near = true;
                    break;
                }
            }
            if (near) {
                continue;
            }
            Group tw = placed == 2 ? TowerFactory.monopole(300, false, beacons, RNG) : TowerFactory.lattice(placed == 0 ? 380 : 320, placed == 1, beacons, RNG);
            tw.setTranslateX(x);
            tw.setTranslateY(GROUND_Y);
            tw.setTranslateZ(z);
            world.getChildren().add(tw);
            registerCull(tw, x, z, 2200);
            addObs(x, z, 64, 64, 60, false);
            poiDefs.add(new Object[]{"Radio Tower", x, z});
            placed++;
        }
        // pylon line running north-south
        final double h = 150, px = 1300;
        double lastZ = Double.NaN;
        PhongMaterial wire = Mats.BLACK_METAL;
        for (double z = -700; z <= 1300; z += 400) {
            if (onRoad(px, z, 60) || blocked(px, z, 40, 0, false) || buildingAt(px, z) != null) {
                lastZ = Double.NaN;
                continue;
            }
            Group py = TowerFactory.pylon(h, RNG);
            py.setTranslateX(px);
            py.setTranslateY(GROUND_Y);
            py.setTranslateZ(z);
            py.setRotationAxis(Rotate.Y_AXIS);
            py.setRotate(0);
            world.getChildren().add(py);
            registerCull(py, px, z, 2000);
            addObs(px, z, 36, 36, 60, false);
            if (!Double.isNaN(lastZ)) {
                Group wires = new Group();
                for (int i = 0; i < 3; i++) {
                    double y = GROUND_Y - h * (0.62 + i * 0.11) + 5, arm = h * (0.26 - i * 0.04);
                    for (int sx = -1; sx <= 1; sx += 2) {
                        Gfx.cable(wires, px + sx * arm, y, lastZ, px + sx * arm, y, z, 9, 0.9, wire);
                    }
                }
                world.getChildren().add(wires);
                registerCull(wires, px, (z + lastZ) / 2, 1800);
            }
            lastZ = z;
        }
        poiDefs.add(new Object[]{"Power Line", px, 300.0});
    }

    /** Swaying grass patches and bushes spread over the whole map (culled by distance). */
    private void createGrass() {
        final double size = 200;
        for (double cx = -1700 + size / 2; cx < 1700; cx += size) {
            for (double cz = -1700 + size / 2; cz < 1700; cz += size) {
                if (cz > 1450) {
                    continue;                                       // downtown has pavements, not grass
                }
                boolean wild = Math.hypot(cx, cz) > 900;
                VegetationFactory.Chunk c = VegetationFactory.grassChunk(cx, cz, size, GROUND_Y, RNG,
                        (x, z) -> !onRoad(x, z, 14) && !inLake(x, z, 20) && Math.abs(z - RIVER_Z) > 60 && buildingAt(x, z) == null
                        && !blocked(x, z, 6, 0, false), 9, 1, wild);
                if (c.node.getChildren().isEmpty()) {
                    continue;
                }
                grassChunks.add(c);
                world.getChildren().add(c.node);
                registerCull(c.node, cx, cz, 650);
            }
        }
    }

    /** Rocks, logs, trash piles, tyres, barrels, scrap and paper scattered around. */
    private void createDebris() {
        for (int i = 0, placed = 0; i < 400 && placed < 90; i++) {
            double x = (RNG.nextDouble() - 0.5) * 3200, z = (RNG.nextDouble() - 0.5) * 3200;
            if (inLake(x, z, 30) || Math.abs(z - RIVER_Z) < 70 || buildingAt(x, z) != null || blocked(x, z, 12, 0, false)) {
                continue;
            }
            Group d = VegetationFactory.debris(RNG.nextInt(8), RNG, GROUND_Y);
            d.setTranslateX(x);
            d.setTranslateZ(z);
            d.setRotationAxis(Rotate.Y_AXIS);
            d.setRotate(RNG.nextDouble() * 360);
            world.getChildren().add(d);
            registerCull(d, x, z, VIEW_SMALL);
            placed++;
        }
    }

    // ===================== CARS / LIGHTS / RUINS =====================
    private void createCars() {
        for (int i = 0; i < 12; i++) {
            double z = -1400 + RNG.nextDouble() * 2800;
            if (Math.abs(z - RIVER_Z) < 90 || Math.abs(z - 700) < 100) {
                continue;
            }
            double x = RNG.nextBoolean() ? -42 : 42;
            if (!blocked(x, z, 40, 0, false)) {
                addCarInternal(x, z, (RNG.nextDouble() - 0.5) * 12, false, RNG.nextDouble() < 0.4);
            }
        }
        for (int i = 0; i < 10; i++) {
            double x = -1400 + RNG.nextDouble() * 2800;
            if (Math.abs(x) < 120 || Math.abs(Math.abs(x) - 600) < 60) {
                continue;
            }
            double z = 700 + (RNG.nextBoolean() ? -28 : 28);
            if (!blocked(x, z, 40, 0, false)) {
                addCarInternal(x, z, (RNG.nextDouble() - 0.5) * 12, true, RNG.nextDouble() < 0.4);
            }
        }
        for (Building b : new ArrayList<>(buildings)) {
            if (RNG.nextDouble() < 0.3) {
                double cx = b.x + (b.w / 2 + 50) * (RNG.nextBoolean() ? 1 : -1), cz = b.z;
                if (!blocked(cx, cz, 40, 0, false) && !onRoad(cx, cz, 30)) {
                    addCarInternal(cx, cz, (RNG.nextDouble() - 0.5) * 12, false, RNG.nextBoolean());
                }
            }
        }
    }

    private void addCarInternal(double x, double z, double jitter, boolean turned, boolean wreck) {
        Color[] cc = {Color.rgb(140, 40, 36), Color.rgb(40, 70, 120), Color.rgb(170, 170, 160), Color.rgb(36, 40, 44), Color.rgb(90, 110, 60), Color.rgb(190, 150, 40)};
        int kind = RNG.nextInt(4);
        boolean burnt = wreck && RNG.nextDouble() < 0.35;
        double len = kind == VehicleFactory.PICKUP ? 132 : kind == VehicleFactory.VAN ? 136 : kind == VehicleFactory.SUV ? 124 : 118;
        Group g = VehicleFactory.create(kind, cc[RNG.nextInt(cc.length)], wreck, burnt, RNG);
        g.setTranslateX(x);
        g.setTranslateY(GROUND_Y);
        g.setTranslateZ(z);
        double base = (turned ? 90 : 0) + jitter;
        Rotate rot = new Rotate(base, Rotate.Y_AXIS);
        g.getTransforms().add(rot);
        world.getChildren().add(g);
        registerCull(g, x, z, VIEW_BUILD);
        addObs(x, z, turned ? len : 66, turned ? 66 : len, 38, false);
        Car c = new Car();
        c.x = x;
        c.z = z;
        c.turned = turned;
        c.g = g;
        c.rot = rot;
        c.baseYaw = base;
        cars.add(c);
    }

    private void createStreetLights() {
        int i = 0;
        for (double z = -1400; z <= 1400; z += 220) {
            if (Math.abs(z - RIVER_Z) < 90 || Math.abs(z - 700) < 110) {
                continue;
            }
            addStreetLight((i++ % 2 == 0) ? -105 : 105, z, RNG.nextDouble() < 0.22);
        }
        for (double x = -1400; x <= 1400; x += 220) {
            if (Math.abs(x) < 130 || Math.abs(Math.abs(x) - 600) < 60) {
                continue;
            }
            addStreetLight(x, 700 + ((i++ % 2 == 0) ? -85 : 85), RNG.nextDouble() < 0.22);
        }
        addStreetLight(-130, 400, false);
        addStreetLight(130, -400, false);
    }

    private void addStreetLight(double x, double z, boolean broken) {
        for (Building b : buildings) {                      // never plant a pole in front of somebody's door
            if (b.layout != null && dist(x, z, b.doorX, b.doorZ) < 80) {
                return;
            }
        }
        Group g = new Group();
        g.setTranslateX(x);
        g.setTranslateY(GROUND_Y);
        g.setTranslateZ(z);
        Group pole = new Group();
        if (broken) {
            pole.getTransforms().add(new Rotate(RNG.nextBoolean() ? 13 : -13, Rotate.Z_AXIS));
        }
        pole.getChildren().add(box(7, 170, 7, matMetalDark, 0, -85, 0));
        pole.getChildren().add(box(35, 10, 25, broken ? matMetalDark : glow(Color.rgb(255, 232, 160)), 0, -170, 0));
        if (!broken) {
            pole.getChildren().add(sph(15, new PhongMaterial(Color.rgb(255, 220, 140, 0.14)), 0, -168, 0, 1, 0.7, 1));
        }
        g.getChildren().add(pole);
        world.getChildren().add(g);
        registerCull(g, x, z, VIEW_BUILD);
        addObs(x, z, 12, 12, 170, false);
        if (!broken) {
            addDynLight(x, GROUND_Y - 150, z, 0, Color.rgb(255, 235, 170), 330);
        } else if (RNG.nextDouble() < 0.6) {
            addDynLight(x, GROUND_Y - 120, z, 1, Color.rgb(255, 235, 170), 260);
        }
    }

    private void createRuins() {
        for (int i = 0; i < 16; i++) {
            double x = (RNG.nextDouble() - 0.5) * 2600, z = (RNG.nextDouble() - 0.5) * 2600;
            if ((Math.abs(x) < 560 && Math.abs(z) < 560) || onRoad(x, z, 20) || inLake(x, z, 30) || Math.abs(z - RIVER_Z) < 90 || blocked(x, z, 70, 0, false)) {
                continue;
            }
            boolean onHouse = false;
            for (Building hb : buildings) {                 // houses are big now: keep ruins clear of the whole footprint, inside and out
                if (Math.abs(x - hb.x) < hb.w / 2 + 90 && Math.abs(z - hb.z) < hb.d / 2 + 90) {
                    onHouse = true;
                    break;
                }
            }
            if (onHouse) {
                continue;
            }
            boolean alongX = RNG.nextBoolean();
            Group g = new Group();
            for (int k = 0; k < 5; k++) {
                if (RNG.nextDouble() < 0.25) {
                    continue;
                }
                double hh = 12 + RNG.nextDouble() * 38;
                double ox = alongX ? (k - 2) * 22 : 0, oz = alongX ? 0 : (k - 2) * 22;
                g.getChildren().add(box(alongX ? 22 : 6, hh, alongX ? 6 : 22, matConcrete, x + ox, GROUND_Y - hh / 2, z + oz));
                addObs(x + ox, z + oz, alongX ? 22 : 6, alongX ? 6 : 22, hh, false);
            }
            world.getChildren().add(g);
            registerCull(g, x, z, VIEW_SMALL);
        }
    }

    // ===================== RIG / CHARACTER MODELS =====================
    private Building insideB;

    // ===================== PLAYER & WEAPON MODEL =====================
    private void createPlayer(Group w) {
        player = new Group();
        player.setTranslateY(feetY - 55);
        player.getTransforms().addAll(playerFacing, sleepRot);
        playerRig = buildSoldier();
        player.getChildren().add(playerRig.root);
        torchLight = new PointLight(Color.rgb(255, 246, 225));
        torchLight.setTranslateX(6);
        torchLight.setTranslateY(-30);
        torchLight.setTranslateZ(50);
        torchLight.setMaxRange(440);
        torchLight.setLightOn(false);
        playerRig.upper.getChildren().add(torchLight);
        weaponModel = createWeaponModel();
        playerRig.upper.getChildren().add(weaponModel);
        w.getChildren().add(player);
        playerShadow = cylY(1, 0.4, SHADOW_MAT, 0, GROUND_Y - 0.3, 0, 32);
        playerShadow.setScaleX(22);
        playerShadow.setScaleZ(22);
        playerShadow.setMouseTransparent(true);
        w.getChildren().add(playerShadow);
    }

    private void rebuildWeaponModel() {
        if (weaponModel != null) {
            playerRig.upper.getChildren().remove(weaponModel);
        }
        weaponModel = createWeaponModel();
        playerRig.upper.getChildren().add(weaponModel);
        swapBlend = 1;
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
        double flashScale = 1;
        boolean gun = heldItem == 0;
        if (heldItem == 1) {
            PhongMaterial olive = mat(Color.rgb(60, 72, 44), Color.rgb(120, 130, 100), 40);
            model.getChildren().addAll(sph(6, olive, 0, -4, 18, 1, 1.15, 1), cylY(2.2, 4, dark, 0, -12, 18), box(1.4, 8, 3, gold, 3, -11, 18),
                    box(5, 1, 1, red, 0, -4, 24));
        } else if (heldItem == 2) {
            model.getChildren().addAll(box(16, 11, 9, mat(Color.rgb(230, 230, 230), Color.WHITE, 40), 0, -4, 18),
                    box(8, 2.4, 1, red, 0, -4, 22.7), box(2.4, 8, 1, red, 0, -4, 22.7), box(6, 2, 3, dark, 0, -10.5, 18));
        } else if (currentWeapon == WeaponType.PISTOL) {
            Box grip = box(6, 17, 9, polymer, 0, 7, -1);
            grip.setRotationAxis(Rotate.X_AXIS);
            grip.setRotate(-12);
            model.getChildren().addAll(box(6.5, 7, 31, chrome, 0, -9, 14), box(6, 5, 28, dark, 0, -3.5, 13), box(6.6, 1.2, 20, gold, 0, -12.7, 15),
                    cyl(2.0, 5, gold, 0, -9, 31.5), box(1.6, 1.6, 9, dark, 0, 0.5, 6), box(1.4, 4, 1.2, dark, 0, 2, 4),
                    box(1.6, 2, 1.6, green, -2.2, -13.4, -0.5), box(1.6, 2, 1.6, green, 2.2, -13.4, -0.5),
                    box(1.8, 2.4, 1.6, green, 0, -13.6, 28.5), box(0.8, 1, 14, cyan, 3.4, -4.5, 13), grip);
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
            model.getChildren().addAll(box(8, 10, 36, gunmetal, 0, -6, 8), box(7, 8, 26, dark, 0, -5, 33), box(3, 2, 24, gunmetal, 0, -10.5, 33),
                    cyl(1.6, 22, dark, 0, -5, 52), cyl(2.5, 6, gunmetal, 0, -5, 66), box(6, 12, 22, polymer, 0, -4, -20),
                    box(6.4, 4, 14, polymer, 0, -9.5, -17), box(5.5, 6, 18, dark, 0, -14, 9), box(4, 4, 0.8, red, 0, -14, 18.3),
                    box(1.4, 3, 1.4, gunmetal, 0, -10, 52), box(0.8, 1, 14, orange, 4.1, -6, 10), box(0.8, 1, 14, orange, -4.1, -6, 10),
                    box(0.8, 4, 8, cyan, 3.2, 2, 12), grip, mag);
            muzzleLocalY = -5;
            muzzleLocalZ = 69;
            flashScale = 1.25;
        } else {
            model.getChildren().addAll(box(7, 10, 26, gunmetal, 0, -6, 4), cyl(2.6, 52, dark, 0, -8, 40), cyl(2.2, 42, gunmetal, 0, -2.5, 36),
                    box(8, 6, 18, wood, 0, -1, 28), box(6, 13, 26, wood, 0, -3, -22), box(6, 14, 8, polymer, 0, 7, -2),
                    box(2.2, 4, 6, red, 4.2, -6, 4), box(2.2, 4, 6, red, 4.2, -6, 11), box(2.2, 4, 6, red, 4.2, -6, 18),
                    box(1.6, 2, 1.6, green, 0, -11.6, 64), cyl(3.0, 4, dark, 0, -8, 67));
            muzzleLocalY = -8;
            muzzleLocalZ = 70;
            flashScale = 1.7;
        }
        muzzleFlash = new Group();
        PhongMaterial core = glow(Color.rgb(255, 250, 215));
        PhongMaterial outer = glow(Color.rgb(255, 150, 40));
        muzzleFlash.getChildren().addAll(sph(3.6, core, 0, 0, 0, 1, 1, 1.6), box(1, 12, 18, outer, 0, 0, 9), box(12, 1, 18, outer, 0, 0, 9),
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
        if (gun) {
            model.getChildren().add(muzzleFlash);
        }
        return model;
    }

    private void createCamera() {
        camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(4000);
        camera.setFieldOfView(32);
    }

    private void createMotes(Group w) {
        for (int i = 0; i < 70; i++) {
            Box m = new Box(0.9, 0.9, 0.9);
            m.setMaterial(moteMat);
            m.setTranslateX((RNG.nextDouble() - 0.5) * 700);
            m.setTranslateY(-150 + RNG.nextDouble() * 250);
            m.setTranslateZ((RNG.nextDouble() - 0.5) * 700);
            w.getChildren().add(m);
            motes.add(m);
            moteVel.add(new double[]{(RNG.nextDouble() - 0.3) * 14, (RNG.nextDouble() - 0.4) * 5, (RNG.nextDouble() - 0.5) * 10});
        }
    }

    private void toggleCameraMode() {
        if (paused || gameOver) {
            return;
        }
        cameraController.toggleCameraMode();
        firstPerson = cameraController.isFirstPerson();
        viewButton.setText(firstPerson ? "TPP" : "FPP");
        playerRig.headGroup.setVisible(!firstPerson);
    }

    private void updateCamera(double dt) {
        smoothedYaw += (cameraYaw - smoothedYaw) * Math.min(1.0, dt * 20);
        smoothedPitch += ((cameraPitch + cameraRecoil) - smoothedPitch) * Math.min(1.0, dt * 20);
        double rad = Math.toRadians(smoothedYaw);
        double px = player.getTranslateX(), pz = player.getTranslateZ();
        double center = feetY - 55;
        double tx, ty, tz;
        if (firstPerson) {
            tx = px + Math.sin(rad) * 10;
            ty = center - 42 + 18 * crouchBlend + landBlend * 6 + Math.sin(walkPhase * 2) * 1.2 * moveBlend;
            tz = pz + Math.cos(rad) * 10;
        } else {
            double dst = (isAiming ? 0.6 : 1.0) * lerp(CAMERA_DISTANCE, 290, insideBlend);
            double hgt = lerp(isAiming ? 150 : 175, Math.max(175, insideH + 115), insideBlend) - 14 * crouchBlend;
            double shoulder = isAiming ? 28 : 0;
            tx = px - Math.sin(rad) * dst + Math.cos(rad) * shoulder;
            ty = center - hgt + Math.sin(walkPhase * 2) * 1.6 * moveBlend;
            tz = pz - Math.cos(rad) * dst - Math.sin(rad) * shoulder;
        }
        double lf = Math.min(1.0, dt * 16);
        smoothedCamX += (tx - smoothedCamX) * lf;
        smoothedCamY += (ty - smoothedCamY) * lf;
        smoothedCamZ += (tz - smoothedCamZ) * lf;
        double sx = shake > 0 ? (RNG.nextDouble() - 0.5) * shake : 0, sy = shake > 0 ? (RNG.nextDouble() - 0.5) * shake : 0;
        shake = Math.max(0, shake - dt * 20);
        camera.setTranslateX(smoothedCamX + sx);
        camera.setTranslateY(smoothedCamY + sy);
        camera.setTranslateZ(smoothedCamZ);
        double fovT = firstPerson ? (isAiming ? 55 : 75) : (isAiming ? 24 : (sprintingNow ? 37 : 32));
        if (climbing) {
            fovT += 4;
        }
        currentFov += (fovT * settings.fovScale - currentFov) * Math.min(1.0, dt * 6);
        camera.setFieldOfView(currentFov);
        camRoll += (-strafeInput * 1.8 - camRoll) * Math.min(1.0, dt * 6);
        camera.getTransforms().setAll(new Rotate(smoothedYaw, Rotate.Y_AXIS), new Rotate(smoothedPitch, Rotate.X_AXIS), new Rotate(camRoll, Rotate.Z_AXIS));
        if (sky != null) {
            sky.setTranslateX(camera.getTranslateX());
            sky.setTranslateY(camera.getTranslateY());
            sky.setTranslateZ(camera.getTranslateZ());
        }
    }

    // ===================== PLAYER ANIMATION =====================
    private void updatePlayerAnimation(double dt, boolean moving, boolean sprinting, double walkDir) {
        animationTime += dt;
        cameraRecoil = Math.max(0, cameraRecoil - recoilRecovery * dt);
        weaponKick = Math.max(0, weaponKick - dt * 9);
        swapBlend = Math.max(0, swapBlend - dt * 4.5);
        landBlend = Math.max(0, landBlend - dt * 3.5);
        hitReact = Math.max(0, hitReact - dt * 3.2);
        if (hitMarkerTimer > 0) {
            hitMarkerTimer -= dt;
            if (hitMarkerTimer <= 0 && hitMarker != null) {
                hitMarker.setVisible(false);
            }
        }
        double k = Math.min(1, dt * 10);
        moveBlend += ((moving ? 1 : 0) - moveBlend) * k;
        aimBlend += ((isAiming ? 1 : 0) - aimBlend) * k;
        crouchBlend += ((crouched ? 1 : 0) - crouchBlend) * Math.min(1, dt * 9);
        airBlend += (((!grounded && !climbing) ? 1 : 0) - airBlend) * Math.min(1, dt * 12);
        climbBlend += ((climbing ? 1 : 0) - climbBlend) * k;
        sprintBlend += ((sprinting ? 1 : 0) - sprintBlend) * k;
        if (moving && !climbing) {
            walkPhase += dt * (sprinting ? 13 : crouched ? 6.5 : 9.5);
        }
        if (climbing) {
            climbPhase += dt * 11;
        }
        Rig r = playerRig;
        double gnd = 1 - airBlend;
        double amp = (sprinting ? 42 : crouched ? 18 : 27) * moveBlend * walkDir * gnd;
        double sw = Math.sin(walkPhase) * amp;
        double kneeAmp = (sprinting ? 62 : crouched ? 22 : 42) * moveBlend * gnd;
        double cs = Math.sin(climbPhase) * climbBlend;
        double hipBase = 55 * crouchBlend + 38 * airBlend + 28 * landBlend;
        double kneeBase = -110 * crouchBlend - 75 * airBlend - 55 * landBlend;
        r.lHipR.setAngle(sw + hipBase + 45 * cs);
        r.rHipR.setAngle(-sw + hipBase - 45 * cs);
        r.lKneeR.setAngle(-Math.max(0, Math.cos(walkPhase)) * kneeAmp + kneeBase - 70 * Math.max(0, cs));
        r.rKneeR.setAngle(-Math.max(0, -Math.cos(walkPhase)) * kneeAmp + kneeBase - 70 * Math.max(0, -cs));
        r.root.setTranslateY(17.9 * crouchBlend + 6 * landBlend);

        double breath = Math.sin(animationTime * 2.2);
        double lean = cameraPitch * 0.35 - 2 * moveBlend - 9 * sprintBlend + weaponKick * 4 + breath * 0.5 - aimBlend
                - 11 * crouchBlend - 9 * landBlend - 14 * climbBlend;
        r.lean.setAngle(lean + 13 * hitReact);
        r.twist.setAngle(Math.sin(walkPhase) * 2.5 * moveBlend + cs * 4 + hitSide * 10 * hitReact);
        r.headX.setAngle(-(lean - cameraPitch * 0.35) * 0.6 + cameraPitch * 0.45);
        r.headZ.setAngle(Math.sin(animationTime * 0.8) * 0.8);

        double[] p = armPose();
        double ap = cameraPitch * 0.65, kick = weaponKick * 6, aimUp = aimBlend * 6;
        double rs = p[0] + ap + kick + aimUp + breath * 0.8 - 28 * sprintBlend;
        double re = p[1] - kick * 0.4 + 12 * sprintBlend;
        double ls = p[3] + ap + kick * 0.8 + aimUp + breath * 0.8 - 28 * sprintBlend;
        double le = p[4], ry = p[2], ly = p[5], reloadTilt = 0;
        if (reloadTimer > 0) {
            double[] rp = ReloadPoses.pose(currentWeapon, 1 - reloadTimer / reloadDur);   // per-weapon sequence
            ls += rp[ReloadPoses.LEFT_SHOULDER];
            le += rp[ReloadPoses.LEFT_ELBOW];
            ly += rp[ReloadPoses.LEFT_YAW];
            rs += rp[ReloadPoses.RIGHT_SHOULDER];
            re += rp[ReloadPoses.RIGHT_ELBOW];
            reloadTilt = rp[ReloadPoses.WEAPON_TILT];
        }
        if (throwing) {
            double a;
            if (throwT < 0.28) {
                a = lerp(0, -115, throwT / 0.28);
            } else if (throwT < 0.4) {
                a = lerp(-115, 70, (throwT - 0.28) / 0.12);
            } else {
                a = lerp(70, 0, Math.min(1, (throwT - 0.4) / 0.25));
            }
            rs += a;
        }
        if (usingMed) {
            double pul = Math.sin(medT * 9) * 3;
            rs += pul;
            ls += pul;
        }
        reachBlend += ((lock.type() == ActionLock.Type.DOOR && lock.progress() < 0.8 ? 1 : 0) - reachBlend) * Math.min(1, dt * 14);
        if (reachBlend > 0.001) {                       // right arm reaches for the handle
            rs = lerp(rs, 84, reachBlend);
            re = lerp(re, 14, reachBlend);
            ry = lerp(ry, 0, reachBlend);
        }
        rs = lerp(rs, 165 + 12 * Math.sin(climbPhase + 1), climbBlend);
        ls = lerp(ls, 165 + 12 * Math.sin(climbPhase), climbBlend);
        re = lerp(re, 8, climbBlend);
        le = lerp(le, 8, climbBlend);
        rs += 24 * airBlend;
        ls += 24 * airBlend;
        r.rShR.setAngle(rs);
        r.rElbR.setAngle(re);
        r.rShYaw.setAngle(ry);
        r.lShR.setAngle(ls);
        r.lElbR.setAngle(le);
        r.lShYaw.setAngle(ly);

        if (weaponModel != null) {
            double bobX = Math.sin(walkPhase * 2) * 0.9 * moveBlend;
            double bobY = Math.abs(Math.cos(walkPhase)) * 1.6 * moveBlend;
            double back = currentWeapon == WeaponType.SHOTGUN ? 8 : 5;
            weaponModel.setTranslateX(GUN_X + bobX - aimBlend * 2);
            weaponModel.setTranslateY(GUN_Y + breath * 0.6 + bobY - weaponKick * 1.4 + swapBlend * 16 + 5 * sprintBlend);
            weaponModel.setTranslateZ(GUN_Z - weaponKick * back - 6 * sprintBlend);
            weaponPitch.setAngle(cameraPitch * 0.65 + weaponKick * (currentWeapon == WeaponType.SHOTGUN ? 16 : 9) + reloadTilt - 15 * sprintBlend);
        }
        if (moving && grounded && !climbing) {
            stepTimer -= dt;
            if (stepTimer <= 0) {
                stepTimer = sprinting ? 0.17 : crouched ? 0.45 : 0.3;
                emit(dustMat, 4, player.getTranslateX() + (RNG.nextDouble() - 0.5) * 10, feetY - 3, player.getTranslateZ() + (RNG.nextDouble() - 0.5) * 10,
                        (RNG.nextDouble() - 0.5) * 14, -14, (RNG.nextDouble() - 0.5) * 14, 0.6, 0, false, false, 0);
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

    // ===================== PLAYER UPDATE =====================
    private void updatePlayer(double dt) {
        double mx = 0, mz = 0;
        if (!climbing) {
            if (settings.input.down(Action.FORWARD, keys)) {
                mz += 1;
            }
            if (settings.input.down(Action.BACK, keys)) {
                mz -= 1;
            }
            if (settings.input.down(Action.LEFT, keys)) {
                mx -= 1;
            }
            if (settings.input.down(Action.RIGHT, keys)) {
                mx += 1;
            }
        }
        strafeInput = mx;
        boolean moving = mx != 0 || mz != 0;
        if (settings.input.down(Action.SPRINT, keys) && moving && crouched) {
            crouched = false;
        }
        boolean sprinting = settings.input.down(Action.SPRINT, keys) && moving && stamina > 0 && !crouched && grounded && !usingMed && !throwing;
        sprintingNow = sprinting;
        double speed = crouched ? CROUCH_SPEED : sprinting ? SPRINT_SPEED : WALK_SPEED;
        if (choke > 0.4 || usingMed) {
            speed *= 0.6;
        }
        speed *= 1 - 0.3 * hitReact;                       // brief stagger after being hit
        stamina = Math.max(0, Math.min(100, stamina + (sprinting ? -30 : 22) * dt));

        if (firstPerson) {
            playerYaw = cameraYaw;
        } else {
            double diff = cameraYaw - playerYaw;
            while (diff > 180) {
                diff -= 360;
            }
            while (diff < -180) {
                diff += 360;
            }
            playerYaw += diff * Math.min(1, dt * 14);
        }
        playerFacing.setAngle(playerYaw);

        if (climbing) {
            updateClimb(dt);
        } else {
            if (moving) {
                double len = Math.sqrt(mx * mx + mz * mz);
                mx /= len;
                mz /= len;
                double rad = Math.toRadians(cameraYaw);
                double fx = Math.sin(rad), fz = Math.cos(rad), rx = Math.cos(rad), rz = -Math.sin(rad);
                double finX = fx * mz + rx * mx, finZ = fz * mz + rz * mx;
                double ox = player.getTranslateX(), oz = player.getTranslateZ();
                double nx = clampX(ox + finX * speed * dt);
                double nz = clampZ(oz + finZ * speed * dt);
                if (!blocked(nx, oz, PLAYER_RADIUS, feetY, true)) {
                    player.setTranslateX(nx);
                }
                if (!blocked(player.getTranslateX(), nz, PLAYER_RADIUS, feetY, true)) {
                    player.setTranslateZ(nz);
                }
            }
            updateVertical(dt);
        }
        double px = player.getTranslateX(), pz = player.getTranslateZ();
        insideB = buildingAt(px, pz);
        insideBlend += ((insideB != null ? 1 : 0) - insideBlend) * Math.min(1, dt * 4);
        if (insideB != null) {
            insideH = lerp(insideH, insideB.h, Math.min(1, dt * 4));
        }
        for (Iterator<Pickup> it = pickups.iterator(); it.hasNext();) {
            Pickup pk = it.next();
            if (Math.abs(feetY - GROUND_Y) < 130 && dist(px, pz, pk.x, pk.z) < 36) {
                if (pk.id != null) {
                    worldState.markTaken(pk.id);
                }
                collect(pk);
                world.getChildren().remove(pk.node);
                it.remove();
            }
        }
        // Animation controller update
                animationController.setHeldItem(heldItem);
                animationController.setPrevHeld(prevHeld);
                animationController.setCurrentWeapon(currentWeapon);
                animationController.setIsAiming(isAiming);
                animationController.setCrouched(crouched);
                animationController.setClimbing(climbing);
                animationController.setFiring(firing);
                animationController.setWeaponKick(weaponKick);
                animationController.setCameraRecoil(cameraRecoil);
                animationController.setReloadTimer(reloadTimer);
                animationController.setReloadDur(reloadDur);
                animationController.setThrowing(throwing);
                animationController.setThrew(threw);
                animationController.setThrowT(throwT);
                animationController.setUsingMed(usingMed);
                animationController.setMedT(medT);
                animationController.setHitReact(hitReact);
                animationController.setHitSide(hitSide);
                animationController.setReachBlend(reachBlend);
                animationController.setMuzzleLight(muzzleLight);
                animationController.setMuzzleFlashTimer(muzzleFlashTimer);
                animationController.update(dt, moving && !climbing, sprinting, mz < 0 ? -1 : 1);

                // Sync back animation state
                weaponKick = animationController.getWeaponKick();
                cameraRecoil = animationController.getCameraRecoil();
                walkPhase = animationController.getWalkPhase();
                moveBlend = animationController.getMoveBlend();
                swapBlend = animationController.getSwapBlend();
        updateHUD();
    }

    private void updateVertical(double dt) {
        double px = player.getTranslateX(), pz = player.getTranslateZ();
        double sup = supportY(px, pz, feetY);
        supY = sup;
        if (grounded) {
            if (sup > feetY + 1.5) {
                grounded = false;
                velY = 0;
            } else {
                feetY = sup;
            }
        } else {
            velY += GRAVITY * dt;
            double ny = feetY + velY * dt;
            if (velY >= 0 && ny >= sup) {
                if (velY > 260) {
                    landBlend = Math.min(1, velY / 700);
                    shake = Math.max(shake, 2 + landBlend * 3);
                }
                feetY = sup;
                velY = 0;
                grounded = true;
            } else {
                feetY = ny;
            }
        }
        player.setTranslateY(feetY - 55);
    }

    private boolean tryClimb() {
        double fx = Math.sin(Math.toRadians(playerYaw)), fz = Math.cos(Math.toRadians(playerYaw));
        double px = player.getTranslateX(), pz = player.getTranslateZ();
        for (double dd : new double[]{PLAYER_RADIUS + 4, PLAYER_RADIUS + 16, PLAYER_RADIUS + 30}) {
            double qx = px + fx * dd, qz = pz + fz * dd;
            Obstacle o = obstacleAt(qx, qz);
            if (o != null && o.top < feetY - 8 && feetY - o.top <= 135) {
                double m = Math.min(10, Math.min(o.halfWidth, o.halfDepth) * 0.5);
                double tx = Math.max(o.x - o.halfWidth + m, Math.min(o.x + o.halfWidth - m, qx + fx * 12));
                double tz = Math.max(o.z - o.halfDepth + m, Math.min(o.z + o.halfDepth - m, qz + fz * 12));
                climbing = true;
                climbT = 0;
                climbSX = px;
                climbSY = feetY;
                climbSZ = pz;
                climbTX = tx;
                climbTY = o.top;
                climbTZ = tz;
                velY = 0;
                crouched = false;
                reloadTimer = 0;
                return true;
            }
        }
        return false;
    }

    private void updateClimb(double dt) {
        climbT += dt;
        double p = Math.min(1, climbT / 0.95);
        double up = Math.min(1, p / 0.65);
        up = up * up * (3 - 2 * up);
        double fw = Math.max(0, (p - 0.55) / 0.45);
        fw = fw * fw * (3 - 2 * fw);
        feetY = lerp(climbSY, climbTY, up);
        player.setTranslateX(lerp(climbSX, climbTX, fw));
        player.setTranslateZ(lerp(climbSZ, climbTZ, fw));
        player.setTranslateY(feetY - 55);
        if (p >= 1) {
            climbing = false;
            grounded = true;
            velY = 0;
            landBlend = 0.4;
        }
    }

    // ===================== DOORS / INTERACT =====================
    // ---- interaction system: doors and beds are Interactables, one key uses whichever is best
    private final class DoorTarget implements Interactable {
        private final Building b;

        DoorTarget(Building b) {
            this.b = b;
        }

        @Override
        public String prompt() {
            return b.doorOpen ? "Close door" : "Open door";
        }

        @Override
        public double x() {
            return b.doorX;
        }

        @Override
        public double z() {
            return b.doorZ;
        }

        @Override
        public double range() {
            return 85;
        }

        @Override
        public boolean available() {
            return !climbing && Math.abs(feetY - GROUND_Y) < 130;
        }

        @Override
        public boolean enabled() {
            return !lock.busy();
        }

        @Override
        public void interact() {
            startDoorInteraction(b);
        }
    }

    private final class BedTarget implements Interactable {
        private final double[] bed;

        BedTarget(double[] bed) {
            this.bed = bed;
        }

        @Override
        public String prompt() {
            return isNightNow() ? "Sleep" : "Sleep (night only)";
        }

        @Override
        public double x() {
            return bed[0];
        }

        @Override
        public double z() {
            return bed[1];
        }

        @Override
        public double range() {
            return BED_RANGE;
        }

        @Override
        public int priority() {
            return 1;
        }

        @Override
        public boolean available() {
            return !climbing && Math.abs(feetY - GROUND_Y) <= 45;
        }

        @Override
        public boolean enabled() {
            return isNightNow() && !lock.busy();
        }

        @Override
        public String disabledReason() {
            return isNightNow() ? "BUSY" : "YOU CAN ONLY SLEEP AT NIGHT";
        }

        @Override
        public void interact() {
            trySleep();
        }
    }

    private void registerInteractables() {
        interactions.clear();
        for (Building b : buildings) {
            interactions.register(new DoorTarget(b));
        }
        for (double[] bed : beds) {
            interactions.register(new BedTarget(bed));
        }
    }

    private void useInteraction() {
        if (paused || gameOver || sleeping) {
            return;
        }
        Interactable cur = interactions.update(player.getTranslateX(), player.getTranslateZ());
        if (cur == null) {
            status("NOTHING TO INTERACT WITH", Color.LIGHTGRAY);
        } else if (!cur.enabled()) {
            if (!lock.busy()) {
                status(cur.disabledReason(), Color.ORANGE);
            }
        } else {
            cur.interact();
        }
    }

    private String doorBlockReason(Building b) {
        if (!b.doorOpen) {
            return null;
        }
        boolean busy = dist(player.getTranslateX(), player.getTranslateZ(), b.doorX, b.doorZ) < 26;
        for (Enemy e : enemies) {
            if (e.alive && dist(e.model.getTranslateX(), e.model.getTranslateZ(), b.doorX, b.doorZ) < 32) {
                busy = true;
            }
        }
        return busy ? "DOORWAY BLOCKED" : null;
    }

    /** Reach out, and move the door at the moment the hand touches it (checked again then, in case things changed). */
    private void startDoorInteraction(Building b) {
        String why = doorBlockReason(b);
        if (why != null) {
            status(why, Color.ORANGE);
            return;
        }
        final boolean opening = !b.doorOpen;
        firing = false;
        lock.start(ActionLock.Type.DOOR, 0.55, 0.4, () -> {
            if (dist(player.getTranslateX(), player.getTranslateZ(), b.doorX, b.doorZ) > 120) {
                status("TOO FAR FROM THE DOOR", Color.ORANGE);
                lock.cancel();
                return;
            }
            String again = doorBlockReason(b);
            if (again != null) {
                status(again, Color.ORANGE);
                lock.cancel();
                return;
            }
            b.doorOpen = opening;
            if (b.id != null) {
                worldState.setDoorOpen(b.id, opening);
            }
            status(opening ? "DOOR OPENED" : "DOOR CLOSED", Color.LIGHTGRAY);
        }, null);
    }

    /** Knocks the torso back and twists the head toward the nearest threat, and briefly slows the player. */
    private void triggerHitReaction() {
        hitReact = 1;
        double px = player.getTranslateX(), pz = player.getTranslateZ(), best = 450;
        hitSide = RNG.nextBoolean() ? 1 : -1;
        for (Enemy e : enemies) {
            double d = dist(px, pz, e.model.getTranslateX(), e.model.getTranslateZ());
            if (e.alive && d < best) {
                best = d;
                double ang = Math.toDegrees(Math.atan2(e.model.getTranslateX() - px, e.model.getTranslateZ() - pz)) - playerYaw;
                ang = ((ang % 360) + 540) % 360 - 180;
                hitSide = ang >= 0 ? 1 : -1;
            }
        }
    }

    // ===================== EQUIP / ITEMS / ACTIONS =====================
    private void status(String s, Color c) {
        if (statusText != null) {
            statusText.setText(s);
            statusText.setFill(c);
        }
    }

    private void cancelWeaponActions() {
        reloadTimer = 0;
        throwing = false;
        usingMed = false;
        firing = false;
    }

    private void cancelActions() {
        cancelWeaponActions();
        lock.cancel();
    }

    private void cycleWeapon(int dir) {
        int cur = heldItem != 0 ? 0 : currentWeapon.ordinal();
        equipSlot(((cur + dir) % 3 + 3) % 3);
    }

    private void equipSlot(int i) {
        if (paused || gameOver || climbing || sleeping) {
            return;
        }
        if (i <= 2) {
            WeaponType w = i == 0 ? WeaponType.PISTOL : i == 1 ? WeaponType.ASSAULT_RIFLE : WeaponType.SHOTGUN;
            if ((currentWeapon == w && heldItem == 0) || lock.busy()) {
                return;
            }
            firing = false;
            lock.start(ActionLock.Type.SWAP, 0.42, 0.45, () -> {
                cancelWeaponActions();
                heldItem = 0;
                currentWeapon = w;
                lastShotTime = 0;
                rebuildWeaponModel();
                status("EQUIPPED: " + w.displayName, Color.GOLD);
            }, null);
        } else if (lock.busy()) {
            return;
        } else if (i == 3) {
            useMedkit();
        } else if (i == 4) {
            if (grenades <= 0) {
                status("NO GRENADES", Color.ORANGE);
                return;
            }
            if (heldItem == 1) {
                return;
            }
            cancelActions();
            heldItem = 1;
            rebuildWeaponModel();
            status("GRENADE READY - CLICK TO THROW", Color.GOLD);
        } else {
            torchOn = !torchOn;
            torchLight.setLightOn(torchOn);
            status(torchOn ? "FLASHLIGHT ON" : "FLASHLIGHT OFF", Color.GOLD);
        }
    }

    private void useMedkit() {
        if (usingMed) {
            return;
        }
        if (medkits <= 0) {
            status("NO MEDKITS", Color.ORANGE);
            return;
        }
        if (health >= 100) {
            status("HEALTH IS FULL", Color.LIGHTGRAY);
            return;
        }
        cancelActions();
        prevHeld = heldItem;
        heldItem = 2;
        usingMed = true;
        medT = 0;
        rebuildWeaponModel();
        status("USING MEDKIT...", Color.WHITE);
    }

    private void reload() {
        if (paused || gameOver || sleeping || health <= 0 || heldItem != 0 || reloadTimer > 0 || climbing || lock.busy()) {
            return;
        }
        if (magazineAmmo.get(currentWeapon) >= currentWeapon.magazineSize || reserveAmmo.get(currentWeapon) <= 0) {
            return;
        }
        reloadDur = reloadTimeFor(currentWeapon);
        reloadTimer = reloadDur;
        status("RELOADING...", Color.WHITE);
    }

    private void startThrow() {
        if (grenades <= 0 || throwing || paused || gameOver || climbing || sleeping || lock.busy()) {
            return;
        }
        throwing = true;
        threw = false;
        throwT = 0;
    }

    private void updateActions(double dt) {
        lock.update(dt);
        if (lock.type() == ActionLock.Type.SWAP && lock.progress() < 0.45) {
            swapBlend = Math.min(1, lock.progress() / 0.45);       // old weapon lowers until the swap moment
        }
        if (reloadTimer > 0) {
            reloadTimer -= dt;
            if (reloadTimer <= 0) {
                int cur = magazineAmmo.get(currentWeapon), res = reserveAmmo.get(currentWeapon);
                int amt = Math.min(currentWeapon.magazineSize - cur, res);
                magazineAmmo.put(currentWeapon, cur + amt);
                reserveAmmo.put(currentWeapon, res - amt);
                status("WEAPON RELOADED", Color.LIGHTGREEN);
            }
        }
        if (usingMed) {
            medT += dt;
            if (medT >= 1.6) {
                usingMed = false;
                medkits--;
                health = Math.min(100, health + 55);
                heldItem = prevHeld;
                rebuildWeaponModel();
                status("HEALED +55", Color.LIGHTGREEN);
            }
        }
        if (throwing) {
            throwT += dt;
            if (!threw && throwT >= 0.32) {
                threw = true;
                grenades--;
                double[] dir = rayDir(cameraYaw, cameraPitch + 8);
                double fx = Math.sin(Math.toRadians(playerYaw)), fz = Math.cos(Math.toRadians(playerYaw));
                Grenade g = new Grenade();
                g.node = sph(4, mat(Color.rgb(60, 72, 44), Color.rgb(120, 130, 100), 40), 0, 0, 0, 1, 1, 1);
                g.x = player.getTranslateX() + fx * 30;
                g.y = feetY - 62;
                g.z = player.getTranslateZ() + fz * 30;
                g.vx = dir[0] * 430;
                g.vy = dir[1] * 430 - 90;
                g.vz = dir[2] * 430;
                g.fuse = 2.1;
                g.node.setTranslateX(g.x);
                g.node.setTranslateY(g.y);
                g.node.setTranslateZ(g.z);
                grenadeList.add(g);
                world.getChildren().add(g.node);
            }
            if (throwT >= 0.7) {
                throwing = false;
                if (grenades <= 0) {
                    heldItem = 0;
                    rebuildWeaponModel();
                    status("OUT OF GRENADES", Color.ORANGE);
                }
            }
        }
    }

    // ===================== INPUT =====================
    private void setupMouse(SubScene gameScene) {
        gameScene.setOnMousePressed(e -> {
            mouseX = e.getSceneX();
            mouseY = e.getSceneY();
            if (e.getButton() == MouseButton.PRIMARY && !paused && !gameOver && !sleeping) {
                if (heldItem == 1) {
                    startThrow();
                } else if (heldItem == 0) {
                    firing = true;
                    shoot();
                }
            }
            if (e.getButton() == MouseButton.SECONDARY && !paused && !gameOver && !sleeping) {
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
            cameraYaw += (e.getSceneX() - mouseX) * 0.22 * settings.sensitivity;
            cameraPitch -= (e.getSceneY() - mouseY) * 0.22 * settings.sensitivity;
            cameraPitch = Math.max(-50, Math.min(50, cameraPitch));
            mouseX = e.getSceneX();
            mouseY = e.getSceneY();
        });
    }

    private void setupKeyboard(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            KeyCode c = e.getCode();
            boolean first = keys.add(c);
            if (!first || settingsPanel.isCapturing()) {
                return;
            }
            if (settings.input.matches(Action.MAP, c)) {
                if (state == GameState.PLAYING && !sleeping) {
                    openMap();
                } else if (state == GameState.MAP) {
                    closeMap();
                }
                return;
            }
            if (paused || gameOver || sleeping) {
                return;
            }
            if (settings.input.matches(Action.JUMP, c)) {
                if (!climbing) {
                    if (worldTime - lastSpaceTime < 0.4) {
                        if (!tryClimb() && grounded && !crouched) {
                            velY = -JUMP_FORCE;
                            grounded = false;
                        }
                    } else if (grounded) {
                        if (crouched) {
                            crouched = false;
                        } else {
                            velY = -JUMP_FORCE;
                            grounded = false;
                        }
                    }
                    lastSpaceTime = worldTime;
                }
            } else if (settings.input.matches(Action.CROUCH, c)) {
                if (grounded && !climbing) {
                    crouched = !crouched;
                }
            } else if (settings.input.matches(Action.INTERACT, c)) {
                useInteraction();
            } else if (settings.input.matches(Action.RELOAD, c)) {
                reload();
            } else if (settings.input.matches(Action.FLASHLIGHT, c)) {
                equipSlot(5);
            } else if (settings.input.matches(Action.QUICK_SAVE, c)) {
                saveGame(true);
            } else if (c == KeyCode.DIGIT1) {
                equipSlot(0);
            } else if (c == KeyCode.DIGIT2) {
                equipSlot(1);
            } else if (c == KeyCode.DIGIT3) {
                equipSlot(2);
            } else if (c == KeyCode.DIGIT4) {
                equipSlot(3);
            } else if (c == KeyCode.DIGIT5) {
                equipSlot(4);
            } else if (c == KeyCode.DIGIT6) {
                equipSlot(5);
            }
        });
        scene.addEventFilter(KeyEvent.KEY_RELEASED, e -> keys.remove(e.getCode()));
    }

    /** Single place that changes the game state; keeps the legacy paused / gameOver flags in sync. */
    private void setState(GameState next) {
        state = next;
        paused = next == GameState.MENU || next == GameState.PAUSED || next == GameState.MAP || next == GameState.SETTINGS;
        gameOver = next == GameState.GAME_OVER;
    }

    private void handleEscape() {
        switch (state) {
            case PLAYING:
                pauseGame();
                break;
            case PAUSED:
                resumeGame();
                break;
            case MAP:
                closeMap();
                break;
            case SETTINGS:
                closeSettings();
                break;
            case MENU:
                if (menu.isConfirmOpen()) {
                    menu.closeConfirm();
                }
                break;
            default:
                break;
        }
    }

    private void togglePause() {
        if (state == GameState.PLAYING) {
            pauseGame();
        } else if (state == GameState.PAUSED) {
            resumeGame();
        }
    }

    private void pauseGame() {
        setState(GameState.PAUSED);
        keys.clear();
        firing = false;
        isAiming = false;
        pauseMsg.setText("");
        pauseOverlay.setVisible(true);
        pauseOverlay.toFront();
    }

    private void resumeGame() {
        setState(GameState.PLAYING);
        keys.clear();
        pauseOverlay.setVisible(false);
        rootPane.requestFocus();
    }

    // ===================== ZOMBIE SPAWNING =====================
    private void spawnInitialZombies() {
        for (int i = 0; i < 22; i++) {
            spawnRandomZombie(true);
        }
    }

    private int countType(int t) {
        int n = 0;
        for (Enemy e : enemies) {
            if (e.alive && e.type == t) {
                n++;
            }
        }
        return n;
    }

    private int pickType() {
        double tank = threat < 2 ? 0 : 1.5 + threat * 0.7, smoker = threat < 1 ? 0 : 4 + threat * 1.1, runner = 14 + threat * 2.5;
        if (countType(Z_TANK) >= 1 + (int) (threat / 3)) {
            tank = 0;
        }
        if (countType(Z_SMOKER) >= 2 + (int) (threat / 3)) {
            smoker = 0;
        }
        double total = 100 + runner + tank + smoker, r = RNG.nextDouble() * total;
        if (r < tank) {
            return Z_TANK;
        }
        r -= tank;
        if (r < smoker) {
            return Z_SMOKER;
        }
        r -= smoker;
        if (r < runner) {
            return Z_RUNNER;
        }
        return Z_WALKER;
    }

    private Enemy spawnEnemy(double x, double z, int type) {
        Enemy e = new Enemy(x, z, type);
        e.model.setTranslateY(GROUND_Y);
        e.spawnTimer = 0;
        e.idleTimer = 1 + RNG.nextDouble() * 4;
        if (bloodMoon) {
            e.setBlood(true);
        }
        enemies.add(e);
        world.getChildren().add(e.model);
        return e;
    }

    private void spawnRandomZombie(boolean initial) {
        double px = player.getTranslateX(), pz = player.getTranslateZ();
        double minD = initial ? 250 : 380, maxD = 1400;
        int type = pickType();
        for (int attempt = 0; attempt < 14; attempt++) {
            double r = RNG.nextDouble();
            if (r < 0.42 && type != Z_TANK && !buildings.isEmpty()) {
                Building b = buildings.get(RNG.nextInt(buildings.size()));
                if (b.spawn.isEmpty()) {
                    continue;
                }
                double[] sp = b.spawn.get(RNG.nextInt(b.spawn.size()));
                double d = dist(px, pz, sp[0], sp[1]);
                if (d < minD || d > maxD) {
                    continue;
                }
                spawnEnemy(sp[0], sp[1], type);
                return;
            } else if (r < 0.68 && type != Z_TANK && !cars.isEmpty()) {
                Car c = cars.get(RNG.nextInt(cars.size()));
                double d = dist(px, pz, c.x, c.z);
                if (d < minD || d > maxD) {
                    continue;
                }
                double sgn = RNG.nextBoolean() ? 1 : -1;
                double sx = c.x + (c.turned ? 0 : 46 * sgn), sz = c.z + (c.turned ? 46 * sgn : 0);
                if (blocked(sx, sz, 22, 0, false)) {
                    continue;
                }
                Enemy e = spawnEnemy(sx, sz, type);
                e.car = c;
                e.hiddenTimer = initial ? 0.01 : 1.8;
                e.model.setVisible(false);
                return;
            } else {
                double[] rr = roadRects.get(RNG.nextInt(roadRects.size()));
                double sx = rr[0] + (RNG.nextDouble() - 0.5) * (rr[2] - 40), sz = rr[1] + (RNG.nextDouble() - 0.5) * (rr[3] - 40);
                double d = dist(px, pz, sx, sz);
                if (d < minD || d > maxD || Math.abs(sx) > WORLD_BOUNDARY || sz < -WORLD_BOUNDARY || sz > WORLD_MAX_Z || blocked(sx, sz, 34, 0, false)) {
                    continue;
                }
                Enemy e = spawnEnemy(sx, sz, type);
                if (!initial) {
                    e.spawnTimer = 1.2;
                    e.model.setTranslateY(GROUND_Y + 62);
                }
                return;
            }
        }
    }

    private void updateSpawning(double dt) {
        survivalTime += dt;
        threat = Math.min(10, survivalTime / 70.0 + kills / 14.0);
        int ti = (int) threat;
        if (ti > lastThreatInt) {
            lastThreatInt = ti;
            showBanner("THREAT LEVEL " + ti, Color.ORANGERED);
        }
        int alive = 0;
        for (Enemy e : enemies) {
            if (e.alive) {
                alive++;
            }
        }
        int cap = 20 + (int) (effThreat() * 3) + (bloodMoon ? 14 : 0);
        spawnClock -= dt;
        if (spawnClock <= 0) {
            spawnClock = Math.max(bloodMoon ? 0.5 : 0.8, (4.0 - effThreat() * 0.3) * (bloodMoon ? 0.5 : 1.0));
            if (alive < cap) {
                spawnRandomZombie(false);
            }
        }
    }

    // ===================== ZOMBIE AI =====================
    private boolean moveEnemy(Enemy e, double tx, double tz, double speed, double dt) {
        double ex = e.model.getTranslateX(), ez = e.model.getTranslateZ();
        double dx = tx - ex, dz = tz - ez, d = Math.sqrt(dx * dx + dz * dz);
        if (d < 1) {
            return false;
        }
        double ux = dx / d, uz = dz / d;
        if (e.sideT > 0) {
            e.sideT -= dt;
            double sx = -uz * e.sideDir, sz = ux * e.sideDir;
            ux = ux * 0.3 + sx * 0.9;
            uz = uz * 0.3 + sz * 0.9;
            double l = Math.sqrt(ux * ux + uz * uz);
            ux /= l;
            uz /= l;
        }
        double nx = ex + ux * speed * dt, nz = ez + uz * speed * dt;
        boolean mxOk = !blocked(nx, ez, e.radius, 0, false);
        if (mxOk) {
            e.model.setTranslateX(clampX(nx));
        }
        boolean mzOk = !blocked(e.model.getTranslateX(), nz, e.radius, 0, false);
        if (mzOk) {
            e.model.setTranslateZ(clampZ(nz));
        }
        if (!mxOk && !mzOk) {
            e.stuck += dt;
            if (e.stuck > 0.4) {
                e.sideT = 0.7;
                e.sideDir = RNG.nextBoolean() ? 1 : -1;
                e.stuck = 0;
            }
        } else {
            e.stuck = 0;
        }
        return true;
    }

    private double[] steerTarget(Enemy e, Building eb, Building pb, double px, double pz) {
        double ex = e.model.getTranslateX(), ez = e.model.getTranslateZ();
        if (eb == null) {
            e.exitStage = 0;
        }
        if (eb == pb || pb == null) {
            e.enterStage = 0;
        }
        if (eb != null && eb != pb) {
            if (e.exitStage == 0) {
                if (dist(ex, ez, eb.inX, eb.inZ) < 34) {
                    e.exitStage = 1;
                    eb.doorOpen = true;
                }
                return new double[]{eb.inX, eb.inZ};
            }
            return new double[]{eb.outX, eb.outZ};
        }
        if (pb != null && eb != pb) {
            if (e.enterStage == 0) {
                if (dist(ex, ez, pb.outX, pb.outZ) < 40) {
                    e.enterStage = 1;
                    pb.doorOpen = true;
                }
                return new double[]{pb.outX, pb.outZ};
            }
            return new double[]{pb.inX, pb.inZ};
        }
        if (eb != null && eb == pb) {
            return eb.route(ex, ez, px, pz);               // same house: go through the doorways, not into the walls
        }
        return new double[]{px, pz};
    }

    private void updateEnemies(double dt) {
        double px = player.getTranslateX(), pz = player.getTranslateZ();
        Building pb = insideB;
        double baseSpeed = 62 + effThreat() * 3.5;
        boolean reachable = feetY > GROUND_Y - 55;
        for (Iterator<Enemy> it = enemies.iterator(); it.hasNext();) {
            Enemy e = it.next();
            double ex = e.model.getTranslateX(), ez = e.model.getTranslateZ();
            double dx = px - ex, dz = pz - ez, dist = Math.sqrt(dx * dx + dz * dz);
            if (!e.alive) {
                e.deathTime += dt;
                double fp = Math.min(1, e.deathTime / 0.55);
                e.fall.setAngle(e.deathDir * fp * fp * (3 - 2 * fp) * 88);
                double sink = e.deathTime > 9 ? (e.deathTime - 9) * 12 : 0;
                e.model.setTranslateY(GROUND_Y - 9 * e.sy * fp + sink);
                e.model.setVisible(dist < 1100);
                if (e.deathTime > 14) {
                    world.getChildren().remove(e.model);
                    it.remove();
                }
                continue;
            }
            if (e.hiddenTimer > 0) {
                e.hiddenTimer -= dt;
                if (e.car != null) {
                    e.car.rot.setAngle(e.car.baseYaw + Math.sin(worldTime * 45) * 1.6);
                }
                if (e.hiddenTimer <= 0) {
                    if (e.car != null) {
                        e.car.rot.setAngle(e.car.baseYaw);
                    }
                    e.model.setVisible(dist < 1100);
                    burst(dustMat, ex, GROUND_Y - 10, ez, 6, 60, 5, 0.8, 0, false, null);
                    e.alert = 6;
                }
                continue;
            }
            if (dist > 1300) {
                e.model.setVisible(false);
                continue;
            }
            e.model.setVisible(dist < 1100);
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
            if (e.slamT > 0) {
                e.slamT -= dt;
            }
            e.attackCd -= dt;
            Building eb = buildingAt(ex, ez);
            double det = e.detect * (crouched ? 0.6 : 1) * (sprintingNow ? 1.35 : 1);
            if (e.alert > 0) {
                e.alert -= dt;
            }
            boolean sees = dist < (eb == pb ? det : Math.min(det * 0.4, 160));
            if (e.blood && dist < 1100) {
                sees = true;
            }
            if (sees || e.alert > 0) {
                if (e.state != S_CHASE) {
                    burst(dustMat, ex, GROUND_Y - 40 * e.sy, ez, 2, 20, 3, 0.5, 0, false, null);
                }
                e.state = S_CHASE;
                e.lostTimer = 7;
            } else if (e.state == S_CHASE) {
                e.lostTimer -= dt;
                if (e.lostTimer <= 0) {
                    e.state = S_IDLE;
                    e.idleTimer = 2 + RNG.nextDouble() * 3;
                }
            }
            boolean moving = false, chasing = e.state == S_CHASE;
            double faceX = dx, faceZ = dz;
            double speed = baseSpeed * e.speedMul * (e.stagger > 0 ? 0.15 : 1);
            if (e.state == S_IDLE) {
                e.idleTimer -= dt;
                if (e.idleTimer <= 0) {
                    e.state = S_WANDER;
                    e.wanderT = 4 + RNG.nextDouble() * 4;
                    if (eb != null && !eb.spawn.isEmpty()) {
                        double[] sp = eb.spawn.get(RNG.nextInt(eb.spawn.size()));
                        e.wx = sp[0];
                        e.wz = sp[1];
                    } else {
                        e.wx = clampX(ex + (RNG.nextDouble() - 0.5) * 260);
                        e.wz = clampZ(ez + (RNG.nextDouble() - 0.5) * 260);
                    }
                }
            } else if (e.state == S_WANDER) {
                e.wanderT -= dt;
                faceX = e.wx - ex;
                faceZ = e.wz - ez;
                moving = moveEnemy(e, e.wx, e.wz, speed * 0.35, dt);
                if (dist(ex, ez, e.wx, e.wz) < 15 || e.wanderT <= 0) {
                    e.state = S_IDLE;
                    e.idleTimer = 2 + RNG.nextDouble() * 4;
                }
            } else if (e.type == Z_SMOKER) {
                e.castCd -= dt;
                if (e.windup > 0) {
                    e.windup -= dt;
                    if (e.stagger > 0.05) {
                        e.windup = -1;
                    } else if (e.windup <= 0) {
                        castSmoke(e, px, pz);
                        e.castCd = 7;
                    }
                } else if (dist <= e.reach) {
                    if (e.attackCd <= 0 && reachable) {
                        damagePlayer(8, "Clawed by a Smoker");
                        e.attackCd = 1.2;
                    }
                } else if (dist <= 380 && dist >= 150 && e.castCd <= 0 && eb == pb) {
                    e.windup = e.windupDur;
                } else if (dist > 240 || eb != pb) {
                    double[] t = steerTarget(e, eb, pb, px, pz);
                    faceX = t[0] - ex;
                    faceZ = t[1] - ez;
                    moving = moveEnemy(e, t[0], t[1], speed, dt);
                }
                e.puffT -= dt;
                if (e.puffT <= 0) {
                    e.puffT = 0.3;
                    double ry = Math.toRadians(e.yaw);
                    emit(smokeMat, 3, ex + Math.sin(ry) * 10, GROUND_Y - 92 * e.sy, ez + Math.cos(ry) * 10, 0, -14, 0, 1.6, -4, false, false, 1.8);
                }
            } else {
                if (e.windup > 0) {
                    e.windup -= dt;
                    if (e.stagger > 0.05 && e.type == Z_WALKER) {
                        e.windup = -1;
                    } else if (e.windup <= 0) {
                        strike(e, dist(ex, ez, px, pz), reachable);
                    }
                } else if (dist <= e.reach && eb == pb) {
                    if (e.attackCd <= 0 && reachable) {
                        e.windup = e.windupDur;
                    }
                } else {
                    double[] t = steerTarget(e, eb, pb, px, pz);
                    faceX = t[0] - ex;
                    faceZ = t[1] - ez;
                    moving = moveEnemy(e, t[0], t[1], speed, dt);
                }
            }
            if (Math.abs(faceX) + Math.abs(faceZ) > 0.01 && (moving || e.windup > 0 || e.state != S_IDLE)) {
                double target = Math.toDegrees(Math.atan2(faceX, faceZ)), diff = target - e.yaw;
                while (diff > 180) {
                    diff -= 360;
                }
                while (diff < -180) {
                    diff += 360;
                }
                e.yaw += diff * Math.min(1, dt * 7);
                e.facing.setAngle(e.yaw);
            }
            if (e.type == Z_TANK && moving && dist < 650) {
                boolean step = Math.sin(e.walkPhase * 2) > 0.97;
                if (step && !e.stepFlag) {
                    shake = Math.max(shake, 2.2);
                }
                e.stepFlag = step;
            }
            e.animate(dt, moving, chasing);
        }
        for (int i = 0; i < enemies.size(); i++) {
            Enemy a = enemies.get(i);
            if (!a.alive || a.spawnTimer > 0 || a.hiddenTimer > 0) {
                continue;
            }
            for (int j = i + 1; j < enemies.size(); j++) {
                Enemy b = enemies.get(j);
                if (!b.alive || b.spawnTimer > 0 || b.hiddenTimer > 0) {
                    continue;
                }
                double dx = b.model.getTranslateX() - a.model.getTranslateX(), dz = b.model.getTranslateZ() - a.model.getTranslateZ();
                double d = Math.sqrt(dx * dx + dz * dz), min = Math.min(80, 36 * (a.sx + b.sx) / 2);
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

    private void strike(Enemy e, double d, boolean reachable) {
        e.attackCd = e.type == Z_RUNNER ? 0.8 : e.type == Z_TANK ? 1.8 : 1.0;
        if (e.type == Z_TANK) {
            e.slamT = 0.25;
            shake = Math.max(shake, 6);
        }
        if (d > e.reach * 1.25 || !reachable) {
            return;
        }
        if (e.type == Z_RUNNER) {
            damagePlayer(9999, "Killed by a Runner bite");
        } else if (e.type == Z_TANK) {
            damagePlayer(9999, "Crushed by a Tank punch");
        } else {
            damagePlayer(14 + threat * 1.2, "Torn apart by the infected");
        }
    }

    private void castSmoke(Enemy e, double px, double pz) {
        SmokeCloud c = new SmokeCloud();
        c.x = px + (RNG.nextDouble() - 0.5) * 30;
        c.z = pz + (RNG.nextDouble() - 0.5) * 30;
        c.r = 95;
        c.life = 8;
        smokeClouds.add(c);
        double ex = e.model.getTranslateX(), ez = e.model.getTranslateZ(), ey = GROUND_Y - 92 * e.sy;
        for (int i = 0; i < 16; i++) {
            double t = (i + 1) / 16.0;
            emit(smokeMat, 5, ex, ey, ez, (c.x - ex) * 1.3 + (RNG.nextDouble() - 0.5) * 30, 10, (c.z - ez) * 1.3 + (RNG.nextDouble() - 0.5) * 30, 0.8 + t * 0.4, 0, false, false, 2.5);
        }
    }

    // ===================== DAMAGE =====================
    private void damagePlayer(double amount, String cause) {
        if (health <= 0 || gameOver || sleeping) {
            return;
        }
        health = Math.max(0, health - amount);
        triggerHitReaction();
        damageFlash = Math.min(0.85, damageFlash + 0.55);
        shake = Math.max(shake, amount > 100 ? 14 : 7);
        updateHUD();
        if (health <= 0) {
            deathCause = cause;
            showGameOver();
        }
    }

    private void showGameOver() {
        setState(GameState.GAME_OVER);
        keys.clear();
        firing = false;
        cancelActions();
        int s = (int) survivalTime;
        finalStatsText.setText(deathCause.toUpperCase() + "\n\nSURVIVED: " + (s / 60) + "m " + (s % 60) + "s\nTOTAL KILLS: " + kills
                + "\nTHREAT LEVEL: " + (int) threat);
        pauseOverlay.setVisible(false);
        gameOverOverlay.setVisible(true);
        gameOverOverlay.toFront();
    }

    private void damageEnemy(Enemy e, double dmg, double hx, double hy, double hz, double[] dir, boolean head) {
        if (!e.alive) {
            return;
        }
        e.health -= dmg;
        e.stagger = 0.22 * e.staggerMul;
        e.alert = 15;
        if (e.stagger > 0.1 && e.type == Z_WALKER) {
            e.windup = -1;
        }
        burst(bloodMat, hx, hy, hz, head ? 9 : 5, 200, 2.2, 1.0, 500, true, dir);
        if (e.health <= 0) {
            killEnemy(e, hx, hy, hz, dir);
        }
    }

    private void killEnemy(Enemy e, double hx, double hy, double hz, double[] dir) {
        e.alive = false;
        kills++;
        e.deathDir = RNG.nextBoolean() ? 1 : -1;
        burst(bloodMat, hx, hy, hz, 14, 260, 2.6, 1.2, 500, true, dir);
        e.rig.lShR.setAngle(-30 + RNG.nextDouble() * 140);
        e.rig.rShR.setAngle(-30 + RNG.nextDouble() * 140);
        e.rig.lElbR.setAngle(RNG.nextDouble() * 50);
        e.rig.rElbR.setAngle(RNG.nextDouble() * 50);
        e.rig.lHipR.setAngle(RNG.nextDouble() * 30 - 10);
        e.rig.rHipR.setAngle(RNG.nextDouble() * 30 - 10);
        e.rig.jaw.setAngle(-30);
        e.shadow.setVisible(false);
        double x = e.model.getTranslateX(), z = e.model.getTranslateZ(), r = RNG.nextDouble();
        if (r < 0.22) {
            createPickup(0, x, z);
        } else if (r < 0.30) {
            createPickup(1, x, z);
        } else if (r < 0.36) {
            createPickup(2, x, z);
        }
        if (e.type == Z_SMOKER) {
            SmokeCloud c = new SmokeCloud();
            c.x = x;
            c.z = z;
            c.r = 70;
            c.life = 4;
            smokeClouds.add(c);
        }
    }

    private void alertZombies(double range) {
        double px = player.getTranslateX(), pz = player.getTranslateZ();
        for (Enemy e : enemies) {
            if (e.alive && dist(px, pz, e.model.getTranslateX(), e.model.getTranslateZ()) < range) {
                e.alert = Math.max(e.alert, 8);
            }
        }
    }

    // ===================== SHOOTING =====================
    private double[] rayDir(double yawDeg, double pitchDeg) {
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        return new double[]{Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)};
    }

    private double[] muzzleWorld() {
        double fx = Math.sin(Math.toRadians(playerYaw)), fz = Math.cos(Math.toRadians(playerYaw));
        double fd = GUN_Z + muzzleLocalZ + 4;
        return new double[]{player.getTranslateX() + fx * fd + fz * GUN_X, player.getTranslateY() + GUN_Y + muzzleLocalY + 17.9 * crouchBlend,
            player.getTranslateZ() + fz * fd - fx * GUN_X};
    }

    private void shoot() {
        if (paused || gameOver || sleeping || health <= 0 || heldItem != 0 || reloadTimer > 0 || climbing || usingMed || lock.busy()) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastShotTime < currentWeapon.cooldown) {
            return;
        }
        int curAmmo = magazineAmmo.get(currentWeapon);
        if (curAmmo <= 0) {
            if (reserveAmmo.get(currentWeapon) > 0) {
                reload();
            } else {
                status("OUT OF AMMO!", Color.ORANGE);
            }
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
        alertZombies(currentWeapon == WeaponType.PISTOL ? 700 : 950);

        double[] mw = muzzleWorld();
        double fx = Math.sin(Math.toRadians(playerYaw)), fz = Math.cos(Math.toRadians(playerYaw));
        for (int i = 0; i < 3; i++) {
            emit(sparkMat, 1.2, mw[0], mw[1], mw[2], fx * (140 + RNG.nextDouble() * 140) + (RNG.nextDouble() - 0.5) * 60,
                    (RNG.nextDouble() - 0.5) * 60, fz * (140 + RNG.nextDouble() * 140) + (RNG.nextDouble() - 0.5) * 60, 0.18, 300, false, false, 0);
        }
        emit(brassMat, 1.5, player.getTranslateX() + fx * (GUN_Z + 6) + fz * (GUN_X + 6), player.getTranslateY() + GUN_Y - 4 + 17.9 * crouchBlend,
                player.getTranslateZ() + fz * (GUN_Z + 6) - fx * (GUN_X + 6), fz * (70 + RNG.nextDouble() * 40), -(80 + RNG.nextDouble() * 50),
                -fx * (70 + RNG.nextDouble() * 40), 3, 520, true, false, 0);

        double cx = camera.getTranslateX(), cy = camera.getTranslateY(), cz = camera.getTranslateZ();
        boolean hit = false, elim = false, headshot = false;
        for (int i = 0; i < currentWeapon.pellets; i++) {
            double sYaw = (Math.random() * 2 - 1) * currentWeapon.spread * (isAiming ? 0.4 : crouched ? 0.7 : 1.0);
            double sPitch = (Math.random() * 2 - 1) * currentWeapon.spread * (isAiming ? 0.4 : crouched ? 0.7 : 1.0);
            double[] dir = rayDir(cameraYaw + sYaw, cameraPitch + sPitch);
            Enemy target = findTarget(dir, currentWeapon.range);
            boolean head = hitHead;
            double d = target != null ? hitDist : currentWeapon.range * 0.8;
            boolean groundHit = false;
            if (target == null && dir[1] > 0.001) {
                double tg = (108 - cy) / dir[1];
                if (tg > 0 && tg < d) {
                    d = tg;
                    groundHit = true;
                }
            }
            double hx = cx + dir[0] * d, hy = cy + dir[1] * d, hz = cz + dir[2] * d;
            if (i < 3) {
                spawnTracer(mw[0], mw[1], mw[2], hx, hy, hz);
            }
            if (groundHit && i < 3) {
                burst(sparkMat, hx, 107, hz, 5, 150, 1.1, 0.35, 400, false, null);
                burst(dustMat, hx, 107, hz, 3, 40, 4, 0.7, 0, false, null);
            }
            if (target != null && target.alive) {
                if (head) {
                    headshot = true;
                }
                damageEnemy(target, currentWeapon.damage * (head ? 2.0 : 1.0), hx, hy, hz, dir, head);
                if (!target.alive) {
                    elim = true;
                }
                hit = true;
            }
        }
        if (hit) {
            hitMarker.setFill(elim ? Color.ORANGERED : headshot ? Color.GOLD : Color.WHITE);
            hitMarker.setVisible(true);
            hitMarkerTimer = 0.2;
            status(elim ? (headshot ? "HEADSHOT KILL!" : "INFECTED ELIMINATED") : (headshot ? "HEADSHOT!" : "TARGET HIT"), headshot ? Color.GOLD : Color.LIGHTGREEN);
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
            if (!e.alive || e.spawnTimer > 0 || e.hiddenTimer > 0 || !e.model.isVisible()) {
                continue;
            }
            double ex = e.model.getTranslateX(), ez = e.model.getTranslateZ();
            double th = raySphere(cx, cy, cz, dir, ex, GROUND_Y - 100 * e.sy, ez, 16 * e.sx);
            double tc = raySphere(cx, cy, cz, dir, ex, GROUND_Y - 62 * e.sy, ez, 30 * e.sx);
            double tl = raySphere(cx, cy, cz, dir, ex, GROUND_Y - 32 * e.sy, ez, 24 * e.sx);
            double tb = tc < 0 ? tl : (tl < 0 ? tc : Math.min(tc, tl));
            double t;
            boolean head;
            if (th >= 0 && (tb < 0 || th <= tb + 25 * e.sy)) {
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

    // ===================== PARTICLES / EFFECTS =====================
    private void emit(PhongMaterial m, double size, double x, double y, double z, double vx, double vy, double vz, double life,
            double gravity, boolean bounce, boolean blood, double grow) {
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
        p.maxLife = life;
        p.fade = Math.min(0.4, life * 0.5);
        p.gravity = gravity;
        p.bounce = bounce;
        p.blood = blood;
        p.grow = grow;
        p.moving = true;
        particles.add(p);
        world.getChildren().add(p.node);
    }

    private void burst(PhongMaterial m, double x, double y, double z, int count, double speed, double size, double life,
            double gravity, boolean blood, double[] dir) {
        for (int i = 0; i < count; i++) {
            double vx = (RNG.nextDouble() - 0.5) * speed, vy = (RNG.nextDouble() - 0.7) * speed, vz = (RNG.nextDouble() - 0.5) * speed;
            if (dir != null) {
                vx += dir[0] * speed * 0.5;
                vy += dir[1] * speed * 0.5;
                vz += dir[2] * speed * 0.5;
            }
            emit(m, size * (0.6 + RNG.nextDouble() * 0.8), x, y, z, vx, vy, vz, life * (0.6 + RNG.nextDouble() * 0.8), gravity, false, blood, 0);
        }
    }

    private void spawnTracer(double sx, double sy, double sz, double ex, double ey, double ez) {
        double dx = ex - sx, dy = ey - sy, dz = ez - sz, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 2) {
            return;
        }
        Box b = new Box(0.9, 0.9, len);
        b.setMaterial(tracerMat);
        b.setMouseTransparent(true);
        b.setTranslateX((sx + ex) / 2);
        b.setTranslateY((sy + ey) / 2);
        b.setTranslateZ((sz + ez) / 2);
        b.getTransforms().setAll(new Rotate(Math.toDegrees(Math.atan2(dx, dz)), Rotate.Y_AXIS), new Rotate(Math.toDegrees(Math.asin(-dy / len)), Rotate.X_AXIS));
        Tracer t = new Tracer();
        t.node = b;
        t.life = 0.07;
        tracers.add(t);
        world.getChildren().add(b);
    }

    private void updateEffects(double dt) {
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
                double x = p.node.getTranslateX() + p.vx * dt, y = p.node.getTranslateY() + p.vy * dt, z = p.node.getTranslateZ() + p.vz * dt;
                if (y >= 108.5 && p.vy > 0 && p.gravity > 0) {
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
                            p.maxLife = p.life;
                            p.fade = 1.2;
                        }
                    }
                }
                p.node.setTranslateX(x);
                p.node.setTranslateY(y);
                p.node.setTranslateZ(z);
            }
            double s = Math.min(1, p.life / p.fade) * (1 + p.grow * (p.maxLife - p.life));
            p.node.setScaleX(s);
            p.node.setScaleZ(s);
            p.node.setScaleY(p.flat ? 0.2 * s : s);
        }
        for (Iterator<Tracer> it = tracers.iterator(); it.hasNext();) {
            Tracer t = it.next();
            t.life -= dt;
            if (t.life <= 0) {
                world.getChildren().remove(t.node);
                it.remove();
            } else {
                t.node.setScaleX(t.life / 0.07);
                t.node.setScaleY(t.life / 0.07);
            }
        }
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
        damageFlash = Math.max(0, damageFlash - dt * 1.8);
        double low = health < 35 && health > 0 ? (0.18 + 0.14 * Math.sin(worldTime * 5)) * (1 - health / 35) * 2 : 0;
        redFlash.setOpacity(clamp01(damageFlash + low));
        gasOverlay.setOpacity(clamp01(choke / 5.0) * 0.75);
        updateCrosshair(dt);
        bloodTint.setOpacity(0.45 * bloodBlend);
        playerShadow.setTranslateX(px);
        playerShadow.setTranslateZ(pz);
        playerShadow.setTranslateY(supY - 0.3);
        String pr = null;
        if (state == GameState.PLAYING && !sleeping && !gameOver && !lock.busy()) {
            Interactable it = interactions.update(px, pz);
            if (it != null) {
                String key = settings.input.get(Action.INTERACT, 0) != null ? settings.input.label(Action.INTERACT, 0)
                        : settings.input.label(Action.INTERACT, 1);
                pr = "[" + key + "]  " + it.prompt();
            }
        }
        promptText.setVisible(pr != null);
        if (pr != null) {
            promptText.setText(pr);
        }
    }

    // ===================== WORLD UPDATE =====================
    private void updateWorld(double dt) {
        for (Building b : buildings) {
            b.doorAmt += ((b.doorOpen ? 1 : 0) - b.doorAmt) * Math.min(1, dt * 7);
            b.doorRot.setAngle(90 * b.front * b.doorAmt);
            b.doorObs.active = !b.doorOpen;
            boolean inside = b == insideB;
            if (b.roof.isVisible() == inside) {
                b.roof.setVisible(!inside);
            }
        }
        final double px = player.getTranslateX(), pz = player.getTranslateZ();
        lightTick -= dt;
        if (lightTick <= 0) {
            lightTick = 0.25;
            List<DynLight> tmp = new ArrayList<>(lights);
            tmp.sort(Comparator.comparingDouble(l -> dist(px, pz, l.x, l.z)));
            for (int i = 0; i < tmp.size(); i++) {
                DynLight l = tmp.get(i);
                l.on = i < 5 && dist(px, pz, l.x, l.z) < 700;
                l.light.setLightOn(l.on);
            }
        }
        for (DynLight l : lights) {
            if (!l.on) {
                continue;
            }
            double f;
            if (l.kind == 1) {
                f = RNG.nextDouble() < 0.12 ? 0.08 : 0.55 + 0.4 * RNG.nextDouble();
            } else if (l.kind == 2) {
                f = 0.78 + 0.22 * Math.sin(worldTime * 12 + l.phase) + (RNG.nextDouble() - 0.5) * 0.25;
            } else {
                f = 0.88 + 0.1 * Math.sin(worldTime * 7 + l.phase) + (RNG.nextDouble() - 0.5) * 0.05;
                if (RNG.nextDouble() < 0.004) {
                    f *= 0.3;
                }
            }
            if (l.kind != 2) {
                f *= 1 - daylightNow * 0.97;
            }
            l.light.setColor(Color.color(clamp01(l.base.getRed() * f), clamp01(l.base.getGreen() * f), clamp01(l.base.getBlue() * f)));
        }
        for (Fire f : fires) {
            if (!f.flames.isVisible()) {
                continue;
            }
            f.phase += dt * 14;
            for (int i = 0; i < f.tongues.length; i++) {
                double k = 0.75 + 0.45 * Math.abs(Math.sin(f.phase + i * 2.1)) + RNG.nextDouble() * 0.15;
                f.tongues[i].setScaleY(k);
                f.tongues[i].setScaleX(0.85 + 0.3 * RNG.nextDouble());
                f.tongues[i].setTranslateY(-f.heights[i] * k / 2);
            }
            f.emberT -= dt;
            if (f.emberT <= 0) {
                f.emberT = 0.1 / Math.max(0.6, f.scale * 0.6);
                emit(emberMat, 1.4, f.x + (RNG.nextDouble() - 0.5) * 14 * f.scale, f.y - 10 * f.scale, f.z + (RNG.nextDouble() - 0.5) * 14 * f.scale,
                        (RNG.nextDouble() - 0.5) * 24, -(35 + RNG.nextDouble() * 40), (RNG.nextDouble() - 0.5) * 24, 1.3, -10, false, false, 0);
                if (f.smoke && RNG.nextDouble() < 0.35) {
                    emit(smokeMat, 9, f.x, f.y - 20 * f.scale, f.z, (RNG.nextDouble() - 0.3) * 14, -30, (RNG.nextDouble() - 0.5) * 14, 3, -6, false, false, 1.0);
                }
            }
        }
        boolean beaconOn = (worldTime % 1.6) < 0.22;
        for (Sphere s : beacons) {
            s.setVisible(beaconOn);
        }
        for (int i = 0; i < beamRotates.size(); i++) {
            Rotate r = beamRotates.get(i);
            r.setAngle(r.getAngle() + dt * (i % 2 == 0 ? 22 : -22));
        }
        for (TreeFactory.Tree t : trees) {
            if (t.node.isVisible()) {
                t.sway(worldTime);
            }
        }
        for (VegetationFactory.Chunk c : grassChunks) {
            if (c.node.isVisible()) {
                c.update(worldTime);
            }
        }
        for (Pickup pk : pickups) {
            pk.phase += dt * 3;
            pk.node.setTranslateY(GROUND_Y - 3 - Math.abs(Math.sin(pk.phase)) * 4);
            pk.node.setRotationAxis(Rotate.Y_AXIS);
            pk.node.setRotate(pk.phase * 40);
        }
        for (Iterator<Grenade> it = grenadeList.iterator(); it.hasNext();) {
            Grenade g = it.next();
            g.fuse -= dt;
            g.vy += 700 * dt;
            double nx = g.x + g.vx * dt, nz = g.z + g.vz * dt;
            if (blocked(nx, g.z, 4, 0, false)) {
                g.vx = -g.vx * 0.5;
            } else {
                g.x = nx;
            }
            if (blocked(g.x, nz, 4, 0, false)) {
                g.vz = -g.vz * 0.5;
            } else {
                g.z = nz;
            }
            g.y += g.vy * dt;
            if (g.y >= 106) {
                g.y = 106;
                g.vy = -g.vy * 0.4;
                g.vx *= 0.7;
                g.vz *= 0.7;
                if (Math.abs(g.vy) < 30) {
                    g.vy = 0;
                }
            }
            g.node.setTranslateX(g.x);
            g.node.setTranslateY(g.y);
            g.node.setTranslateZ(g.z);
            if (g.fuse <= 0) {
                explode(g.x, g.y, g.z);
                world.getChildren().remove(g.node);
                it.remove();
            }
        }
        for (Iterator<Explosion> it = explosions.iterator(); it.hasNext();) {
            Explosion ex = it.next();
            ex.t += dt;
            double s = 1 + ex.t * 17;
            ex.node.setScaleX(s);
            ex.node.setScaleY(s);
            ex.node.setScaleZ(s);
            if (ex.t > 0.4) {
                world.getChildren().remove(ex.node);
                it.remove();
            }
        }
        boolean inGas = false;
        for (Iterator<SmokeCloud> it = smokeClouds.iterator(); it.hasNext();) {
            SmokeCloud c = it.next();
            c.life -= dt;
            if (c.life <= 0) {
                it.remove();
                continue;
            }
            c.emit -= dt;
            if (c.emit <= 0) {
                c.emit = 0.06;
                double a = RNG.nextDouble() * 6.28, rr = Math.sqrt(RNG.nextDouble()) * c.r;
                emit(smokeMat, 10, c.x + Math.cos(a) * rr, GROUND_Y - 8 - RNG.nextDouble() * 50, c.z + Math.sin(a) * rr,
                        (RNG.nextDouble() - 0.5) * 10, -8, (RNG.nextDouble() - 0.5) * 10, 2.4, -4, false, false, 1.2);
            }
            if (dist(px, pz, c.x, c.z) < c.r * 0.95 && feetY > GROUND_Y - 90) {
                inGas = true;
            }
        }
        if (!gameOver && health > 0 && !sleeping) {
            if (inGas) {
                choke = Math.min(10, choke + dt);
                health = Math.max(0, health - 9 * dt);
                shake = Math.max(shake, 1.3);
                stamina = Math.max(0, stamina - 20 * dt);
                damageFlash = Math.max(damageFlash, 0.12);
                if (health <= 0) {
                    deathCause = "Suffocated by Smoker gas";
                    showGameOver();
                }
            } else {
                choke = Math.max(0, choke - dt * 1.5);
            }
        }
        cullTick -= dt;
        if (cullTick <= 0) {
            cullTick = 0.3;
            updateCulling();
        }
    }

    private void explode(double x, double y, double z) {
        Explosion ex = new Explosion();
        ex.node = sph(10, glow(Color.rgb(255, 170, 60)), x, y, z, 1, 1, 1);
        explosions.add(ex);
        world.getChildren().add(ex.node);
        burst(emberMat, x, y, z, 22, 420, 5, 0.9, 300, false, null);
        burst(sparkMat, x, y, z, 14, 520, 1.4, 0.6, 500, false, null);
        for (int i = 0; i < 10; i++) {
            emit(smokeMat, 8, x, y, z, (RNG.nextDouble() - 0.5) * 120, -40 - RNG.nextDouble() * 60, (RNG.nextDouble() - 0.5) * 120, 2.2, -20, false, false, 1.6);
        }
        double px = player.getTranslateX(), pz = player.getTranslateZ(), dp = dist(px, pz, x, z);
        shake = Math.max(shake, Math.max(0, 16 - dp / 30));
        alertZombies(1000);
        for (Enemy e : new ArrayList<>(enemies)) {
            double d = dist(x, z, e.model.getTranslateX(), e.model.getTranslateZ());
            if (e.alive && d < 190) {
                damageEnemy(e, 280 * (1 - d / 190) + 25, e.model.getTranslateX(), GROUND_Y - 50 * e.sy, e.model.getTranslateZ(), null, false);
            }
        }
        if (dp < 150 && !gameOver) {
            damagePlayer(70 * (1 - dp / 150), "Blown up by a grenade");
        }
    }

    // ===================== PICKUPS =====================
    private Pickup createPickup(int kind, double x, double z) {
        if (pickups.size() > 160) {
            Pickup old = pickups.remove(0);
            world.getChildren().remove(old.node);
        }
        Pickup pk = new Pickup();
        pk.kind = kind;
        pk.x = x;
        pk.z = z;
        pk.phase = RNG.nextDouble() * 6;
        Group g = new Group();
        Color c;
        if (kind == 0) {
            c = Color.rgb(255, 200, 60);
            g.getChildren().addAll(box(14, 9, 9, mat(Color.rgb(110, 92, 40), Color.rgb(90, 90, 60), 20), 0, -6, 0), box(10, 2, 10, glow(c), 0, -11, 0));
        } else if (kind == 1) {
            c = Color.rgb(255, 80, 80);
            g.getChildren().addAll(box(14, 10, 9, mat(Color.rgb(230, 230, 230), Color.WHITE, 40), 0, -6, 0), box(8, 2, 1, glow(c), 0, -6, 5), box(2, 8, 1, glow(c), 0, -6, 5));
        } else {
            c = Color.rgb(120, 255, 90);
            g.getChildren().addAll(sph(5, mat(Color.rgb(60, 72, 44), Color.rgb(120, 130, 100), 40), 0, -6, 0, 1, 1.15, 1), box(2, 3, 2, glow(c), 0, -13, 0));
        }
        g.getChildren().add(box(1, 36, 1, glow(c), 0, -26, 0));
        g.setTranslateX(x);
        g.setTranslateY(GROUND_Y);
        g.setTranslateZ(z);
        pk.node = g;
        pickups.add(pk);
        world.getChildren().add(g);
        return pk;
    }

    private void collect(Pickup pk) {
        if (pk.kind == 0) {
            for (WeaponType w : WeaponType.values()) {
                reserveAmmo.put(w, reserveAmmo.get(w) + w.magazineSize);
            }
            status("AMMO PICKED UP", Color.GOLD);
        } else if (pk.kind == 1) {
            medkits++;
            status("MEDKIT +1", Color.LIGHTGREEN);
        } else {
            grenades++;
            status("GRENADE +1", Color.LIGHTGREEN);
        }
    }

    // ===================== HUD =====================
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
        double target = 7 + moveBlend * 7 + weaponKick * 10 - (isAiming ? 3 : 0) - (crouched ? 2 : 0);
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
        Text subtitle = new Text("OPEN WORLD EDITION");
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
        enemyText = new Text("INFECTED NEARBY: 0");
        enemyText.setFill(Color.ORANGERED);
        weaponText = new Text("WEAPON: PISTOL");
        weaponText.setFill(Color.GOLD);
        weaponText.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        ammoText = new Text("AMMO: 12 / 48");
        ammoText.setFill(Color.WHITE);
        killsText = new Text("KILLS: 0");
        killsText.setFill(Color.LIGHTGREEN);
        threatText = new Text("THREAT: 0");
        threatText.setFill(Color.GOLD);
        threatText.setFont(Font.font("Arial", FontWeight.BOLD, 15));
        timeText = new Text("SURVIVED: 0:00");
        timeText.setFill(Color.LIGHTGRAY);
        statusText = new Text("STATUS: SURVIVING");
        statusText.setFill(Color.LIGHTGREEN);
        Text hint = new Text("C crouch  |  F door  |  SPACE x2 climb/window\nT torch  |  1-6 quick equip  |  R reload");
        hint.setFill(Color.rgb(170, 170, 170));
        hint.setFont(Font.font("Arial", 10));
        VBox hud = new VBox(7, title, subtitle, healthText, healthBar, staminaText, staminaBar, enemyText, weaponText, ammoText, killsText,
                threatText, timeText, statusText, hint);
        hud.setPadding(new Insets(18));
        hud.setMaxWidth(270);
        hud.setMaxHeight(Region.USE_PREF_SIZE);
        hud.setStyle("-fx-background-color: rgba(12, 16, 22, 0.72); -fx-background-radius: 10; -fx-border-color: rgba(255,255,255,0.1); -fx-border-radius: 10;");
        hud.setMouseTransparent(true);
        return hud;
    }

    private HBox createHotbar() {
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER);
        bar.setPickOnBounds(false);
        bar.setMaxWidth(Region.USE_PREF_SIZE);
        bar.setMaxHeight(Region.USE_PREF_SIZE);
        String[] names = {"PISTOL", "RIFLE", "SHOTGUN", "MEDKIT", "GRENADE", "TORCH"};
        for (int i = 0; i < 6; i++) {
            Text num = new Text(String.valueOf(i + 1));
            num.setFill(Color.GOLD);
            num.setFont(Font.font("Arial", FontWeight.BOLD, 12));
            Text name = new Text(names[i]);
            name.setFill(Color.WHITE);
            name.setFont(Font.font("Arial", FontWeight.BOLD, 10));
            slotCount[i] = new Text("");
            slotCount[i].setFill(Color.LIGHTGRAY);
            slotCount[i].setFont(Font.font("Arial", 11));
            VBox v = new VBox(2, num, name, slotCount[i]);
            v.setAlignment(Pos.CENTER);
            StackPane sp = new StackPane(v);
            sp.setPrefSize(78, 60);
            sp.setMinSize(78, 60);
            sp.setMaxSize(78, 60);
            final int idx = i;
            sp.setOnMouseClicked(ev -> {
                equipSlot(idx);
                ev.consume();
            });
            slotPanes[i] = sp;
            bar.getChildren().add(sp);
        }
        return bar;
    }

    private void updateHUD() {
        if (healthBar == null) {
            return;
        }
        healthBar.setProgress(health / 100);
        staminaBar.setProgress(stamina / 100);
        healthText.setText("HEALTH " + (int) health + " / 100");
        staminaText.setText("STAMINA " + (int) stamina + " / 100");
        weaponText.setText(heldItem == 1 ? "HOLDING: GRENADE" : heldItem == 2 ? "USING: MEDKIT" : "WEAPON: " + currentWeapon.displayName + (crouched ? "  [CROUCHED]" : ""));
        ammoText.setText("AMMO: " + magazineAmmo.get(currentWeapon) + " / " + reserveAmmo.get(currentWeapon) + (reloadTimer > 0 ? "  RELOADING" : ""));
        killsText.setText("KILLS: " + kills);
        threatText.setText("THREAT LEVEL: " + (int) effThreat() + (bloodMoon ? "  [BLOOD MOON]" : "") + (choke > 0.3 ? "   !! GAS !!" : ""));
        int s = (int) survivalTime;
        timeText.setText(String.format("SURVIVED: %d:%02d", s / 60, s % 60));
        int near = 0;
        double px = player.getTranslateX(), pz = player.getTranslateZ();
        for (Enemy e : enemies) {
            if (e.alive && dist(px, pz, e.model.getTranslateX(), e.model.getTranslateZ()) < 450) {
                near++;
            }
        }
        enemyText.setText("INFECTED NEARBY: " + near);
        for (WeaponType w : WeaponType.values()) {
            slotCount[w.ordinal()].setText(magazineAmmo.get(w) + "/" + reserveAmmo.get(w));
        }
        slotCount[3].setText("x" + medkits);
        slotCount[4].setText("x" + grenades);
        slotCount[5].setText(torchOn ? "ON" : "OFF");
        int sel = heldItem == 1 ? 4 : heldItem == 2 ? 3 : currentWeapon.ordinal();
        int key = sel * 2 + (torchOn ? 1 : 0);
        if (key != lastSlotSel) {
            lastSlotSel = key;
            for (int i = 0; i < 6; i++) {
                boolean on = i == sel || (i == 5 && torchOn);
                slotPanes[i].setStyle("-fx-background-color: " + (on ? "rgba(217,155,50,0.85)" : "rgba(12,16,22,0.78)")
                        + "; -fx-background-radius: 6; -fx-border-color: " + (i == sel ? "gold" : "rgba(255,255,255,0.2)")
                        + "; -fx-border-radius: 6; -fx-cursor: hand;");
            }
        }
    }

    // ===================== MINIMAP =====================
    private void createMinimap() {
        miniMap = new Canvas(190, 190);
        miniMap.setMouseTransparent(true);
        mgc = miniMap.getGraphicsContext2D();
    }

    private void mapRect(double cx, double cz, double w, double d, double px, double pz) {
        mgc.fillRect(cx - w / 2 - px, -(cz + d / 2 - pz), w, d);
    }

    private void drawMinimap() {
        double S = 190, c = S / 2, range = 340, sc = (c - 2) / range;
        double px = player.getTranslateX(), pz = player.getTranslateZ();
        mgc.clearRect(0, 0, S, S);
        mgc.save();
        mgc.beginPath();
        mgc.arc(c, c, c - 2, c - 2, 0, 360);
        mgc.closePath();
        mgc.clip();
        mgc.setFill(Color.rgb(24, 36, 26));
        mgc.fillRect(0, 0, S, S);
        mgc.translate(c, c);
        mgc.rotate(-smoothedYaw);
        mgc.scale(sc, sc);
        mgc.setFill(Color.rgb(74, 76, 78));
        for (double[] r : roadRects) {
            mapRect(r[0], r[1], r[2], r[3], px, pz);
        }
        mgc.setFill(Color.rgb(30, 80, 120));
        mapRect(0, RIVER_Z, 3400, 100, px, pz);
        mgc.fillOval(LAKE_X - LAKE_RX - px, -(LAKE_Z + LAKE_RZ - pz), LAKE_RX * 2, LAKE_RZ * 2);
        for (Building b : buildings) {
            mgc.setFill(b.burning ? Color.rgb(230, 110, 40) : Color.rgb(170, 160, 135));
            mapRect(b.x, b.z, b.w, b.d, px, pz);
        }
        mgc.setFill(Color.rgb(120, 135, 150));
        for (Car cr : cars) {
            mapRect(cr.x, cr.z, cr.turned ? 40 : 20, cr.turned ? 20 : 40, px, pz);
        }
        mgc.setFill(Color.GOLD);
        double pr = 4 / sc;
        for (Pickup pk : pickups) {
            mgc.fillOval(pk.x - px - pr / 2, -(pk.z - pz) - pr / 2, pr, pr);
        }
        for (Enemy e : enemies) {
            if (!e.alive) {
                continue;
            }
            mgc.setFill(e.type == Z_TANK ? Color.ORANGE : e.type == Z_SMOKER ? Color.LIMEGREEN : e.type == Z_RUNNER ? Color.rgb(255, 60, 200) : Color.RED);
            double r = (e.type == Z_TANK ? 7 : 4.5) / sc;
            mgc.fillOval(e.model.getTranslateX() - px - r / 2, -(e.model.getTranslateZ() - pz) - r / 2, r, r);
        }
        mgc.setFill(Color.rgb(120, 180, 80, 0.5));
        for (SmokeCloud sm : smokeClouds) {
            mgc.fillOval(sm.x - sm.r - px, -(sm.z + sm.r - pz), sm.r * 2, sm.r * 2);
        }
        if (worldMap != null && worldMap.hasWaypoint()) {
            double dx = worldMap.waypointX() - px, dz = worldMap.waypointZ() - pz, d = Math.hypot(dx, dz), lim = range * 0.92;
            if (d > lim) {
                dx *= lim / d;
                dz *= lim / d;
            }
            double wr = 6 / sc;
            mgc.setFill(Color.rgb(80, 220, 255));
            mgc.fillPolygon(new double[]{dx, dx + wr, dx, dx - wr}, new double[]{-dz - wr * 1.4, -dz, -dz + wr * 1.4, -dz}, 4);
        }
        mgc.restore();
        mgc.setFill(Color.WHITE);
        mgc.fillPolygon(new double[]{c, c - 6, c + 6}, new double[]{c - 8, c + 7, c + 7}, 3);
        mgc.setStroke(Color.GOLD);
        mgc.setLineWidth(2);
        mgc.strokeOval(1, 1, S - 2, S - 2);
    }

    // ===================== MENU / SESSIONS / SAVE / MAP / SETTINGS (3rd update) =====================
    private void setHudVisible(boolean on) {
        for (Node n : hudNodes) {
            if (!on) {
                n.setVisible(false);
            } else if (n != hitMarker && n != promptText) {
                n.setVisible(true);
            }
        }
    }

    private void spawnMenuZombies() {
        double[][] spots = {{-150, 690}, {130, 712}, {-310, 705}, {300, 690}, {-60, 765}, {40, 618}};
        int[] types = {Z_WALKER, Z_WALKER, Z_RUNNER, Z_WALKER, Z_TANK, Z_SMOKER};
        for (int i = 0; i < spots.length; i++) {
            if (!blocked(spots[i][0], spots[i][1], 24, 0, false)) {
                spawnEnemy(spots[i][0], spots[i][1], types[i]);
            }
        }
    }

    private void enterMenu() {
        setState(GameState.MENU);
        keys.clear();
        firing = false;
        isAiming = false;
        cancelActions();
        sleeping = false;
        sleepBlend = 0;
        sleepRot.setAngle(0);
        sleepOverlay.setOpacity(0);
        sleepText.setVisible(false);
        setHudVisible(false);
        player.setVisible(false);
        playerShadow.setVisible(false);
        pauseOverlay.setVisible(false);
        gameOverOverlay.setVisible(false);
        worldMap.setVisible(false);
        settingsPanel.setVisible(false);
        introFade.setVisible(false);
        gameMinutes = 19 * 60 + 20;          // dusk behind the menu
        lastSkyMin = -999;
        menuTime = 0;
        refreshMenu();
        menu.setVisible(true);
        menu.toFront();
    }

    private void refreshMenu() {
        SaveManager.LoadResult r = saves.load();
        boolean ok = r.status() == SaveManager.Status.OK;
        String info;
        if (ok) {
            SaveData d = r.data();
            int mi = (int) d.gameMinutes, hr = (mi % 1440) / 60, mn = mi % 60;
            String when = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(d.savedAtMillis), java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            info = dateString(mi / 1440) + String.format("  %02d:%02d  |  %d kills  |  saved %s", hr, mn, d.kills, when);
            if (!"OK".equals(r.message())) {
                info += "\n" + r.message();
            }
        } else {
            info = r.status() == SaveManager.Status.MISSING ? "No saved game yet." : r.message();
        }
        menu.refresh(ok, info);
    }

    /** Cinematic flyover: the camera slowly orbits the town while the menu is shown. */
    private void updateMenu(double dt) {
        menuTime += dt;
        worldTime += dt;
        final double fx = 0, fz = 650;
        player.setTranslateX(fx);                 // culling, lights and effects follow the player, so park it here
        player.setTranslateZ(fz);
        updateClock(dt);
        for (Enemy e : enemies) {
            if (e.alive) {
                e.animate(dt, false, false);
            }
        }
        updateWorld(dt);
        updateEffects(dt);
        double a = menuTime * 0.06 + 0.8;
        double r = 430 + 70 * Math.sin(menuTime * 0.09), h = 170 + 45 * Math.sin(menuTime * 0.05 + 1);
        camera.setTranslateX(fx + Math.sin(a) * r);
        camera.setTranslateY(GROUND_Y - h);
        camera.setTranslateZ(fz + Math.cos(a) * r);
        camera.setFieldOfView(40);
        camera.getTransforms().setAll(new Rotate(Math.toDegrees(a) + 180, Rotate.Y_AXIS),
                new Rotate(-Math.toDegrees(Math.atan2(h - 40, r)), Rotate.X_AXIS), new Rotate(0, Rotate.Z_AXIS));
        if (sky != null) {
            sky.setTranslateX(camera.getTranslateX());
            sky.setTranslateY(camera.getTranslateY());
            sky.setTranslateZ(camera.getTranslateZ());
        }
    }

    /** Leaves the menu and starts playing. */
    private void beginRun(boolean fresh) {
        setState(GameState.PLAYING);
        streamNow();
        menu.setVisible(false);
        settingsPanel.setVisible(false);
        worldMap.setVisible(false);
        pauseOverlay.setVisible(false);
        gameOverOverlay.setVisible(false);
        setHudVisible(true);
        player.setVisible(true);
        playerShadow.setVisible(true);
        keys.clear();
        lastSkyMin = -999;
        lastClockMin = -1;
        autosaveClock = 0;
        exploreClock = 0;
        worldMap.reveal(player.getTranslateX(), player.getTranslateZ(), 260);
        updateCamera(1.0);
        updateHUD();
        introFade.setVisible(true);
        introFade.setOpacity(1);
        FadeTransition intro = new FadeTransition(Duration.seconds(fresh ? 2.8 : 1.4), introFade);
        intro.setFromValue(1);
        intro.setToValue(0);
        intro.setOnFinished(e -> introFade.setVisible(false));
        intro.play();
        showBanner(fresh ? "SURVIVE THE NIGHT" : "GAME LOADED", fresh ? Color.GOLD : Color.LIGHTGREEN);
        if (fresh) {
            saveGame(false);                 // the new run replaces the old save straight away
        }
        rootPane.requestFocus();
    }

    private void menuContinue() {
        SaveManager.LoadResult r = saves.load();
        if (r.status() != SaveManager.Status.OK) {
            refreshMenu();
            return;
        }
        if (r.data().seed == worldSeed) {
            applySave(r.data());
            beginRun(false);
        } else {
            rebuild(Mode.LOAD, r.data().seed, r.data());
        }
    }

    private void menuNewGame() {
        if (saves.load().status() == SaveManager.Status.OK) {
            menu.showConfirmNew(() -> startNewGame(true));
        } else {
            startNewGame(false);
        }
    }

    /** With a saved game a brand-new world seed is used; with none, the world behind the menu is already fresh. */
    private void startNewGame(boolean newWorld) {
        if (newWorld) {
            rebuild(Mode.NEW, new Random().nextLong(), null);
        } else {
            restartGame();
            beginRun(true);
        }
    }

    /** Builds a different world in a fresh Main instance and swaps it into the same window. */
    private void rebuild(Mode mode, long seed, SaveData data) {
        menu.showLoading(mode == Mode.LOAD ? "LOADING SAVED GAME..." : "GENERATING WORLD...");
        PauseTransition wait = new PauseTransition(Duration.millis(120));   // let the loading cover paint first
        wait.setOnFinished(e -> {
            try {
                new Main().beginSession(stage, seed, mode, data);
                loop.stop();
            } catch (RuntimeException ex) {
                ex.printStackTrace();
                refreshMenu();
                status("COULD NOT BUILD THE WORLD: " + ex.getMessage(), Color.ORANGERED);
            }
        });
        wait.play();
    }

    private void quitToMenu() {
        if (state == GameState.PAUSED && health > 0 && !sleeping) {
            saveGame(false);
        }
        enterMenu();
    }

    // ---- save / load
    private SaveData buildSaveData() {
        SaveData d = new SaveData();
        d.seed = worldSeed;
        d.savedAtMillis = System.currentTimeMillis();
        d.gameMinutes = gameMinutes;
        d.x = player.getTranslateX();
        d.z = player.getTranslateZ();
        d.feetY = feetY;
        d.yaw = cameraYaw;
        d.pitch = cameraPitch;
        d.health = Math.max(1, health);
        d.stamina = stamina;
        d.medkits = medkits;
        d.grenades = grenades;
        d.kills = kills;
        d.survivalTime = survivalTime;
        d.threat = threat;
        d.torchOn = torchOn;
        d.firstPerson = firstPerson;
        d.weapon = currentWeapon.name();
        for (WeaponType w : WeaponType.values()) {
            d.magazine.put(w.name(), magazineAmmo.get(w));
            d.reserve.put(w.name(), reserveAmmo.get(w));
        }
        StringBuilder doors = new StringBuilder();
        for (int i = 0; i < staticBuildingCount && i < buildings.size(); i++) {
            doors.append(buildings.get(i).doorOpen ? '1' : '0');
        }
        d.doors = doors.toString();
        for (Building b : buildings) {                       // loaded streamed doors: record their current state first
            if (b.id != null) {
                worldState.setDoorOpen(b.id, b.doorOpen);
            }
        }
        d.openDoorIds.addAll(worldState.openDoorIds());
        d.takenIds.addAll(worldState.takenIds());
        d.explored = worldMap.exportExplored();
        d.poiFound = worldMap.exportPoi();
        d.hasWaypoint = worldMap.hasWaypoint();
        d.waypointX = worldMap.waypointX();
        d.waypointZ = worldMap.waypointZ();
        for (Enemy e : enemies) {
            if (e.alive && e.hiddenTimer <= 0 && d.enemies.size() < 300) {
                d.enemies.add(new double[]{e.origType, e.model.getTranslateX(), e.model.getTranslateZ(), e.health, e.yaw});
            }
        }
        for (Pickup pk : pickups) {
            if (d.pickups.size() < 300) {
                d.pickups.add(new double[]{pk.kind, pk.x, pk.z});
            }
        }
        return d;
    }

    /** Writes the save file. {@code verbose} shows feedback to the player. */
    private boolean saveGame(boolean verbose) {
        if (state == GameState.MENU || state == GameState.GAME_OVER || health <= 0 || sleeping) {
            if (verbose) {
                pauseMsg.setText("Cannot save right now.");
                status("CANNOT SAVE RIGHT NOW", Color.ORANGE);
            }
            return false;
        }
        try {
            saves.save(buildSaveData());
            if (verbose) {
                pauseMsg.setText("Game saved.");
                pauseMsg.setFill(Color.LIGHTGREEN);
                status("GAME SAVED", Color.LIGHTGREEN);
                showBanner("GAME SAVED", Color.LIGHTGREEN);
            }
            return true;
        } catch (java.io.IOException ex) {
            pauseMsg.setText("Save failed: " + ex.getMessage());
            pauseMsg.setFill(Color.ORANGERED);
            status("SAVE FAILED", Color.ORANGERED);
            return false;
        }
    }

    /** Restores a run. The world itself already matches the save (same seed); this puts back everything that changed. */
    private void applySave(SaveData d) {
        resetRunState();
        gameMinutes = d.gameMinutes;
        health = d.health;
        stamina = d.stamina;
        medkits = d.medkits;
        grenades = d.grenades;
        kills = d.kills;
        survivalTime = d.survivalTime;
        threat = d.threat;
        lastThreatInt = (int) threat;
        try {
            currentWeapon = WeaponType.valueOf(d.weapon);
        } catch (IllegalArgumentException ex) {
            currentWeapon = WeaponType.PISTOL;
        }
        for (WeaponType w : WeaponType.values()) {
            magazineAmmo.put(w, Math.min(w.magazineSize, d.magazine.getOrDefault(w.name(), w.magazineSize)));
            reserveAmmo.put(w, d.reserve.getOrDefault(w.name(), w.startingReserve));
        }
        rebuildWeaponModel();
        torchOn = d.torchOn;
        torchLight.setLightOn(torchOn);
        feetY = d.feetY;
        velY = 0;
        grounded = true;
        player.setTranslateX(d.x);
        player.setTranslateZ(d.z);
        player.setTranslateY(feetY - 55);
        cameraYaw = d.yaw;
        smoothedYaw = d.yaw;
        playerYaw = d.yaw;
        cameraPitch = d.pitch;
        smoothedPitch = d.pitch;
        firstPerson = d.firstPerson;
        viewButton.setText(firstPerson ? "TPP" : "FPP");
        playerRig.headGroup.setVisible(!firstPerson);
        chunks.unloadAll();
        worldState.load(d.openDoorIds, d.takenIds);
        for (int i = 0; i < staticBuildingCount && i < buildings.size() && i < d.doors.length(); i++) {
            Building b = buildings.get(i);
            b.doorOpen = d.doors.charAt(i) == '1';
            b.doorAmt = b.doorOpen ? 1 : 0;
            b.doorRot.setAngle(90 * b.front * b.doorAmt);
        }
        worldMap.resetExploration();
        worldMap.importExplored(d.explored);
        worldMap.importPoi(d.poiFound);
        worldMap.setWaypoint(d.hasWaypoint, d.waypointX, d.waypointZ);
        for (Enemy e : enemies) {
            world.getChildren().remove(e.model);
        }
        enemies.clear();
        for (double[] en : d.enemies) {
            int type = Math.max(0, Math.min(3, (int) en[0]));
            Enemy e = spawnEnemy(en[1], en[2], type);
            e.health = Math.max(1, Math.min(en[3], e.maxHealth));
            e.yaw = en[4];
            e.facing.setAngle(en[4]);
            e.idleTimer = 1 + RNG.nextDouble() * 3;
        }
        for (Pickup pk : pickups) {
            world.getChildren().remove(pk.node);
        }
        pickups.clear();
        for (double[] pk : d.pickups) {
            createPickup((int) pk[0], pk[1], pk[2]);
        }
        streamNow();
        updateCulling();
    }

    private void tickMapAndAutosave(double dt) {
        exploreClock -= dt;
        if (exploreClock <= 0) {
            exploreClock = 0.5;
            for (String n : worldMap.reveal(player.getTranslateX(), player.getTranslateZ(), 260)) {
                showBanner("DISCOVERED: " + n.toUpperCase(), Color.GOLD);
                status("NEW LOCATION: " + n, Color.GOLD);
            }
        }
        autosaveClock += dt;
        if (autosaveClock >= 120) {
            autosaveClock = 0;
            if (!sleeping && saveGame(false)) {
                status("AUTOSAVED", Color.LIGHTGRAY);
            }
        }
    }

    // ---- streamed downtown district
    /** Loads everything within range at once (new game, loading a save): no visible pop-in on the first frame. */
    private void streamNow() {
        double px = player.getTranslateX(), pz = player.getTranslateZ();
        for (int i = 0; i < 60 && chunks.update(px, pz) > 0; i++) {
            // keep going until nothing is left to build
        }
    }

    private DistrictBuilder.Content loadChunk(int cx, int cz) {
        DistrictBuilder.Content c = DistrictBuilder.build(DistrictPlan.generate(worldSeed, cx, cz), this::addObs);
        for (int i = 0; i < c.buildings.size(); i++) {
            Building b = c.buildings.get(i);
            b.id = c.doorIds.get(i);
            if (worldState.isDoorOpen(b.id)) {                 // somebody opened this door earlier
                b.doorOpen = true;
                b.doorAmt = 1;
                b.doorRot.setAngle(90 * b.front);
                b.doorObs.active = false;
            }
            buildings.add(b);
            Interactable door = new DoorTarget(b);
            streamedDoors.put(b, door);
            interactions.register(door);
            for (double[] sp : b.spawn) {                      // the odd zombie is already inside
                if (enemies.size() < 60 && RNG.nextDouble() < 0.35) {
                    spawnEnemy(sp[0], sp[1], RNG.nextInt(3) == 0 ? Z_RUNNER : Z_WALKER);
                }
            }
        }
        trees.addAll(c.trees);
        for (Object[] l : c.loot) {
            String id = (String) l[0];
            if (!worldState.isTaken(id)) {
                createPickup((Integer) l[1], (Double) l[2], (Double) l[3]).id = id;
            }
        }
        world.getChildren().add(c.root);
        return c;
    }

    private void unloadChunk(DistrictBuilder.Content c) {
        world.getChildren().remove(c.root);
        for (Obstacle o : c.obstacles) {
            removeObs(o);
        }
        for (Building b : c.buildings) {
            worldState.setDoorOpen(b.id, b.doorOpen);          // whoever opened or closed it (zombies do too), it stays that way
            buildings.remove(b);
            Interactable t = streamedDoors.remove(b);
            if (t != null) {
                interactions.remove(t);
            }
            if (insideB == b) {
                insideB = null;
            }
        }
        trees.removeAll(c.trees);
        String prefix = "p:" + c.cx + ":" + c.cz + ":";
        for (Iterator<Pickup> it = pickups.iterator(); it.hasNext();) {
            Pickup pk = it.next();
            if (pk.id != null && pk.id.startsWith(prefix)) {
                world.getChildren().remove(pk.node);
                it.remove();
            }
        }
    }

    private void removeObs(Obstacle o) {
        int x0 = (int) Math.floor((o.x - o.halfWidth) / CELL), x1 = (int) Math.floor((o.x + o.halfWidth) / CELL);
        int z0 = (int) Math.floor((o.z - o.halfDepth) / CELL), z1 = (int) Math.floor((o.z + o.halfDepth) / CELL);
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                List<Obstacle> l = grid.get(cellKey(cx, cz));
                if (l != null) {
                    l.remove(o);
                }
            }
        }
    }

    // ---- world map
    private void openMap() {
        setState(GameState.MAP);
        firing = false;
        worldMap.reveal(player.getTranslateX(), player.getTranslateZ(), 260);
        worldMap.setPlayer(player.getTranslateX(), player.getTranslateZ(), smoothedYaw);
        worldMap.setVisible(true);
        worldMap.toFront();
        worldMap.redraw();
    }

    private void closeMap() {
        worldMap.setVisible(false);
        setState(GameState.PLAYING);
        rootPane.requestFocus();
    }

    // ---- settings
    private void applySettings() {
        gameScene.setEffect(POST_FX && settings.quality.bloom ? bloom : null);
        if (stage != null && stage.isFullScreen() != settings.fullscreen) {
            stage.setFullScreen(settings.fullscreen);
        }
        updateCulling();
    }

    private void openSettings(GameState from) {
        settingsReturn = from;
        setState(GameState.SETTINGS);
        if (from == GameState.PAUSED) {
            pauseOverlay.setVisible(false);
        }
        settingsPanel.refresh();
        settingsPanel.setVisible(true);
        settingsPanel.toFront();
    }

    private void closeSettings() {
        settings.save();
        settingsPanel.cancelCapture();
        settingsPanel.setVisible(false);
        setState(settingsReturn);
        if (settingsReturn == GameState.PAUSED) {
            pauseOverlay.setVisible(true);
            pauseOverlay.toFront();
        }
    }

    // ===================== OVERLAYS / RESTART / LOOP =====================
    private StackPane createPauseOverlay() {
        Text title = new Text("GAME PAUSED");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 42));
        title.setFill(Color.WHITE);
        Button resumeBtn = new Button("RESUME");
        resumeBtn.setOnAction(e -> togglePause());
        Button saveBtn = new Button("SAVE GAME");
        saveBtn.setOnAction(e -> saveGame(true));
        Button settingsBtn = new Button("SETTINGS");
        settingsBtn.setOnAction(e -> openSettings(GameState.PAUSED));
        Button restartBtn = new Button("RESTART RUN");
        restartBtn.setOnAction(e -> restartGame());
        Button quitBtn = new Button("QUIT TO MENU");
        quitBtn.setOnAction(e -> quitToMenu());
        pauseMsg = new Text("");
        pauseMsg.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        pauseMsg.setFill(Color.LIGHTGREEN);
        for (Button b : List.of(resumeBtn, saveBtn, settingsBtn, restartBtn, quitBtn)) {
            b.setFocusTraversable(false);
            b.setFont(Font.font("Arial", FontWeight.BOLD, 15));
            b.setPrefWidth(220);
            b.setPrefHeight(44);
            b.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: white; -fx-background-radius: 6; -fx-cursor: hand;");
        }
        VBox panel = new VBox(16, title, resumeBtn, saveBtn, settingsBtn, restartBtn, quitBtn, pauseMsg);
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
        Button menuBtn = new Button("MAIN MENU");
        menuBtn.setOnAction(e -> quitToMenu());
        menuBtn.setFont(Font.font("Arial", FontWeight.BOLD, 15));
        menuBtn.setPrefWidth(220);
        menuBtn.setPrefHeight(44);
        menuBtn.setFocusTraversable(false);
        menuBtn.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: white; -fx-background-radius: 6; -fx-cursor: hand;");
        restartBtn.setFocusTraversable(false);
        VBox panel = new VBox(20, title, finalStatsText, restartBtn, menuBtn);
        panel.setAlignment(Pos.CENTER);
        StackPane overlay = new StackPane(panel);
        overlay.setStyle("-fx-background-color: rgba(8, 11, 15, 0.94);");
        overlay.setVisible(false);
        return overlay;
    }

    /** Puts every run variable back to the start of a fresh run (does not spawn enemies or touch the UI state). */
    private void resetRunState() {
        keys.clear();
        health = 100;
        stamina = 100;
        initializeWeaponAmmo();
        kills = 0;
        threat = 0;
        survivalTime = 0;
        lastThreatInt = 0;
        gameMinutes = 7 * 60 + 30;
        bloodMoon = false;
        bloodBlend = 0;
        sleeping = false;
        sleepBlend = 0;
        sleepRot.setAngle(0);
        sleepOverlay.setOpacity(0);
        sleepText.setVisible(false);
        lastSkyMin = -999;
        spawnClock = 3;
        choke = 0;
        medkits = 1;
        grenades = 2;
        heldItem = 0;
        cancelActions();
        isAiming = false;
        crouched = false;
        climbing = false;
        velY = 0;
        grounded = true;
        feetY = GROUND_Y;
        torchOn = false;
        torchLight.setLightOn(false);
        lastShotTime = 0;
        cameraRecoil = 0;
        weaponKick = 0;
        shake = 0;
        damageFlash = 0;
        playerYaw = 0;
        cameraYaw = 0;
        cameraPitch = -10.5;
        player.setTranslateX(0);
        player.setTranslateZ(0);
        player.setTranslateY(feetY - 55);
        for (Particle p : particles) {
            world.getChildren().remove(p.node);
        }
        particles.clear();
        for (Tracer t : tracers) {
            world.getChildren().remove(t.node);
        }
        tracers.clear();
        for (Grenade g : grenadeList) {
            world.getChildren().remove(g.node);
        }
        grenadeList.clear();
        for (Explosion x : explosions) {
            world.getChildren().remove(x.node);
        }
        explosions.clear();
        smokeClouds.clear();
        for (Enemy e : enemies) {
            world.getChildren().remove(e.model);
        }
        enemies.clear();
        for (Building b : buildings) {
            b.doorOpen = false;
        }
        rebuildWeaponModel();
        hitMarker.setVisible(false);
        lastClockMin = -1;
    }

    private void restartGame() {
        resetRunState();
        worldState.clear();
        chunks.unloadAll();
        worldMap.resetExploration();
        setState(GameState.PLAYING);
        gameOverOverlay.setVisible(false);
        pauseOverlay.setVisible(false);
        status("STATUS: SURVIVING", Color.LIGHTGREEN);
        spawnInitialZombies();
        updateCamera(1.0);
        updateHUD();
        showBanner("SURVIVE THE NIGHT", Color.GOLD);
        autosaveClock = 0;
        rootPane.requestFocus();
    }

    private void startGameLoop() {
        loop = new AnimationTimer() {
            private long lastTime = 0;

            @Override
            public void handle(long now) {
                if (lastTime == 0) {
                    lastTime = now;
                    return;
                }
                double dt = Math.min((now - lastTime) / 1_000_000_000.0, 0.05);
                lastTime = now;
                if (state == GameState.MENU) {
                    updateMenu(dt);
                    return;
                }
                if (paused) {
                    return;
                }
                worldTime += dt;

                // Update day/night cycle
                dayNightSystem.update(dt, sleeping,
                        Main.this::showBanner,
                        Main.this::status,
                        bm -> {
                            bloodMoon = bm;
                            for (Enemy e : enemies) e.setBlood(bm);
                        });

                // Update weather
                weatherSystem.update(dt, player.getTranslateX(), player.getTranslateZ(), camera.getTranslateY());

                // Update survival system
                survivalSystem.update(dt, sprintingNow, sleeping, grounded);
                health = survivalSystem.getHealth();
                stamina = survivalSystem.getStamina();

                // Auto-shot for assault rifle
                if (firing && heldItem == 0 && currentWeapon == WeaponType.ASSAULT_RIFLE) {
                    combatSystem.shoot(playerYaw, cameraYaw, cameraPitch,
                            player.getTranslateX(), player.getTranslateY(), player.getTranslateZ(),
                            feetY, crouchBlend, crouched, isAiming, firing, heldItem,
                            paused, gameOver, sleeping, climbing, usingMed, enemies,
                            () -> effThreat());
                }

                if (health > 0 && !gameOver) {
                    if (sleeping) {
                        updateSleep(dt);
                        animationController.update(dt, false, false, 1);
                    } else {
                        updatePlayer(dt);
                    }
                    zombieAISystem.updateEnemies(dt);
                    zombieAISystem.updateSpawning(dt);
                    combatSystem.updateActions(dt);
                } else {
                    animationController.update(dt, false, false, 1);
                }
                updateWorld(dt);
                updateEffects(dt);
                // Camera controller update
                cameraController.setPlayerPosition(player.getTranslateX(), player.getTranslateZ(), feetY);
                cameraController.setPlayerYaw(playerYaw);
                cameraController.setCameraRotation(cameraYaw, cameraPitch);
                cameraController.setCrouchBlend(crouchBlend);
                cameraController.setInsideBlend(insideBlend);
                cameraController.setInsideH(insideH);
                cameraController.setWalkPhase(walkPhase);
                cameraController.setMoveBlend(moveBlend);
                cameraController.setAimBlend(aimBlend);
                cameraController.setSprintBlend(sprintBlend);
                cameraController.setLandBlend(landBlend);
                cameraController.setClimbBlend(climbBlend);
                cameraController.setIsAiming(isAiming);
                cameraController.setSprintingNow(sprintingNow);
                cameraController.setCrouched(crouched);
                cameraController.setClimbing(climbing);
                cameraController.setCameraRecoil(cameraRecoil);
                cameraController.setWeaponKick(weaponKick);
                cameraController.setStrafeInput(strafeInput);
                cameraController.update(dt);

                // Sync camera state back
                smoothedYaw = cameraController.getSmoothedYaw();
                smoothedPitch = cameraController.getSmoothedPitch();
                camRoll = cameraController.getCamRoll();
                shake = cameraController.getShake();
                currentFov = cameraController.getCurrentFov();
                if ((++mapTick & 1) == 0) {
                    drawMinimap();
                }
                chunks.update(player.getTranslateX(), player.getTranslateZ());
                if (state == GameState.PLAYING && health > 0) {
                    tickMapAndAutosave(dt);
                }
            }
        };
        loop.start();
    }

    // ===================== NESTED CLASSES =====================
    public static void main(String[] args) {
        launch(args);
    }
}
