package com.minescore.client.render;

import com.minescore.block.VinylDisplayBlock;
import com.minescore.block.VinylDisplayBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Matrix4f;

/**
 * Draws the record standing in a Vinyl Display's slot: the bottom half sunk into the plaque, the top half
 * showing above it. Like an item frame, its title floats above it while you look at it.
 */
public class VinylDisplayRenderer implements BlockEntityRenderer<VinylDisplayBlockEntity> {
    public VinylDisplayRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(VinylDisplayBlockEntity display, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack disc = display.disc();
        if (disc.isEmpty() || display.getLevel() == null) {
            return;
        }
        BlockState state = display.getBlockState();
        boolean floor = state.getValue(VinylDisplayBlock.FLOOR);
        float turn = -switch (state.getValue(VinylDisplayBlock.FACING)) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(turn));
        double z = floor ? VinylDisplayBlock.FLOOR_CD_Z : VinylDisplayBlock.WALL_CD_Z;
        pose.translate(0, VinylDisplayBlock.CD_Y / 16, (z - 8) / 16);
        pose.scale(0.85f, 0.85f, 0.85f); // the record about as wide as the plaque it stands in
        Minecraft.getInstance().getItemRenderer().renderStatic(disc, ItemDisplayContext.FIXED, light, overlay, pose, buffers,
                display.getLevel(), (int) display.getBlockPos().asLong());
        pose.popPose();

        HitResult hit = Minecraft.getInstance().hitResult;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                && blockHit.getBlockPos().equals(display.getBlockPos())) {
            label(disc.getHoverName(), pose, buffers, light);
        }
    }

    /** The record's title above the display, facing the camera, like a named item in a frame. */
    private static void label(Component text, PoseStack pose, MultiBufferSource buffers, int light) {
        Minecraft mc = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(0.5, 1.15, 0.5);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-0.025f, -0.025f, 0.025f);
        Matrix4f matrix = pose.last().pose();
        Font font = mc.font;
        float x = -font.width(text) / 2f;
        int background = (int) (mc.options.getBackgroundOpacity(0.25f) * 255) << 24;
        font.drawInBatch(text, x, 0, 0x20FFFFFF, false, matrix, buffers, Font.DisplayMode.SEE_THROUGH, background, light);
        font.drawInBatch(text, x, 0, 0xFFFFFFFF, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
    }
}
