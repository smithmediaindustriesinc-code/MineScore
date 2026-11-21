package com.minescore.config;

import com.minescore.MineScore;
import com.minescore.network.MineScoreNetwork;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server settings travelling (SCOPE.md §10): the server sends all of them to each player who joins and again
 * whenever they change; operators change them in-game from the config menu.
 */
public final class ConfigPackets {
    /** Set by the client: an open config menu shows new server settings when they arrive. */
    public static Runnable onSynced = () -> {};

    private ConfigPackets() {}

    private static void writeMap(FriendlyByteBuf buf, Map<String, String> values) {
        buf.writeVarInt(values.size());
        values.forEach((k, v) -> {
            buf.writeUtf(k, 64);
            buf.writeUtf(v, 1024);
        });
    }

    private static Map<String, String> readMap(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 512);
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            values.put(buf.readUtf(64), buf.readUtf(1024));
        }
        return values;
    }

    /** Server: the server's settings now. */
    public record Sync(Map<String, String> values) {
        public void write(FriendlyByteBuf buf) {
            writeMap(buf, values);
        }

        public static Sync read(FriendlyByteBuf buf) {
            return new Sync(readMap(buf));
        }
    }

    /** Client (an operator): change these server settings. */
    public record Change(Map<String, String> values) {
        public void write(FriendlyByteBuf buf) {
            writeMap(buf, values);
        }

        public static Change read(FriendlyByteBuf buf) {
            return new Change(readMap(buf));
        }
    }

    public static void sendTo(ServerPlayer player) {
        MineScoreNetwork.sendToPlayer(player, new Sync(MineScoreConfig.serverValues()));
    }

    /** Whether a player may change server settings: an operator, or whoever's world it is. */
    public static boolean mayChange(ServerPlayer player) {
        return player.hasPermissions(2) || player.server.isSingleplayerOwner(player.getGameProfile());
    }

    /** On the server: an operator changed some settings; they're saved and everyone gets them. */
    public static void onChange(Change packet, ServerPlayer player) {
        if (!mayChange(player)) {
            player.displayClientMessage(Component.translatable("msg.minescore.config.not_op"), true);
            sendTo(player); // put their menu back
            return;
        }
        // Always saved, never only "if it changed": in singleplayer the menu has already set these very settings in
        // this same game before the packet arrives, so they'd look unchanged and never reach the file.
        MineScoreConfig.apply(packet.values(), Setting.Side.SERVER);
        MineScoreConfig.save(Setting.Side.SERVER);
        MineScore.LOGGER.info("{} changed MineScore's server settings: {}", player.getGameProfile().getName(), packet.values().keySet());
        Sync sync = new Sync(MineScoreConfig.serverValues());
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            MineScoreNetwork.sendToPlayer(other, sync);
        }
        for (var level : player.server.getAllLevels()) {
            com.minescore.playback.PlaybackManager.get(level).refresh(); // ranges, costs and the like apply at once
        }
    }

    /** On a client: the server's settings arrived. */
    public static void onSync(Sync packet) {
        MineScoreConfig.apply(packet.values(), Setting.Side.SERVER);
        onSynced.run();
    }
}
