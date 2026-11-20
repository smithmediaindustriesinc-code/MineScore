package com.minescore.wiring;

import com.minescore.block.PortBlock;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** {@link WireWorld} backed by a real dimension. */
public record LevelWireWorld(ServerLevel level, WireNetwork network) implements WireWorld {
    public LevelWireWorld(ServerLevel level) {
        this(level, WireNetwork.get(level));
    }

    @Override
    public @Nullable Endpoint endpoint(PortRef ref) {
        BlockState state = level.getBlockState(ref.pos());
        if (!(state.getBlock() instanceof PortBlock block)) {
            return null;
        }
        return block.layout().port(ref.port())
                .map(port -> new Endpoint(ref, block.layout().kind(), port, block.attachPoint(state, ref.pos(), port)))
                .orElse(null);
    }

    @Override
    public Collection<Cable> cablesAt(BlockPos pos) {
        return network.endsAt(pos);
    }
}
