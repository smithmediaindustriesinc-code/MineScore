package com.minescore.block;

import com.minescore.power.PowerSettings;
import com.minescore.power.PowerSource;
import com.minescore.wiring.SpeakerSystems;
import com.minescore.wiring.WireNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Whether a hub (Mixer, Channel Manager, Amplifier, Multi-Effects Unit) looks switched on: lit while its
 * system has power, dark with its lights off when it hasn't (SCOPE.md §13.2).
 */
public final class HubPower {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private HubPower() {}

    /** Once a second on the server: shows whether the hub has power. */
    public static void tick(Level level, BlockPos pos, BlockState state) {
        if (level.getGameTime() % 20 != 0 || !(level instanceof ServerLevel serverLevel) || !state.hasProperty(POWERED)) {
            return;
        }
        RedstoneHub.neighbourChanged(level, pos); // catches up on redstone after the world loads, too
        boolean powered = powered(serverLevel, pos);
        if (state.getValue(POWERED) != powered) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
        }
    }

    /**
     * Always in None mode. In Energy mode: its system's source has at least what the hubs need; a hub in
     * no system is dark, except an Amplifier on its own with enough FE of its own to work.
     */
    public static boolean powered(ServerLevel level, BlockPos pos) {
        if (!PowerSettings.energyMode()) {
            return true;
        }
        if (level.getBlockEntity(pos) instanceof PowerSource source) {
            return source.power().running();
        }
        BlockPos source = SpeakerSystems.sourceOf(WireNetwork.get(level)::endsAt, pos);
        if (source == null) {
            return level.getBlockEntity(pos) instanceof AmplifierBlockEntity amp && amp.energy().stored() >= PowerSettings.hubCost.getAsInt();
        }
        return level.getBlockEntity(source) instanceof PowerSource powered && powered.power().running();
    }
}
