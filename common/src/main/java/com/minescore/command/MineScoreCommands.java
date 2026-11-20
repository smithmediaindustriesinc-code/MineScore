package com.minescore.command;

import com.minescore.audio.DemoTrack;
import com.minescore.playback.PlaybackManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * {@code /minescore play <pos> [track]} and {@code /minescore stop <pos>}: start and stop a source until
 * the MP3 Player and Audio Mixer screens arrive. Tracks: {@code minescore:demo}, or {@code file:<name>}
 * for a WAV or OGG in each listener's own {@code blkcdsongs} folder.
 */
public final class MineScoreCommands {
    private MineScoreCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("minescore")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("play")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> play(ctx, DemoTrack.ID))
                                .then(Commands.argument("track", StringArgumentType.greedyString())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                new String[] {DemoTrack.ID, "file:"}, builder))
                                        .executes(ctx -> play(ctx, StringArgumentType.getString(ctx, "track"))))))
                .then(Commands.literal("stop")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(MineScoreCommands::stop))));
    }

    private static int play(CommandContext<CommandSourceStack> ctx, String track) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        if (!PlaybackManager.get(ctx.getSource().getLevel()).play(pos, track)) {
            ctx.getSource().sendFailure(Component.translatable("commands.minescore.not_source"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.minescore.play", track, pos.toShortString()), true);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        PlaybackManager.get(ctx.getSource().getLevel()).stop(pos);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.minescore.stop", pos.toShortString()), true);
        return 1;
    }
}
