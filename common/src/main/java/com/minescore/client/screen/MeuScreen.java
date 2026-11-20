package com.minescore.client.screen;

import com.minescore.client.UiTheme;
import com.minescore.MineScore;
import com.minescore.audio.fx.SongAnalysis;
import com.minescore.block.MeuBlockEntity;
import com.minescore.block.MeuPackets;
import com.minescore.block.MeuPackets.Action;
import com.minescore.client.ClientPlayback;
import com.minescore.client.ClientSystems;
import com.minescore.client.audio.SongAnalyses;
import com.minescore.item.CardItem;
import com.minescore.menu.MeuMenu;
import com.minescore.meu.CardSettings;
import com.minescore.meu.Effect;
import com.minescore.meu.Preset;
import com.minescore.network.MineScoreNetwork;
import com.minescore.playback.Playback;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The Multi-Effects Unit's screen (SCOPE.md §3.10), laid out like a vanilla container: the six card slots
 * and the expression pedal along the top, the chosen card's effect and knobs in the dark panel below, and
 * the player's inventory with the presets, the Preset Chip slot and the master switch beside it.
 */
public class MeuScreen extends AbstractContainerScreen<MeuMenu> {
    private static final ResourceLocation TEXTURE = MineScore.id("textures/gui/multi_effects_unit.png");
    /** Vanilla's label grey on the panel; light text inside the dark card panel. */
    private static final int RIGHT_X = 178, PANEL_Y = 52;

    private int selected;
    private int browse = -1;
    private @Nullable EditBox chipName;
    private String lastKey = "";

    public MeuScreen(MeuMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = MeuMenu.WIDTH;
        imageHeight = MeuMenu.HEIGHT;
        inventoryLabelX = MeuMenu.INVENTORY_X;
        inventoryLabelY = MeuMenu.INVENTORY_Y - 11;
    }

