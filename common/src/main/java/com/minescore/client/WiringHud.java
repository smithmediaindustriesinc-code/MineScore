package com.minescore.client;

import com.minescore.MineScoreSettings;
import com.minescore.item.WireItem;
import com.minescore.wiring.PendingCable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * The wiring HUD (SCOPE.md §11.4): while a cable is being laid, a line above the hotbar says which block it
 * started at and how far the last fixed point is, turning amber past the hearing range (where the distance
 * warning would fire).
 */
public final class WiringHud {
    private WiringHud() {}

    public static void render(GuiGraphics g, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        PendingCable pending = null;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = mc.player.getItemInHand(hand);
            if (held.getItem() instanceof WireItem && (pending = PendingCable.read(held)) != null) {
                break;
            }
        }
        if (pending == null || !pending.dimension().equals(mc.level.dimension())) {
            return;
        }
        double distance = pending.lastPoint().distanceTo(mc.player.getEyePosition());
        Component block = mc.level.getBlockState(pending.start().pos()).getBlock().getName();
        Component line = Component.translatable("hud.minescore.wiring", block, (int) Math.round(distance), pending.anchors().size());
        int color = distance > MineScoreSettings.hearingRange() ? UiTheme.c("warn") : UiTheme.c("text");
        int y = height - 59 - (mc.gameMode != null && mc.gameMode.canHurtPlayer() ? 10 : 0);
        g.drawCenteredString(mc.font, line, width / 2, y, color);
    }
}
