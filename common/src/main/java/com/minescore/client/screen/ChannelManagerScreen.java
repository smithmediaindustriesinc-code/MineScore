package com.minescore.client.screen;

import com.minescore.client.UiTheme;
import com.minescore.audio.OutputChannel;
import com.minescore.block.ChannelManagerBlockEntity;
import com.minescore.client.ClientLocate;
import com.minescore.hub.HubPackets;
import com.minescore.hub.HubPackets.Row;
import com.minescore.hub.HubPackets.Setting;
import com.minescore.network.MineScoreNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The Channel Manager's screen (SCOPE.md §3.4): its Left, Right and Mono switches, and every speaker after
 * it with its name, type, range, channel, trim and a Locate button. Click a name to rename that speaker.
 */
public class ChannelManagerScreen extends DeviceScreen<ChannelManagerBlockEntity> {
    private static final int LIST_Y = 62, ROW_H = 20, ROWS = 7;
    private static final int NAME_X = 10, TYPE_X = 96, RANGE_X = 128, CHANNEL_X = 160, TRIM_X = 186, LOCATE_X = 232;

    private final List<Row> rows = new ArrayList<>();
    private int scroll;
    private @Nullable BlockPos renaming;
    private @Nullable EditBox rename;
    private boolean rebuildOnRelease;

    public ChannelManagerScreen(BlockPos manager, List<Row> rows) {
        super(Component.translatable("block.minescore.channel_manager"), manager, ChannelManagerBlockEntity.class, "channel_manager",
                256, 226);
        this.rows.addAll(rows);
    }

    public BlockPos manager() {
        return pos;
    }

    /** A fresh speaker list from the server, after a change. */
    public void update(List<Row> fresh) {
        rows.clear();
        rows.addAll(fresh);
        if (isDragging()) {
            rebuildOnRelease = true; // don't pull a trim slider out from under the mouse
        } else {
            rebuild();
        }
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean handled = super.mouseReleased(mx, my, button);
        if (rebuildOnRelease) {
            rebuildOnRelease = false;
            rebuild();
        }
        return handled;
    }

    private void send(BlockPos target, Setting setting, int i, float f, String text) {
        MineScoreNetwork.sendToServer(new HubPackets.Set(pos, target, setting, i, f, text));
    }

