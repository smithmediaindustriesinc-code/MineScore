package com.minescore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** The Channel Manager's Locate button: the speaker glows with particles for a few seconds, and its position shows. */
public final class ClientLocate {
    private static final int TICKS = 120;

    private static @Nullable BlockPos target;
    private static Component label = Component.empty();
    private static int ticksLeft;

    private ClientLocate() {}

    public static void start(BlockPos pos, Component name) {
        target = pos;
        label = name;
        ticksLeft = TICKS;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (target == null || mc.level == null || mc.player == null || --ticksLeft < 0) {
            target = null;
            return;
        }
        if (ticksLeft % 2 == 0) {
            for (int i = 0; i < 4; i++) {
                double x = target.getX() + mc.level.random.nextDouble();
                double y = target.getY() + mc.level.random.nextDouble() * 1.2;
                double z = target.getZ() + mc.level.random.nextDouble();
                mc.level.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0.02, 0);
            }
            mc.level.addParticle(ParticleTypes.END_ROD, target.getX() + 0.5, target.getY() + 1.2 + (TICKS - ticksLeft) % 20 * 0.1,
                    target.getZ() + 0.5, 0, 0, 0);
        }
        if (ticksLeft % 20 == 0) {
            int distance = (int) Math.round(Math.sqrt(mc.player.distanceToSqr(target.getCenter())));
            mc.player.displayClientMessage(Component.translatable("msg.minescore.locate", label, target.getX(), target.getY(),
                    target.getZ(), distance), true);
        }
    }
}
