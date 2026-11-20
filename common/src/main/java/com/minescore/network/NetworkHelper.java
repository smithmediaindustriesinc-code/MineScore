package com.minescore.network;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Sends packets in a loader-specific way. Handlers run on the receiving side's main thread. */
public interface NetworkHelper {
    <M> void registerClientbound(Class<M> type, BiConsumer<M, FriendlyByteBuf> encoder,
                                 Function<FriendlyByteBuf, M> decoder, Consumer<M> handler);

    <M> void registerServerbound(Class<M> type, BiConsumer<M, FriendlyByteBuf> encoder,
                                 Function<FriendlyByteBuf, M> decoder, BiConsumer<M, ServerPlayer> handler);

    void sendToPlayer(ServerPlayer player, Object message);

    /** Sends to every player in {@code level}'s dimension. */
    void sendToLevel(ServerLevel level, Object message);

    void sendToServer(Object message);
}
