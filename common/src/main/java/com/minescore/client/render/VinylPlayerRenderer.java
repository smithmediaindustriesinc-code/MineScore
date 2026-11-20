package com.minescore.client.render;

import com.minescore.block.VinylPlayerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/** Draws the record on the Vinyl Player's platter, spinning while it plays. */
public class VinylPlayerRenderer implements BlockEntityRenderer<VinylPlayerBlockEntity> {
    public VinylPlayerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(VinylPlayerBlockEntity vinyl, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
                       int overlay) {
        ItemStack disc = vinyl.disc();
        if (disc.isEmpty() || vinyl.getLevel() == null) {
            return;
        }
        float turn = -switch (vinyl.getBlockState().getValue(HorizontalDirectionalBlock.FACING)) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(turn));
        pose.translate(-0.5 / 16, 6.55 / 16, 0.5 / 16); // platter centre, as on the Lathe
        if (vinyl.playing()) {
            pose.mulPose(Axis.YP.rotationDegrees(-(vinyl.getLevel().getGameTime() + partialTick) * 10)); // 33⅓ rpm = 10° a tick
        }
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.scale(0.58f, 0.58f, 0.58f);
        Minecraft.getInstance().getItemRenderer().renderStatic(disc, ItemDisplayContext.FIXED, light, overlay, pose, buffers,
                vinyl.getLevel(), (int) vinyl.getBlockPos().asLong());
        pose.popPose();
    }
}
