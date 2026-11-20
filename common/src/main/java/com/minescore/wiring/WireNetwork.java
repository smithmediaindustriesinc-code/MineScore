package com.minescore.wiring;

import com.minescore.network.CablePackets;
import com.minescore.network.MineScoreNetwork;
import com.minescore.playback.PlaybackManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;

/** Every cable in one dimension. Saved with the world and mirrored to players in that dimension. */
public final class WireNetwork extends SavedData {
    private static final String NAME = "minescore_wires";

    private final Map<UUID, Cable> cables = new LinkedHashMap<>();
    /** Cable ids by every block they touch: both ends and every anchor. */
    private final Map<BlockPos, Set<UUID>> byBlock = new HashMap<>();
    private ServerLevel level;

    public static WireNetwork get(ServerLevel level) {
        WireNetwork network = level.getDataStorage().computeIfAbsent(WireNetwork::load, WireNetwork::new, NAME);
        network.level = level;
        return network;
    }

    public Collection<Cable> all() {
        return Collections.unmodifiableCollection(cables.values());
    }

    /** Cables with an end at {@code pos} (not ones only passing over it on an anchor). */
    public List<Cable> endsAt(BlockPos pos) {
        return touching(pos).stream().filter(c -> c.hasEndAt(pos)).toList();
    }

    public List<Cable> atPort(PortRef port) {
        return endsAt(port.pos()).stream().filter(c -> c.usesPort(port)).toList();
    }

    public void add(Cable cable) {
        put(cable);
        setDirty();
        MineScoreNetwork.sendToLevel(level, new CablePackets.Upsert(cable));
        PlaybackManager.get(level).refresh();
    }

    public void remove(UUID id) {
        Cable cable = cables.remove(id);
        if (cable == null) {
            return;
        }
        unindex(cable);
        setDirty();
        MineScoreNetwork.sendToLevel(level, new CablePackets.Remove(id));
        PlaybackManager.get(level).refresh();
    }

    /** A block with jacks was removed: its cables go, and each drops its wire there. */
    public void removeBlock(BlockPos pos) {
        for (Cable cable : endsAt(pos)) {
            remove(cable.id());
            Block.popResource(level, pos, new ItemStack(cable.gauge().item()));
        }
    }

    /** A Cable Anchor was removed: cables through it now run straight past. */
    public void removeAnchor(BlockPos pos) {
        for (Cable cable : touching(pos)) {
            if (cable.anchors().contains(pos)) {
                unindex(cable);
                put(cable.withoutAnchor(pos));
                MineScoreNetwork.sendToLevel(level, new CablePackets.Upsert(cables.get(cable.id())));
            }
        }
        setDirty();
    }

    private List<Cable> touching(BlockPos pos) {
        Set<UUID> ids = byBlock.getOrDefault(pos, Set.of());
        List<Cable> result = new ArrayList<>(ids.size());
        ids.forEach(id -> result.add(cables.get(id)));
        return result;
    }

    private void put(Cable cable) {
        cables.put(cable.id(), cable);
        index(cable, cable.from().pos());
        index(cable, cable.to().pos());
        cable.anchors().forEach(anchor -> index(cable, anchor));
    }

    private void index(Cable cable, BlockPos pos) {
        byBlock.computeIfAbsent(pos.immutable(), p -> new HashSet<>()).add(cable.id());
    }

    private void unindex(Cable cable) {
        List<BlockPos> positions = new ArrayList<>(cable.anchors());
        positions.add(cable.from().pos());
        positions.add(cable.to().pos());
        for (BlockPos pos : positions) {
            Set<UUID> ids = byBlock.get(pos);
            if (ids != null && ids.remove(cable.id()) && ids.isEmpty()) {
                byBlock.remove(pos);
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        cables.values().forEach(cable -> list.add(cable.save()));
        tag.put("Cables", list);
        return tag;
    }

    private static WireNetwork load(CompoundTag tag) {
        WireNetwork network = new WireNetwork();
        for (Tag t : tag.getList("Cables", Tag.TAG_COMPOUND)) {
            network.put(Cable.load((CompoundTag) t));
        }
        return network;
    }
}
