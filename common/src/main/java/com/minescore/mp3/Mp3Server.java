package com.minescore.mp3;

import com.minescore.block.MixerBlockEntity;
import com.minescore.item.Mp3PlayerItem;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Server side of MP3 Players carried by players: applying their buttons, moving on between songs, and
 * pausing a player that left an inventory (it resumes from where it was last heard).
 */
public final class Mp3Server {
    private static final Random RANDOM = new Random();
    /** Seconds into its song each playing MP3 Player was at when last seen in an inventory. */
    private static final Map<UUID, Double> LAST_SEEN = new HashMap<>();
    /** MP3 Players in each player's inventory at the last scan. */
    private static final Map<UUID, Set<UUID>> PRESENT = new HashMap<>();

    private Mp3Server() {}

    public static void onCommand(Mp3Packets.Command command, ServerPlayer player) {
        if (!command.target().inInventory()) {
            if (player.distanceToSqr(command.target().mixer().getCenter()) <= 64
                    && player.level().getBlockEntity(command.target().mixer()) instanceof MixerBlockEntity mixer) {
                mixer.command(command, player);
            }
            return;
        }
        ItemStack stack = find(player.getInventory(), command.target().player());
        if (stack == null) {
            return;
        }
        long now = player.level().getGameTime();
        Mp3State state = Mp3State.read(stack);
        apply(state, command, now);
        if (state.playing && state.link == null) {
            state.holder = player.getUUID();
            pauseOthers(player.getInventory(), state.id, now);
        }
        state.write(stack);
        if (state.link != null) {
            Bluetooth.onStateChanged(player.serverLevel(), state);
        }
    }

    /** Applies a button press to an MP3 Player's state. */
    public static void apply(Mp3State state, Mp3Packets.Command c, long now) {
        switch (c.action()) {
            case TOGGLE -> state.toggle(now);
            case NEXT -> state.next(now, RANDOM);
            case PREVIOUS -> state.previous(now);
            case SEEK -> state.seek(c.f(), now);
            case PLAY_SONG -> state.playFromLibrary(c.a(), now, RANDOM);
            case JUMP -> state.jumpTo(c.i(), now);
            case SHUFFLE -> state.setShuffle(!state.shuffle, RANDOM);
            case REPEAT -> state.cycleRepeat();
            case VOLUME -> state.volume = Mth.clamp(c.f(), 0, 1);
            case EQ -> {
                if (c.i() >= 0 && c.i() < Mp3State.EQ_BANDS) {
                    state.eq[c.i()] = Mth.clamp(c.f(), -Mp3State.EQ_RANGE, Mp3State.EQ_RANGE);
                }
            }
            case EQ_RESET -> java.util.Arrays.fill(state.eq, 0);
            case REMOVE_SONG -> state.removeSong(c.a(), now);
            case PLAYLIST_CREATE -> state.createPlaylist(c.a());
            case PLAYLIST_DELETE -> state.deletePlaylist(c.a());
            case PLAYLIST_PLAY -> state.playPlaylist(c.a(), c.b().isEmpty() ? null : c.b(), now, RANDOM);
            case PLAYLIST_ADD -> state.addToPlaylist(c.a(), c.b());
            case PLAYLIST_REMOVE -> state.removeFromPlaylist(c.a(), c.b());
            case EJECT -> { }
        }
    }

    /** Only one MP3 Player plays in a player's ears at a time. */
    private static void pauseOthers(Inventory inventory, UUID keep, long now) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof Mp3PlayerItem && Mp3State.has(stack)) {
                Mp3State other = Mp3State.read(stack);
                if (!other.id.equals(keep) && other.playing && other.link == null) {
                    other.pause(now);
                    other.write(stack);
                }
            }
        }
    }

    public static ItemStack find(Inventory inventory, UUID id) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof Mp3PlayerItem && id.equals(Mp3State.idOf(stack))) {
                return stack;
            }
        }
        return null;
    }

    /** Every half second: move on between songs, and pause players that changed hands or came back. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            scan(player);
        }
    }

    private static void scan(ServerPlayer player) {
        long now = player.level().getGameTime();
        Inventory inventory = player.getInventory();
        Set<UUID> before = PRESENT.getOrDefault(player.getUUID(), Set.of());
        Set<UUID> present = new HashSet<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!(stack.getItem() instanceof Mp3PlayerItem) || !Mp3State.has(stack)) {
                continue;
            }
            Mp3State state = Mp3State.read(stack);
            present.add(state.id);
            boolean changed = false;
            if (state.link != null && !Bluetooth.isLinked(state.id)) {
                state.link = null; // its Bluetooth link ended while it was elsewhere
                state.pause(now);
                changed = true;
            }
            if (state.playing && state.link == null) {
                boolean arrived = !before.contains(state.id) || !player.getUUID().equals(state.holder);
                if (arrived) {
                    // It left an inventory while playing and has just come back (or changed hands): pause where it was.
                    state.playing = false;
                    state.position = LAST_SEEN.getOrDefault(state.id, 0.0);
                    state.holder = null;
                    changed = true;
                } else {
                    changed |= state.tick(now, RANDOM);
                    LAST_SEEN.put(state.id, state.position(now));
                }
            }
            if (changed) {
                state.write(stack);
            }
        }
        PRESENT.put(player.getUUID(), present);
    }

    /** A player leaving pauses what they were listening to, so it resumes from the same place. */
    public static void onLogout(ServerPlayer player) {
        long now = player.level().getGameTime();
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof Mp3PlayerItem && Mp3State.has(stack)) {
                Mp3State state = Mp3State.read(stack);
                if (state.playing && state.link == null) {
                    state.pause(now);
                    state.holder = null;
                    state.write(stack);
                }
            }
        }
        PRESENT.remove(player.getUUID());
    }
}
