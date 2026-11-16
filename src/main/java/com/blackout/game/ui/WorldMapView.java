package com.blackout.game.ui;

import java.util.ArrayList;
import java.util.List;

import com.blackout.game.environment.Building;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

/**
 * Full-screen world map (M). North is +Z (up), east is +X, the same convention as the mini-map. Unexplored cells stay dark,
 * landmarks appear when discovered, and a waypoint can be placed with a left click (right click removes it).
 * The explored grid and landmark flags are plain data so they can be saved and restored.
 */
public final class WorldMapView extends StackPane {

    /** The map covers x, z in [-HALF, HALF]. */
    public static final double HALF = 1500;
    public static final int CELLS = 30;
    private static final double CELL = 2 * HALF / CELLS;
    /** World units per metre (the soldier is about 110 units tall). */
    private static final double UNITS_PER_METRE = 60;

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
    private final double[] lake;       // x, z, rx, rz
    private final double riverZ, riverWidth;
    private final boolean[][] explored = new boolean[CELLS][CELLS];
    private final List<Poi> pois = new ArrayList<>();
    private final Canvas canvas = new Canvas(700, 700);
    private final Text coords = UiTheme.text("", 13, Color.web("#c9ced3"), FontWeight.NORMAL);
    private boolean hasWaypoint;
    private double wpX, wpZ, px, pz, heading;
    private Runnable onWaypointChanged;

    public WorldMapView(List<double[]> roads, List<Building> buildings, double[] lake, double riverZ, double riverWidth) {
        this.roads = roads;
        this.buildings = buildings;
        this.lake = lake;
        this.riverZ = riverZ;
        this.riverWidth = riverWidth;

        Text title = UiTheme.text("WORLD MAP", 30, Color.WHITE, FontWeight.BOLD);
        Text hint = UiTheme.text("Left click: set waypoint     Right click: remove     M or Esc: close", 13, UiTheme.GOLD,
                FontWeight.NORMAL);
        hint.setTextAlignment(TextAlignment.CENTER);
        VBox box = new VBox(8, title, canvas, coords, hint);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(12));
        box.setMaxSize(javafx.scene.layout.Region.USE_PREF_SIZE, javafx.scene.layout.Region.USE_PREF_SIZE);
        getChildren().add(box);
        setStyle("-fx-background-color: rgba(4,6,9,0.93);");
        setVisible(false);

