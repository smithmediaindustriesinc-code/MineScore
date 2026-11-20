package com.minescore.client.screen;

import com.minescore.client.UiTheme;
import com.minescore.client.score.ScoreFiles;
import com.minescore.score.Score;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Importing into the score editor (SCOPE.md §8.3): pick a MIDI, Note Block Studio or MineScore file from
 * {@code blkcdsongs/} or {@code minescore/} (or browse for one). Importing overwrites the score, so it first
 * offers to keep the score as it is as a copy.
 */
public class ImportScreen extends Screen {
    private static final int ROW = 14, LIST_W = 300;

    private final Screen parent;
    private final BlockPos desk;
    private List<Path> files = List.of();
    private int scroll;
    /** The file read and waiting for the copy question, or null while choosing. */
    private @Nullable Score chosen;
    private @Nullable Path chosenFile;
    private boolean busy;
    private @Nullable Component error;

    public ImportScreen(Screen parent, BlockPos desk) {
        super(Component.translatable("gui.minescore.import.title"));
        this.parent = parent;
        this.desk = desk;
    }

    private int rows() {
        return Math.max(3, (height - 100) / ROW);
    }

    @Override
    protected void init() {
        files = ScoreFiles.importable();
        int left = (width - LIST_W) / 2;
        if (chosen != null) {
            int y = height / 2 + 4;
            addRenderableWidget(Button.builder(Component.translatable("gui.minescore.import.copy_then"), b -> send(true))
                    .bounds(width / 2 - 154, y, 100, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.minescore.import.without_copy"), b -> send(false))
                    .bounds(width / 2 - 50, y, 100, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> {
                chosen = null;
                rebuildWidgets();
            }).bounds(width / 2 + 54, y, 100, 20).build());
            return;
        }
        for (int i = 0; i < rows() && scroll + i < files.size(); i++) {
            Path file = files.get(scroll + i);
            String label = font.plainSubstrByWidth(file.getParent().getFileName() + "/" + file.getFileName(), LIST_W - 8);
            Button b = addRenderableWidget(Button.builder(Component.literal(label), btn -> open(file))
                    .bounds(left, 40 + i * ROW, LIST_W, ROW - 1).build());
            b.active = !busy;
        }
        Button browse = addRenderableWidget(Button.builder(Component.translatable("gui.minescore.import.browse"), b -> {
            busy = true;
            rebuildWidgets();
            ScoreFiles.pickFile().whenCompleteAsync((path, failure) -> {
                busy = false;
                if (path != null) {
                    open(path);
                } else {
                    rebuildWidgets();
                }
            }, minecraft);
        }).bounds(width / 2 - 154, height - 28, 150, 20).build());
        browse.active = !busy;
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(width / 2 + 4, height - 28, 150, 20).build());
    }

    /** Reads a file (off the game's thread), then asks about the copy. */
    private void open(Path file) {
        busy = true;
        error = null;
        rebuildWidgets();
        CompletableFuture.supplyAsync(() -> {
            try {
                return ScoreFiles.read(file);
            } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, Util.backgroundExecutor()).whenCompleteAsync((score, failure) -> {
            busy = false;
            if (failure != null) {
                Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
                error = Component.translatable("gui.minescore.import.failed", cause.getMessage()).withStyle(ChatFormatting.RED);
            } else {
                chosen = score;
                chosenFile = file;
            }
            rebuildWidgets();
        }, minecraft);
    }

    private void send(boolean copyFirst) {
        try {
            ScoreFiles.replace(desk, chosen, copyFirst); // the editor reopens on the new score once the server has it
            minecraft.setScreen(parent);
        } catch (Exception e) {
            error = Component.translatable("gui.minescore.import.failed", e.getMessage()).withStyle(ChatFormatting.RED);
            chosen = null;
            rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 10, UiTheme.c("text"));
        if (chosen != null) {
            String name = chosenFile == null ? "" : chosenFile.getFileName().toString();
            g.drawCenteredString(font, Component.translatable("gui.minescore.import.replaces", name), width / 2, height / 2 - 30, UiTheme.c("text"));
            g.drawCenteredString(font, Component.translatable("gui.minescore.import.copy_question"), width / 2, height / 2 - 16, UiTheme.c("dim"));
        } else {
            g.drawCenteredString(font, Component.translatable(busy ? "gui.minescore.import.reading" : "gui.minescore.import.hint"), width / 2, 24, UiTheme.c("dim"));
            if (files.isEmpty()) {
                g.drawCenteredString(font, Component.translatable("gui.minescore.import.none"), width / 2, 60, UiTheme.c("dim"));
            }
        }
        if (error != null) {
            g.drawCenteredString(font, error, width / 2, height - 44, UiTheme.c("error"));
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (chosen == null) {
            scroll = Math.max(0, Math.min(Math.max(0, files.size() - rows()), scroll - (int) Math.signum(delta) * 3));
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
