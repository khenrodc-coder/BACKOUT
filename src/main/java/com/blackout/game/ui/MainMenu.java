package com.blackout.game.ui;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

/**
 * Landing screen drawn over the live 3D world (the camera flies over the ruined town behind it). Has drifting fog, a dark
 * gradient for legibility, Continue / Start New Game / Settings / Exit, a confirm dialog and a loading cover.
 */
public final class MainMenu extends StackPane {

    public interface Actions {
        void onContinue();

        void onNewGame();

        void onSettings();

        void onExit();
    }

    private final Button continueBtn;
    private final Text saveInfo;
    private final StackPane confirm = new StackPane(), loading = new StackPane();
    private final Text loadingText = UiTheme.text("", 26, Color.WHITE, FontWeight.BOLD);
    private final Animation fogA, fogB;
    private Runnable confirmYes;

    public MainMenu(Actions actions) {
        // drifting fog: two very wide soft gradients sliding at different speeds, clipped to the screen
        Pane fog = new Pane();
        fog.setMouseTransparent(true);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(fog.widthProperty());
        clip.heightProperty().bind(fog.heightProperty());
        fog.setClip(clip);
        Region f1 = fogLayer(0.55, 0.14), f2 = fogLayer(0.8, 0.10);
        f1.setLayoutY(120);
        f2.layoutYProperty().bind(fog.heightProperty().multiply(0.45));
        fog.getChildren().addAll(f1, f2);
        fogA = drift(f1, 70);
        fogB = drift(f2, 46);

        Region shade = new Region();
        shade.setMouseTransparent(true);
        shade.setStyle("-fx-background-color: linear-gradient(to right, rgba(4,6,9,0.92) 0%, rgba(4,6,9,0.62) 34%, rgba(4,6,9,0) 68%);");
        Region vignette = new Region();
        vignette.setMouseTransparent(true);
        vignette.setStyle("-fx-background-color: radial-gradient(center 55% 50%, radius 80%, rgba(0,0,0,0) 50%, rgba(0,0,0,0.65) 100%);");

        Text title = UiTheme.text("BLACKOUT", 92, Color.web("#d8d2c4"), FontWeight.BOLD);
        title.setEffect(new DropShadow(26, Color.rgb(150, 20, 20, 0.85)));
        Text sub = UiTheme.text("THE LAST OUTPOST", 24, UiTheme.GOLD, FontWeight.BOLD);
        Text date = UiTheme.text("JANUARY 1, 2099  -  NO SIGNAL", 13, Color.web("#8d949b"), FontWeight.NORMAL);

        continueBtn = UiTheme.button("CONTINUE", 300);
        continueBtn.setOnAction(e -> actions.onContinue());
        Button newBtn = UiTheme.button("START NEW GAME", 300);
        newBtn.setOnAction(e -> actions.onNewGame());
        Button setBtn = UiTheme.button("SETTINGS", 300);
        setBtn.setOnAction(e -> actions.onSettings());
        Button exitBtn = UiTheme.button("EXIT", 300);
        exitBtn.setOnAction(e -> actions.onExit());
        saveInfo = UiTheme.text("", 13, Color.web("#9aa3ab"), FontWeight.NORMAL);
        saveInfo.setWrappingWidth(300);

        VBox col = new VBox(12, title, sub, date, spacer(26), continueBtn, saveInfo, newBtn, setBtn, exitBtn);
        col.setAlignment(Pos.CENTER_LEFT);
        col.setPadding(new Insets(0, 0, 0, 90));
        col.setMaxWidth(Region.USE_PREF_SIZE);

        Text hint = UiTheme.text("WASD move  -  M map  -  F5 quick save  -  Esc pause", 12, Color.web("#6f777e"), FontWeight.NORMAL);
        StackPane.setAlignment(hint, Pos.BOTTOM_LEFT);
        StackPane.setMargin(hint, new Insets(0, 0, 22, 94));

        buildConfirm();
        buildLoading();
        getChildren().addAll(fog, shade, vignette, col, hint, confirm, loading);
        StackPane.setAlignment(col, Pos.CENTER_LEFT);

        visibleProperty().addListener((o, was, now) -> {
            if (now) {
                fogA.play();
                fogB.play();
            } else {
                fogA.pause();
                fogB.pause();
            }
        });
        fogA.play();
        fogB.play();
    }

    private static Region spacer(double h) {
        Region r = new Region();
        r.setMinHeight(h);
        r.setPrefHeight(h);
        return r;
    }

    private static Region fogLayer(double a1, double a2) {
        Region r = new Region();
        r.setPrefSize(3400, 420);
        r.setMouseTransparent(true);
        r.setStyle("-fx-background-color: linear-gradient(to right, rgba(150,170,180,0) 0%, rgba(150,170,180," + a2
                + ") 22%, rgba(150,170,180,0) 45%, rgba(160,175,185," + a2 * a1 + ") 70%, rgba(150,170,180,0) 100%);"
                + " -fx-background-radius: 300;");
        r.setEffect(new javafx.scene.effect.GaussianBlur(40));
        return r;
    }

    private static Animation drift(Region r, double seconds) {
        TranslateTransition t = new TranslateTransition(Duration.seconds(seconds), r);
        t.setFromX(-1800);
        t.setToX(0);
        t.setCycleCount(Animation.INDEFINITE);
        t.setAutoReverse(true);
        t.setInterpolator(Interpolator.EASE_BOTH);
        return t;
    }

    private void buildConfirm() {
        Text q = UiTheme.text("START A NEW GAME?", 30, Color.WHITE, FontWeight.BOLD);
        Text d = UiTheme.text("Your current saved game will be replaced.", 15, Color.web("#c9ced3"), FontWeight.NORMAL);
        d.setTextAlignment(TextAlignment.CENTER);
        Button yes = UiTheme.button("YES, START NEW", 220);
        yes.setOnAction(e -> {
            confirm.setVisible(false);
            if (confirmYes != null) {
                confirmYes.run();
            }
        });
        Button no = UiTheme.button("CANCEL", 160);
        no.setOnAction(e -> confirm.setVisible(false));
        VBox box = new VBox(16, q, d, new javafx.scene.layout.HBox(14, yes, no));
        ((javafx.scene.layout.HBox) box.getChildren().get(2)).setAlignment(Pos.CENTER);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(34, 48, 34, 48));
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        box.setStyle("-fx-background-color: rgba(12,15,20,0.96); -fx-border-color: #d99b32; -fx-border-radius: 6; -fx-background-radius: 6;");
        confirm.getChildren().add(box);
        confirm.setStyle("-fx-background-color: rgba(0,0,0,0.6);");
        confirm.setVisible(false);
    }

    private void buildLoading() {
        loading.getChildren().add(loadingText);
        loading.setStyle("-fx-background-color: rgba(3,4,6,0.96);");
        loading.setVisible(false);
    }

    /** Enables Continue only when a valid save exists and shows a one-line summary or the reason it is unavailable. */
    public void refresh(boolean hasSave, String info) {
        continueBtn.setDisable(!hasSave);
        saveInfo.setText(info == null ? "" : info);
        confirm.setVisible(false);
        loading.setVisible(false);
    }

    public void showConfirmNew(Runnable onYes) {
        confirmYes = onYes;
        confirm.setVisible(true);
        confirm.toFront();
    }

    public void showLoading(String message) {
        loadingText.setText(message);
        loading.setVisible(true);
        loading.toFront();
    }

    public boolean isConfirmOpen() {
        return confirm.isVisible();
    }

    public void closeConfirm() {
        confirm.setVisible(false);
    }
}
