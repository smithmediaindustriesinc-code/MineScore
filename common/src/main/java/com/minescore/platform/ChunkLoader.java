package com.minescore.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Force-loads chunks on behalf of a block, in a loader-specific way that survives restarts. */
public interface ChunkLoader {
    void setForced(ServerLevel level, BlockPos owner, ChunkPos chunk, boolean forced);
}
