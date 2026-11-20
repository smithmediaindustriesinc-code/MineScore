package com.minescore.forge.platform;

import com.minescore.MineScore;
import com.minescore.platform.ChunkLoader;
import com.minescore.playback.PlaybackManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.world.ForgeChunkManager;

/** Forge chunk tickets owned by the playing source's position, so they're tracked separately from /forceload. */
public final class ForgeChunkLoader implements ChunkLoader {
    public ForgeChunkLoader() {
        // Tickets survive restarts; drop any whose source stopped playing while the world was closed.
        ForgeChunkManager.setForcedChunkLoadingCallback(MineScore.MOD_ID, (level, tickets) -> {
            PlaybackManager playback = PlaybackManager.peek(level);
            for (BlockPos owner : tickets.getBlockTickets().keySet()) {
                if (!playback.isPlaying(owner)) {
                    tickets.removeAllTickets(owner);
                }
            }
        });
    }

    @Override
    public void setForced(ServerLevel level, BlockPos owner, ChunkPos chunk, boolean forced) {
        ForgeChunkManager.forceChunk(level, MineScore.MOD_ID, owner, chunk.x, chunk.z, forced, true);
    }
}
