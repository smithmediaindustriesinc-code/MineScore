package com.minescore.hub;

import com.minescore.audio.OutputChannel;
import com.minescore.wiring.Gauge;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/** Packets for the Amplifier, Channel Manager and speaker screens, and the Mixer tab's system controls. */
public final class HubPackets {
    private HubPackets() {}

    public enum Setting {
        /** f = 0..4 */
        AMP_MASTER,
        /** i = band, f = dB */
        AMP_EQ,
        AMP_EQ_RESET,
        /** f = 1 on, 0 off */
        CM_LEFT, CM_RIGHT, CM_MONO,
        /** text = new name */
        SPEAKER_NAME,
        /** i = {@link OutputChannel} ordinal */
        SPEAKER_CHANNEL,
        /** f = 0..1 */
        SPEAKER_TRIM,
        /** f = 1..4 */
        MIXER_BOOST,
        /** On a Mixer: sets this EQ band on every Amplifier in its system. i = band, f = dB */
        MIXER_EQ,
        MIXER_EQ_RESET,
        /** A hub's Redstone Control toggle (SCOPE.md §7). f = 1 on, 0 off */
        REDSTONE_CONTROL
    }

    /**
     * Client → server: change a setting on the block at {@code pos}, from the screen of the block at
     * {@code via} (the same block, or a Channel Manager or Mixer upstream of it).
     */
    public record Set(BlockPos via, BlockPos pos, Setting setting, int i, float f, String text) {
        public static Set of(BlockPos via, BlockPos pos, Setting setting, int i, float f) {
            return new Set(via, pos, setting, i, f, "");
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(via);
            buf.writeBlockPos(pos);
            buf.writeEnum(setting);
            buf.writeVarInt(i);
            buf.writeFloat(f);
            buf.writeUtf(text, 64);
        }

        public static Set read(FriendlyByteBuf buf) {
            return new Set(buf.readBlockPos(), buf.readBlockPos(), buf.readEnum(Setting.class), buf.readVarInt(), buf.readFloat(),
                    buf.readUtf(64));
        }
    }

    /**
     * One speaker in a Channel Manager's list.
     *
     * @param direct whether it's wired straight to the last hub, so has a trim of its own
     * @param range  how far it's heard right now (or would be, at the system's current volume)
     */
    public record Row(BlockPos pos, Gauge band, String name, OutputChannel channel, float trim, boolean direct, float range) {
        void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeEnum(band);
            buf.writeUtf(name, 64);
            buf.writeEnum(channel);
            buf.writeFloat(trim);
            buf.writeBoolean(direct);
            buf.writeFloat(range);
        }

        static Row read(FriendlyByteBuf buf) {
            return new Row(buf.readBlockPos(), buf.readEnum(Gauge.class), buf.readUtf(64), buf.readEnum(OutputChannel.class),
                    buf.readFloat(), buf.readBoolean(), buf.readFloat());
        }
    }

    /**
     * Server → client: a Channel Manager's speakers.
     *
     * @param open true to open its screen, false to update one already open
     */
    public record Channels(BlockPos manager, boolean open, List<Row> rows) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(manager);
            buf.writeBoolean(open);
            buf.writeCollection(rows, (b, row) -> row.write(b));
        }

        public static Channels read(FriendlyByteBuf buf) {
            return new Channels(buf.readBlockPos(), buf.readBoolean(), buf.readList(Row::read));
        }
    }
}
