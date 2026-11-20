package com.minescore;

import com.minescore.block.MusicCabinetBlock;
import com.minescore.block.RedstoneHub;
import com.minescore.block.SpeakerBlockEntity;
import com.minescore.block.VinylDisplayBlockEntity;
import com.minescore.block.VinylPlayerBlockEntity;
import com.minescore.block.WritersDeskBlockEntity;
import com.minescore.item.ScoreItem;
import com.minescore.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What Jade and The One Probe show for MineScore's blocks (SCOPE.md §14, M12 stage 5): a few lines from
 * the block and its block entity, using only what clients are sent too, so Jade can read it on the client
 * and The One Probe on the server.
 */
public final class BlockInfo {
    private BlockInfo() {}

    public static List<Component> lines(Level level, BlockPos pos, BlockState state) {
        List<Component> lines = new ArrayList<>();
        BlockEntity be = level.getBlockEntity(pos);
        if (state.is(ModBlocks.SUBWOOFER.get()) || state.is(ModBlocks.MIDDLE_SPEAKER.get()) || state.is(ModBlocks.TWEETER_SPEAKER.get())) {
            String band = state.is(ModBlocks.SUBWOOFER.get()) ? "bass" : state.is(ModBlocks.MIDDLE_SPEAKER.get()) ? "mid" : "treble";
            lines.add(Component.translatable("info.minescore.band." + band).withStyle(ChatFormatting.GRAY));
            if (be instanceof SpeakerBlockEntity speaker) {
                if (!speaker.name().isBlank()) {
                    lines.add(Component.literal(speaker.name()).withStyle(ChatFormatting.WHITE));
                }
                lines.add(Component.translatable("info.minescore.channel",
                        Component.translatable("info.minescore.channel." + speaker.channel().name().toLowerCase(java.util.Locale.ROOT)))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        if (be instanceof VinylPlayerBlockEntity player && !player.disc().isEmpty()) {
            lines.add(Component.translatable(player.playing() ? "info.minescore.playing" : "info.minescore.record",
                    player.disc().getHoverName()).withStyle(ChatFormatting.AQUA));
        }
        if (be instanceof VinylDisplayBlockEntity display && !display.disc().isEmpty()) {
            lines.add(Component.translatable("info.minescore.showing", display.disc().getHoverName()).withStyle(ChatFormatting.AQUA));
        }
        if (state.getBlock() instanceof MusicCabinetBlock) {
            lines.add(Component.translatable("info.minescore.cabinet", state.getValue(MusicCabinetBlock.SCORES), MusicCabinetBlock.SCORE_SLOTS,
                    state.getValue(MusicCabinetBlock.VINYLS), MusicCabinetBlock.VINYL_SLOTS).withStyle(ChatFormatting.GRAY));
        }
        if (be instanceof WritersDeskBlockEntity desk) {
            ScoreItem.Data score = desk.score();
            if (score != null) {
                lines.add(Component.translatable("info.minescore.score", score.title().isBlank()
                        ? Component.translatable("item.minescore.score.untitled") : Component.literal(score.title()))
                        .withStyle(ChatFormatting.AQUA));
            }
        }
        if (be instanceof com.minescore.power.PowerSource source && source.power().budget() > 0) {
            lines.add(Component.translatable("info.minescore.power", source.power().budget()).withStyle(ChatFormatting.GOLD));
        }
        if (be instanceof RedstoneHub hub) {
            lines.add(Component.translatable(hub.redstoneControl() ? "info.minescore.redstone.on" : "info.minescore.redstone.off")
                    .withStyle(ChatFormatting.DARK_RED));
        }
        return lines;
    }
}
