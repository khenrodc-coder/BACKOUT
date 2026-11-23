package com.blackout.game.environment;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

import javax.imageio.ImageIO;

/**
 * Developer tool (not a test): draws top-down floor plans of generated houses to a PNG so layouts can be checked by eye.
 * Run: java -cp ... com.blackout.game.environment.HouseLayoutPreview out.png [count]
 */
public final class HouseLayoutPreview {

    public static void main(String[] a) throws Exception {
        String out = a.length > 0 ? a[0] : "houses.png";
        int n = a.length > 1 ? Integer.parseInt(a[1]) : 6;
        int cols = 3, rows = (n + cols - 1) / cols;
        double scale = 1.5;
        int cw = (int) (460 * scale), ch = (int) (400 * scale);
        BufferedImage img = new BufferedImage(cols * cw, rows * ch, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(24, 26, 30));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        Random sizes = new Random(7);
        for (int i = 0; i < n; i++) {
            double w = 300 + sizes.nextInt(141), d = 310 + sizes.nextInt(51);
            boolean ruined = i % 4 == 3;
            HouseLayout L = HouseLayout.generate(w, d, new Random(1000 + i), ruined);
            AffineTransform keep = g.getTransform();
            g.translate((i % cols) * cw + cw / 2.0, (i / cols) * ch + ch / 2.0);
            g.scale(scale, -scale);                      // +z (front) is drawn at the bottom of the picture
            draw(g, L);
            g.setTransform(keep);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            g.drawString(String.format("#%d  %.0f x %.0f  (%.1f x %.1f m)%s  props=%d  reach=%b", i, w, d, w / 65, d / 65, ruined ? "  RUINED" : "", L.props.size(), L.allDoorwaysReachable()),
                    (i % cols) * cw + 8, (i / cols) * ch + 18);
        }
        ImageIO.write(img, "png", new File(out));
        System.out.println("wrote " + out);
    }

    static void rect(Graphics2D g, double x0, double z0, double x1, double z1, Color fill, Color line) {
        Rectangle2D r = new Rectangle2D.Double(x0, z0, x1 - x0, z1 - z0);
        if (fill != null) {
            g.setColor(fill);
            g.fill(r);
        }
        if (line != null) {
            g.setColor(line);
            g.setStroke(new BasicStroke(0.8f));
            g.draw(r);
        }
    }

    static void draw(Graphics2D g, HouseLayout L) {
        double hw = L.w / 2, hd = L.d / 2;
        rect(g, -hw, -hd, hw, hd, new Color(70, 72, 78), null);                       // exterior wall mass
        for (HouseLayout.Room r : L.rooms) {
            Color c = r.type == HouseLayout.RoomType.LIVING ? new Color(120, 96, 70) : r.type == HouseLayout.RoomType.BEDROOM ? new Color(96, 84, 110)
                    : new Color(120, 140, 150);
            rect(g, r.x0, r.z0, r.x1, r.z1, c, null);
        }
        for (HouseLayout.Wall w : L.walls) {
            double t = L.P / 2;
            if (w.alongX) {
                rect(g, w.from, w.fixed - t, w.to, w.fixed + t, new Color(60, 60, 64), null);
                for (double[] o : w.openings) {
                    rect(g, o[0] - o[1] / 2, w.fixed - t, o[0] + o[1] / 2, w.fixed + t, new Color(150, 120, 90), null);
                }
            } else {
                rect(g, w.fixed - t, w.from, w.fixed + t, w.to, new Color(60, 60, 64), null);
            }
        }
        for (HouseLayout.Opening o : L.openings) {
            double a = o.center - o.width / 2, b = o.center + o.width / 2, t = L.T;
            Color c = o.door ? new Color(230, 150, 40) : new Color(120, 200, 255);
            switch (o.side) {
                case FRONT: rect(g, a, L.innerD / 2, b, L.innerD / 2 + t, c, null); break;
                case BACK: rect(g, a, -L.innerD / 2 - t, b, -L.innerD / 2, c, null); break;
                case LEFT: rect(g, -L.innerW / 2 - t, a, -L.innerW / 2, b, c, null); break;
                default: rect(g, L.innerW / 2, a, L.innerW / 2 + t, b, c, null); break;
            }
        }
        for (HouseLayout.Prop p : L.props) {
            double[] a = p.aabb();
            Color c;
            switch (p.kind) {
                case BED: c = new Color(90, 120, 200); break;
                case SOFA: case ARMCHAIR: c = new Color(160, 70, 70); break;
                case RUG: c = new Color(140, 110, 90); break;
                case STAIN: c = new Color(110, 20, 20); break;
                case CHAIR: c = new Color(190, 160, 110); break;
                case TOILET: case SINK: case TUB: c = new Color(235, 235, 240); break;
                case FRIDGE: case COUNTER: case SINK_COUNTER: case STOVE_COUNTER: c = new Color(200, 200, 190); break;
                default: c = new Color(170, 130, 80);
            }
            if (p.fallen) {
                c = c.darker();
            }
            rect(g, a[0], a[1], a[2], a[3], c, Color.BLACK);
            // front direction tick
            double fx = Math.sin(Math.toRadians(p.yaw)), fz = Math.cos(Math.toRadians(p.yaw));
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(1.2f));
            g.drawLine((int) p.x, (int) p.z, (int) (p.x + fx * 12), (int) (p.z + fz * 12));
        }
        g.setColor(new Color(255, 80, 80));
        for (double[] s : L.spawnPoints) {
            g.fill(new Rectangle2D.Double(s[0] - 3, s[1] - 3, 6, 6));
        }
        g.setColor(new Color(80, 255, 120));
        for (double[] s : L.lootSpots) {
            g.fill(new Rectangle2D.Double(s[0] - 3, s[1] - 3, 6, 6));
        }
    }
}
