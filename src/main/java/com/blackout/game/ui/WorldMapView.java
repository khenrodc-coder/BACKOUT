package com.blackout.game.ui;

import java.util.ArrayList;
import java.util.List;

import com.blackout.game.environment.Building;
import com.blackout.game.world.DistrictPlan;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

/**
 * Full-screen world map (M). North is +Z (up), east is +X, the same convention as the mini-map. The map covers the original
 * countryside and the downtown district to the north (x in [-1500, 1500], z in [-1500, 3300]). Unexplored cells stay dark,
 * landmarks appear when discovered, and a waypoint can be placed with a left click (right click removes it).
 * The explored grid and landmark flags are plain data so they can be saved and restored.
 */
public final class WorldMapView extends StackPane {

    public static final double MIN_X = -1500, MAX_X = 1500, MIN_Z = -1500, MAX_Z = 3300;
    public static final double WIDTH = MAX_X - MIN_X, HEIGHT = MAX_Z - MIN_Z;
    public static final int CELLS_X = 30, CELLS_Z = 48;
    private static final double CELL = 100;
    /** World units per metre (the soldier is about 118 units tall). */
    private static final double UNITS_PER_METRE = 65;

    public static final class Poi {

        public final String name;
        public final double x, z;
        public boolean found;

        Poi(String name, double x, double z) {
            this.name = name;
            this.x = x;
            this.z = z;
        }
    }

    private final List<double[]> roads;
    private final List<Building> buildings;
    private final List<double[]> districtLots = new ArrayList<>();          // {x0, z0, x1, z1, colour index}
    private final double[] lake;       // x, z, rx, rz
    private final double riverZ, riverWidth;
    private final boolean[][] explored = new boolean[CELLS_X][CELLS_Z];
    private final List<Poi> pois = new ArrayList<>();
    private final Canvas canvas = new Canvas(440, 700);
    private final Text coords = UiTheme.text("", 13, Color.web("#c9ced3"), FontWeight.NORMAL);
    private boolean hasWaypoint;
    private double wpX, wpZ, px, pz, heading;
    private Runnable onWaypointChanged;

    public WorldMapView(List<double[]> roads, List<Building> buildings, double[] lake, double riverZ, double riverWidth, long worldSeed) {
        this.roads = roads;
        this.buildings = buildings;
        this.lake = lake;
        this.riverZ = riverZ;
        this.riverWidth = riverWidth;
        for (int cx = DistrictPlan.MIN_CX; cx <= DistrictPlan.MAX_CX; cx++) {          // the city is known in advance: it is only fogged
            for (int cz = DistrictPlan.MIN_CZ; cz <= DistrictPlan.MAX_CZ; cz++) {
                for (DistrictPlan.Lot l : DistrictPlan.generate(worldSeed, cx, cz).lots) {
                    double[] r = l.rect();
                    districtLots.add(new double[]{r[0], r[1], r[2], r[3], l.kind.ordinal()});
                }
            }
        }

        Text title = UiTheme.text("WORLD MAP", 30, Color.WHITE, FontWeight.BOLD);
        Text hint = UiTheme.text("Left click: set waypoint     Right click: remove     M or Esc: close", 13, UiTheme.GOLD, FontWeight.NORMAL);
        hint.setTextAlignment(TextAlignment.CENTER);
        VBox box = new VBox(8, title, canvas, coords, hint);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(12));
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        getChildren().add(box);
        setStyle("-fx-background-color: rgba(4,6,9,0.93);");
        setVisible(false);

