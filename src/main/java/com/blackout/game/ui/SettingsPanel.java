package com.blackout.game.ui;

import java.util.EnumMap;
import java.util.Map;

import com.blackout.game.core.Action;
import com.blackout.game.core.Settings;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * Graphics quality, audio volume, mouse sensitivity, FOV, fullscreen and key bindings. Changes apply live through
 * {@code onApply}; Back writes them to disk.
 */
public final class SettingsPanel extends StackPane {

    private final Settings settings;
    private final Runnable onApply;
    private final Map<Action, Button[]> bindButtons = new EnumMap<>(Action.class);
    private final ToggleGroup quality = new ToggleGroup();
    private final Slider volume = new Slider(0, 1, 0.8), sens = new Slider(0.3, 2.5, 1), fov = new Slider(0.8, 1.3, 1);
    private final Text volumeLabel = label(""), sensLabel = label(""), fovLabel = label("");
    private final CheckBox fullscreen = new CheckBox("Fullscreen");
    private final Text bindHint = UiTheme.text("", 13, UiTheme.GOLD, FontWeight.NORMAL);
    private boolean loading;
    private Action capAction;
    private int capSlot;

    public SettingsPanel(Settings settings, Runnable onApply, Runnable onBack) {
        this.settings = settings;
        this.onApply = onApply;

        VBox content = new VBox(18);
        content.setPadding(new Insets(10, 30, 20, 30));
        content.getChildren().addAll(section("GRAPHICS"), qualityRow(), section("AUDIO / CONTROLS / DISPLAY"),
                sliderRow("Master volume (used once sound is added)", volume, volumeLabel),
                sliderRow("Mouse sensitivity", sens, sensLabel), sliderRow("Field of view", fov, fovLabel), fullscreenRow(),
                section("KEY BINDINGS"), bindHint, bindings());

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setFocusTraversable(false);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scroll.setPrefViewportHeight(470);

        Button reset = UiTheme.button("RESET TO DEFAULTS", 230);
        reset.setOnAction(e -> {
            cancelCapture();
            settings.resetDefaults();
            refresh();
            onApply.run();
        });
        Button back = UiTheme.button("SAVE & BACK", 230);
        back.setOnAction(e -> {
            cancelCapture();
            settings.save();
            onBack.run();
        });
        HBox buttons = new HBox(16, reset, back);
        buttons.setAlignment(Pos.CENTER);

        VBox panel = new VBox(14, UiTheme.text("SETTINGS", 36, Color.WHITE, FontWeight.BOLD), scroll, buttons);
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setPadding(new Insets(26, 30, 26, 30));
        panel.setMaxSize(760, 680);
        panel.setStyle("-fx-background-color: rgba(10,13,18,0.97); -fx-border-color: rgba(217,155,50,0.7); -fx-border-radius: 6;"
                + " -fx-background-radius: 6;");
        getChildren().add(panel);
        setStyle("-fx-background-color: rgba(3,4,6,0.78);");
        setVisible(false);
        refresh();
    }

    private static Text label(String s) {
        return UiTheme.text(s, 14, Color.web("#d5d9dd"), FontWeight.NORMAL);
    }

    private static Text section(String s) {
        return UiTheme.text(s, 15, UiTheme.GOLD, FontWeight.BOLD);
    }

    private HBox qualityRow() {
        HBox row = new HBox(10);
        for (Settings.Quality q : Settings.Quality.values()) {
            ToggleButton b = new ToggleButton(q.label);
            b.setUserData(q);
            b.setToggleGroup(quality);
            b.setFocusTraversable(false);
            b.setPrefWidth(120);
            b.setPrefHeight(36);
            b.setStyle("-fx-background-color: rgba(26,33,42,0.92); -fx-text-fill: #e6e6e6; -fx-cursor: hand;");
            b.selectedProperty().addListener((o, was, is) -> b.setStyle(is
                    ? "-fx-background-color: #d99b32; -fx-text-fill: #1b1405; -fx-font-weight: bold; -fx-cursor: hand;"
                    : "-fx-background-color: rgba(26,33,42,0.92); -fx-text-fill: #e6e6e6; -fx-cursor: hand;"));
            row.getChildren().add(b);
        }
        quality.selectedToggleProperty().addListener((o, was, now) -> {
            if (now == null) {
                if (was != null) {
                    was.setSelected(true);     // one option always stays selected
                }
                return;
            }
            if (!loading) {
                settings.quality = (Settings.Quality) now.getUserData();
                onApply.run();
            }
        });
        Text note = UiTheme.text("Low turns off bloom and shortens draw distance. Anti-aliasing changes on the next new game.", 12,
                Color.web("#7f878e"), FontWeight.NORMAL);
        note.setWrappingWidth(520);
        VBox box = new VBox(8, row, note);
        return new HBox(box);
    }