    @Override
    protected void build(ChannelManagerBlockEntity manager) {
        addRenderableWidget(Button.builder(Mp3Screen.onOff("gui.minescore.redstone", manager.redstoneControl()),
                b -> send(pos, Setting.REDSTONE_CONTROL, 0, manager.redstoneControl() ? 0 : 1, "")).bounds(left + w - 124, top + 4, 112, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.minescore.redstone.cm"))).build());
        int y = top + 24;
        addRenderableWidget(Button.builder(Mp3Screen.onOff("gui.minescore.channel.left", manager.leftOn()),
                b -> send(pos, Setting.CM_LEFT, 0, manager.leftOn() ? 0 : 1, "")).bounds(left + 10, y, 76, 18).build());
        addRenderableWidget(Button.builder(Mp3Screen.onOff("gui.minescore.channel.right", manager.rightOn()),
                b -> send(pos, Setting.CM_RIGHT, 0, manager.rightOn() ? 0 : 1, "")).bounds(left + 90, y, 76, 18).build());
        addRenderableWidget(Button.builder(Component.translatable(manager.forceMono()
                        ? "gui.minescore.channel.mono_forced" : "gui.minescore.channel.mono_auto"),
                b -> send(pos, Setting.CM_MONO, 0, manager.forceMono() ? 0 : 1, "")).bounds(left + 170, y, 76, 18).build());

        scroll = Math.max(0, Math.min(scroll, rows.size() - ROWS));
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int ry = top + LIST_Y + i * ROW_H;
            OutputChannel next = OutputChannel.values()[(row.channel().ordinal() + 1) % OutputChannel.values().length];
            Button channel = addRenderableWidget(Button.builder(Component.translatable(
                            "gui.minescore.channel.short." + row.channel().name().toLowerCase(Locale.ROOT)),
                    b -> send(row.pos(), Setting.SPEAKER_CHANNEL, next.ordinal(), 0, "")).bounds(left + CHANNEL_X, ry, 22, 18).build());
            channel.setTooltip(Tooltip.create(Component.translatable("gui.minescore.channel.cycle")));
            if (row.direct()) {
                addRenderableWidget(new ValueSlider(left + TRIM_X, ry, 44, 18, row.trim(),
                        v -> Component.literal(Math.round(v * 100) + "%"),
                        v -> send(row.pos(), Setting.SPEAKER_TRIM, 0, (float) v, "")));
            }
            Button locate = addRenderableWidget(Button.builder(Component.literal("⌖"), b -> {
                ClientLocate.start(row.pos(), label(row));
                onClose();
            }).bounds(left + LOCATE_X, ry, 16, 18).build());
            locate.setTooltip(Tooltip.create(Component.translatable("gui.minescore.cm.locate")));
        }

        if (renaming != null) {
            String typed = rename == null ? rows.stream().filter(r -> r.pos().equals(renaming)).map(Row::name).findFirst().orElse("")
                    : rename.getValue();
            rename = new EditBox(font, left + 11, top + 205, 180, 14, Component.translatable("gui.minescore.speaker.name"));
            rename.setMaxLength(32);
            rename.setValue(typed);
            rename.setHint(Component.translatable("gui.minescore.speaker.unnamed").withStyle(ChatFormatting.DARK_GRAY));
            addRenderableWidget(rename);
            setFocused(rename);
            addRenderableWidget(Button.builder(Component.translatable("gui.minescore.save"), b -> saveRename())
                    .bounds(left + 196, top + 203, 50, 18).build());
        } else {
            rename = null;
        }
    }

    private void saveRename() {
        if (renaming != null && rename != null) {
            send(renaming, Setting.SPEAKER_NAME, 0, 0, rename.getValue());
        }
        renaming = null;
        rename = null;
        rebuild();
    }

    private Component label(Row row) {
        return row.name().isEmpty() ? SpeakerScreen.bandName(row.band()) : Component.literal(row.name());
    }

    @Override
    protected void draw(GuiGraphics g, ChannelManagerBlockEntity manager, int mouseX, int mouseY) {
        int hy = top + LIST_Y - 12;
        g.drawString(font, Component.translatable("gui.minescore.cm.name"), left + NAME_X + 2, hy, UiTheme.c("device_dim"), false);
        g.drawString(font, Component.translatable("gui.minescore.cm.type"), left + TYPE_X, hy, UiTheme.c("device_dim"), false);
        g.drawString(font, Component.translatable("gui.minescore.cm.range"), left + RANGE_X, hy, UiTheme.c("device_dim"), false);
        g.drawString(font, Component.translatable("gui.minescore.cm.channel"), left + CHANNEL_X, hy, UiTheme.c("device_dim"), false);
        g.drawString(font, Component.translatable("gui.minescore.cm.trim"), left + TRIM_X, hy, UiTheme.c("device_dim"), false);
        if (rows.isEmpty()) {
            g.drawWordWrap(font, Component.translatable("gui.minescore.cm.empty"), left + 12, top + LIST_Y + 4, w - 24, UiTheme.c("device_dim"));
        }
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int ry = top + LIST_Y + i * ROW_H;
            boolean hover = Mp3Screen.inside(mouseX, mouseY, left + NAME_X, ry, TYPE_X - NAME_X - 4, 18);
            if (row.pos().equals(renaming)) {
                g.fill(left + NAME_X, ry, left + TYPE_X - 4, ry + 18, UiTheme.c("device_pick"));
            } else if (hover) {
                g.fill(left + NAME_X, ry, left + TYPE_X - 4, ry + 18, UiTheme.c("device_hover"));
            }
            String name = font.plainSubstrByWidth(label(row).getString(), TYPE_X - NAME_X - 8);
            g.drawString(font, name, left + NAME_X + 2, ry + 5, row.name().isEmpty() ? UiTheme.c("device_dim") : UiTheme.c("device_text"), false);
            g.drawString(font, Component.translatable("gui.minescore.cm.type." + row.band().name().toLowerCase(Locale.ROOT)),
                    left + TYPE_X, ry + 5, UiTheme.c("device_text"), false);
            g.drawString(font, Math.round(row.range()) + "m", left + RANGE_X, ry + 5, UiTheme.c("device_text"), false);
            if (!row.direct()) {
                g.drawString(font, Component.translatable("gui.minescore.cm.chained"), left + TRIM_X + 2, ry + 5, UiTheme.c("device_dim"), false);
            }
        }
        ValueSlider.scrollbar(g, left + w - 6, top + LIST_Y, ROWS * ROW_H, scroll, ROWS, rows.size());
        if (renaming == null && !rows.isEmpty()) {
            g.drawString(font, Component.translatable("gui.minescore.cm.rename_hint"), left + 12, top + 209, UiTheme.c("device_dim"), false);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        int i = (int) Math.floor((my - top - LIST_Y) / ROW_H);
        if (Mp3Screen.inside(mx, my, left + NAME_X, top + LIST_Y, TYPE_X - NAME_X - 4, ROWS * ROW_H) && scroll + i < rows.size()) {
            renaming = rows.get(scroll + i).pos();
            rename = null;
            rebuild();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (rename != null && rename.isFocused() && (key == 257 || key == 335)) { // Enter saves
            saveRename();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int before = scroll;
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(delta), rows.size() - ROWS));
        if (scroll != before) {
            rebuild();
        }
        return true;
    }

    @Override
    protected String stateKey(ChannelManagerBlockEntity manager) {
        return manager.leftOn() + "/" + manager.rightOn() + "/" + manager.forceMono() + "/" + manager.redstoneControl();
    }
}
