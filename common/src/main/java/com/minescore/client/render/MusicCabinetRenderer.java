package com.minescore.client.render;

import com.minescore.block.MusicCabinetBlock;
import com.minescore.block.MusicCabinetBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Matrix4f;

/**
 * The shelves themselves are the block model (its blockstate counts them); this only marks the Score or
 * sleeve you're pointing at, with an outline and its title floating above the cabinet.
 */
public class MusicCabinetRenderer implements BlockEntityRenderer<MusicCabinetBlockEntity> {
    /** The sleeve colours on the cabinet's item, which has no records of its own. */
    public static final int[] ITEM_SLEEVES = {0xFFBE323C, 0xFF3C6FC8, 0xFFE8BE40, 0xFF4A9A50, 0xFF8A4FB0};

    public MusicCabinetRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(MusicCabinetBlockEntity cabinet, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        HitResult hit = Minecraft.getInstance().hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK
                || !blockHit.getBlockPos().equals(cabinet.getBlockPos())) {
            return;
        }
        BlockState state = cabinet.getBlockState();
        MusicCabinetBlock.Spot spot = MusicCabinetBlock.spotAt(state, cabinet.getBlockPos(), blockHit.getLocation(), blockHit.getDirection());
        ItemStack stack = spot == null ? null : cabinet.at(spot);
        if (stack == null) {
            return;
        }
        float turn = -switch (state.getValue(MusicCabinetBlock.FACING)) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(turn));
        pose.translate(-0.5, 0, -0.5);
        AABB box = spot.box();
        AABB inBlock = new AABB(box.minX / 16, box.minY / 16, box.minZ / 16, box.maxX / 16, box.maxY / 16, box.maxZ / 16).inflate(0.004);
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), inBlock, 1, 1, 1, 0.8f);
        pose.popPose();
        label(stack.getHoverName(), pose, buffers, light);
    }

    /** The pointed-at title above the cabinet, facing the camera, like the Vinyl Display's. */
    private static void label(Component text, PoseStack pose, MultiBufferSource buffers, int light) {
        Minecraft mc = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(0.5, 1.25, 0.5);
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