        canvas.setOnMouseClicked(e -> {
            double s = canvas.getWidth() / (2 * HALF);
            double x = e.getX() / s - HALF, z = HALF - e.getY() / s;
            if (e.getButton() == MouseButton.PRIMARY && Math.abs(x) <= HALF && Math.abs(z) <= HALF) {
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

    /** Marks cells around a position as explored. Returns the names of landmarks discovered by this call. */
    public List<String> reveal(double x, double z, double radius) {
        List<String> found = new ArrayList<>();
        int c0 = cell(x - radius), c1 = cell(x + radius), r0 = cell(z - radius), r1 = cell(z + radius);
        for (int cx = c0; cx <= c1; cx++) {
            for (int cz = r0; cz <= r1; cz++) {
                double mx = -HALF + (cx + 0.5) * CELL, mz = -HALF + (cz + 0.5) * CELL;
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

    private static int cell(double v) {
        return Math.max(0, Math.min(CELLS - 1, (int) Math.floor((v + HALF) / CELL)));
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
        StringBuilder sb = new StringBuilder(CELLS * CELLS);
        for (int x = 0; x < CELLS; x++) {
            for (int z = 0; z < CELLS; z++) {
                sb.append(explored[x][z] ? '1' : '0');
            }
        }
        return sb.toString();
    }

    public void importExplored(String s) {
        if (s == null || s.length() != CELLS * CELLS) {
            return;
        }
        for (int x = 0; x < CELLS; x++) {
            for (int z = 0; z < CELLS; z++) {
                explored[x][z] = s.charAt(x * CELLS + z) == '1';
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
        return n * 100 / (CELLS * CELLS);
    }

    // ------------------------------------------------------------ drawing

    public void redraw() {
        double avail = getHeight() > 100 ? getHeight() - 150 : 640;
        double side = Math.max(320, Math.min(avail, Math.max(320, getWidth() - 80)));
        if (Math.abs(canvas.getWidth() - side) > 1) {
            canvas.setWidth(side);
            canvas.setHeight(side);
        }
        final double S = side, s = S / (2 * HALF);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, S, S);
        g.setFill(Color.rgb(27, 40, 29));
        g.fillRect(0, 0, S, S);

        // terrain features
        g.setFill(Color.rgb(34, 84, 128));
        rect(g, s, 0, riverZ, 2 * HALF, riverWidth);
        g.fillOval((lake[0] - lake[2] + HALF) * s, (HALF - (lake[1] + lake[3])) * s, lake[2] * 2 * s, lake[3] * 2 * s);
        g.setFill(Color.rgb(86, 90, 96));
        for (double[] r : roads) {
            rect(g, s, r[0], r[1], r[2], r[3]);
        }
        for (Building b : buildings) {
            g.setFill(b.burning ? Color.rgb(228, 112, 44) : Color.rgb(178, 168, 142));
            rect(g, s, b.x, b.z, b.w, b.d);
        }

        // grid
        g.setStroke(Color.rgb(255, 255, 255, 0.07));
        g.setLineWidth(1);
        for (int i = 1; i < 6; i++) {
            double p = S * i / 6;
            g.strokeLine(p, 0, p, S);
            g.strokeLine(0, p, S, p);
        }

        // fog of war
        g.setFill(Color.rgb(6, 8, 11, 0.9));
        for (int cx = 0; cx < CELLS; cx++) {
            for (int cz = 0; cz < CELLS; cz++) {
                if (!explored[cx][cz]) {
                    double x0 = cx * CELL * s, y0 = (2 * HALF - (cz + 1) * CELL) * s;
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
            double x = (p.x + HALF) * s, y = (HALF - p.z) * s;
            g.setFill(Color.GOLD);
            g.fillPolygon(new double[]{x, x + 6, x, x - 6}, new double[]{y - 7, y, y + 7, y}, 4);
            g.setFill(Color.BLACK);
            g.fillText(p.name, x + 9, y + 5);
            g.setFill(Color.WHITE);
            g.fillText(p.name, x + 8, y + 4);
        }

        // waypoint
        if (hasWaypoint) {
            double wx = (wpX + HALF) * s, wy = (HALF - wpZ) * s, ux = (px + HALF) * s, uy = (HALF - pz) * s;
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
        g.translate((px + HALF) * s, (HALF - pz) * s);
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
        g.strokeRect(1, 1, S - 2, S - 2);
        g.setFill(Color.WHITE);
        g.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        g.fillText("N", S / 2 - 5, 18);

        String wp = hasWaypoint ? "     WAYPOINT: " + Math.round(Math.hypot(wpX - px, wpZ - pz) / UNITS_PER_METRE) + " m" : "";
        coords.setText("EXPLORED " + exploredPercent() + "%     POSITION " + Math.round(px / UNITS_PER_METRE) + ", "
                + Math.round(pz / UNITS_PER_METRE) + wp);
    }

    private static void rect(GraphicsContext g, double s, double cx, double cz, double w, double d) {
        double x0 = Math.max(-HALF, cx - w / 2), x1 = Math.min(HALF, cx + w / 2);
        double z0 = Math.max(-HALF, cz - d / 2), z1 = Math.min(HALF, cz + d / 2);
        if (x1 > x0 && z1 > z0) {
            g.fillRect((x0 + HALF) * s, (HALF - z1) * s, (x1 - x0) * s, (z1 - z0) * s);
        }
    }
}
