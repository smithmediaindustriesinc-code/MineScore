package com.minescore.block;

import com.minescore.menu.MeuMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Client → server: a change made on a Multi-Effects Unit's screen. */
public final class MeuPackets {
    private MeuPackets() {}

    public enum Action {
        /** The Redstone Control toggle (SCOPE.md §6.4). */
        REDSTONE,
        /** slot, i = which of the card's effects */
        EFFECT,
        /** slot, i = knob, f = value */
        KNOB,
        /** slot: toggles the slot's bypass */
        BYPASS,
        /** slot: back to its effect's defaults */
        RESET_CARD,
        /** toggles the master bypass */
        MASTER,
        /** f = 0..1 */
        EXPRESSION,
        /** i = built-in preset */
        LOAD_BUILT_IN,
        /** text = name to save under (blank: the current preset's name) */
        SAVE_CHIP,
        LOAD_CHIP
    }

    public record Set(BlockPos pos, Action action, int slot, int i, float f, String text) {
        public static Set of(BlockPos pos, Action action, int slot, int i, float f) {
            return new Set(pos, action, slot, i, f, "");
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeEnum(action);
            buf.writeVarInt(slot);
            buf.writeVarInt(i);
            buf.writeFloat(f);
            buf.writeUtf(text, 64);
        }

        public static Set read(FriendlyByteBuf buf) {
            return new Set(buf.readBlockPos(), buf.readEnum(Action.class), buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
                    buf.readUtf(64));
        }
    }

    public static void handle(Set packet, ServerPlayer player) {
        if (player.containerMenu instanceof MeuMenu menu && menu.pos().equals(packet.pos())
                && player.distanceToSqr(packet.pos().getCenter()) <= 64
                && player.level().getBlockEntity(packet.pos()) instanceof MeuBlockEntity meu) {
            meu.handle(packet, player);
        }
    }
}
