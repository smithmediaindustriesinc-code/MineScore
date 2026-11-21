package com.minescore.block;

import com.minescore.audio.SynthSound;
import com.minescore.item.CdData;
import com.minescore.item.Mp3PlayerItem;
import com.minescore.menu.LatheMenu;
import com.minescore.mp3.Mp3Packets;
import com.minescore.mp3.Mp3State;
import com.minescore.network.MineScoreNetwork;
import com.minescore.power.EnergyBuffer;
import com.minescore.power.KineticSettings;
import com.minescore.power.PowerSettings;
import com.minescore.registry.ModItems;
import com.minescore.song.SongMeta;
import com.minescore.song.SongPackets;
import com.minescore.song.SongStore;
import com.minescore.song.SongTransfers;
import com.minescore.wiring.PortRef;
import com.minescore.wiring.WireNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The Disc Cutting Lathe's workings, shared by the plain Lathe and the one that takes Create rotation: it
 * cuts a song onto a Blank Vinyl, or copies a Vinyl Record onto one.
 *
 * <p>Slots: 0 input (a Blank Vinyl to cut, or the Vinyl Record to copy), 1 the Blank Vinyl a copy goes onto (or
 * the MP3 Player a song is loaded into), 2 output.
 *
 * <p>What drives it (SCOPE.md §2.4, S3): 20 FE/t of its own, at a speed set by any Amplifier band wired in;
 * or Create rotation, 3 × 64 ÷ RPM seconds a cut, for free. Whichever is faster at the moment wins. Turned
 * past the maximum RPM, the record being cut shatters. With neither it waits. Previews need no power.
 */
public final class LatheCore implements MenuProvider {
    public static final int INPUT = 0;
    public static final int EXTRA = 1;
    public static final int OUTPUT = 2;
    /** How much FE a Lathe holds. */
    public static final int ENERGY_CAPACITY = 4_000;
    /** The screen gets progress in thousandths. */
    public static final int PROGRESS_SCALE = 1000;

    /** Cut time on FE with no Amplifier wired in: 10 s. */
    private static final int BASE_TICKS = 200;
    /** Printing a Multi-Effects Unit's effects takes 2 s longer for each one changed from its defaults. */
    private static final int PRINT_TICKS_PER_EFFECT = 40;

