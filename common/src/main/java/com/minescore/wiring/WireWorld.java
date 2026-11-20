package com.minescore.wiring;

import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** What {@link WiringRules} needs to know about the world, so the rules can be tested without one. */
public interface WireWorld {
    /** The jack at {@code ref}, or null if the block or jack no longer exists. */
    @Nullable Endpoint endpoint(PortRef ref);

    /** Every cable with an end at {@code pos}. */
    Collection<Cable> cablesAt(BlockPos pos);

    /**
     * @param point where a cable plugs into this jack, in world coordinates
     */
    record Endpoint(PortRef ref, BlockKind kind, Port port, Vec3 point) {}
}
