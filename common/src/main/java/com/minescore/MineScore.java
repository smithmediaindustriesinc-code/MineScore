package com.minescore;

import com.minescore.command.MineScoreCommands;
import com.minescore.mp3.Bluetooth;
import com.minescore.mp3.Mp3Server;
import com.minescore.network.MineScoreNetwork;
import com.minescore.network.NetworkHelper;
import com.minescore.platform.ChunkLoader;
import com.minescore.platform.PlatformHelper;
import com.minescore.platform.RegistryHelper;
import com.minescore.playback.PlaybackManager;
import com.minescore.registry.ModBlockEntities;
import com.minescore.registry.ModBlocks;
import com.minescore.registry.ModCreativeTabs;
import com.minescore.registry.ModItems;
import com.minescore.registry.ModMenus;
import com.minescore.song.SongStore;
import com.minescore.song.SongTransfers;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-independent entry point. Each loader module calls {@link #init} once during mod construction
 * and forwards the events below.
 */
public final class MineScore {
    public static final String MOD_ID = "minescore";
    public static final Logger LOGGER = LoggerFactory.getLogger("MineScore");

    private static PlatformHelper platform;

    private MineScore() {}

    public static void init(RegistryHelper registry, NetworkHelper network, ChunkLoader chunkLoader, PlatformHelper platformHelper) {
        platform = platformHelper;
        com.minescore.config.MineScoreConfig.load(java.nio.file.Path.of("config"));
        ModBlocks.register(registry);
        ModItems.register(registry);
        ModBlockEntities.register(registry, platformHelper);
        ModMenus.register(registry, platformHelper);
        ModCreativeTabs.register(registry);
        com.minescore.registry.ModParticles.register(registry);
        ModAdvancements.register();
        MineScoreNetwork.register(network);
        PlaybackManager.setChunkLoader(chunkLoader);
        LOGGER.info("MineScore initialised");
    }

    public static PlatformHelper platform() {
        return platform;
    }

    /** A player logged in, respawned or changed dimension. */
    public static void onPlayerEnteredLevel(ServerPlayer player) {
        MineScoreNetwork.syncLevel(player);
        com.minescore.config.ConfigPackets.sendTo(player); // the server's settings
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        SongTransfers.onLogout(player);
        Mp3Server.onLogout(player);
        com.minescore.score.ScoreSessions.onLoggedOut(player);
    }

    public static void onServerTick(MinecraftServer server) {
        SongTransfers.tick(server);
        Mp3Server.tick(server);
        Bluetooth.tick(server);
        com.minescore.score.ScoreSessions.tick(server);
        for (ServerLevel level : server.getAllLevels()) {
            PlaybackManager.get(level).tick();
        }
    }

    public static void onServerStopped(MinecraftServer server) {
        SongStore.forget(server);
        com.minescore.score.ScoreSessions.saveAll(server);
        com.minescore.score.ScoreStore.forget(server);
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        MineScoreCommands.register(dispatcher);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