    private @Nullable MeuBlockEntity unit() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(menu.pos()) instanceof MeuBlockEntity meu
                ? meu : null;
    }

    private @Nullable CardSettings card(int slot) {
        ItemStack stack = menu.card(slot);
        return stack.getItem() instanceof CardItem card ? CardSettings.read(stack, card.type()) : null;
    }

    private void send(Action action, int slot, int i, float f) {
        MineScoreNetwork.sendToServer(MeuPackets.Set.of(menu.pos(), action, slot, i, f));
    }

    // --- widgets --------------------------------------------------------------------------------

    @Override
    protected void init() {
        super.init();
        build();
    }

    private void build() {
        clearWidgets();
        MeuBlockEntity unit = unit();
        if (browse < 0) {
            browse = unit == null ? 0 : unit.builtIn();
        }
        for (int i = 0; i < MeuMenu.CARDS; i++) {
            int slot = i;
            Button pick = addRenderableWidget(Button.builder(Component.literal(String.valueOf(i + 1)), b -> {
                selected = slot;
                build();
            }).bounds(leftPos + MeuMenu.cardX(i) - 1, topPos + 36, 18, 12).build());
            pick.active = i != selected;
            ItemStack stack = menu.card(i);
            pick.setTooltip(Tooltip.create(stack.isEmpty() ? Component.translatable("gui.minescore.meu.empty") : stack.getHoverName()));
        }
        if (unit != null) {
            addRenderableWidget(Button.builder(Mp3Screen.onOff("gui.minescore.redstone", unit.redstoneControl()),
                    b -> send(Action.REDSTONE, 0, 0, 0)).bounds(leftPos + 132, topPos + 39, 164, 12)
                    .tooltip(Tooltip.create(Component.translatable("gui.minescore.redstone.meu"))).build());
        }
        addRenderableWidget(new ValueSlider(leftPos + 132, topPos + 18, 164, 20, unit == null ? 0 : unit.expression(),
                v -> Component.translatable("gui.minescore.meu.expression", Math.round(v * 100)), v -> send(Action.EXPRESSION, 0, 0, (float) v)));

        CardSettings card = card(selected);
        if (card != null) {
            int top = topPos + PANEL_Y;
            List<Effect> choices = Effect.of(card.type());
            int index = choices.indexOf(card.effect());
            addRenderableWidget(Button.builder(Component.literal("◀"), b -> send(Action.EFFECT, selected, index - 1, 0))
                    .bounds(leftPos + 12, top + 3, 16, 16).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), b -> send(Action.EFFECT, selected, index + 1, 0))
                    .bounds(leftPos + 172, top + 3, 16, 16).build());
            addRenderableWidget(Button.builder(Component.translatable(card.bypass() ? "gui.minescore.meu.slot_off" : "gui.minescore.meu.slot_on"),
                    b -> send(Action.BYPASS, selected, 0, 0)).bounds(leftPos + 192, top + 3, 48, 16).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.minescore.meu.reset_card"), b -> send(Action.RESET_CARD, selected, 0, 0))
                    .bounds(leftPos + 244, top + 3, 48, 16).build());
            List<Effect.Knob> knobs = card.effect().knobs();
            for (int k = 0; k < knobs.size() && k < 8; k++) {
                Effect.Knob knob = knobs.get(k);
                int knobIndex = k;
                addRenderableWidget(new ValueSlider(leftPos + 12 + (k % 2) * 142, top + 22 + (k / 2) * 18, 138, 16,
                        knob.fraction(card.value(k)), v -> knobLabel(card.effect(), knob, knob.at((float) v)),
                        v -> send(Action.KNOB, selected, knobIndex, knob.at((float) v))));
            }
        }

        // Beside the inventory: the presets, the chip and the master switch.
        int y = topPos + MeuMenu.INVENTORY_Y;
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> {
            browse = Math.floorMod(browse - 1, Preset.BUILT_IN.size());
            build();
        }).bounds(leftPos + RIGHT_X, y, 14, 16).build());
        String browsing = Preset.BUILT_IN.get(browse % Preset.BUILT_IN.size()).name();
        Button load = addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(browsing, 84)),
                b -> send(Action.LOAD_BUILT_IN, 0, browse, 0)).bounds(leftPos + RIGHT_X + 15, y, 88, 16).build());
        load.setTooltip(Tooltip.create(Component.translatable("gui.minescore.meu.load_hint", browsing)));
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
            browse = Math.floorMod(browse + 1, Preset.BUILT_IN.size());
            build();
        }).bounds(leftPos + RIGHT_X + 104, y, 14, 16).build());
        if (chipName == null) {
            chipName = new EditBox(font, 0, 0, 94, 14, Component.translatable("gui.minescore.meu.chip_name"));
            chipName.setMaxLength(Preset.MAX_NAME);
            chipName.setHint(Component.translatable("gui.minescore.meu.chip_name").withStyle(ChatFormatting.DARK_GRAY));
        }
        chipName.setPosition(leftPos + RIGHT_X + 23, y + 23);
        addRenderableWidget(chipName);
        addRenderableWidget(Button.builder(Component.translatable("gui.minescore.meu.save_chip"),
                b -> MineScoreNetwork.sendToServer(new MeuPackets.Set(menu.pos(), Action.SAVE_CHIP, 0, 0, 0, chipName.getValue())))
                .bounds(leftPos + RIGHT_X, y + 42, 58, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.minescore.meu.load_chip"), b -> send(Action.LOAD_CHIP, 0, 0, 0))
                .bounds(leftPos + RIGHT_X + 60, y + 42, 58, 16).build());
        addRenderableWidget(Button.builder(Mp3Screen.onOff("gui.minescore.meu.master", unit == null || !unit.bypass()),
                b -> send(Action.MASTER, 0, 0, 0)).bounds(leftPos + RIGHT_X, y + 60, 118, 14).build());
    }

    /** A knob's name and value, the way that knob's unit reads. */
    static Component knobLabel(Effect effect, Effect.Knob knob, float v) {
        Component name = Component.translatable("knob.minescore." + knob.id());
        String value = switch (knob.unit()) {
            case "%" -> Math.round(v * 100) + "%";
            case "%x" -> Math.round(v) + "%";
            case "dB" -> String.format(Locale.ROOT, "%+.1f dB", v);
            case "ms" -> Math.round(v) + " ms";
            case "Hz" -> v < 10 ? String.format(Locale.ROOT, "%.2f Hz", v) : Math.round(v) + " Hz";
            case ":1" -> String.format(Locale.ROOT, "%.1f:1", v);
            case "st" -> String.format(Locale.ROOT, "%+d st", Math.round(v));
            case "ct" -> String.format(Locale.ROOT, "%+d ct", Math.round(v));
            case "knob" -> String.valueOf(Math.round(v));
            default -> Component.translatable("knob.minescore." + knob.unit() + "." + Math.round(v)).getString();
        };
        return Component.empty().append(name).append(": " + value);
    }

    // --- drawing --------------------------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 512, 512);
        for (int i = 0; i < MeuMenu.CARDS; i++) {
            CardSettings card = card(i);
            if (card != null) { // a lamp under each card: lit while its slot plays
                int x = leftPos + MeuMenu.cardX(i);
                g.fill(x + 6, topPos + 49, x + 10, topPos + 51, card.bypass() ? UiTheme.c("card_off") : UiTheme.c("card_on"));
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, UiTheme.c("label"), false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, UiTheme.c("label"), false);
        Component analysis = analysis();
        if (analysis != null) {
            g.drawString(font, analysis, imageWidth - 8 - font.width(analysis), titleLabelY, UiTheme.c("label"), false);
        }
        MeuBlockEntity unit = unit();
        String current = font.plainSubstrByWidth(unit == null ? "" : unit.presetLabel(), 80);
        g.drawString(font, Component.translatable("gui.minescore.meu.preset", current), RIGHT_X, inventoryLabelY, UiTheme.c("label"), false);

        CardSettings card = card(selected);
        if (card == null) {
            g.drawWordWrap(font, Component.translatable("gui.minescore.meu.empty_slot", selected + 1), 12, PANEL_Y + 8, 280, UiTheme.c("device_text"));
            return;
        }
        Component effect = Component.translatable("effect.minescore." + card.effect().id());
        g.drawCenteredString(font, effect, 100, PANEL_Y + 7, card.bypass() ? UiTheme.c("meu_off") : UiTheme.c("device_text"));
    }

    /** The song through this unit right now: its key, tuning and tempo. */
    private @Nullable Component analysis() {
        BlockPos source = ClientSystems.sourceOf(menu.pos());
        Playback playback = source == null ? null : ClientPlayback.at(source);
        if (playback == null) {
            return null;
        }
        SongAnalysis a = SongAnalyses.peek(playback.track());
        if (a == SongAnalysis.UNKNOWN) {
            return Component.translatable("gui.minescore.meu.analysing");
        }
        return Component.translatable("gui.minescore.meu.analysis", a.keyName(), String.format(Locale.ROOT, "%.1f", a.tuningHz()),
                Math.round(a.bpm()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (chipName != null) {
            chipName.tick();
        }
        // Rebuild when anything shown changes, knobs included (a Reset or a loaded preset moves them), but
        // never mid-drag, so a dragged slider stays put.
        StringBuilder key = new StringBuilder().append(selected);
        for (int i = 0; i < MeuMenu.CARDS; i++) {
            CardSettings card = card(i);
            key.append('/').append(card == null ? "-" : card.effect() + ":" + card.bypass() + java.util.Arrays.toString(card.values()));
        }
        MeuBlockEntity unit = unit();
        key.append('/').append(unit == null ? "" : unit.bypass() + "/" + unit.redstoneControl() + unit.presetLabel());
        if (!key.toString().equals(lastKey) && !isDragging()) {
            lastKey = key.toString();
            build();
        }
    }

    /** Container screens keep mouse drags for moving items, so hand a drag that started on a slider to it. */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDragging() && button == 0 && getFocused() instanceof AbstractSliderButton slider) {
            return slider.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDragging() && button == 0 && getFocused() instanceof AbstractSliderButton slider) {
            setDragging(false);
            slider.mouseReleased(mouseX, mouseY, button); // even when let go of off the slider
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (chipName != null && chipName.isFocused() && key != 256) {
            return chipName.keyPressed(key, scanCode, modifiers) || chipName.canConsumeInput();
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
}
