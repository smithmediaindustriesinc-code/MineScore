package com.minescore.client.screen;

import com.minescore.client.UiTheme;
import com.minescore.MineScore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Shared frame for the screens of blocks without an inventory (Amplifier, Channel Manager, speakers): a
 * panel texture, the block entity it edits, and closing when the block goes or the player walks off.
 */
abstract class DeviceScreen<T extends BlockEntity> extends Screen {
    private static final double REACH_SQR = 8 * 8;

    protected final BlockPos pos;
    private final Class<T> type;
    private final ResourceLocation texture;
    protected final int w, h;
    protected int left, top;
    private String lastKey = "";

    DeviceScreen(Component title, BlockPos pos, Class<T> type, String texture, int w, int h) {
        super(title);
        this.pos = pos;
        this.type = type;
        this.texture = MineScore.id("textures/gui/" + texture + ".png");
        this.w = w;
        this.h = h;
    }

    /** The block entity being edited, as the server last sent it; null if it's gone or out of reach. */
    protected @Nullable T device() {
        if (minecraft == null || minecraft.level == null || minecraft.player == null
                || minecraft.player.distanceToSqr(pos.getCenter()) > REACH_SQR) {
            return null;
        }
        BlockEntity be = minecraft.level.getBlockEntity(pos);
        return type.isInstance(be) ? type.cast(be) : null;
    }

    @Override
    protected final void init() {
        left = (width - w) / 2;
        top = (height - h) / 2;
        clearWidgets();
        T device = device();
        if (device != null) {
            build(device);
        }
    }

    /** Adds this screen's widgets, from the device's current settings. */
    protected abstract void build(T device);

    /** Draws everything but the widgets. */
    protected abstract void draw(GuiGraphics g, T device, int mouseX, int mouseY);

    /** A summary of whatever the widgets show; when it changes, they're built again. */
    protected abstract String stateKey(T device);

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.blit(texture, left, top, 0, 0, w, h);
        T device = device();
        if (device == null) {
            onClose();
            return;
        }
        g.drawString(font, title, left + 10, top + 8, UiTheme.c("device_text"), false);
        draw(g, device, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        T device = device();
        if (device == null) {
            return;
        }
        for (var child : children()) {
            if (child instanceof EditBox box) {
                box.tick();
            }
        }
        String key = stateKey(device);
        if (!key.equals(lastKey)) {
            lastKey = key;
            rebuild();
        }
    }

    /** Builds the widgets again, keeping what's typed in text boxes. */
    protected void rebuild() {
        init();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
