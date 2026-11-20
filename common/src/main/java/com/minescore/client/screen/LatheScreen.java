package com.minescore.client.screen;

import com.minescore.client.UiTheme;
import com.minescore.MineScore;
import com.minescore.power.PowerSettings;
import com.minescore.block.MeuBlockEntity;
import com.minescore.client.ClientSongs;
import com.minescore.client.ClientSystems;
import com.minescore.meu.EffectChain;
import net.minecraft.core.BlockPos;
import com.minescore.client.audio.PreviewPlayer;
import com.minescore.client.audio.SongFiles;
import com.minescore.client.audio.TrackLibrary;
import com.minescore.item.CdData;
import com.minescore.menu.LatheMenu;
import com.minescore.mp3.Mp3Packets;
import com.minescore.network.MineScoreNetwork;
import com.minescore.registry.ModItems;
import com.minescore.song.SongPackets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

/**
 * The Disc Cutting Lathe screen. Left: the songs in this player's {@code blkcdsongs} folder. Right: the
 * record's name, the slots, Preview and Cut (or Copy, when a Vinyl Record and a Blank Vinyl are loaded).
 */
public class LatheScreen extends AbstractContainerScreen<LatheMenu> {
    private static final ResourceLocation TEXTURE = MineScore.id("textures/gui/disc_cutting_lathe.png");
    /** The FE gauge's well in the texture, and how much the Lathe holds. */
    private static final int ENERGY_X = 236, ENERGY_Y = 48, ENERGY_H = 48, ENERGY_CAPACITY = 4_000;
    private static final int LIST_X = 8, LIST_Y = 46, LIST_W = 106, LIST_ROWS = 7, ROW_H = 10;
    private static final int SCROLL_X = 116;
    /** The sort buttons above the list: each column's left edge, then the right edge of the last. */
    private static final int HEADER_Y = 34, HEADER_H = 11;
    private static final int[] HEADER_X = {8, 44, 72, 96, 123};

    /** What the list can be sorted by. Name and Type start A–Z; Date and Size start newest and biggest first. */
    private enum Sort {
        NAME(false), DATE(true), TYPE(false), SIZE(true);

        final boolean startsDescending;

        Sort(boolean startsDescending) {
            this.startsDescending = startsDescending;
        }
    }

    /** A song file and what the sort buttons need to know about it. */
    private record Entry(Path path, String name, String type, long modified, long size) {
        static Entry of(Path path) {
            long modified = 0;
            long size = 0;
            try {
                modified = Files.getLastModifiedTime(path).toMillis();
                size = Files.size(path);
            } catch (IOException ignored) {
                // listed anyway; it just sorts as old and empty
            }
            return new Entry(path, path.getFileName().toString(), SongFiles.extension(path), modified, size);
        }
    }

    /** Everything in the folder, and the part of it matching the search, in the chosen order. */
    private final List<Entry> allFiles = new ArrayList<>();
    private final List<Entry> files = new ArrayList<>();
    private static Sort sort = Sort.NAME;
    private static boolean descending;
    private int scroll;
    private @Nullable Path selected;
    private EditBox search;
    private EditBox name;
    private Button preview;
    private Button cut;
    /** Set while the chosen file is being read for a cut. */
    private @Nullable String preparing;
    private @Nullable Component error;

    public LatheScreen(LatheMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 256;
        imageHeight = 222;
        inventoryLabelX = LatheMenu.INVENTORY_X;
        inventoryLabelY = LatheMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        ClientSongs.INSTANCE.clearStatus();
        search = new EditBox(font, leftPos + 9, topPos + 19, 113, 11, Component.translatable("gui.minescore.search"));
        search.setHint(Component.translatable("gui.minescore.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setMaxLength(64);
        search.setResponder(text -> filter());
        addRenderableWidget(search);
        name = new EditBox(font, leftPos + 132, topPos + 28, 116, 14, Component.translatable("gui.minescore.lathe.name"));
        name.setMaxLength(64);
        addRenderableWidget(name);
        preview = addRenderableWidget(Button.builder(Component.translatable("gui.minescore.lathe.preview"), b -> togglePreview())
                .bounds(leftPos + 130, topPos + 100, 58, 18).build());
        cut = addRenderableWidget(Button.builder(Component.translatable("gui.minescore.lathe.cut"), b -> cutOrCopy())
                .bounds(leftPos + 192, topPos + 100, 58, 18).build());
        refreshFiles();
    }

    private void refreshFiles() {
        allFiles.clear();
        try (Stream<Path> list = Files.list(TrackLibrary.songsDir())) {
            list.filter(Files::isRegularFile)
                    .filter(p -> SongFiles.CUTTABLE.contains(SongFiles.extension(p)))
                    .map(Entry::of)
                    .forEach(allFiles::add);
        } catch (IOException e) {
            MineScore.LOGGER.warn("Couldn't list blkcdsongs", e);
        }
        filter();
    }

    /** Shows the files whose names contain every word typed in the search box. */
    private void filter() {
        String[] words = search.getValue().toLowerCase(Locale.ROOT).trim().split("\\s+");
        files.clear();
        for (Entry file : allFiles) {
            String fileName = file.name().toLowerCase(Locale.ROOT);
            boolean matches = true;
            for (String word : words) {
                matches &= fileName.contains(word);
            }
            if (matches) {
                files.add(file);
            }
        }
        Comparator<Entry> byName = Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT));
        Comparator<Entry> order = switch (sort) {
            case NAME -> byName;
            case DATE -> Comparator.comparingLong(Entry::modified);
            case TYPE -> Comparator.comparing(Entry::type);
            case SIZE -> Comparator.comparingLong(Entry::size);
        };
        if (descending) {
            order = order.reversed();
        }
        files.sort(order.thenComparing(byName)); // ties stay alphabetical
        scroll = 0;
    }

