package com.minescore.forge.platform;

import com.minescore.MineScore;
import com.minescore.network.NetworkHelper;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ForgeNetworkHelper implements NetworkHelper {
    /** Bump when packets change; clients and servers must match. */
    private static final String VERSION = "6";

    private final SimpleChannel channel = NetworkRegistry.newSimpleChannel(MineScore.id("main"), () -> VERSION,
            VERSION::equals, VERSION::equals);
    private int nextId;

    @Override
    public <M> void registerClientbound(Class<M> type, BiConsumer<M, FriendlyByteBuf> encoder,
                                        Function<FriendlyByteBuf, M> decoder, Consumer<M> handler) {
        channel.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(encoder)
                .decoder(decoder)
                .consumerMainThread((message, context) -> handler.accept(message))
                .add();
    }

    @Override
    public <M> void registerServerbound(Class<M> type, BiConsumer<M, FriendlyByteBuf> encoder,
                                        Function<FriendlyByteBuf, M> decoder, BiConsumer<M, ServerPlayer> handler) {
        channel.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(encoder)
                .decoder(decoder)
                .consumerMainThread((message, context) -> {
                    ServerPlayer sender = context.get().getSender();
                    if (sender != null) {
                        handler.accept(message, sender);
                    }
                })
                .add();
    }

    @Override
    public void sendToPlayer(ServerPlayer player, Object message) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    @Override
    public void sendToLevel(ServerLevel level, Object message) {
        channel.send(PacketDistributor.DIMENSION.with(level::dimension), message);
    }

    @Override
    public void sendToServer(Object message) {
        channel.sendToServer(message);
    }
}
