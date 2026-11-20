package com.minescore.wiring;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** A world made of named MineScore blocks at fixed spots, using the real jack layouts. */
final class FakeWireWorld implements WireWorld {
    private final Map<BlockPos, String> blocks = new HashMap<>();
    private final List<Cable> cables = new ArrayList<>();

    BlockPos place(String block, int x) {
        BlockPos pos = new BlockPos(x, 64, 0);
        blocks.put(pos, block);
        return pos;
    }

    @Override
    public @Nullable Endpoint endpoint(PortRef ref) {
        String block = blocks.get(ref.pos());
        if (block == null) {
            return null;
        }
        PortLayouts.Layout layout = PortLayouts.get(block);
        return layout.port(ref.port())
                .map(port -> new Endpoint(ref, layout.kind(), port,
                        Vec3.atLowerCornerOf(ref.pos()).add(port.attachPoint().scale(1 / 16.0))))
                .orElse(null);
    }

    @Override
    public Collection<Cable> cablesAt(BlockPos pos) {
        return cables.stream().filter(c -> c.hasEndAt(pos)).toList();
    }

    /** Checks a connection and, if allowed, lays it. */
    WiringRules.Result connect(BlockPos a, String portA, BlockPos b, String portB, Gauge gauge, boolean shifted) {
        PortRef first = new PortRef(a, portA);
        PortRef second = new PortRef(b, portB);
        WiringRules.Result result = WiringRules.check(this, first, second, gauge, shifted);
        if (result instanceof WiringRules.Ok ok) {
            cables.add(Cable.create(ok, gauge, List.of(), List.of(endpoint(first).point(), endpoint(second).point())));
        }
        return result;
    }

    List<Cable> cables() {
        return cables;
    }
}
