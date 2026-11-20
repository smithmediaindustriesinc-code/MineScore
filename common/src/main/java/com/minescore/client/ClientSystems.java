package com.minescore.client;

import com.minescore.wiring.BlockKind;
import com.minescore.wiring.SpeakerSystems;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/** Questions about a speaker system, answered from the client's copy of the cables (for screens). */
public final class ClientSystems {
    private ClientSystems() {}

    /** The main Amplifier wired after a source, or null. */
    public static @Nullable BlockPos mainAmp(BlockPos source) {
        for (BlockPos amp : SpeakerSystems.downstream(ClientCables::endsAt, source, BlockKind.AMPLIFIER)) {
            if (!SpeakerSystems.isLineAmp(ClientCables::endsAt, amp)) {
                return amp;
            }
        }
        return null;
    }

    /** The Multi-Effects Unit after a source, or null. */
    public static @Nullable BlockPos meu(BlockPos source) {
        return SpeakerSystems.first(ClientCables::endsAt, source, BlockKind.MEU);
    }

    /** The Multi-Effects Unit linked into a Lathe's olive jack, or null. */
    public static @Nullable BlockPos meuForLathe(BlockPos lathe) {
        for (var cable : ClientCables.endsAt(lathe)) {
            if (cable.to().pos().equals(lathe) && cable.fromKind() == BlockKind.MEU) {
                return cable.from().pos();
            }
        }
        return null;
    }

    public static @Nullable BlockPos channelManager(BlockPos source) {
        return SpeakerSystems.first(ClientCables::endsAt, source, BlockKind.CHANNEL_MANAGER);
    }

    public static boolean isLineAmp(BlockPos amp) {
        return SpeakerSystems.isLineAmp(ClientCables::endsAt, amp);
    }

    /** How far a main Amplifier may boost, from the wire coming into it. */
    public static float boostCap(BlockPos amp) {
        return SpeakerSystems.boostCap(ClientCables::endsAt, amp);
    }

    /**
     * The cap a Line Amplifier works under: the main Amplifier's, or 100% without one (SCOPE.md S4).
     */
    public static float lineCap(BlockPos lineAmp) {
        BlockPos source = SpeakerSystems.sourceOf(ClientCables::endsAt, lineAmp);
        BlockPos main = source == null ? null : mainAmp(source);
        return main == null ? 1 : boostCap(main);
    }

    public static @Nullable BlockPos sourceOf(BlockPos pos) {
        return SpeakerSystems.sourceOf(ClientCables::endsAt, pos);
    }
}