    /**
     * Work in progress.
     *
     * @param copy    true when copying the input record onto the Blank Vinyl in the second slot
     * @param feTicks ticks the job takes on FE (an Amplifier wired in shortens it; 0 is instant)
     * @param print   extra ticks for printing a Multi-Effects Unit's effects, on any drive
     * @param blank   the Blank Vinyl being cut, taken out of its slot when Cut (or Copy) was pressed
     */
    private record Job(SongMeta song, String title, String cutBy, boolean copy, int feTicks, int print, ItemStack blank) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", song.id());
            tag.putString("Format", song.format());
            tag.putString("SongTitle", song.title());
            tag.putDouble("Seconds", song.seconds());
            tag.putString("Title", title);
            tag.putString("CutBy", cutBy);
            tag.putBoolean("Copy", copy);
            tag.putInt("Total", feTicks);
            tag.putInt("Print", print);
            tag.put("Blank", blank.save(new CompoundTag()));
            return tag;
        }

        static Job load(CompoundTag tag) {
            return new Job(new SongMeta(tag.getString("Id"), tag.getString("Format"), tag.getString("SongTitle"), tag.getDouble("Seconds")),
                    tag.getString("Title"), tag.getString("CutBy"), tag.getBoolean("Copy"), tag.getInt("Total"), tag.getInt("Print"),
                    ItemStack.of(tag.getCompound("Blank")));
        }
    }

    private final BlockEntity owner;
    private final LatheHost host;
    private final SimpleContainer items = new SimpleContainer(3);
    private final EnergyBuffer energy = new EnergyBuffer(ENERGY_CAPACITY);
    private @Nullable Job job;
    /** How much of the job is done, 0–1. */
    private double done;
    /** The last tick's work stopped for want of power or rotation (shown on the screen). */
    private boolean waiting;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> (int) Math.round(done * PROGRESS_SCALE);
                case 1 -> job == null ? 0 : PROGRESS_SCALE;
                case 2 -> energy.stored();
                case 3 -> (waiting ? 1 : 0) | (PowerSettings.energyMode() ? 2 : 0) | (host.kinetic() ? 4 : 0);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return 4;
        }
    };

    /** @param owner the block entity holding it, which must be a {@link LatheHost} */
    public LatheCore(BlockEntity owner) {
        this.owner = owner;
        this.host = (LatheHost) owner;
        items.addListener(container -> {
            owner.setChanged();
            sync();
        });
    }

    public SimpleContainer items() {
        return items;
    }

    public EnergyBuffer energy() {
        return energy;
    }

    public boolean cutting() {
        return job != null;
    }

    private @Nullable Level level() {
        return owner.getLevel();
    }

    private BlockPos pos() {
        return owner.getBlockPos();
    }

    /** The record to draw on the platter: the one being cut, else whatever record sits in the Lathe. */
    public ItemStack platterDisc() {
        if (job != null) {
            return job.blank();
        }
        ItemStack input = items.getItem(INPUT);
        return input.isEmpty() ? items.getItem(OUTPUT) : input;
    }

    /** Everything to drop when the Lathe is broken, including a record halfway through being cut. */
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        for (int i = 0; i < items.getContainerSize(); i++) {
            all.add(items.getItem(i));
        }
        if (job != null) {
            all.add(job.blank());
        }
        return all;
    }

    // --- driving it --------------------------------------------------------------------------

    /** Turned by Create at a speed it can cut at. */
    private boolean turning() {
        float rpm = Math.abs(host.rpm());
        return rpm > 0 && rpm <= KineticSettings.maxRpm.getAsInt();
    }

    /** Pays for one tick of FE work (or one instant job): its own 20 FE, plus an Amplifier speeding it up. */
    private boolean payForWork() {
        if (!PowerSettings.energyMode()) {
            return true;
        }
        int cost = PowerSettings.latheCost.getAsInt();
        AmplifierBlockEntity amp = job != null && job.feTicks() < BASE_TICKS ? speedAmp() : null;
        if (energy.stored() < cost || (amp != null && !amp.powered(false))) {
            return false;
        }
        energy.take(cost);
        if (amp != null) {
            amp.powered(true);
        }
        owner.setChanged();
        return true;
    }

    /** Whether it can start a job: it has the FE for it, or it's being turned. */
    private boolean canStart() {
        return !PowerSettings.energyMode() || energy.stored() >= PowerSettings.latheCost.getAsInt() || turning();
    }

    public void serverTick() {
        if (job == null) {
            waiting = false;
            return;
        }
        if (done < 1) {
            if (Math.abs(host.rpm()) > KineticSettings.maxRpm.getAsInt()) {
                shatter();
                return;
            }
            // Per tick, the faster of FE (paid for) and rotation (free) does the work (S3).
            double byRotation = turning() ? 1 / (KineticSettings.cutTicks(Math.abs(host.rpm())) + job.print()) : 0;
            double byPower = job.feTicks() <= 0 ? 1 : 1.0 / job.feTicks();
            double rate = byPower > byRotation && payForWork() ? byPower : byRotation;
            waiting = rate <= 0;
            done = Math.min(1, done + rate);
            owner.setChanged();
        }
        if (done >= 1) {
            finishWhenSongArrives();
        }
    }

    /** Turned too fast: the record being cut (or copied onto) breaks and is gone. */
    private void shatter() {
        job = null;
        done = 0;
        waiting = false;
        if (level() instanceof ServerLevel serverLevel) {
            double x = pos().getX() + 0.5, y = pos().getY() + 0.45, z = pos().getZ() + 0.5;
            serverLevel.sendParticles(com.minescore.registry.ModParticles.SHARD.get(), x, y, z, 24, 0.25, 0.05, 0.25, 0);
            SynthSound.RECORD_SHATTER.play(serverLevel, pos());
            com.minescore.ModAdvancements.awardNear(serverLevel, pos(), "overclocked");
        }
        owner.setChanged();
        sync();
    }

    /** The song uploads in the background while the record is cut; the record comes out once both are done. */
    private void finishWhenSongArrives() {
        if (!(level() instanceof ServerLevel serverLevel) || job == null) {
            return;
        }
        if (SongStore.get(serverLevel.getServer()).has(job.song().id())) {
            finish();
        } else if (!SongTransfers.uploading(job.song().id())) {
            giveBack(); // the upload was abandoned (the player left) or failed its checksum
        }
    }

    /** Cancels the job and puts its Blank Vinyl back (or drops it, if the slot has filled up since). */
    private void giveBack() {
        ItemStack blank = job.blank();
        int slot = job.copy() || items.getItem(INPUT).is(ModItems.SCORE.get()) ? EXTRA : INPUT;
        job = null;
        done = 0;
        ItemStack there = items.getItem(slot);
        if (there.isEmpty()) {
            items.setItem(slot, blank);
        } else if (ItemStack.isSameItemSameTags(there, blank) && there.getCount() < there.getMaxStackSize()) {
            there.grow(1);
            items.setChanged();
        } else if (level() != null) {
            Containers.dropItemStack(level(), pos().getX() + 0.5, pos().getY() + 0.6, pos().getZ() + 0.5, blank);
        }
        owner.setChanged();
        sync();
    }

    /** True if the slots hold what a cut (or copy) needs and the output is free. */
    private boolean ready(boolean copy) {
        ItemStack input = items.getItem(INPUT);
        if (!items.getItem(OUTPUT).isEmpty()) {
            return false;
        }
        if (copy) {
            return input.is(ModItems.ENCODED_CD.get()) && CdData.read(input) != null && items.getItem(EXTRA).is(ModItems.BLANK_CD.get());
        }
        return input.is(ModItems.BLANK_CD.get()) || scoreCut();
    }

    /** A Score to cut, with a Blank Vinyl in the second slot to cut it onto (SCOPE.md §2.4). */
    private boolean scoreCut() {
        return items.getItem(INPUT).is(ModItems.SCORE.get()) && items.getItem(EXTRA).is(ModItems.BLANK_CD.get());
    }

    /** Extra ticks for printing the linked Multi-Effects Unit's effects, or -1 if it isn't linked to one. */
    private int printTicks() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return -1;
        }
        for (var cable : WireNetwork.get(serverLevel).atPort(new PortRef(pos(), "in_meu"))) {
            if (level().getBlockEntity(cable.from().pos()) instanceof MeuBlockEntity meu) {
                return PRINT_TICKS_PER_EFFECT * meu.chain().changedEffects();
            }
        }
        return -1;
    }

    /** Ticks a cut takes on FE: faster when a powered Amplifier band output is wired into a matching input jack. */
    private int feTicks(int print) {
        if (print >= 0) {
            return BASE_TICKS + print; // printing effects: no Amplifier speed-up
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            return BASE_TICKS;
        }
        WireNetwork network = WireNetwork.get(serverLevel);
        for (String port : new String[] {"in_12", "in_16", "in_20"}) {
            for (var cable : network.atPort(new PortRef(pos(), port))) {
                if (level().getBlockEntity(cable.from().pos()) instanceof AmplifierBlockEntity amp && amp.powered(false)) {
                    return switch (port) {
                        case "in_12" -> 0; // instant
                        case "in_16" -> 40;
                        default -> 100;
                    };
                }
            }
        }
        return BASE_TICKS;
    }

    /** The Amplifier wired into a band jack, if any. */
    private @Nullable AmplifierBlockEntity speedAmp() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        WireNetwork network = WireNetwork.get(serverLevel);
        for (String port : new String[] {"in_12", "in_16", "in_20"}) {
            for (var cable : network.atPort(new PortRef(pos(), port))) {
                if (level().getBlockEntity(cable.from().pos()) instanceof AmplifierBlockEntity amp) {
                    return amp;
                }
            }
        }
        return null;
    }

    // --- requests from the screen ------------------------------------------------------------

    /** A player asked to cut a song (from the Lathe screen). */
    public static void handleCut(SongPackets.Cut packet, ServerPlayer player) {
        LatheCore lathe = open(player, packet.lathe());
        if (lathe == null) {
            return;
        }
        SongMeta song = packet.song();
        if (!SongMeta.validId(song.id()) || !SongMeta.validFormat(song.format()) || packet.size() <= 0) {
            return;
        }
        if (lathe.job != null || !lathe.ready(false)) {
            status(player, packet.lathe(), lathe.job != null ? "msg.minescore.lathe.busy" : "msg.minescore.lathe.needs_blank");
            return;
        }
        if (!lathe.canStart()) {
            status(player, packet.lathe(), "msg.minescore.lathe.no_power");
            return;
        }
        String title = cleanTitle(packet.title(), song.title());
        String cutBy = player.getGameProfile().getName();
        SongStore store = SongStore.get(player.server);
        if (!store.has(song.id())) {
            SongTransfers.requestUpload(player, song, packet.size(), uploaded -> { });
        } else {
            song = store.correctLength(song);
        }
        if (packet.loudness().length > 0) {
            store.writeLoudness(song.id(), packet.loudness());
        }
        boolean score = lathe.scoreCut();
        lathe.start(song, title, cutBy, false, lathe.takeBlank(score ? EXTRA : INPUT));
        com.minescore.ModAdvancements.award(player, score ? "pressed_score" : "fresh_cut");
    }

    /** A player asked to copy the Vinyl Record in the Lathe. */
    public static void handleCopy(SongPackets.Copy packet, ServerPlayer player) {
        LatheCore lathe = open(player, packet.lathe());
        if (lathe == null) {
            return;
        }
        if (lathe.job != null || !lathe.ready(true)) {
            status(player, packet.lathe(), lathe.job != null ? "msg.minescore.lathe.busy" : "msg.minescore.lathe.needs_copy");
            return;
        }
        if (!com.minescore.config.MineScoreConfig.RECORD_COPIES.bool()) {
            status(player, packet.lathe(), "msg.minescore.lathe.no_copies");
            return;
        }
        if (!lathe.canStart()) {
            status(player, packet.lathe(), "msg.minescore.lathe.no_power");
            return;
        }
        CdData original = CdData.read(lathe.items.getItem(INPUT));
        lathe.start(original.song(), cleanTitle(packet.title(), original.title()), original.cutBy(), true, lathe.takeBlank(EXTRA));
        com.minescore.ModAdvancements.award(player, "bootleg");
    }

    /** A player asked to add the Vinyl Record's song to the MP3 Player in the second slot (instant; the record stays). */
    public static void handleLoad(Mp3Packets.LoadIntoPlayer packet, ServerPlayer player) {
        LatheCore lathe = open(player, packet.lathe());
        if (lathe == null) {
            return;
        }
        ItemStack mp3 = lathe.items.getItem(EXTRA);
        CdData data = CdData.read(lathe.items.getItem(INPUT));
        if (data == null || !(mp3.getItem() instanceof Mp3PlayerItem)) {
            status(player, packet.lathe(), "msg.minescore.lathe.needs_load");
            return;
        }
        if (!lathe.turning() && !lathe.payForWork()) { // instant: one tick's worth of power, or turning at all (S3)
            status(player, packet.lathe(), "msg.minescore.lathe.no_power");
            return;
        }
        Mp3State state = Mp3State.read(mp3);
        boolean added = state.addSong(new SongMeta(data.song().id(), data.song().format(), data.title(), data.song().seconds()));
        state.write(mp3);
        if (state.library.size() >= 10) {
            com.minescore.ModAdvancements.award(player, "mixtape");
        }
        lathe.items.setChanged();
        status(player, packet.lathe(), added ? "msg.minescore.lathe.loaded" : "msg.minescore.lathe.already_loaded");
    }

    /** The Lathe the player has open at {@code pos}, or null if they don't. */
    private static @Nullable LatheCore open(ServerPlayer player, BlockPos pos) {
        if (!(player.containerMenu instanceof LatheMenu menu) || !menu.pos().equals(pos)
                || player.distanceToSqr(pos.getCenter()) > 64) {
            return null;
        }
        return player.level().getBlockEntity(pos) instanceof LatheHost host ? host.lathe() : null;
    }

    private static String cleanTitle(String requested, String fallback) {
        String title = requested == null ? "" : requested.strip();
        if (title.length() > 64) {
            title = title.substring(0, 64);
        }
        return title.isEmpty() ? fallback : title;
    }

    private static void status(ServerPlayer player, BlockPos pos, String key) {
        MineScoreNetwork.sendToPlayer(player, new SongPackets.LatheStatus(pos, key));
    }

    /** Takes one Blank Vinyl out of a slot: pressing Cut uses it up straight away. */
    private ItemStack takeBlank(int slot) {
        ItemStack blank = items.getItem(slot).split(1);
        items.setChanged();
        return blank;
    }

    private void start(SongMeta song, String title, String cutBy, boolean copy, ItemStack blank) {
        int print = printTicks();
        job = new Job(song, title, cutBy, copy, feTicks(print), Math.max(0, print), blank);
        done = 0;
        owner.setChanged();
        sync();
    }

    private void finish() {
        if (job == null || !items.getItem(OUTPUT).isEmpty()) {
            return; // wait for the output slot to be emptied
        }
        Job finished = job;
        job = null;
        done = 0;
        // The Blank Vinyl being cut keeps its dye colour and trim.
        ItemStack blank = finished.blank();
        ItemStack result = new ItemStack(ModItems.ENCODED_CD.get());
        if (blank.getTag() != null) {
            result.setTag(blank.getTag().copy());
        }
        int copyNumber = finished.copy() && level() instanceof ServerLevel serverLevel
                ? SongStore.get(serverLevel.getServer()).nextCopyNumber(finished.song()) : 0;
        new CdData(finished.song(), finished.title(), finished.cutBy(), copyNumber).write(result);
        items.setItem(OUTPUT, result);
        if (level() != null) {
            level().playSound(null, pos(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 1, 1);
        }
        owner.setChanged();
        sync();
    }

    private void sync() {
        if (level() != null && !level().isClientSide) {
            level().sendBlockUpdated(pos(), owner.getBlockState(), owner.getBlockState(), 3);
        }
    }

    // --- menu and saving ---------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.minescore.disc_cutting_lathe");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new LatheMenu(id, inventory, items, data, pos());
    }

    public void save(CompoundTag tag) {
        saveItems(tag);
        energy.save(tag);
        if (job != null) {
            tag.put("Job", job.save());
            tag.putDouble("Done", done);
        }
    }

    public void load(CompoundTag tag) {
        NonNullList<ItemStack> loaded = NonNullList.withSize(items.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, loaded);
        for (int i = 0; i < loaded.size(); i++) {
            items.setItem(i, loaded.get(i));
        }
        job = tag.contains("Job") ? Job.load(tag.getCompound("Job")) : null;
        if (tag.contains("Done")) {
            done = tag.getDouble("Done");
        } else { // saved before M7, as ticks done
            done = job == null || job.feTicks() <= 0 ? 0 : Math.min(1, tag.getInt("Progress") / (double) job.feTicks());
        }
        energy.load(tag);
    }

    /** What clients need to draw the record on the platter. */
    public void saveForClient(CompoundTag tag) {
        saveItems(tag);
        if (job != null) {
            tag.put("Job", job.save());
        }
    }

    private void saveItems(CompoundTag tag) {
        NonNullList<ItemStack> list = NonNullList.withSize(items.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < list.size(); i++) {
            list.set(i, items.getItem(i));
        }
        ContainerHelper.saveAllItems(tag, list, true);
    }
}
