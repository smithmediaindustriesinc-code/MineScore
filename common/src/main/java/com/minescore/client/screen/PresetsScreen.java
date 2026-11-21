package com.minescore.client.screen;

import com.minescore.client.UiTheme;
import com.minescore.config.MineScoreConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The config menu's presets (SCOPE.md §10): MineScore's own, and any saved here from the settings as they
 * are (kept in {@code config/minescore-presets/}). Applying one changes only the settings it holds; server
 * settings only for operators.
 */
public class PresetsScreen extends Screen {
    private static final int ROW = 22, W = 320;

    private final ConfigScreen parent;
    private EditBox name;
    private int scroll;
    private @Nullable Component notice;

    public PresetsScreen(ConfigScreen parent) {
        super(Component.translatable("gui.minescore.config.presets_title"));
        this.parent = parent;
    }

    private List<MineScoreConfig.Preset> presets() {
        List<MineScoreConfig.Preset> all = new ArrayList<>(MineScoreConfig.builtInPresets());
        all.addAll(MineScoreConfig.savedPresets());
        return all;
    }

    private int rows() {
        return Math.max(2, (height - 110) / ROW);
    }

    @Override
    protected void init() {
        int left = (width - W) / 2;
        List<MineScoreConfig.Preset> all = presets();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, all.size() - rows())));
        for (int i = 0; i < rows() && scroll + i < all.size(); i++) {
            MineScoreConfig.Preset preset = all.get(scroll + i);
            int y = 34 + i * ROW;
            addRenderableWidget(Button.builder(Component.literal(preset.name()), b -> {
                parent.applyPreset(preset);
                minecraft.setScreen(parent);
            }).bounds(left, y, preset.builtIn() ? W : W - 24, 20).tooltip(Tooltip.create(Component.literal(preset.description()
                    + "\n" + preset.values().size() + " settings"))).build());
            if (!preset.builtIn()) {
                addRenderableWidget(Button.builder(Component.literal("✕"), b -> {
                    try {
                        MineScoreConfig.deletePreset(preset.name());
                    } catch (Exception e) {
                        notice = Component.literal(e.getMessage()).withStyle(ChatFormatting.RED);
                    }
                    rebuildWidgets();
                }).bounds(left + W - 20, y, 20, 20).tooltip(Tooltip.create(Component.translatable("gui.minescore.config.delete_preset"))).build());
            }
        }
        int y = height - 54;
        String kept = name == null ? "" : name.getValue();
        name = new EditBox(font, left, y, W - 104, 20, Component.translatable("gui.minescore.config.preset_name"));
        name.setHint(Component.translatable("gui.minescore.config.preset_name").withStyle(ChatFormatting.DARK_GRAY));
        name.setMaxLength(40);
        name.setValue(kept);
        addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.translatable("gui.minescore.config.save_preset"), b -> {
            try {
                MineScoreConfig.savePreset(name.getValue());
                notice = Component.translatable("gui.minescore.config.preset_saved", name.getValue().strip());
                name.setValue("");
            } catch (Exception e) {
                notice = Component.literal(e.getMessage()).withStyle(ChatFormatting.RED);
            }
            rebuildWidgets();
        }).bounds(left + W - 100, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(width / 2 - 75, height - 28, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 10, UiTheme.c("text"));
        g.drawCenteredString(font, Component.translatable("gui.minescore.config.presets_hint"), width / 2, 21, UiTheme.c("dim"));
        if (notice != null) {
            g.drawCenteredString(font, notice, width / 2, height - 66, UiTheme.c("text"));
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, scroll - (int) Math.signum(delta));
        rebuildWidgets();
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
