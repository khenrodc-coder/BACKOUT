package com.blackout.game.ui;

import javafx.scene.control.Button;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/** Shared look for menus: dark panels, gold accents. */
public final class UiTheme {

    public static final Color GOLD = Color.web("#d99b32");
    public static final Color BLOOD = Color.web("#b3261e");

    private UiTheme() {
    }

    public static Text text(String s, double size, Color c, FontWeight w) {
        Text t = new Text(s);
        t.setFont(Font.font("Arial", w, size));
        t.setFill(c);
        return t;
    }

    public static Button button(String label, double width) {
        Button b = new Button(label);
        b.setFont(Font.font("Arial", FontWeight.BOLD, 15));
        b.setPrefSize(width, 46);
        b.setFocusTraversable(false);   // Space / Enter must never trigger a menu button by accident
        style(b, false);
        b.setOnMouseEntered(e -> style(b, true));
        b.setOnMouseExited(e -> style(b, false));
        b.disabledProperty().addListener((o, was, is) -> style(b, false));
        return b;
    }

    public static void style(Button b, boolean hover) {
        if (b.isDisabled()) {
            b.setStyle("-fx-background-color: rgba(20,24,30,0.65); -fx-text-fill: #5d636a; -fx-background-radius: 4;"
                    + " -fx-border-color: rgba(255,255,255,0.05); -fx-border-radius: 4;");
        } else if (hover) {
            b.setStyle("-fx-background-color: rgba(60,46,22,0.95); -fx-text-fill: #ffd98a; -fx-background-radius: 4;"
                    + " -fx-border-color: #d99b32; -fx-border-radius: 4; -fx-cursor: hand;");
        } else {
            b.setStyle("-fx-background-color: rgba(26,33,42,0.92); -fx-text-fill: #e6e6e6; -fx-background-radius: 4;"
                    + " -fx-border-color: rgba(255,255,255,0.14); -fx-border-radius: 4; -fx-cursor: hand;");
        }
    }
}