    /** A sort button was clicked: sort by it, or flip the order if it's already the one in use. */
    private void sortBy(Sort column) {
        if (sort == column) {
            descending = !descending;
        } else {
            sort = column;
            descending = column.startsDescending;
        }
        filter();
    }

    /** The detail shown at the right of each row for the current sort. */
    private static String detail(Entry e) {
        return switch (sort) {
            case NAME, TYPE -> e.type().toUpperCase(Locale.ROOT);
            case DATE -> ShortDate.format(e.modified());
            case SIZE -> size(e.size());
        };
    }

    private static String size(long bytes) {
        if (bytes >= 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1fM", bytes / (1024.0 * 1024));
        }
        return Math.max(1, bytes / 1024) + "K";
    }

    /** Short dates: the time for today, then the day and month, then the year for older files. */
    private static final class ShortDate {
        static String format(long millis) {
            java.time.LocalDateTime then = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis),
                    java.time.ZoneId.systemDefault());
            java.time.LocalDate today = java.time.LocalDate.now();
            String pattern = then.toLocalDate().equals(today) ? "HH:mm" : then.getYear() == today.getYear() ? "d MMM" : "MMM yy";
            return then.format(java.time.format.DateTimeFormatter.ofPattern(pattern, Locale.ROOT));
        }
    }

    /** A Score and a Blank Vinyl: the button renders the score and cuts it. */
    private boolean scoreCutting() {
        return menu.input().is(ModItems.SCORE.get()) && menu.extra().is(ModItems.BLANK_CD.get());
    }

    private boolean copying() {
        return menu.input().is(ModItems.ENCODED_CD.get()) && menu.extra().is(ModItems.BLANK_CD.get());
    }

    /** A Vinyl Record and an MP3 Player: the button adds the record's song to the player. */
    private boolean loading() {
        return menu.input().is(ModItems.ENCODED_CD.get()) && menu.extra().is(ModItems.MP3_PLAYER.get());
    }

    private @Nullable Path selectedFile() {
        return selected == null || !Files.exists(selected) ? null : selected;
    }

    private void select(Path file) {
        selected = file;
        name.setValue(SongFiles.title(file));
        error = null;
    }

    private void togglePreview() {
        Path file = selectedFile();
        if (file != null) {
            PreviewPlayer.toggle("file:" + file.getFileName());
        } else if (PreviewPlayer.active()) {
            PreviewPlayer.stop();
        }
    }

    private void cutOrCopy() {
        error = null;
        if (loading()) {
            MineScoreNetwork.sendToServer(new Mp3Packets.LoadIntoPlayer(menu.pos()));
            return;
        }
        if (copying()) {
            MineScoreNetwork.sendToServer(new SongPackets.Copy(menu.pos(), name.getValue()));
            return;
        }
        if (scoreCutting()) { // the server sends the score back to render; the cut starts once it's rendered
            MineScoreNetwork.sendToServer(new com.minescore.score.ScorePackets.Render(menu.pos(), com.minescore.score.ScorePackets.Purpose.LATHE_CUT));
            return;
        }
        Path file = selectedFile();
        if (file == null || preparing != null) {
            return;
        }
        String title = name.getValue().isBlank() ? SongFiles.title(file) : name.getValue().strip();
        EffectChain effects = printedEffects();
        preparing = "";
        CompletableFuture.supplyAsync(() -> {
            try {
                return SongFiles.prepare(file, title, effects);
            } catch (IOException | RuntimeException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, Util.backgroundExecutor()).whenCompleteAsync((prepared, failure) -> {
            preparing = null;
            if (failure != null) {
                Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
                error = Component.translatable("msg.minescore.lathe.failed", cause.getMessage()).withStyle(ChatFormatting.RED);
                return;
            }
            ClientSongs.INSTANCE.prepare(prepared.meta(), prepared.payload());
            MineScoreNetwork.sendToServer(new SongPackets.Cut(menu.pos(), prepared.meta(), prepared.payload().length, title,
                    prepared.loudness().encode()));
        }, minecraft);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        search.tick();
        name.tick();
        boolean copy = copying();
        boolean load = loading();
        cut.setMessage(Component.translatable(load ? "gui.minescore.lathe.load" : copy ? "gui.minescore.lathe.copy" : "gui.minescore.lathe.cut"));
        boolean busy = menu.progress() >= 0 || preparing != null;
        cut.active = load || (!busy && menu.output().isEmpty()
                && (copy || scoreCutting() || (selectedFile() != null && menu.input().is(ModItems.BLANK_CD.get()))));
        Path file = selectedFile();
        boolean previewing = file != null && PreviewPlayer.playing("file:" + file.getFileName());
        preview.setMessage(Component.translatable(previewing || (file == null && PreviewPlayer.active())
                ? "gui.minescore.lathe.stop" : "gui.minescore.lathe.preview"));
        preview.active = file != null || PreviewPlayer.active();
        if (copy && name.getValue().isEmpty()) {
            CdData data = CdData.read(menu.input());
            if (data != null) {
                name.setValue(data.title());
            }
        }
    }

    @Override
    public void removed() {
        super.removed();
        PreviewPlayer.stop();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        float progress = menu.progress();
        if (progress > 0) {
            g.blit(TEXTURE, leftPos + 168, topPos + 62, 0, 224, Math.round(24 * progress), 16);
        }
        if (menu.energyMode()) { // the FE gauge, filling from the bottom
            int filled = Math.round(ENERGY_H * Math.min(1f, menu.energy() / (float) ENERGY_CAPACITY));
            g.fill(leftPos + ENERGY_X, topPos + ENERGY_Y + ENERGY_H - filled, leftPos + ENERGY_X + 8, topPos + ENERGY_Y + ENERGY_H, UiTheme.c("energy"));
            if (filled > 0) {
                g.fill(leftPos + ENERGY_X, topPos + ENERGY_Y + ENERGY_H - filled, leftPos + ENERGY_X + 2, topPos + ENERGY_Y + ENERGY_H, UiTheme.c("energy_light"));
            }
        }
        // Sort buttons
        for (Sort column : Sort.values()) {
            int x1 = leftPos + HEADER_X[column.ordinal()];
            int x2 = leftPos + HEADER_X[column.ordinal() + 1] - 1;
            int y1 = topPos + HEADER_Y;
            boolean hover = mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y1 + HEADER_H;
            g.fill(x1, y1, x2, y1 + HEADER_H, column == sort ? UiTheme.c("list_pick") : hover ? UiTheme.c("list_header_hover") : UiTheme.c("list_header"));
            String label = Component.translatable("gui.minescore.lathe.sort." + column.name().toLowerCase(Locale.ROOT)).getString()
                    + (column == sort ? (descending ? "▼" : "▲") : "");
            g.drawString(font, font.plainSubstrByWidth(label, x2 - x1 - 2), x1 + 2, y1 + 2, column == sort ? UiTheme.c("list_text_on") : UiTheme.c("list_header_text"),
                    false);
        }
        // Song list
        for (int row = 0; row < LIST_ROWS; row++) {
            int index = scroll + row;
            if (index >= files.size()) {
                break;
            }
            int y = topPos + LIST_Y + row * ROW_H;
            boolean hover = mouseX >= leftPos + LIST_X && mouseX < leftPos + LIST_X + LIST_W && mouseY >= y && mouseY < y + ROW_H;
            Entry entry = files.get(index);
            Path file = entry.path();
            String extra = detail(entry);
            int extraWidth = font.width(extra);
            if (file.equals(selected)) {
                g.fill(leftPos + LIST_X, y, leftPos + LIST_X + LIST_W, y + ROW_H, UiTheme.c("list_pick"));
            } else if (hover) {
                g.fill(leftPos + LIST_X, y, leftPos + LIST_X + LIST_W, y + ROW_H, UiTheme.c("list_row"));
            }
            String label = font.plainSubstrByWidth(SongFiles.title(file), LIST_W - 8 - extraWidth);
            g.drawString(font, label, leftPos + LIST_X + 2, y + 1, file.equals(selected) ? UiTheme.c("list_text_on") : UiTheme.c("list_text"), false);
            g.drawString(font, extra, leftPos + LIST_X + LIST_W - 2 - extraWidth, y + 1,
                    file.equals(selected) ? UiTheme.c("list_dim_on") : UiTheme.c("list_dim"), false);
        }
        if (files.isEmpty()) {
            Component empty = Component.translatable(allFiles.isEmpty() ? "gui.minescore.lathe.empty_folder" : "gui.minescore.no_matches");
            g.drawWordWrap(font, empty, leftPos + LIST_X + 2, topPos + LIST_Y + 2, LIST_W - 4, UiTheme.c("list_empty"));
        }
        int maxScroll = Math.max(0, files.size() - LIST_ROWS);
        int thumb = maxScroll == 0 ? 0 : Math.round((LIST_ROWS * ROW_H - 15) * (float) scroll / maxScroll);
        g.blit(TEXTURE, leftPos + SCROLL_X, topPos + LIST_Y + thumb, 24, 224, 6, 15);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, UiTheme.c("label"), false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, UiTheme.c("label"), false);
        g.drawString(font, Component.translatable("gui.minescore.lathe.name"), 132, 18, UiTheme.c("label"), false);
        g.drawString(font, Component.translatable(copying() ? "gui.minescore.lathe.copy_from"
                        : menu.input().is(ModItems.SCORE.get()) ? "gui.minescore.lathe.score" : "gui.minescore.lathe.blank"),
                156, 54, UiTheme.c("label"), false);
        g.drawString(font, Component.translatable(loading() ? "gui.minescore.lathe.load_into" : "gui.minescore.lathe.copy_to"),
                156, 80, UiTheme.c("label"), false);
        Component status = statusLine();
        if (status != null) {
            g.drawWordWrap(font, status, 130, 121, 122, UiTheme.c("label"));
        }
    }

    /** The effects of the Multi-Effects Unit linked to this Lathe, if any: they get printed onto the record. */
    private @Nullable EffectChain printedEffects() {
        BlockPos meuPos = ClientSystems.meuForLathe(menu.pos());
        return meuPos != null && minecraft.level.getBlockEntity(meuPos) instanceof MeuBlockEntity meu ? meu.chain() : null;
    }

    private @Nullable Component statusLine() {
        if (error != null) {
            return error;
        }
        if (preparing != null) {
            return Component.translatable("msg.minescore.lathe.cutting", 0); // reading the file; the upload runs unseen
        }
        float progress = menu.progress();
        if (progress >= 0) {
            if (menu.waitingForPower() && !menu.kinetic()) {
                return Component.translatable("msg.minescore.lathe.waiting_power", PowerSettings.latheCost.getAsInt());
            }
            return Component.translatable("msg.minescore.lathe.cutting", Math.round(progress * 100));
        }
        String key = ClientSongs.INSTANCE.status(menu.pos());
        if (key != null) {
            return Component.translatable(key);
        }
        EffectChain effects = printedEffects();
        if (effects != null && !effects.passesThrough() && !copying() && !loading()) {
            return Component.translatable("msg.minescore.lathe.prints", effects.activeCount(), 10 + 2 * effects.changedEffects());
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = mouseX - leftPos - LIST_X;
        double y = mouseY - topPos - LIST_Y;
        if (x >= 0 && x < LIST_W && y >= 0 && y < LIST_ROWS * ROW_H) {
            int index = scroll + (int) (y / ROW_H);
            if (index < files.size()) {
                select(files.get(index).path());
                return true;
            }
        }
        double hy = mouseY - topPos - HEADER_Y;
        if (hy >= 0 && hy < HEADER_H) {
            for (Sort column : Sort.values()) {
                if (mouseX >= leftPos + HEADER_X[column.ordinal()] && mouseX < leftPos + HEADER_X[column.ordinal() + 1] - 1) {
                    sortBy(column);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = Math.max(0, files.size() - LIST_ROWS);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        for (EditBox box : new EditBox[] {search, name}) {
            if (box.isFocused() && key != 256) { // let Escape still close the screen
                return box.keyPressed(key, scanCode, modifiers) || box.canConsumeInput();
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        double x = mouseX - leftPos - LIST_X;
        double y = mouseY - topPos - LIST_Y;
        int index = scroll + (int) Math.floor(y / ROW_H);
        if (x >= 0 && x < LIST_W && y >= 0 && y < LIST_ROWS * ROW_H && index < files.size()) {
            Entry e = files.get(index); // the full details of the song under the mouse
            g.renderComponentTooltip(font, List.of(Component.literal(e.name()),
                    Component.translatable("gui.minescore.lathe.details", e.type().toUpperCase(Locale.ROOT), size(e.size()) + "B",
                            ShortDate.format(e.modified())).withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
        if (menu.energyMode() && Mp3Screen.inside(mouseX, mouseY, leftPos + ENERGY_X, topPos + ENERGY_Y, 8, ENERGY_H)) {
            g.renderComponentTooltip(font, List.of(Component.translatable("gui.minescore.power.stored", menu.energy(), ENERGY_CAPACITY),
                    Component.translatable("gui.minescore.lathe.power_use", PowerSettings.latheCost.getAsInt())
                            .withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }
}
