package com.minescore.client.screen;

import com.minescore.client.UiTheme;
import com.minescore.MineScore;
import com.minescore.audio.Equalizer;
import com.minescore.block.AmplifierBlockEntity;
import com.minescore.block.ChannelManagerBlockEntity;
import com.minescore.block.MixerBlockEntity;
import com.minescore.client.ClientPlayback;
import com.minescore.client.ClientSystems;
import com.minescore.hub.HubPackets;
import com.minescore.hub.HubPackets.Setting;
import com.minescore.item.Mp3PlayerItem;
import com.minescore.mp3.Mp3Packets;
import com.minescore.mp3.Mp3Packets.Action;
import com.minescore.mp3.Mp3State;
import com.minescore.network.MineScoreNetwork;
import com.minescore.playback.Playback;
import com.minescore.song.SongMeta;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The MP3 Player's screen: Now Playing, Library, Playlists and EQ, plus a Mixer tab when it's docked in an
 * Audio Mixer. Every button is sent to the server, which updates the player; this screen just shows it.
 *
 * <p>Everything sits inside the display (y 26–174); the strip below it holds each tab's buttons.
 */
public class Mp3Screen extends Screen {
    private static final ResourceLocation TEXTURE = MineScore.id("textures/gui/mp3_player.png");
    private static final int W = 256, H = 200;
    private static final int ROW = 12;
    private static final int STRIP_Y = 177;

    // Now Playing: the "Up next" list
    private static final int NEXT_Y = 102, NEXT_ROWS = 5;
    // Library: search box, then the song list
    private static final int LIB_Y = 46, LIB_ROWS = 10, LIB_W = 228;
    // Playlists: names on the left, the chosen one's songs on the right
    private static final int PL_Y = 30, PL_ROWS = 11, PL_W = 80, PL_SONGS_X = 98, PL_SONGS_W = 142;
    // The "Add to" dropdown, opening upward from its button
    private static final int ADD_X = 118, ADD_W = 126, DROP_ROWS = 8;

    private enum Tab { NOW, LIBRARY, PLAYLISTS, EQ, MIXER }

    private final Mp3Packets.Target target;
    private Tab tab = Tab.NOW;
    private int left, top;
    private int scroll, scroll2;
    private @Nullable String selectedSong;
    private @Nullable String selectedPlaylist;
    private @Nullable EditBox search;
    private @Nullable EditBox playlistName;
    private boolean dropdown;
    private int dropdownScroll;
    private String lastStateKey = "";

    public Mp3Screen(Mp3Packets.Target target) {
        super(Component.translatable("item.minescore.mp3_player"));
        this.target = target;
    }

    private boolean docked() {
        return !target.inInventory();
    }

