package com.minescore.client.screen;

import com.minescore.client.UiTheme;
import com.minescore.block.PortBlock;
import com.minescore.block.SpeakerBlockEntity;
import com.minescore.client.ClientPlayback;
import com.minescore.client.ClientSystems;
import com.minescore.hub.HubPackets;
import com.minescore.hub.HubPackets.Setting;
import com.minescore.network.MineScoreNetwork;
import com.minescore.playback.Playback;
import com.minescore.playback.SpeakerInfo;
import com.minescore.wiring.Gauge;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** A speaker's screen (SCOPE.md §5.3): rename it, and see its channel, trim, power and what it's doing. */
public class SpeakerScreen extends DeviceScreen<SpeakerBlockEntity> {
    private EditBox name;

    public SpeakerScreen(BlockPos pos) {
        super(Component.translatable("gui.minescore.speaker.title"), pos, SpeakerBlockEntity.class, "speaker", 220, 124);
    }

    @Override
    protected void build(SpeakerBlockEntity speaker) {
        String typed = name == null ? speaker.name() : name.getValue();
        name = new EditBox(font, left + 12, top + 24, 146, 16, Component.translatable("gui.minescore.speaker.name"));
        name.setMaxLength(SpeakerBlockEntity.MAX_NAME);
        name.setValue(typed);
        name.setHint(Component.translatable("gui.minescore.speaker.unnamed").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.translatable("gui.minescore.save"), b -> save())
                .bounds(left + 162, top + 23, 46, 18).build());
    }

    private void save() {
        SpeakerBlockEntity speaker = device();
        if (speaker != null && !name.getValue().strip().equals(speaker.name())) {
            MineScoreNetwork.sendToServer(new HubPackets.Set(pos, pos, Setting.SPEAKER_NAME, 0, 0, name.getValue()));
        }
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (name != null && name.isFocused() && (key == 257 || key == 335)) { // Enter saves
            save();
            name.setFocused(false);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (name != null) {
            save();
        }
        super.onClose();
    }

    static Component bandName(Gauge band) {
        return Component.translatable("gui.minescore.speaker.type." + band.name().toLowerCase(Locale.ROOT));
    }

    @Override
    protected void draw(GuiGraphics g, SpeakerBlockEntity speaker, int mouseX, int mouseY) {
        if (minecraft.level.getBlockState(pos).getBlock() instanceof PortBlock block) {
            Gauge band = block.layout().ports().get(0).signal().band();
            Component type = bandName(band);
            g.drawString(font, type, left + w - 10 - font.width(type), top + 8, UiTheme.c("device_dim"), false);
        }
        BlockPos source = ClientSystems.sourceOf(pos);
        boolean managed = source != null && ClientSystems.channelManager(source) != null;
        Component channel = managed
                ? Component.translatable("gui.minescore.speaker.channel", Component.translatable(
                        "gui.minescore.channel.name." + speaker.channel().name().toLowerCase(Locale.ROOT)))
                : Component.translatable("gui.minescore.speaker.channel_unmanaged");
        g.drawString(font, channel, left + 12, top + 48, UiTheme.c("device_text"), false);
        g.drawString(font, Component.translatable("gui.minescore.speaker.trim", Math.round(speaker.trim() * 100)), left + 12, top + 60,
                managed ? UiTheme.c("device_text") : UiTheme.c("device_dim"), false);
        SpeakerInfo info = playing();
        Component power;
        int color = UiTheme.c("device_dim");
        if (info == null || info.power() < 0) {
            power = Component.translatable("gui.minescore.speaker.power_none");
        } else if (info.power() >= 1) {
            power = Component.translatable("gui.minescore.speaker.power_full", info.cost());
            color = UiTheme.c("device_text");
        } else {
            power = Component.translatable("gui.minescore.speaker.power_over", Math.round(info.power() * 100), info.cost());
            color = UiTheme.c("warn");
        }
        g.drawString(font, power, left + 12, top + 72, color, false);
        g.drawString(font, state(), left + 12, top + 84, UiTheme.c("device_accent"), false);
        g.drawWordWrap(font, Component.translatable("gui.minescore.speaker.hint"), left + 12, top + 100, w - 24, UiTheme.c("device_dim"));
    }

    /** This speaker as its system plays it right now, or null if it's silent. */
    private @Nullable SpeakerInfo playing() {
        for (Playback playback : ClientPlayback.all()) {
            for (SpeakerInfo info : playback.speakers()) {
                if (info.pos().equals(pos)) {
                    return info;
                }
            }
        }
        return null;
    }

    private Component state() {
        for (Playback playback : ClientPlayback.all()) {
            if (playback.kind() == Playback.Kind.BLUETOOTH && playback.source().equals(pos)) {
                return Component.translatable("gui.minescore.speaker.state.bluetooth");
            }
            if (playback.speakers().stream().anyMatch(s -> s.pos().equals(pos))) {
                return Component.translatable("gui.minescore.speaker.state.playing");
            }
        }
        return Component.translatable("gui.minescore.speaker.state.idle");
    }

    @Override
    protected String stateKey(SpeakerBlockEntity speaker) {
        return "";
    }
}
