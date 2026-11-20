package com.minescore.forge.compat;

import com.minescore.BlockInfo;
import com.minescore.MineScore;
import java.util.function.Function;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The One Probe (SCOPE.md §14, M12 stage 5): MineScore's {@link BlockInfo} lines in the probe. Only touched
 * when The One Probe is installed: MineScoreForge only sends it this, by inter-mod message, then.
 */
public final class TheOneProbeCompat implements Function<ITheOneProbe, Void> {
    @Override
    public Void apply(ITheOneProbe probe) {
        probe.registerProvider(new IProbeInfoProvider() {
            @Override
            public ResourceLocation getID() {
                return MineScore.id("info");
            }

            @Override
            public void addProbeInfo(ProbeMode mode, IProbeInfo info, Player player, Level level, BlockState state, IProbeHitData data) {
                if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(MineScore.MOD_ID)) {
                    return;
                }
                for (Component line : BlockInfo.lines(level, data.getPos(), state)) {
                    info.text(line);
                }
            }
        });
        return null;
    }
}