    private VBox sliderRow(String title, Slider s, Text value) {
        s.setFocusTraversable(false);
        s.setPrefWidth(380);
        s.valueProperty().addListener((o, was, now) -> {
            if (loading) {
                return;
            }
            if (s == volume) {
                settings.volume = now.doubleValue();
            } else if (s == sens) {
                settings.sensitivity = now.doubleValue();
            } else {
                settings.fovScale = now.doubleValue();
            }
            updateLabels();
            onApply.run();
        });
        HBox line = new HBox(14, s, value);
        line.setAlignment(Pos.CENTER_LEFT);
        return new VBox(4, label(title), line);
    }

    private HBox fullscreenRow() {
        fullscreen.setTextFill(Color.web("#d5d9dd"));
        fullscreen.setFocusTraversable(false);
        fullscreen.selectedProperty().addListener((o, was, now) -> {
            if (!loading) {
                settings.fullscreen = now;
                onApply.run();
            }
        });
        return new HBox(fullscreen);
    }

    private GridPane bindings() {
        GridPane g = new GridPane();
        g.setHgap(14);
        g.setVgap(8);
        int row = 0;
        g.add(UiTheme.text("ACTION", 12, Color.web("#7f878e"), FontWeight.BOLD), 0, row);
        g.add(UiTheme.text("PRIMARY", 12, Color.web("#7f878e"), FontWeight.BOLD), 1, row);
        g.add(UiTheme.text("SECONDARY", 12, Color.web("#7f878e"), FontWeight.BOLD), 2, row++);
        for (Action a : Action.values()) {
            Button[] bs = new Button[2];
            for (int s = 0; s < 2; s++) {
                Button b = UiTheme.button("", 130);
                b.setPrefHeight(32);
                final int slot = s;
                b.setOnAction(e -> beginCapture(a, slot));
                bs[s] = b;
            }
            bindButtons.put(a, bs);
            g.add(label(a.label), 0, row);
            g.add(bs[0], 1, row);
            g.add(bs[1], 2, row++);
        }
        return g;
    }

    // ------------------------------------------------------------ key capture

    private void beginCapture(Action a, int slot) {
        cancelCapture();
        capAction = a;
        capSlot = slot;
        bindButtons.get(a)[slot].setText("press a key...");
        bindHint.setText("Press the new key for \"" + a.label + "\"  -  Esc cancels, Backspace clears.");
    }

    public boolean isCapturing() {
        return capAction != null;
    }

    /** Called by the game's key filter while capturing. */
    public void offerKey(KeyCode k) {
        if (capAction == null) {
            return;
        }
        if (k == KeyCode.ESCAPE) {
            cancelCapture();
            return;
        }
        if (k == KeyCode.BACK_SPACE || k == KeyCode.DELETE) {
            settings.input.clear(capAction, capSlot);
        } else if (!settings.input.rebind(capAction, capSlot, k)) {
            bindHint.setText("That key is reserved (Esc and 1-6). Pick another key.");
            return;
        }
        capAction = null;
        bindHint.setText("");
        refreshBindings();
        onApply.run();
    }

    public void cancelCapture() {
        if (capAction != null) {
            capAction = null;
            bindHint.setText("");
            refreshBindings();
        }
    }

    // ------------------------------------------------------------ refresh

    private void refreshBindings() {
        for (Action a : Action.values()) {
            for (int s = 0; s < 2; s++) {
                bindButtons.get(a)[s].setText(settings.input.label(a, s));
            }
        }
    }

    private void updateLabels() {
        volumeLabel.setText((int) Math.round(settings.volume * 100) + "%");
        sensLabel.setText(String.format("x%.2f", settings.sensitivity));
        fovLabel.setText(String.format("x%.2f", settings.fovScale));
    }

    /** Re-reads every control from the Settings object. */
    public void refresh() {
        loading = true;
        for (javafx.scene.control.Toggle t : quality.getToggles()) {
            t.setSelected(t.getUserData() == settings.quality);
        }
        volume.setValue(settings.volume);
        sens.setValue(settings.sensitivity);
        fov.setValue(settings.fovScale);
        fullscreen.setSelected(settings.fullscreen);
        loading = false;
        updateLabels();
        refreshBindings();
        bindHint.setText("");
    }
}
