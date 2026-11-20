package com.minescore.client.screen;

import com.minescore.Tr;
import com.minescore.client.UiTheme;
import com.minescore.MineScoreSettings;
import com.minescore.client.score.EditorKeys;
import com.minescore.client.score.EditorKeys.Action;
import com.minescore.client.score.EditorKeys.Combo;
import com.minescore.client.score.EditorPrefs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The score editor's Preferences (SCOPE.md §8.1): which note the arrow keys select, and every shortcut,
 * each of which can be changed. Click a shortcut, then press the new keys (with Ctrl, Shift or Alt if
 * wanted); Delete clears it, Esc cancels.
 */
public class EditorKeysScreen extends Screen {
    private static final int ROW = 18, TOP = 76, LIST_W = 340;

    private final Screen parent;
    private int scroll;
    private @Nullable Action waiting;

    public EditorKeysScreen(Screen parent) {
        super(Component.translatable("gui.minescore.editor.preferences"));
        this.parent = parent;
    }

    private int rows() {
        return Math.max(1, (height - TOP - 34) / ROW);
    }

    @Override
    protected void init() {
        int left = (width - LIST_W) / 2;
        EditorPrefs.ArrowSelect pick = EditorPrefs.arrowSelect();
        addRenderableWidget(Button.builder(Tr.c(pick.label), b -> {
            EditorPrefs.ArrowSelect[] all = EditorPrefs.ArrowSelect.values();
            EditorPrefs.setArrowSelect(all[(pick.ordinal() + 1) % all.length]);
            rebuildWidgets();
        }).bounds(left + LIST_W - 130, 32, 130, ROW - 2).build());
        boolean restrike = MineScoreSettings.restrikeLongNotes.getAsBoolean();
        addRenderableWidget(Button.builder(Component.literal(restrike ? "Re-struck every beat" : "Held for their length"), b -> {
            MineScoreSettings.setRestrikeLongNotes.accept(!restrike);
            rebuildWidgets();
        }).bounds(left + LIST_W - 130, 52, 130, ROW - 2).build());
        Action[] all = Action.values();
        for (int i = 0; i < rows() && scroll + i < all.length; i++) {
            Action action = all[scroll + i];
            Combo combo = EditorKeys.get(action);
            Component label = action == waiting ? Component.literal("> press keys <") : Component.literal(combo.describe());
            addRenderableWidget(Button.builder(label, b -> {
                waiting = action;
                rebuildWidgets();
            }).bounds(left + LIST_W - 130, TOP + i * ROW, 130, ROW - 2).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.minescore.editor.keys_reset"), b -> {
            EditorKeys.resetAll();
            waiting = null;
            rebuildWidgets();
        }).bounds(width / 2 - 154, height - 28, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 + 4, height - 28, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 10, UiTheme.c("text"));
        g.drawCenteredString(font, Component.translatable("gui.minescore.editor.keys_hint"), width / 2, 21, UiTheme.c("dim"));
        int left = (width - LIST_W) / 2;
        g.drawString(font, Component.translatable("gui.minescore.editor.arrow_select"), left, 36, UiTheme.c("text"), false);
        g.drawString(font, Component.translatable("gui.minescore.editor.long_notes"), left, 56, UiTheme.c("text"), false);
        g.fill(left, TOP - 4, left + LIST_W, TOP - 3, 0x40FFFFFF);
        Action[] all = Action.values();
        for (int i = 0; i < rows() && scroll + i < all.length; i++) {
            Action action = all[scroll + i];
            g.drawString(font, Tr.s(action.label), left, TOP + i * ROW + 4, action == waiting ? UiTheme.c("waiting") : UiTheme.c("text"), false);
        }
        if (all.length > rows()) {
            ValueSlider.scrollbar(g, left + LIST_W + 6, TOP, rows() * ROW, scroll, rows(), all.length);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (waiting != null) {
            switch (key) {
                case GLFW.GLFW_KEY_ESCAPE -> waiting = null;
                case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL,
                     GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> {
                    return true; // waiting for the key that goes with them
                }
                case GLFW.GLFW_KEY_DELETE -> {
                    EditorKeys.set(waiting, Combo.of(GLFW.GLFW_KEY_UNKNOWN));
                    waiting = null;
                }
                default -> {
                    EditorKeys.set(waiting, new Combo(key, hasControlDown(), hasShiftDown(), hasAltDown()));
                    waiting = null;
                }
            }
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, Action.values().length - rows()), scroll - (int) Math.signum(delta) * 3));
        rebuildWidgets();
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
