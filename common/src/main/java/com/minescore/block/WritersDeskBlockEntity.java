package com.minescore.block;

import com.minescore.item.ScoreItem;
import com.minescore.playback.Playback;
import com.minescore.playback.PlaybackManager;
import com.minescore.power.PowerSource;
import com.minescore.power.SourcePower;
import com.minescore.score.ScorePackets;
import com.minescore.song.SongMeta;
import com.minescore.song.SongStore;
import com.minescore.song.SongTransfers;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import com.minescore.menu.DeskMenu;
import com.minescore.registry.ModBlockEntities;
import com.minescore.registry.ModItems;
import com.minescore.score.Score;
import com.minescore.score.ScoreStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A Writer's Desk's pedestal (SCOPE.md §3.6): a score slot and a copy slot. A Blank Score on the desk
 * becomes a new, untitled score; a Blank Score in the copy slot becomes a numbered copy of it at once.
 *
 * <p>It's also a source, like the Mixer: Play renders the score on the desk (as it is then) on the
 * player's client and plays it through the speakers wired to it, or from the desk itself when none are.
 * It takes the system's power (S5), and can loop.
 */
public class WritersDeskBlockEntity extends BlockEntity implements MenuProvider, PowerSource {
    public static final int SCORE = 0;
    public static final int COPY = 1;
    /** Measures a new score starts with. */
    private static final int NEW_MEASURES = 16;

    private final SimpleContainer items = new SimpleContainer(2) {
        @Override
        public int getMaxStackSize() {
            return 1;
        }
    };
    private boolean working;
    private final SourcePower power = new SourcePower();
    /** What it's playing ({@code song:<id>}), or null; how long that is; when it started; and whether it loops. */
    private @Nullable String track;
    private double seconds;
    private long startTick;
    private boolean loop;