        canvas.setOnMouseClicked(e -> {
            double s = canvas.getHeight() / HEIGHT;
            double x = MIN_X + e.getX() / s, z = MAX_Z - e.getY() / s;
            if (e.getButton() == MouseButton.PRIMARY && x >= MIN_X && x <= MAX_X && z >= MIN_Z && z <= MAX_Z) {
                hasWaypoint = true;
                wpX = x;
                wpZ = z;
            } else if (e.getButton() == MouseButton.SECONDARY) {
                hasWaypoint = false;
            } else {
                return;
            }
            redraw();
            if (onWaypointChanged != null) {
                onWaypointChanged.run();
            }
        });
        heightProperty().addListener((o, a, b) -> {
            if (isVisible()) {
                redraw();
            }
        });
        visibleProperty().addListener((o, a, now) -> {
            if (now) {
                redraw();
            }
        });
    }

    // ------------------------------------------------------------ data

    public void addPoi(String name, double x, double z) {
        pois.add(new Poi(name, x, z));
    }

    public void setOnWaypointChanged(Runnable r) {
        onWaypointChanged = r;
    }

    public void setPlayer(double x, double z, double headingDeg) {
        px = x;
        pz = z;
        heading = headingDeg;
    }

    private static int cellX(double v) {
        return Math.max(0, Math.min(CELLS_X - 1, (int) Math.floor((v - MIN_X) / CELL)));
    }

    private static int cellZ(double v) {
        return Math.max(0, Math.min(CELLS_Z - 1, (int) Math.floor((v - MIN_Z) / CELL)));
    }

    /** Marks cells around a position as explored. Returns the names of landmarks discovered by this call. */
    public List<String> reveal(double x, double z, double radius) {
        List<String> found = new ArrayList<>();
        int c0 = cellX(x - radius), c1 = cellX(x + radius), r0 = cellZ(z - radius), r1 = cellZ(z + radius);
        for (int cx = c0; cx <= c1; cx++) {
            for (int cz = r0; cz <= r1; cz++) {
                double mx = MIN_X + (cx + 0.5) * CELL, mz = MIN_Z + (cz + 0.5) * CELL;
                if (Math.hypot(mx - x, mz - z) <= radius + CELL * 0.5) {
                    explored[cx][cz] = true;
                }
            }
        }
        for (Poi p : pois) {
            if (!p.found && Math.hypot(p.x - x, p.z - z) < radius * 0.9) {
                p.found = true;
                found.add(p.name);
            }
        }
        return found;
    }

    public void resetExploration() {
        for (boolean[] row : explored) {
            java.util.Arrays.fill(row, false);
        }
        for (Poi p : pois) {
            p.found = false;
        }
        hasWaypoint = false;
    }

    public boolean hasWaypoint() {
        return hasWaypoint;
    }

    public double waypointX() {
        return wpX;
    }

    public double waypointZ() {
        return wpZ;
    }

    public void setWaypoint(boolean has, double x, double z) {
        hasWaypoint = has;
        wpX = x;
        wpZ = z;
    }

    public String exportExplored() {
        StringBuilder sb = new StringBuilder(CELLS_X * CELLS_Z);
        for (int x = 0; x < CELLS_X; x++) {
            for (int z = 0; z < CELLS_Z; z++) {
                sb.append(explored[x][z] ? '1' : '0');
            }
        }
        return sb.toString();
    }

    /** Saves from before the map grew have a shorter string and are ignored (the player simply re-explores). */
    public void importExplored(String s) {
        if (s == null || s.length() != CELLS_X * CELLS_Z) {
            return;
        }
        for (int x = 0; x < CELLS_X; x++) {
            for (int z = 0; z < CELLS_Z; z++) {
                explored[x][z] = s.charAt(x * CELLS_Z + z) == '1';
            }
        }
    }

    public String exportPoi() {
        StringBuilder sb = new StringBuilder();
        for (Poi p : pois) {
            sb.append(p.found ? '1' : '0');
        }
        return sb.toString();
    }

    public void importPoi(String s) {
        if (s == null || s.length() != pois.size()) {
            return;
        }
        for (int i = 0; i < pois.size(); i++) {
            pois.get(i).found = s.charAt(i) == '1';
        }
    }

    public int exploredPercent() {
        int n = 0;
        for (boolean[] row : explored) {
            for (boolean b : row) {
                if (b) {
                    n++;
                }
            }
        }
        return n * 100 / (CELLS_X * CELLS_Z);
    }

    // ------------------------------------------------------------ drawing

    private static final Color[] LOT_COLORS = {
        Color.rgb(96, 108, 126), Color.rgb(150, 118, 98), Color.rgb(190, 170, 130), Color.rgb(170, 190, 150), Color.rgb(196, 160, 90),
        Color.rgb(120, 120, 124), Color.rgb(64, 66, 72), Color.rgb(150, 150, 146), Color.rgb(58, 100, 56)};

    public void redraw() {
        double availH = getHeight() > 100 ? getHeight() - 150 : 640;
        double side = Math.max(360, Math.min(availH, Math.max(360, getWidth() - 80) * HEIGHT / WIDTH));
        if (Math.abs(canvas.getHeight() - side) > 1) {
            canvas.setHeight(side);
            canvas.setWidth(side * WIDTH / HEIGHT);
        }
        final double CW = canvas.getWidth(), CH = canvas.getHeight(), s = CH / HEIGHT;
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, CW, CH);
        g.setFill(Color.rgb(27, 40, 29));
        g.fillRect(0, 0, CW, CH);

        // terrain features
        g.setFill(Color.rgb(34, 84, 128));
        rect(g, s, 0, riverZ, WIDTH, riverWidth);
        g.fillOval((lake[0] - lake[2] - MIN_X) * s, (MAX_Z - (lake[1] + lake[3])) * s, lake[2] * 2 * s, lake[3] * 2 * s);
        for (double[] l : districtLots) {                                   // downtown: ground cover first, buildings on top
            int k = (int) l[4];
            if (k == DistrictPlan.Kind.PLAZA.ordinal() || k == DistrictPlan.Kind.PARK.ordinal() || k == DistrictPlan.Kind.PARKING.ordinal()) {
                g.setFill(LOT_COLORS[k]);
                rectCorners(g, s, l);
            }
        }
        g.setFill(Color.rgb(86, 90, 96));
        for (double[] r : roads) {
            rect(g, s, r[0], r[1], r[2], r[3]);
        }
        for (double[] l : districtLots) {
            int k = (int) l[4];
            if (k != DistrictPlan.Kind.PLAZA.ordinal() && k != DistrictPlan.Kind.PARK.ordinal() && k != DistrictPlan.Kind.PARKING.ordinal()) {
                g.setFill(LOT_COLORS[k]);
                rectCorners(g, s, l);
            }
        }
        for (Building b : buildings) {
            if (b.id == null) {
                g.setFill(b.burning ? Color.rgb(228, 112, 44) : Color.rgb(178, 168, 142));
                rect(g, s, b.x, b.z, b.w, b.d);
            }
        }

        // grid
        g.setStroke(Color.rgb(255, 255, 255, 0.07));
        g.setLineWidth(1);
        for (double gx = MIN_X + 500; gx < MAX_X; gx += 500) {
            g.strokeLine((gx - MIN_X) * s, 0, (gx - MIN_X) * s, CH);
        }
        for (double gz = MIN_Z + 500; gz < MAX_Z; gz += 500) {
            g.strokeLine(0, (MAX_Z - gz) * s, CW, (MAX_Z - gz) * s);
        }

        // fog of war
        g.setFill(Color.rgb(6, 8, 11, 0.9));
        for (int cx = 0; cx < CELLS_X; cx++) {
            for (int cz = 0; cz < CELLS_Z; cz++) {
                if (!explored[cx][cz]) {
                    double x0 = cx * CELL * s, y0 = CH - (cz + 1) * CELL * s;
                    g.fillRect(x0 - 0.5, y0 - 0.5, CELL * s + 1, CELL * s + 1);
                }
            }
        }

        // landmarks
        g.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        for (Poi p : pois) {
            if (!p.found) {
                continue;
            }
            double x = (p.x - MIN_X) * s, y = (MAX_Z - p.z) * s;
            g.setFill(Color.GOLD);
            g.fillPolygon(new double[]{x, x + 6, x, x - 6}, new double[]{y - 7, y, y + 7, y}, 4);
            g.setFill(Color.BLACK);
            g.fillText(p.name, x + 9, y + 5);
            g.setFill(Color.WHITE);
            g.fillText(p.name, x + 8, y + 4);
        }

        // waypoint
        if (hasWaypoint) {
            double wx = (wpX - MIN_X) * s, wy = (MAX_Z - wpZ) * s, ux = (px - MIN_X) * s, uy = (MAX_Z - pz) * s;
            g.setStroke(Color.rgb(80, 220, 255, 0.55));
            g.setLineWidth(1.5);
            g.setLineDashes(8, 6);
            g.strokeLine(ux, uy, wx, wy);
            g.setLineDashes(null);
            g.setFill(Color.rgb(80, 220, 255));
            g.fillPolygon(new double[]{wx, wx + 8, wx, wx - 8}, new double[]{wy - 11, wy, wy + 11, wy}, 4);
            g.setStroke(Color.WHITE);
            g.setLineWidth(1.5);
            g.strokePolygon(new double[]{wx, wx + 8, wx, wx - 8}, new double[]{wy - 11, wy, wy + 11, wy}, 4);
        }

        // player (arrow rotated by heading; heading 0 faces +Z = up)
        g.save();
        g.translate((px - MIN_X) * s, (MAX_Z - pz) * s);
        g.rotate(heading);
        g.setFill(Color.rgb(255, 255, 255, 0.18));
        g.fillOval(-14, -14, 28, 28);
        g.setFill(Color.WHITE);
        g.fillPolygon(new double[]{0, -8, 0, 8}, new double[]{-13, 9, 4, 9}, 4);
        g.setStroke(UiTheme.GOLD);
        g.setLineWidth(2);
        g.setLineCap(StrokeLineCap.ROUND);
        g.strokePolygon(new double[]{0, -8, 0, 8}, new double[]{-13, 9, 4, 9}, 4);
        g.restore();

        // frame + compass
        g.setStroke(UiTheme.GOLD);
        g.setLineWidth(2);
        g.strokeRect(1, 1, CW - 2, CH - 2);
        g.setFill(Color.WHITE);
        g.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        g.fillText("N", CW / 2 - 5, 18);

        String wp = hasWaypoint ? "     WAYPOINT: " + Math.round(Math.hypot(wpX - px, wpZ - pz) / UNITS_PER_METRE) + " m" : "";
        coords.setText("EXPLORED " + exploredPercent() + "%     POSITION " + Math.round(px / UNITS_PER_METRE) + ", " + Math.round(pz / UNITS_PER_METRE) + wp);
    }

    private static void rectCorners(GraphicsContext g, double s, double[] r) {
        double x0 = Math.max(MIN_X, r[0]), x1 = Math.min(MAX_X, r[2]), z0 = Math.max(MIN_Z, r[1]), z1 = Math.min(MAX_Z, r[3]);
        if (x1 > x0 && z1 > z0) {
            g.fillRect((x0 - MIN_X) * s, (MAX_Z - z1) * s, (x1 - x0) * s, (z1 - z0) * s);
        }
    }

    private static void rect(GraphicsContext g, double s, double cx, double cz, double w, double d) {
        rectCorners(g, s, new double[]{cx - w / 2, cz - d / 2, cx + w / 2, cz + d / 2});
    }
}
