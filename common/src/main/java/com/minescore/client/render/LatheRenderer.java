package com.minescore.client.render;

import com.minescore.block.LatheHost;
import com.minescore.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Draws the record sitting on the Lathe's platter, spinning while it's cut, with vinyl shavings curling off
 * the cutter. An empty Lathe shows its bare platter.
 */
public class LatheRenderer implements BlockEntityRenderer<BlockEntity> {
    /** The game tick each Lathe last threw a shaving, so they come at a steady rate whatever the frame rate. */
    private final java.util.Map<BlockEntity, Long> lastShaving = new java.util.WeakHashMap<>();

    public LatheRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(BlockEntity lathe, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(lathe instanceof LatheHost host)) {
            return;
        }
        ItemStack disc = host.lathe().platterDisc();
        if (!isCd(disc) || lathe.getLevel() == null) {
            return;
        }
        float turn = -switch (lathe.getBlockState().getValue(HorizontalDirectionalBlock.FACING)) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(turn));
        // Platter centre in the north-facing model: x 7.5, z 8.5, top at y 6.5.
        pose.translate(-0.5 / 16, 6.55 / 16, 0.5 / 16);
        if (host.lathe().cutting()) {
            pose.mulPose(Axis.YP.rotationDegrees((lathe.getLevel().getGameTime() + partialTick) * 12));
            shavings(lathe, turn);
        }
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.scale(0.58f, 0.58f, 0.58f);
        Minecraft.getInstance().getItemRenderer().renderStatic(disc, ItemDisplayContext.FIXED, light, overlay, pose, buffers,
                lathe.getLevel(), (int) lathe.getBlockPos().asLong());
        pose.popPose();
    }

    /** Now and then a curl of swarf flies off the cutting head (model x 9, z 13, on the deck) away from the platter. */
    private void shavings(BlockEntity lathe, float turn) {
        long tick = lathe.getLevel().getGameTime();
        Long last = lastShaving.get(lathe);
        if (last != null && last == tick) {
            return;
        }
        lastShaving.put(lathe, tick);
        if (lathe.getLevel().random.nextFloat() > 0.45f) {
            return;
        }
        double angle = Math.toRadians(-turn);
        double mx = 9 / 16.0 - 0.5, mz = 13 / 16.0 - 0.5;
        double x = mx * Math.cos(angle) + mz * Math.sin(angle), z = -mx * Math.sin(angle) + mz * Math.cos(angle);
        var pos = lathe.getBlockPos();
        lathe.getLevel().addParticle(com.minescore.registry.ModParticles.SHAVING.get(), pos.getX() + 0.5 + x, pos.getY() + 0.6,
                pos.getZ() + 0.5 + z, x * 0.08, 0, z * 0.08);
    }

    private static boolean isCd(ItemStack stack) {
        return stack.is(ModItems.BLANK_CD.get()) || stack.is(ModItems.ENCODED_CD.get());
    }
}
