package com.minescore.client.screen;

import com.minescore.Tr;
import com.minescore.client.UiTheme;
import com.minescore.client.ModKeys;
import com.minescore.client.score.InstrumentPacks;
import com.minescore.client.score.ScoreFiles;
import com.minescore.config.ConfigPackets;
import com.minescore.config.MineScoreConfig;
import com.minescore.config.MineScoreConfig.Category;
import com.minescore.config.Setting;
import com.minescore.network.MineScoreNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * MineScore's config menu (SCOPE.md §10), in Tweakeroo's style: the categories down the left, their settings
 * as rows with a value and a reset button, a search across everything, presets, and import and export.
 * Server settings are the server's: operators (or whoever's world it is) change them in-game, and everyone
 * else sees them locked. Opened with Ctrl+M or from the Mods list.
 */
public class ConfigScreen extends Screen {
    private static final int ROW = 20, TOP = 46;
    /** Lines kept free under the settings for the legend (or a notice), wrapped in narrow windows. */
    private static final int LEGEND_LINES = 2;
    /** The tab last open, kept while the game runs. */
    private static Category current = Category.POWER;
    /** Re-runs while a menu is open, when the server's settings arrive. */
    private static @Nullable Runnable refresh;

    private final @Nullable Screen parent;
    private EditBox search;
    private String query = "";
    private int scroll, tabScroll;
    private final Map<EditBox, Setting> fields = new LinkedHashMap<>();
    private @Nullable Setting waitingKey;
    private @Nullable Component notice;

