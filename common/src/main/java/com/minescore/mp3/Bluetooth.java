package com.minescore.mp3;

import com.minescore.block.PortBlock;
import com.minescore.item.Mp3PlayerItem;
import com.minescore.playback.Playback;
import com.minescore.playback.PlaybackManager;
import com.minescore.wiring.BlockKind;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * An MP3 Player playing straight through one speaker (and its daisy chain), for everyone nearby. It
 * keeps going while its holder stays within 16 blocks of the speaker, or while it sits in an item frame
 * within 8 blocks; otherwise it disconnects.
 */
public final class Bluetooth {
    public static final double HOLDER_RANGE = 16;
    public static final double FRAME_RANGE = 8;

    private record Link(UUID mp3, ResourceKey<Level> dimension, BlockPos speaker, UUID owner) {}

    private static final Map<UUID, Link> LINKS = new HashMap<>();
    private static final Random RANDOM = new Random();

    private Bluetooth() {}

    public static boolean isLinked(UUID mp3) {
        return LINKS.containsKey(mp3);
    }

    /** Right-clicking a speaker with an MP3 Player connects it, or disconnects it if it was already. */
    public static void toggle(ServerPlayer player, ItemStack stack, BlockPos speaker) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Mp3State state = Mp3State.read(stack);
        GlobalPos here = GlobalPos.of(level.dimension(), speaker.immutable());
        if (here.equals(state.link)) {
            disconnect(level, state);
            state.pause(now);
            state.write(stack);
            player.displayClientMessage(Component.translatable("msg.minescore.mp3.disconnected"), true);
            return;
        }
        if (state.link != null) {
            disconnect(level, state);
        }
        // A speaker plays one MP3 Player at a time.
        LINKS.values().stream().filter(l -> l.dimension().equals(level.dimension()) && l.speaker().equals(speaker))
                .map(Link::mp3).toList().forEach(LINKS::remove);
        if (state.library.isEmpty()) {
            player.displayClientMessage(Component.translatable("msg.minescore.mp3.empty"), true);
            return;
        }
        state.link = here;
        state.holder = player.getUUID();
        state.play(now);
        state.write(stack);
        LINKS.put(state.id, new Link(state.id, level.dimension(), speaker.immutable(), player.getUUID()));
        play(level, state);
        player.displayClientMessage(Component.translatable("msg.minescore.mp3.connected"), true);
        com.minescore.ModAdvancements.award(player, "bluetooth");
    }

    /** The MP3 Player's buttons were pressed while linked. */
    public static void onStateChanged(ServerLevel level, Mp3State state) {
        if (state.link != null && LINKS.containsKey(state.id)) {
            play(level, state);
        }
    }

    private static void play(ServerLevel level, Mp3State state) {
        Link link = LINKS.get(state.id);
        if (link == null) {
            return;
        }
        PlaybackManager playback = PlaybackManager.get(level);
        Optional<String> track = state.current().map(s -> s.trackId());
        if (state.playing && track.isPresent()) {
            playback.start(Playback.Kind.BLUETOOTH, link.speaker(), track.get(), state.startTick, state.volume, ItemStack.EMPTY);
        } else {
            playback.stop(link.speaker());
        }
    }

    private static void disconnect(ServerLevel level, Mp3State state) {
        Link link = LINKS.remove(state.id);
        if (link != null) {
            ServerLevel linkLevel = level.getServer().getLevel(link.dimension());
            if (linkLevel != null) {
                PlaybackManager.get(linkLevel).stop(link.speaker());
            }
        }
        state.link = null;
    }

    /** Every half second: keep each link going, move it on between songs, or drop it. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 5) {
            return;
        }
        Iterator<Link> it = LINKS.values().iterator();
        while (it.hasNext()) {
            Link link = it.next();
            ServerLevel level = server.getLevel(link.dimension());
            if (level == null || !stillSpeaker(level, link.speaker()) || !keepGoing(level, link)) {
                it.remove();
                if (level != null) {
                    PlaybackManager.get(level).stop(link.speaker());
                }
            }
        }
    }

    private static boolean stillSpeaker(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof PortBlock block && block.layout().kind() == BlockKind.SPEAKER;
    }

    /** Finds the MP3 Player (with its holder in range, or in a nearby item frame) and advances it. */
    private static boolean keepGoing(ServerLevel level, Link link) {
        long now = level.getGameTime();
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(link.owner());
        if (owner != null && owner.level() == level) {
            ItemStack stack = Mp3Server.find(owner.getInventory(), link.mp3());
            if (stack != null) {
                if (owner.distanceToSqr(link.speaker().getCenter()) > HOLDER_RANGE * HOLDER_RANGE) {
                    Mp3State state = Mp3State.read(stack);
                    state.link = null;
                    state.pause(now);
                    state.write(stack);
                    owner.displayClientMessage(Component.translatable("msg.minescore.mp3.out_of_range"), true);
                    return false;
                }
                advance(level, stack, null, now);
                return true;
            }
        }
        AABB around = new AABB(link.speaker()).inflate(FRAME_RANGE);
        for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, around)) {
            ItemStack stack = frame.getItem();
            if (stack.getItem() instanceof Mp3PlayerItem && link.mp3().equals(Mp3State.idOf(stack))) {
                advance(level, stack, frame, now);
                return true;
            }
        }
        return false;
    }

    private static void advance(ServerLevel level, ItemStack stack, ItemFrame frame, long now) {
        Mp3State state = Mp3State.read(stack);
        if (state.tick(now, RANDOM)) {
            if (frame != null) {
                ItemStack copy = stack.copy();
                state.write(copy);
                frame.setItem(copy, false);
            } else {
                state.write(stack);
            }
            play(level, state);
        }
    }
}
