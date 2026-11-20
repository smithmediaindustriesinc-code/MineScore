package com.minescore.client.render;

import com.minescore.block.HubPower;
import com.minescore.block.MeuBlockEntity;
import com.minescore.menu.MeuMenu;
import com.minescore.meu.CardSettings;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/**
 * The Multi-Effects Unit's front: each card standing in its slot (a bypassed one sunk a little deeper),
 * and the preset's name glowing on the display above them.
 */
public class MeuRenderer implements BlockEntityRenderer<MeuBlockEntity> {
    /** Slot centres across the front, in pixels from the left as you face it, and their height. */
    private static final double SLOT_Y = 6.5;

    public MeuRenderer(BlockEntityRendererProvider.Context context) {}

    private static double slotX(int slot) {
        return 2.6 + slot * 2.16;
    }

    @Override
    public void render(MeuBlockEntity meu, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (meu.getLevel() == null) {
            return;
        }
        float turn = -switch (meu.getBlockState().getValue(HorizontalDirectionalBlock.FACING)) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(turn));
        pose.mulPose(Axis.YP.rotationDegrees(180)); // now +x is the viewer's right and +z points out of the front
        pose.translate(-0.5, 0, 0.5);
        for (int i = 0; i < MeuMenu.CARDS; i++) {
            ItemStack stack = meu.items().getItem(i);
            CardSettings card = meu.card(i);
            if (stack.isEmpty() || card == null) {
                continue;
            }
            pose.pushPose();
            pose.translate(slotX(i) / 16, SLOT_Y / 16, (card.bypass() ? 0.05 : 0.3) / 16);
            pose.scale(0.3f, 0.3f, 0.3f); // a card stands about 4 pixels tall in its slot
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers,
                    meu.getLevel(), (int) meu.getBlockPos().asLong() + i);
            pose.popPose();
        }
        // The display: the preset's name, or BYPASS. Blank while the unit has no power.
        if (meu.getBlockState().hasProperty(HubPower.POWERED) && !meu.getBlockState().getValue(HubPower.POWERED)) {
            pose.popPose();
            return;
        }
        Font font = Minecraft.getInstance().font;
        String text = meu.disabled() ? "BYPASS" : meu.presetLabel();
        text = font.plainSubstrByWidth(text, 46);
        pose.pushPose();
        pose.translate(8.0 / 16, 12.4 / 16, 0.02 / 16);
        float scale = 1 / 64f; // 2 pixels tall on the 4-pixel display
        pose.scale(scale, -scale, scale);
        font.drawInBatch(text, -font.width(text) / 2f, -4, meu.disabled() ? 0xFFFF9060 : 0xFFC8E86A, false, pose.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
        pose.popPose();
    }
}