    public ConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("gui.minescore.config.title"));
        this.parent = parent;
    }

    /** What the menu's buttons do, once at startup. */
    public static void setUp() {
        ConfigPackets.onSynced = ConfigScreen::onSynced;
        MineScoreConfig.EDITOR_SHORTCUTS.action = () -> minecraftScreen(s -> new EditorKeysScreen(s));
        MineScoreConfig.CHOOSE_INSTRUMENTS.action = () -> {
            InstrumentPacks.reload();
            minecraftScreen(InstrumentPickerScreen::choosing);
        };
        MineScoreConfig.PICK_EXPORT_FOLDER.action = () -> ScoreFiles.pickFolder().whenCompleteAsync((folder, failure) -> {
            if (folder != null) {
                MineScoreConfig.EXPORT_FOLDER.decode(folder.toString());
                MineScoreConfig.save(Setting.Side.CLIENT);
                if (refresh != null) {
                    refresh.run();
                }
            }
        }, net.minecraft.client.Minecraft.getInstance());
    }

    private static void minecraftScreen(java.util.function.Function<Screen, Screen> make) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.setScreen(make.apply(mc.screen));
    }

    /** The server's settings changed: an open menu shows them. */
    public static void onSynced() {
        if (refresh != null) {
            refresh.run();
        }
    }

    // --- who may change what --------------------------------------------------------------------

    /** Whether this player may change a setting: their own always; the server's when they're an operator or out of any world. */
    private boolean editable(Setting s) {
        if (s.side == Setting.Side.CLIENT) {
            return true;
        }
        if (minecraft.level == null || minecraft.player == null) {
            return true; // no world: the settings file on this computer
        }
        return minecraft.player.hasPermissions(2) || minecraft.hasSingleplayerServer();
    }

    /** Sets a setting from text: saved here (client, or no world), or sent to the server for it to apply and share. */
    private void change(Setting s, String value) {
        if (!editable(s)) {
            return;
        }
        if (s.side == Setting.Side.SERVER && minecraft.level != null) {
            MineScoreNetwork.sendToServer(new ConfigPackets.Change(Map.of(s.id, value)));
            s.decode(value); // shown straight away; the server's reply confirms it
        } else {
            s.decode(value);
            MineScoreConfig.save(s.side);
        }
    }

    /** Applies many values at once (a preset or an import); returns how many server ones were skipped. */
    private int applyAll(Map<String, String> values) {
        Map<String, String> server = new LinkedHashMap<>();
        Map<String, String> client = new LinkedHashMap<>();
        int skipped = 0;
        for (Map.Entry<String, String> e : values.entrySet()) {
            Setting s = MineScoreConfig.byId(e.getKey());
            if (s == null || !s.saved()) {
                continue;
            }
            if (s.side == Setting.Side.CLIENT) {
                client.put(e.getKey(), e.getValue());
            } else if (editable(s)) {
                server.put(e.getKey(), e.getValue());
            } else {
                skipped++;
            }
        }
        if (MineScoreConfig.apply(client, Setting.Side.CLIENT)) {
            MineScoreConfig.save(Setting.Side.CLIENT);
        }
        if (!server.isEmpty()) {
            if (minecraft.level != null) {
                MineScoreNetwork.sendToServer(new ConfigPackets.Change(server));
            }
            if (MineScoreConfig.apply(server, Setting.Side.SERVER) && minecraft.level == null) {
                MineScoreConfig.save(Setting.Side.SERVER);
            }
        }
        return skipped;
    }

    // --- layout -----------------------------------------------------------------------------

    private List<Setting> shown() {
        String q = query.strip().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return MineScoreConfig.in(current);
        }
        List<Setting> found = new ArrayList<>();
        for (Setting s : MineScoreConfig.all()) {
            if (Tr.s(s.label).toLowerCase(Locale.ROOT).contains(q) || Tr.s(s.help).toLowerCase(Locale.ROOT).contains(q)
                    || s.label.toLowerCase(Locale.ROOT).contains(q) || s.id.toLowerCase(Locale.ROOT).contains(q)
                    || Tr.s(s.category.label).toLowerCase(Locale.ROOT).contains(q)) {
                found.add(s);
            }
        }
        return found;
    }

    /** The category tabs' width: narrower in a small window. */
    private int tabsW() {
        return Math.max(72, Math.min(118, width / 5));
    }

    /** The value buttons' width: narrower in a small window, so labels and units keep their room. */
    private int valueW() {
        return Math.max(70, Math.min(130, (width - tabsW() - 40) * 2 / 5));
    }

    private int rows() {
        return Math.max(1, (height - TOP - 38 - LEGEND_LINES * 10) / ROW);
    }

    @Override
    protected void init() {
        refresh = this::rebuildWidgets;
        fields.clear();
        int left = tabsW() + 10, right = width - 8;
        // search, presets, import, export
        String kept = search == null ? query : search.getValue();
        int bw = Math.max(44, Math.min(70, (right - left - 90) / 3 - 4)); // the Presets, Import and Export buttons
        search = new EditBox(font, left, 22, Math.max(60, right - left - 3 * (bw + 4)), 16, Component.translatable("gui.minescore.config.search"));
        search.setHint(Component.translatable("gui.minescore.config.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(kept);
        search.setResponder(v -> {
            if (!v.equals(query)) {
                query = v;
                scroll = 0;
                rebuildWidgets();
            }
        });
        addRenderableWidget(search);
        if (!query.isEmpty()) {
            setInitialFocus(search);
            search.setFocused(true);
        }
        int bx = right - 3 * (bw + 4) + 4;
        addRenderableWidget(Button.builder(Component.translatable("gui.minescore.config.presets"), b -> minecraft.setScreen(new PresetsScreen(this)))
                .bounds(bx, 21, bw, 18).tooltip(Tooltip.create(Component.translatable("gui.minescore.config.presets_hint"))).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.minescore.config.import"), b -> importClipboard())
                .bounds(bx + bw + 4, 21, bw, 18).tooltip(Tooltip.create(Component.translatable("gui.minescore.config.import_hint"))).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.minescore.config.export"), b -> export())
                .bounds(bx + 2 * (bw + 4), 21, bw, 18).tooltip(Tooltip.create(Component.translatable("gui.minescore.config.export_hint"))).build());
        // tabs
        Category[] cats = Category.values();
        int tabRows = Math.max(1, (height - TOP - 34) / 14);
        tabScroll = Math.max(0, Math.min(tabScroll, cats.length - tabRows));
        for (int i = 0; i < tabRows && tabScroll + i < cats.length; i++) {
            Category c = cats[tabScroll + i];
            Button tab = addRenderableWidget(Button.builder(Tr.c(c.label), b -> {
                current = c;
                scroll = 0;
                search.setValue("");
                rebuildWidgets();
            }).bounds(6, TOP + i * 14, tabsW(), 13).build());
            tab.active = !(c == current && query.isEmpty());
        }
        // the settings
        List<Setting> list = shown();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - rows())));
        int vx = right - valueW() - 22;
        for (int i = 0; i < rows() && scroll + i < list.size(); i++) {
            Setting s = list.get(scroll + i);
            int y = TOP + i * ROW;
            addValue(s, vx, y);
            if (s.saved()) {
                Button reset = addRenderableWidget(Button.builder(Component.literal("↺"), b -> {
                    change(s, s.defaultValue());
                    rebuildWidgets();
                }).bounds(right - 20, y, 20, 18).tooltip(Tooltip.create(Component.translatable("gui.minescore.config.reset", s.defaultValue()))).build());
                reset.active = editable(s) && !s.isDefault();
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 75, height - 26, 150, 20).build());
    }

    /** A setting's value: a toggle, a cycling choice, a text box, a button or a key. */
    private void addValue(Setting s, int x, int y) {
        boolean can = editable(s);
        Tooltip tip = Tooltip.create(help(s));
        switch (s.type) {
            case BOOL -> {
                Button b = addRenderableWidget(Button.builder(Component.translatable(s.bool() ? "options.on" : "options.off")
                                .withStyle(s.bool() ? ChatFormatting.GREEN : ChatFormatting.RED),
                        btn -> {
                            change(s, s.next(false));
                            rebuildWidgets();
                        }).bounds(x, y, valueW(), 18).tooltip(tip).build());
                b.active = can;
            }
            case CHOICE -> {
                Button b = addRenderableWidget(Button.builder(Tr.c(choiceLabel(s.text())), btn -> {
                    change(s, s.next(hasShiftDown()));
                    rebuildWidgets();
                }).bounds(x, y, valueW(), 18).tooltip(tip).build());
                b.active = can;
            }
            case INT, DOUBLE, TEXT -> {
                EditBox box = new EditBox(font, x + 1, y + 1, valueW() - 2, 16, Tr.c(s.label));
                box.setMaxLength(s.type == Setting.Type.TEXT ? 512 : 16);
                box.setValue(s.type == Setting.Type.DOUBLE ? trim(s.number()) : s.text());
                if (s.type != Setting.Type.TEXT) {
                    box.setFilter(t -> t.matches("-?[0-9]*\\.?[0-9]*"));
                }
                box.setEditable(can);
                box.setTooltip(tip);
                addRenderableWidget(box);
                fields.put(box, s);
            }
            case ACTION -> addRenderableWidget(Button.builder(Tr.c(s.label), b -> {
                if (s.action != null) {
                    s.action.run();
                }
            }).bounds(x, y, valueW(), 18).tooltip(tip).build());
            case HOTKEY -> {
                KeyMapping key = (KeyMapping) s.key;
                Component label = s == waitingKey ? Component.literal("> ").append(Component.translatable("gui.minescore.config.press_keys")).append(" <")
                        .withStyle(ChatFormatting.YELLOW) : key == null ? Component.empty() : key.getTranslatedKeyMessage();
                addRenderableWidget(Button.builder(label, b -> {
                    waitingKey = s;
                    rebuildWidgets();
                }).bounds(x, y, valueW(), 18).tooltip(tip).build());
            }
        }
    }

    private static String trim(double d) {
        return d == Math.rint(d) && Math.abs(d) < 1e9 ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static String choiceLabel(String value) {
        String spaced = value.replace('_', ' ').toLowerCase(Locale.ROOT);
        return value.equals("WSOLA") ? "WSOLA" : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private Component help(Setting s) {
        Component text = Tr.c(s.help);
        if (s.side == Setting.Side.SERVER) {
            text = Component.empty().append(text).append("\n").append(Component.translatable(editable(s)
                    ? "gui.minescore.config.server_setting" : "gui.minescore.config.server_locked").withStyle(ChatFormatting.GOLD));
        }
        if (s.saved()) {
            text = Component.empty().append(text).append("\n").append(Component.translatable("gui.minescore.config.default", s.defaultValue())
                    .withStyle(ChatFormatting.GRAY));
        }
        return text;
    }

    /** Text boxes take effect when you press Enter, click elsewhere or leave. */
    private void commitFields() {
        boolean any = false;
        for (Map.Entry<EditBox, Setting> e : fields.entrySet()) {
            Setting s = e.getValue();
            String typed = e.getKey().getValue();
            String now = s.type == Setting.Type.DOUBLE ? trim(s.number()) : s.text();
            if (!typed.equals(now) && (s.type == Setting.Type.TEXT || !typed.isBlank())) {
                change(s, typed);
                any = true;
            }
        }
        if (any) {
            rebuildWidgets();
        }
    }

    // --- import, export -----------------------------------------------------------------------

    private void export() {
        String json = MineScoreConfig.toJson();
        minecraft.keyboardHandler.setClipboard(json);
        try {
            Files.writeString(MineScoreConfig.exportFile(), json);
            notice = Component.translatable("gui.minescore.config.exported", MineScoreConfig.exportFile().toString());
        } catch (Exception e) {
            notice = Component.translatable("gui.minescore.config.exported_clipboard");
        }
    }

    private void importClipboard() {
        try {
            int skipped = applyAll(MineScoreConfig.fromJson(minecraft.keyboardHandler.getClipboard()));
            notice = skipped > 0 ? Component.translatable("gui.minescore.config.imported_some", skipped)
                    : Component.translatable("gui.minescore.config.imported");
        } catch (RuntimeException e) {
            notice = Component.translatable("gui.minescore.config.import_failed").withStyle(ChatFormatting.RED);
        }
        rebuildWidgets();
    }

    /** Applies a preset; says if server settings had to be left alone. */
    void applyPreset(MineScoreConfig.Preset preset) {
        int skipped = applyAll(preset.values());
        notice = skipped > 0 ? Component.translatable("gui.minescore.config.preset_some", preset.name(), skipped)
                : Component.translatable("gui.minescore.config.preset_applied", preset.name());
    }

    // --- drawing ------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(4, TOP - 4, tabsW() + 8, height - 32, UiTheme.c("panel"));
        g.fill(tabsW() + 8, TOP - 4, width - 4, height - 32, UiTheme.c("panel"));
        g.drawCenteredString(font, title, width / 2, 7, UiTheme.c("text"));
        Component heading = query.isEmpty() ? Tr.c(current.label)
                : Component.translatable("gui.minescore.config.results", shown().size());
        g.drawString(font, heading, 8, 26, UiTheme.c("dim"), false);
        List<Setting> list = shown();
        int left = tabsW() + 14;
        for (int i = 0; i < rows() && scroll + i < list.size(); i++) {
            Setting s = list.get(scroll + i);
            int y = TOP + i * ROW;
            int color = s.saved() && !s.isDefault() ? UiTheme.c("changed") : UiTheme.c("text");
            String label = Tr.s(s.label);
            if (!query.isEmpty()) {
                label = Tr.s(s.category.label) + " › " + label;
            }
            String unit = Tr.s(s.unit);
            int unitX = width - 8 - valueW() - 22 - font.width(unit) - 4;
            int mark = s.side == Setting.Side.SERVER ? 10 : 0;
            int labelRoom = Math.max(20, (unit.isEmpty() ? width - 8 - valueW() - 26 : unitX - 6) - left - mark);
            String shown = font.width(label) > labelRoom ? font.plainSubstrByWidth(label, labelRoom - font.width("…")) + "…" : label;
            g.drawString(font, shown, left, y + 5, color, false);
            if (mark > 0) {
                g.drawString(font, editable(s) ? "◆" : "◇", left + font.width(shown) + 3, y + 5, UiTheme.c("server"), false);
            }
            if (!unit.isEmpty()) {
                g.drawString(font, unit, unitX, y + 5, UiTheme.c("dim"), false);
            }
        }
        if (list.isEmpty()) {
            g.drawString(font, Component.translatable("gui.minescore.no_matches").getString(), left, TOP + 4, UiTheme.c("dim"), false);
        }
        if (list.size() > rows()) {
            ValueSlider.scrollbar(g, width - 6, TOP, rows() * ROW, scroll, rows(), list.size());
        }
        Component bottom = notice != null ? notice : Component.translatable("gui.minescore.config.legend");
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(bottom, width - 16);
        int ly = height - 34 - Math.min(lines.size(), LEGEND_LINES) * 10;
        for (int i = 0; i < Math.min(lines.size(), LEGEND_LINES); i++) { // wrapped in narrow windows, never cut off
            g.drawCenteredString(font, lines.get(i), width / 2, ly + i * 10, notice != null ? UiTheme.c("text") : UiTheme.c("dim"));
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    // --- input ------------------------------------------------------------------------------

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (waitingKey != null) {
            KeyMapping mapping = (KeyMapping) waitingKey.key;
            switch (key) {
                case GLFW.GLFW_KEY_ESCAPE -> waitingKey = null;
                case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL,
                     GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> {
                    return true; // waiting for the key that goes with them
                }
                case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> {
                    ModKeys.rebind(mapping, InputConstants.UNKNOWN, false, false, false);
                    waitingKey = null;
                }
                default -> {
                    ModKeys.rebind(mapping, InputConstants.getKey(key, scan), hasControlDown(), hasShiftDown(), hasAltDown());
                    waitingKey = null;
                }
            }
            rebuildWidgets();
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && fields.keySet().stream().anyMatch(EditBox::isFocused)) {
            commitFields();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        boolean wasEditing = fields.keySet().stream().anyMatch(b -> b.isFocused() && !b.isMouseOver(mx, my));
        boolean handled = super.mouseClicked(mx, my, button);
        if (wasEditing) {
            commitFields();
        }
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < tabsW() + 8) {
            tabScroll = Math.max(0, tabScroll - (int) Math.signum(delta));
        } else {
            scroll = Math.max(0, scroll - (int) Math.signum(delta) * 3);
        }
        commitFields();
        rebuildWidgets();
        return true;
    }

    @Override
    public void onClose() {
        commitFields();
        refresh = null;
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
