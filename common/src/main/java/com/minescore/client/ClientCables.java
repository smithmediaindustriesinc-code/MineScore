package com.minescore.client;

import com.minescore.network.CablePackets;
import com.minescore.wiring.Cable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import java.util.Map;
import java.util.UUID;

/**
 * The client's copy of the cables in its current dimension. Holds plain data only, so it is safe to
 * reference from packet handlers on a dedicated server.
 */
public final class ClientCables {
    private static final Map<UUID, Cable> CABLES = new LinkedHashMap<>();

    private ClientCables() {}

    public static Collection<Cable> all() {
        return Collections.unmodifiableCollection(CABLES.values());
    }

    /** Cables with an end at {@code pos}, like {@code WireNetwork.endsAt} on the server. */
    public static List<Cable> endsAt(BlockPos pos) {
        return CABLES.values().stream().filter(c -> c.hasEndAt(pos)).toList();
    }

    public static void onSnapshot(CablePackets.Snapshot packet) {
        CABLES.clear();
        packet.cables().forEach(cable -> CABLES.put(cable.id(), cable));
    }

    public static void onUpsert(CablePackets.Upsert packet) {
        CABLES.put(packet.cable().id(), packet.cable());
    }

    public static void onRemove(CablePackets.Remove packet) {
        CABLES.remove(packet.id());
    }

    public static void clear() {
        CABLES.clear();
    }
}