    public WritersDeskBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WRITERS_DESK.get(), pos, state);
        items.addListener(container -> {
            setChanged();
            work();
        });
    }

    public SimpleContainer items() {
        return items;
    }

    @Override
    public SourcePower power() {
        return power;
    }

    public boolean playing() {
        return track != null;
    }

    public boolean looping() {
        return loop;
    }

    /** Plays a rendered score from now (after the usual short delay, so clients are ready). */
    public void play(SongMeta song) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        track = song.trackId();
        seconds = song.seconds();
        startTick = level.getGameTime() + PlaybackManager.START_DELAY;
        PlaybackManager.get(serverLevel).start(Playback.Kind.SYSTEM, worldPosition, track, startTick, 1, ItemStack.EMPTY);
        changed();
    }

    public void stop() {
        track = null;
        if (level instanceof ServerLevel serverLevel) {
            PlaybackManager.get(serverLevel).stop(worldPosition);
        }
        changed();
    }

    /** A speaker in its system shorted: it stops. */
    public void halt() {
        stop();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WritersDeskBlockEntity desk) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        PlaybackManager playback = PlaybackManager.get(serverLevel);
        if (desk.power.tick(serverLevel, pos, playback.isPlaying(pos))) {
            playback.refresh();
            level.sendBlockUpdated(pos, state, state, 3);
        }
        if (desk.track == null) {
            return;
        }
        if (!playback.isPlaying(pos)) { // stopped some other way (a short, a command)
            desk.track = null;
            desk.changed();
            return;
        }
        long end = desk.startTick + (long) Math.ceil(desk.seconds * 20);
        if (level.getGameTime() >= end) {
            if (desk.loop) { // round again from the top
                desk.startTick = level.getGameTime() + 2;
                playback.start(Playback.Kind.SYSTEM, pos, desk.track, desk.startTick, 1, ItemStack.EMPTY);
                desk.changed();
            } else {
                desk.stop();
            }
        }
    }

    /** A player's client rendered the score on this desk: play it, once it's uploaded if the server hasn't got it. */
    public static void onPlay(ScorePackets.DeskPlay packet, ServerPlayer player) {
        if (player.distanceToSqr(packet.desk().getCenter()) > 64 || !(player.serverLevel().getBlockEntity(packet.desk()) instanceof WritersDeskBlockEntity desk)
                || desk.score() == null) {
            return;
        }
        SongMeta song = packet.song();
        if (!SongMeta.validId(song.id()) || !SongMeta.validFormat(song.format()) || packet.size() <= 0) {
            return;
        }
        if (!PlaybackManager.get(player.serverLevel()).recover(packet.desk())) {
            player.displayClientMessage(Component.translatable(PlaybackManager.shortResetMessage()), true);
            return;
        }
        SongStore store = SongStore.get(player.server);
        if (packet.loudness().length > 0) {
            store.writeLoudness(song.id(), packet.loudness());
        }
        if (store.has(song.id())) {
            desk.play(store.correctLength(song));
        } else {
            SongTransfers.requestUpload(player, song, packet.size(), uploaded -> {
                if (!desk.isRemoved()) {
                    desk.play(uploaded);
                }
            });
        }
    }

    /** The desk screen's Stop and Loop. */
    public static void onControl(ScorePackets.DeskControl packet, ServerPlayer player) {
        if (player.distanceToSqr(packet.desk().getCenter()) > 64 || !(player.serverLevel().getBlockEntity(packet.desk()) instanceof WritersDeskBlockEntity desk)) {
            return;
        }
        switch (packet.action()) {
            case STOP -> desk.stop();
            case LOOP -> {
                desk.loop = !desk.loop;
                desk.changed();
            }
        }
    }

    /** The score on the desk, if any. */
    public @Nullable ScoreItem.Data score() {
        return ScoreItem.Data.read(items.getItem(SCORE));
    }

    /** The score was retitled in the editor: the Score item on the desk shows it. */
    public void setTitles(String title, String composer) {
        ScoreItem.Data data = score();
        if (data != null) {
            working = true;
            data.withTitles(title, composer).write(items.getItem(SCORE));
            items.setChanged();
            working = false;
        }
    }

    /** Starts new scores and makes copies as soon as their paper is put in. */
    private void work() {
        if (working || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        working = true;
        try {
            ScoreStore store = ScoreStore.get(serverLevel.getServer());
            ItemStack onDesk = items.getItem(SCORE);
            if (onDesk.is(ModItems.BLANK_SCORE.get())) { // a fresh sheet: a new, untitled score
                String id = store.create(Score.blank(NEW_MEASURES));
                ItemStack score = new ItemStack(ModItems.SCORE.get());
                new ScoreItem.Data(id, "", "", id, 0).write(score);
                items.setItem(SCORE, score);
                level.playSound(null, worldPosition, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8f, 1.1f);
            }
            ScoreItem.Data original = score();
            ItemStack paper = items.getItem(COPY);
            if (original != null && paper.is(ModItems.BLANK_SCORE.get()) && com.minescore.config.MineScoreConfig.SCORE_COPIES.bool()) {
                String id = store.copy(original.id());
                if (id != null) {
                    ItemStack copy = new ItemStack(ModItems.SCORE.get());
                    new ScoreItem.Data(id, original.title(), original.composer(), original.original(),
                            store.nextCopyNumber(original.original())).write(copy);
                    items.setItem(COPY, copy);
                    level.playSound(null, worldPosition, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.BLOCKS, 0.8f, 1.2f);
                }
            }
        } finally {
            working = false;
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.minescore.writers_desk");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new DeskMenu(id, inventory, items, worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        NonNullList<ItemStack> list = NonNullList.withSize(items.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < list.size(); i++) {
            list.set(i, items.getItem(i));
        }
        ContainerHelper.saveAllItems(tag, list, true);
        if (track != null) {
            tag.putString("Track", track);
            tag.putDouble("Seconds", seconds);
            tag.putLong("StartTick", startTick);
        }
        tag.putBoolean("Loop", loop);
        power.save(tag);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        tag.put("Power", power.saveForClient());
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        NonNullList<ItemStack> list = NonNullList.withSize(items.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, list);
        working = true; // loading isn't putting paper in
        for (int i = 0; i < list.size(); i++) {
            items.setItem(i, list.get(i));
        }
        working = false;
        track = tag.contains("Track") ? tag.getString("Track") : null;
        seconds = tag.getDouble("Seconds");
        startTick = tag.getLong("StartTick");
        loop = tag.getBoolean("Loop");
        power.load(tag);
        if (tag.contains("Power")) { // on clients
            power.loadForClient(tag.getCompound("Power"));
        }
    }
}