    /** The player's current state, as the server last sent it; null once it's gone. */
    private @Nullable Mp3State state() {
        if (minecraft == null || minecraft.player == null) {
            return null;
        }
        if (docked()) {
            return minecraft.level != null && minecraft.level.getBlockEntity(target.mixer()) instanceof MixerBlockEntity mixer
                    && target.player().equals(Mp3State.idOf(mixer.mp3())) ? Mp3State.read(mixer.mp3()) : null;
        }
        Inventory inventory = minecraft.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof Mp3PlayerItem && target.player().equals(Mp3State.idOf(stack))) {
                return Mp3State.read(stack);
            }
        }
        return null;
    }

    private long now() {
        return minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0;
    }

    private void send(Action action, int i, float f, String a, String b) {
        MineScoreNetwork.sendToServer(new Mp3Packets.Command(target, action, i, f, a, b));
    }

    private void send(Action action) {
        send(action, 0, 0, "", "");
    }

    /** A setting on a block in the Mixer's system (its boost, the amps' EQ, the Channel Manager's switches). */
    private void sendHub(BlockPos pos, Setting setting, int i, float f) {
        MineScoreNetwork.sendToServer(HubPackets.Set.of(target.mixer(), pos, setting, i, f));
    }

    private <T> @Nullable T blockEntity(@Nullable BlockPos pos, Class<T> type) {
        if (pos == null || minecraft == null || minecraft.level == null) {
            return null;
        }
        var be = minecraft.level.getBlockEntity(pos);
        return type.isInstance(be) ? type.cast(be) : null;
    }

    private @Nullable BlockPos mainAmp() {
        return docked() ? ClientSystems.mainAmp(target.mixer()) : null;
    }

    private @Nullable BlockPos channelManager() {
        return docked() ? ClientSystems.channelManager(target.mixer()) : null;
    }

    // --- layout --------------------------------------------------------------------------------

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        clearWidgets();
        int x = left + 8;
        for (Tab t : Tab.values()) {
            if (t == Tab.MIXER && !docked()) {
                continue;
            }
            Button b = addRenderableWidget(Button.builder(Component.translatable("gui.minescore.mp3.tab." + t.name().toLowerCase(Locale.ROOT)),
                    btn -> {
                        tab = t;
                        scroll = 0;
                        scroll2 = 0;
                        dropdown = false;
                        setFocused(null);
                        init();
                    }).bounds(x, top + 6, 46, 16).build());
            b.active = t != tab;
            x += 48;
        }
        Mp3State s = state();
        if (s == null) {
            return;
        }
        switch (tab) {
            case NOW -> initNow(s);
            case LIBRARY -> initLibrary(s);
            case PLAYLISTS -> initPlaylists(s);
            case EQ -> initEq(s);
            case MIXER -> initMixer(s);
        }
    }

    private void initNow(Mp3State s) {
        int y = top + 66;
        addRenderableWidget(Button.builder(Component.translatable(s.shuffle ? "gui.minescore.mp3.shuffle_on" : "gui.minescore.mp3.shuffle_off"),
                b -> send(Action.SHUFFLE)).bounds(left + 12, y, 52, 18).build());
        addRenderableWidget(Button.builder(Component.literal("⏮"), b -> send(Action.PREVIOUS)).bounds(left + 68, y, 30, 18).build());
        addRenderableWidget(Button.builder(Component.literal(s.playing ? "⏸" : "▶"), b -> send(Action.TOGGLE))
                .bounds(left + 102, y, 52, 18).build());
        addRenderableWidget(Button.builder(Component.literal("⏭"), b -> send(Action.NEXT)).bounds(left + 158, y, 30, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.minescore.mp3.repeat." + s.repeat.name().toLowerCase(Locale.ROOT)),
                b -> send(Action.REPEAT)).bounds(left + 192, y, 52, 18).build());
        addRenderableWidget(volumeSlider(left + 12, top + STRIP_Y, 232, s));
    }

    private void initLibrary(Mp3State s) {
        if (search == null) {
            search = new EditBox(font, 0, 0, 230, 11, Component.translatable("gui.minescore.search"));
            search.setMaxLength(64);
            search.setHint(Component.translatable("gui.minescore.search").withStyle(ChatFormatting.DARK_GRAY));
            search.setResponder(text -> scroll = 0);
        }
        search.setPosition(left + 13, top + 31);
        addRenderableWidget(search);
        int y = top + STRIP_Y;
        addRenderableWidget(Button.builder(Component.translatable("gui.minescore.mp3.play"), b -> {
            if (selectedSong != null) {
                send(Action.PLAY_SONG, 0, 0, selectedSong, "");
            }
        }).bounds(left + 12, y, 48, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.minescore.mp3.remove"), b -> {
            if (selectedSong != null) {
                send(Action.REMOVE_SONG, 0, 0, selectedSong, "");
                selectedSong = null;
            }
        }).bounds(left + 62, y, 52, 18).build());
        Button add = addRenderableWidget(Button.builder(Component.translatable("gui.minescore.mp3.add_to_menu"), b -> {
            dropdown = !dropdown;
            dropdownScroll = 0;
        }).bounds(left + ADD_X, y, ADD_W, 18).build());
        add.active = selectedSong != null;
    }

    private void initPlaylists(Mp3State s) {
        int y = top + STRIP_Y;
        if (playlistName == null) {
            playlistName = new EditBox(font, 0, 0, 76, 16, Component.translatable("gui.minescore.mp3.playlist_name"));
            playlistName.setMaxLength(32);
            playlistName.setHint(Component.translatable("gui.minescore.mp3.playlist_name").withStyle(ChatFormatting.DARK_GRAY));
        }
        playlistName.setPosition(left + 12, y + 1);
        addRenderableWidget(playlistName);
        addRenderableWidget(Button.builder(Component.translatable("gui.minescore.mp3.new"), b -> {
            if (!playlistName.getValue().isBlank()) {
                send(Action.PLAYLIST_CREATE, 0, 0, playlistName.getValue(), "");
                selectedPlaylist = playlistName.getValue().strip();
                playlistName.setValue("");
            }
        }).bounds(left + 90, y, 36, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.minescore.mp3.delete"), b -> {
            if (selectedPlaylist != null) {
                send(Action.PLAYLIST_DELETE, 0, 0, selectedPlaylist, "");
                selectedPlaylist = null;
            }
        }).bounds(left + 128, y, 40, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.minescore.mp3.play"), b -> {
            if (selectedPlaylist != null) {
                send(Action.PLAYLIST_PLAY, 0, 0, selectedPlaylist, selectedSong == null ? "" : selectedSong);
            }
        }).bounds(left + 170, y, 34, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.minescore.mp3.take_out"), b -> {
            if (selectedPlaylist != null && selectedSong != null) {
                send(Action.PLAYLIST_REMOVE, 0, 0, selectedPlaylist, selectedSong);
            }
        }).bounds(left + 206, y, 38, 18).build());
    }

    /**
     * Carried, the EQ is the player's own. Docked, its sound goes through the system, so the EQ tab sets
     * every Amplifier in it instead (and shows the main one's).
     */
    private void initEq(Mp3State s) {
        AmplifierBlockEntity amp = docked() ? blockEntity(mainAmp(), AmplifierBlockEntity.class) : null;
        float[] gains = docked() ? (amp == null ? new float[Mp3State.EQ_BANDS] : amp.settingEq().gains()) : s.eq;
        boolean usable = !docked() || amp != null;
        for (int band = 0; band < Mp3State.EQ_BANDS; band++) {
            int column = band / 5;
            int row = band % 5;
            int b = band;
            String freq = Equalizer.FREQUENCIES[band] >= 1000
                    ? (int) (Equalizer.FREQUENCIES[band] / 1000) + " kHz" : (int) Equalizer.FREQUENCIES[band] + " Hz";
            ValueSlider slider = new ValueSlider(left + 12 + column * 118, top + 46 + row * 24, 114,
                    (gains[band] + Mp3State.EQ_RANGE) / (2 * Mp3State.EQ_RANGE),
                    v -> Component.literal(String.format("%s: %+.1f dB", freq, v * 2 * Mp3State.EQ_RANGE - Mp3State.EQ_RANGE)),
                    v -> {
                        float db = (float) (v * 2 * Mp3State.EQ_RANGE - Mp3State.EQ_RANGE);
                        if (docked()) {
                            sendHub(target.mixer(), Setting.MIXER_EQ, b, db);
                        } else {
                            send(Action.EQ, b, db, "", "");
                        }
                    });
            slider.active = usable;
            addRenderableWidget(slider);
        }
        Button reset = addRenderableWidget(Button.builder(Component.translatable("gui.minescore.mp3.eq_reset"), b -> {
            if (docked()) {
                sendHub(target.mixer(), Setting.MIXER_EQ_RESET, 0, 0);
            } else {
                send(Action.EQ_RESET);
            }
            minecraft.tell(this::init);
        }).bounds(left + 12, top + STRIP_Y, 60, 18).build());
        reset.active = usable;
    }

    private void initMixer(Mp3State s) {
        addRenderableWidget(volumeSlider(left + 12, top + 32, 232, s));
        BlockPos amp = mainAmp();
        MixerBlockEntity mixer = blockEntity(target.mixer(), MixerBlockEntity.class);
        if (amp != null && mixer != null) {
            float cap = ClientSystems.boostCap(amp);
            ValueSlider boost = new ValueSlider(left + 12, top + 54, 232, cap <= 1 ? 0 : (Math.min(mixer.boost(), cap) - 1) / (cap - 1),
                    v -> Component.translatable("gui.minescore.mixer.boost_value", Math.round((1 + v * (cap - 1)) * 100),
                            Math.round(cap * 100)),
                    v -> sendHub(target.mixer(), Setting.MIXER_BOOST, 0, (float) (1 + v * (cap - 1))));
            boost.active = cap > 1;
            addRenderableWidget(boost);
        }
        ChannelManagerBlockEntity manager = blockEntity(channelManager(), ChannelManagerBlockEntity.class);
        if (manager != null) {
            BlockPos pos = manager.getBlockPos();
            int y = top + 116;
            addRenderableWidget(Button.builder(onOff("gui.minescore.channel.left", manager.leftOn()),
                    b -> sendHub(pos, Setting.CM_LEFT, 0, manager.leftOn() ? 0 : 1)).bounds(left + 12, y, 74, 18).build());
            addRenderableWidget(Button.builder(onOff("gui.minescore.channel.right", manager.rightOn()),
                    b -> sendHub(pos, Setting.CM_RIGHT, 0, manager.rightOn() ? 0 : 1)).bounds(left + 91, y, 74, 18).build());
            addRenderableWidget(Button.builder(Component.translatable(manager.forceMono()
                            ? "gui.minescore.channel.mono_forced" : "gui.minescore.channel.mono_auto"),
                    b -> sendHub(pos, Setting.CM_MONO, 0, manager.forceMono() ? 0 : 1)).bounds(left + 170, y, 74, 18).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.minescore.mixer.eject"), b -> {
            send(Action.EJECT);
            onClose();
        }).bounds(left + 12, top + STRIP_Y, 100, 18).build());
        if (mixer != null) {
            addRenderableWidget(Button.builder(onOff("gui.minescore.redstone", mixer.redstoneControl()), b -> {
                boolean on = !mixer.redstoneControl();
                sendHub(target.mixer(), Setting.REDSTONE_CONTROL, 0, on ? 1 : 0);
                b.setMessage(onOff("gui.minescore.redstone", on));
            }).bounds(left + 116, top + STRIP_Y, 128, 18).tooltip(Tooltip.create(Component.translatable("gui.minescore.redstone.mixer"))).build());
        }
    }

    static Component onOff(String key, boolean on) {
        return Component.translatable(key, Component.translatable(on ? "gui.minescore.on" : "gui.minescore.off"));
    }

    private ValueSlider volumeSlider(int x, int y, int w, Mp3State s) {
        return new ValueSlider(x, y, w, s.volume, v -> Component.translatable("gui.minescore.mp3.volume", Math.round(v * 100)),
                v -> send(Action.VOLUME, 0, (float) v, "", ""));
    }

    // --- lists ---------------------------------------------------------------------------------

    /** Library songs whose titles contain every word in the search box. */
    private List<SongMeta> librarySongs(Mp3State s) {
        String query = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT).trim();
        if (query.isEmpty()) {
            return s.library;
        }
        List<SongMeta> result = new ArrayList<>();
        for (SongMeta song : s.library) {
            String title = song.title().toLowerCase(Locale.ROOT);
            boolean matches = true;
            for (String word : query.split("\\s+")) {
                matches &= title.contains(word);
            }
            if (matches) {
                result.add(song);
            }
        }
        return result;
    }

    private List<SongMeta> playlistSongs(Mp3State s) {
        return s.playlist(selectedPlaylist == null ? "" : selectedPlaylist)
                .map(p -> p.songs.stream().map(s::song).flatMap(Optional::stream).toList()).orElse(List.of());
    }

    private static int clampScroll(int scroll, int total, int visible) {
        return Math.max(0, Math.min(scroll, total - visible));
    }

    // --- drawing ------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.blit(TEXTURE, left, top, 0, 0, W, H);
        Mp3State s = state();
        if (s == null) {
            onClose();
            return;
        }
        boolean underDropdown = dropdown && insideDropdown(s, mouseX, mouseY);
        int mx = underDropdown ? -1 : mouseX; // no hover highlights under the open dropdown
        switch (tab) {
            case NOW -> renderNow(g, s, mx, mouseY);
            case LIBRARY -> {
                List<SongMeta> songs = librarySongs(s);
                scroll = clampScroll(scroll, songs.size(), LIB_ROWS);
                renderSongs(g, songs, left + 12, top + LIB_Y, LIB_W, LIB_ROWS, scroll, mx, mouseY, s,
                        s.library.isEmpty() ? "gui.minescore.mp3.empty_library" : "gui.minescore.no_matches");
                ValueSlider.scrollbar(g, left + 241, top + LIB_Y, LIB_ROWS * ROW, scroll, LIB_ROWS, songs.size());
            }
            case PLAYLISTS -> renderPlaylists(g, s, mx, mouseY);
            case EQ -> {
                g.drawString(font, Component.translatable(docked() ? "gui.minescore.mp3.eq_title_docked" : "gui.minescore.mp3.eq_title"),
                        left + 12, top + 32, UiTheme.c("device_text"), false);
                if (docked() && mainAmp() == null) {
                    g.drawWordWrap(font, Component.translatable("gui.minescore.mp3.eq_needs_amp"), left + 80, top + 176, 164, UiTheme.c("device_dim"));
                } else if (docked()) {
                    g.drawWordWrap(font, Component.translatable("gui.minescore.mp3.eq_docked"), left + 80, top + 176, 164, UiTheme.c("device_dim"));
                }
            }
            case MIXER -> renderMixer(g);
        }
        super.render(g, mx, mouseY, partialTick);
        if (dropdown && tab == Tab.LIBRARY) {
            renderDropdown(g, s, mouseX, mouseY);
        }
    }

    private void renderNow(GuiGraphics g, Mp3State s, int mouseX, int mouseY) {
        long now = now();
        SongMeta song = s.current().orElse(null);
        String title = song == null ? Component.translatable("gui.minescore.mp3.no_songs").getString() : song.title();
        int textWidth = font.width(title);
        int x = left + 12;
        g.enableScissor(left + 12, top + 28, left + 244, top + 42);
        if (textWidth > 232) {
            int offset = (int) (now % (textWidth + 40)); // scrolling title
            g.drawString(font, title, x - offset, top + 30, UiTheme.c("device_accent"), false);
            g.drawString(font, title, x - offset + textWidth + 40, top + 30, UiTheme.c("device_accent"), false);
        } else {
            g.drawString(font, title, x, top + 30, UiTheme.c("device_accent"), false);
        }
        g.disableScissor();
        if (song != null) {
            double position = Math.min(s.position(now), song.seconds());
            g.drawString(font, time(position) + " / " + time(song.seconds()), x, top + 44, UiTheme.c("device_dim"), false);
            String remaining = "-" + time(song.seconds() - position);
            g.drawString(font, remaining, left + 244 - font.width(remaining), top + 44, UiTheme.c("device_dim"), false);
            g.fill(left + 12, top + 56, left + 244, top + 60, UiTheme.c("device_bar"));
            g.fill(left + 12, top + 56, left + 12 + (int) (232 * position / Math.max(0.1, song.seconds())), top + 60, UiTheme.c("device_accent"));
        }
        g.drawString(font, Component.translatable("gui.minescore.mp3.up_next"), left + 12, top + 90, UiTheme.c("device_text"), false);
        if (s.link != null) {
            Component bt = Component.translatable("gui.minescore.mp3.bluetooth");
            g.drawString(font, bt, left + 244 - font.width(bt), top + 90, UiTheme.c("bluetooth"), false);
        }
        int upcoming = Math.max(0, s.queue.size() - s.index - 1);
        scroll = clampScroll(scroll, upcoming, NEXT_ROWS);
        for (int row = 0; row < NEXT_ROWS; row++) {
            int i = s.index + 1 + scroll + row;
            if (i >= s.queue.size()) {
                break;
            }
            int y = top + NEXT_Y + row * ROW;
            String name = s.song(s.queue.get(i)).map(SongMeta::title).orElse("?");
            boolean hover = inside(mouseX, mouseY, left + 12, y, LIB_W, ROW);
            if (hover) {
                g.fill(left + 12, y, left + 12 + LIB_W, y + ROW, UiTheme.c("device_pick"));
            }
            g.drawString(font, font.plainSubstrByWidth((i + 1) + ". " + name, LIB_W - 4), left + 14, y + 2, hover ? UiTheme.c("device_text") : UiTheme.c("device_dim"), false);
        }
        if (upcoming == 0) {
            g.drawString(font, Component.translatable("gui.minescore.mp3.nothing_next"), left + 14, top + NEXT_Y + 2, UiTheme.c("device_dim"), false);
        }
        ValueSlider.scrollbar(g, left + 241, top + NEXT_Y, NEXT_ROWS * ROW, scroll, NEXT_ROWS, upcoming);
    }

    private void renderSongs(GuiGraphics g, List<SongMeta> songs, int x, int y, int w, int rows, int offset, int mouseX, int mouseY,
                             Mp3State s, String emptyKey) {
        if (songs.isEmpty()) {
            g.drawWordWrap(font, Component.translatable(emptyKey), x + 2, y + 2, w - 4, UiTheme.c("device_dim"));
            return;
        }
        String playing = s.current().map(SongMeta::id).orElse(null);
        for (int row = 0; row < rows && offset + row < songs.size(); row++) {
            SongMeta song = songs.get(offset + row);
            int ry = y + row * ROW;
            boolean picked = song.id().equals(selectedSong);
            if (picked) {
                g.fill(x, ry, x + w, ry + ROW, UiTheme.c("device_pick"));
            } else if (inside(mouseX, mouseY, x, ry, w, ROW)) {
                g.fill(x, ry, x + w, ry + ROW, UiTheme.c("device_hover"));
            }
            String length = time(song.seconds());
            g.drawString(font, font.plainSubstrByWidth(song.title(), w - font.width(length) - 10), x + 2, ry + 2,
                    song.id().equals(playing) ? UiTheme.c("device_accent") : UiTheme.c("device_text"), false);
            g.drawString(font, length, x + w - font.width(length) - 2, ry + 2, UiTheme.c("device_dim"), false);
        }
    }

    private void renderPlaylists(GuiGraphics g, Mp3State s, int mouseX, int mouseY) {
        int x = left + 12;
        int y = top + PL_Y;
        scroll = clampScroll(scroll, s.playlists.size(), PL_ROWS);
        if (s.playlists.isEmpty()) {
            g.drawWordWrap(font, Component.translatable("gui.minescore.mp3.no_playlists"), x + 2, y + 2, PL_W, UiTheme.c("device_dim"));
        }
        for (int row = 0; row < PL_ROWS && scroll + row < s.playlists.size(); row++) {
            Mp3State.Playlist p = s.playlists.get(scroll + row);
            int ry = y + row * ROW;
            if (p.name.equals(selectedPlaylist)) {
                g.fill(x, ry, x + PL_W, ry + ROW, UiTheme.c("device_pick"));
            } else if (inside(mouseX, mouseY, x, ry, PL_W, ROW)) {
                g.fill(x, ry, x + PL_W, ry + ROW, UiTheme.c("device_hover"));
            }
            g.drawString(font, font.plainSubstrByWidth(p.name, PL_W - 4), x + 2, ry + 2, p.name.equals(s.queueSource) ? UiTheme.c("device_accent") : UiTheme.c("device_text"), false);
        }
        ValueSlider.scrollbar(g, x + PL_W + 1, y, PL_ROWS * ROW, scroll, PL_ROWS, s.playlists.size());
        g.fill(left + 95, y, left + 96, y + PL_ROWS * ROW, UiTheme.c("device_line"));
        if (selectedPlaylist != null && s.playlist(selectedPlaylist).isPresent()) {
            List<SongMeta> songs = playlistSongs(s);
            scroll2 = clampScroll(scroll2, songs.size(), PL_ROWS);
            renderSongs(g, songs, left + PL_SONGS_X, y, PL_SONGS_W, PL_ROWS, scroll2, mouseX, mouseY, s, "gui.minescore.mp3.empty_playlist");
            ValueSlider.scrollbar(g, left + PL_SONGS_X + PL_SONGS_W + 1, y, PL_ROWS * ROW, scroll2, PL_ROWS, songs.size());
        }
    }

    // --- the "Add to" dropdown ----------------------------------------------------------------

    private int dropdownRows(Mp3State s) {
        return Math.max(1, Math.min(DROP_ROWS, s.playlists.size()));
    }

    /** The dropdown's top edge: it opens upward from the Add to button. */
    private int dropdownTop(Mp3State s) {
        return top + STRIP_Y - 1 - dropdownRows(s) * ROW;
    }

    private boolean insideDropdown(Mp3State s, double mx, double my) {
        return tab == Tab.LIBRARY && inside(mx, my, left + ADD_X, dropdownTop(s), ADD_W, dropdownRows(s) * ROW);
    }

    private void renderDropdown(GuiGraphics g, Mp3State s, int mouseX, int mouseY) {
        int x = left + ADD_X;
        int y = dropdownTop(s);
        int rows = dropdownRows(s);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(x - 1, y - 1, x + ADD_W + 1, y + rows * ROW + 1, UiTheme.c("device_box_edge"));
        g.fill(x, y, x + ADD_W, y + rows * ROW, UiTheme.c("device_box"));
        if (s.playlists.isEmpty()) {
            g.drawString(font, Component.translatable("gui.minescore.mp3.make_playlist_first"), x + 3, y + 2, UiTheme.c("device_dim"), false);
        }
        dropdownScroll = clampScroll(dropdownScroll, s.playlists.size(), rows);
        for (int row = 0; row < rows && dropdownScroll + row < s.playlists.size(); row++) {
            Mp3State.Playlist p = s.playlists.get(dropdownScroll + row);
            int ry = y + row * ROW;
            boolean has = selectedSong != null && p.songs.contains(selectedSong);
            if (inside(mouseX, mouseY, x, ry, ADD_W, ROW) && !has) {
                g.fill(x, ry, x + ADD_W, ry + ROW, UiTheme.c("device_pick"));
            }
            String label = font.plainSubstrByWidth(p.name, ADD_W - 16);
            g.drawString(font, label, x + 3, ry + 2, has ? UiTheme.c("device_dim") : UiTheme.c("device_text"), false);
            if (has) {
                g.drawString(font, "✔", x + ADD_W - 10, ry + 2, UiTheme.c("device_accent"), false);
            }
        }
        ValueSlider.scrollbar(g, x + ADD_W - 3, y, rows * ROW, dropdownScroll, rows, s.playlists.size());
        g.pose().popPose();
    }

    private static String time(double seconds) {
        long s = Math.max(0, Math.round(seconds));
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
    }

    private void renderMixer(GuiGraphics g) {
        BlockPos meuPos = ClientSystems.meu(target.mixer());
        if (meuPos != null) {
            com.minescore.block.MeuBlockEntity meu = blockEntity(meuPos, com.minescore.block.MeuBlockEntity.class);
            g.drawString(font, Component.translatable("gui.minescore.mixer.effects",
                    meu == null ? Component.translatable("gui.minescore.mixer.effects_far") : Component.literal(meu.presetLabel())),
                    left + 12, top + 140, UiTheme.c("device_text"), false);
            if (meu != null) {
                List<String> names = new ArrayList<>();
                for (var card : meu.chain().cards()) {
                    String name = Component.translatable("effect.minescore." + card.effect().id()).getString();
                    names.add(card.bypass() ? "(" + name + ")" : name);
                }
                Component line = meu.disabled() ? Component.translatable("gui.minescore.mixer.effects_bypassed")
                        : Component.literal(names.isEmpty() ? "-" : String.join(" › ", names));
                g.drawWordWrap(font, line, left + 24, top + 152, 220, UiTheme.c("device_dim"));
            }
        }
        Playback playback = ClientPlayback.at(target.mixer());
        int speakers = playback == null ? 0 : playback.speakers().size();
        g.drawString(font, Component.translatable("gui.minescore.mixer.speakers", speakers), left + 12, top + 80, UiTheme.c("device_text"), false);
        MixerBlockEntity mixer = blockEntity(target.mixer(), MixerBlockEntity.class);
        if (mixer != null && mixer.power().energyMode()) {
            var power = mixer.power();
            boolean short_ = power.budget() < power.minimum();
            boolean over = power.budget() < power.demand();
            Component line = short_ ? Component.translatable("gui.minescore.mixer.power_short", power.minimum())
                    : Component.translatable(over ? "gui.minescore.mixer.power_over" : "gui.minescore.mixer.power", power.budget(), power.demand());
            g.drawString(font, line, left + 12, top + 91, short_ || over ? UiTheme.c("warn") : UiTheme.c("device_dim"), false);
        }
        if (mainAmp() == null) {
            g.drawString(font, Component.translatable("gui.minescore.mixer.boost"), left + 12, top + 54, UiTheme.c("device_dim"), false);
            g.drawString(font, Component.translatable("gui.minescore.mixer.boost_locked"), left + 24, top + 64, UiTheme.c("device_dim"), false);
        }
        g.drawString(font, Component.translatable("gui.minescore.mixer.channels"), left + 12, top + 104, UiTheme.c("device_text"), false);
        if (channelManager() == null) {
            g.drawString(font, Component.translatable("gui.minescore.mixer.channels_locked"), left + 24, top + 118, UiTheme.c("device_dim"), false);
        } else if (blockEntity(channelManager(), ChannelManagerBlockEntity.class) == null) {
            g.drawString(font, Component.translatable("gui.minescore.mixer.channels_far"), left + 24, top + 118, UiTheme.c("device_dim"), false);
        }
    }

    static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // --- input --------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Mp3State s = state();
        if (s == null) {
            return false;
        }
        if (dropdown && tab == Tab.LIBRARY) {
            dropdown = false;
            if (insideDropdown(s, mx, my)) {
                int row = dropdownScroll + (int) ((my - dropdownTop(s)) / ROW);
                if (row < s.playlists.size() && selectedSong != null && !s.playlists.get(row).songs.contains(selectedSong)) {
                    send(Action.PLAYLIST_ADD, 0, 0, s.playlists.get(row).name, selectedSong);
                }
                return true;
            }
            if (inside(mx, my, left + ADD_X, top + STRIP_Y, ADD_W, 18)) {
                return true; // clicking the button again just closes it
            }
        }
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (search != null && tab == Tab.LIBRARY && !search.isMouseOver(mx, my)) {
            search.setFocused(false);
        }
        switch (tab) {
            case NOW -> {
                SongMeta song = s.current().orElse(null);
                if (song != null && inside(mx, my, left + 12, top + 53, 232, 10)) {
                    send(Action.SEEK, 0, (float) ((mx - left - 12) / 232 * song.seconds()), "", "");
                    return true;
                }
                int row = (int) Math.floor((my - top - NEXT_Y) / ROW);
                int i = s.index + 1 + scroll + row;
                if (inside(mx, my, left + 12, top + NEXT_Y, LIB_W, NEXT_ROWS * ROW) && i < s.queue.size()) {
                    send(Action.JUMP, i, 0, "", "");
                    return true;
                }
            }
            case LIBRARY -> {
                List<SongMeta> songs = librarySongs(s);
                int row = (int) Math.floor((my - top - LIB_Y) / ROW);
                if (inside(mx, my, left + 12, top + LIB_Y, LIB_W, LIB_ROWS * ROW) && scroll + row < songs.size()) {
                    String id = songs.get(scroll + row).id();
                    if (id.equals(selectedSong)) {
                        send(Action.PLAY_SONG, 0, 0, id, ""); // second click plays it
                    }
                    selectedSong = id;
                    init(); // Add to needs a song picked
                    return true;
                }
            }
            case PLAYLISTS -> {
                int row = (int) Math.floor((my - top - PL_Y) / ROW);
                if (inside(mx, my, left + 12, top + PL_Y, PL_W, PL_ROWS * ROW) && scroll + row < s.playlists.size()) {
                    selectedPlaylist = s.playlists.get(scroll + row).name;
                    selectedSong = null;
                    scroll2 = 0;
                    return true;
                }
                if (selectedPlaylist != null && inside(mx, my, left + PL_SONGS_X, top + PL_Y, PL_SONGS_W, PL_ROWS * ROW)) {
                    List<SongMeta> songs = playlistSongs(s);
                    if (scroll2 + row < songs.size()) {
                        selectedSong = songs.get(scroll2 + row).id();
                        return true;
                    }
                }
            }
            default -> { }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int step = (int) -Math.signum(delta);
        Mp3State s = state();
        if (dropdown && s != null && insideDropdown(s, mx, my)) {
            dropdownScroll = Math.max(0, dropdownScroll + step);
        } else if (tab == Tab.PLAYLISTS && mx > left + 96) {
            scroll2 = Math.max(0, scroll2 + step);
        } else {
            scroll = Math.max(0, scroll + step); // clamped to the list when drawn
        }
        return true;
    }

    @Override
    public void tick() {
        Mp3State s = state();
        if (s == null) {
            return;
        }
        if (search != null) {
            search.tick();
        }
        if (playlistName != null) {
            playlistName.tick();
        }
        // Buttons show state (play/pause, shuffle, repeat, the Mixer's switches); rebuild when it changes.
        String key = s.playing + "/" + s.shuffle + "/" + s.repeat + "/" + s.playlists.size() + "/" + systemKey();
        if (!key.equals(lastStateKey)) {
            lastStateKey = key;
            if (tab != Tab.EQ) {
                init(); // the text boxes are kept, with what's typed in them
            }
        }
    }

    /** What the Mixer tab shows about the rest of the system. */
    private String systemKey() {
        if (!docked()) {
            return "";
        }
        ChannelManagerBlockEntity manager = blockEntity(channelManager(), ChannelManagerBlockEntity.class);
        return mainAmp() + "/" + (manager == null ? "-" : manager.leftOn() + "," + manager.rightOn() + "," + manager.forceMono());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
