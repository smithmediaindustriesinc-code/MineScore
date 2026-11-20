package com.minescore.client.screen;

import com.minescore.client.UiTheme;
import com.minescore.audio.EqCurve;
import com.minescore.audio.Equalizer;
import com.minescore.block.AmplifierBlockEntity;
import com.minescore.client.ClientCables;
import com.minescore.client.ClientSystems;
import com.minescore.hub.HubPackets;
import com.minescore.hub.HubPackets.Setting;
import com.minescore.network.MineScoreNetwork;
import com.minescore.wiring.SpeakerSystems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * The Amplifier's screen (SCOPE.md §3.3): its role in the system, the boost cap its wiring allows, a
 * master volume up to that cap, and a 10-band EQ.
 */
public class AmplifierScreen extends DeviceScreen<AmplifierBlockEntity> {
    private enum Role { MAIN, LINE, ALONE }

    public AmplifierScreen(BlockPos pos) {
        super(Component.translatable("block.minescore.amplifier"), pos, AmplifierBlockEntity.class, "amplifier", 256, 220);
    }

    private void send(Setting setting, int i, float f) {
        MineScoreNetwork.sendToServer(HubPackets.Set.of(pos, pos, setting, i, f));
    }

    private Role role() {
        if (ClientSystems.isLineAmp(pos)) {
            return Role.LINE;
        }
        return ClientSystems.sourceOf(pos) != null ? Role.MAIN : Role.ALONE;
    }

    /** How far this amp may boost: its own wiring's cap for a main amp, the main amp's for a line amp. */
    private float cap() {
        return switch (role()) {
            case MAIN -> ClientSystems.boostCap(pos);
            case LINE -> ClientSystems.lineCap(pos);
            case ALONE -> 1;
        };
    }

    /** The thinnest hub link wire leading here, in gauge, or 0. */
    private int thinnestWire() {
        return SpeakerSystems.thinnestLink(ClientCables::endsAt, pos);
    }

    @Override
    protected void build(AmplifierBlockEntity amp) {
        float cap = cap();
        addRenderableWidget(new ValueSlider(left + 12, top + 58, 232, Math.min(amp.master(), cap) / cap,
                v -> Component.translatable("gui.minescore.amp.master", Math.round(v * cap * 100)),
                v -> send(Setting.AMP_MASTER, 0, (float) (v * cap))));
        float[] gains = amp.settingEq().gains();
        for (int band = 0; band < EqCurve.BANDS; band++) {
            int b = band;
            String freq = Equalizer.FREQUENCIES[band] >= 1000
                    ? (int) (Equalizer.FREQUENCIES[band] / 1000) + " kHz" : (int) Equalizer.FREQUENCIES[band] + " Hz";
            addRenderableWidget(new ValueSlider(left + 12 + band / 5 * 118, top + 96 + band % 5 * 20, 114,
                    (gains[band] + EqCurve.RANGE) / (2 * EqCurve.RANGE),
                    v -> Component.literal(String.format("%s: %+.1f dB", freq, v * 2 * EqCurve.RANGE - EqCurve.RANGE)),
                    v -> send(Setting.AMP_EQ, b, (float) (v * 2 * EqCurve.RANGE - EqCurve.RANGE))));
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.minescore.mp3.eq_reset"), b -> send(Setting.AMP_EQ_RESET, 0, 0))
                .bounds(left + 12, top + 198, 60, 16).build());
        addRenderableWidget(Button.builder(Mp3Screen.onOff("gui.minescore.redstone", amp.redstoneControl()),
                b -> send(Setting.REDSTONE_CONTROL, 0, amp.redstoneControl() ? 0 : 1)).bounds(left + w - 124, top + 4, 112, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.minescore.redstone.amp"))).build());
    }

    @Override
    protected void draw(GuiGraphics g, AmplifierBlockEntity amp, int mouseX, int mouseY) {
        Role role = role();
        Component roleText = switch (role) {
            case MAIN -> Component.translatable("gui.minescore.amp.main");
            case LINE -> Component.translatable("gui.minescore.amp.line");
            case ALONE -> Component.translatable("gui.minescore.amp.alone");
        };
        g.drawString(font, roleText, left + 12, top + 28, UiTheme.c("device_accent"), false);
        int cap = Math.round(cap() * 100);
        Component capText = switch (role) {
            case MAIN -> thinnestWire() == 0 ? Component.translatable("gui.minescore.amp.cap_no_wire")
                    : Component.translatable("gui.minescore.amp.cap", cap, thinnestWire());
            case LINE -> Component.translatable("gui.minescore.amp.cap_line", cap);
            case ALONE -> Component.translatable("gui.minescore.amp.cap_alone");
        };
        g.drawString(font, capText, left + 12, top + 40, UiTheme.c("device_dim"), false);
        if (role == Role.ALONE && com.minescore.power.PowerSettings.energyMode()) {
            Component fe = Component.translatable("gui.minescore.power.stored", amp.energy().stored(), amp.energy().capacity());
            g.drawString(font, fe, left + w - 12 - font.width(fe), top + 28, UiTheme.c("device_dim"), false);
        }
        g.drawString(font, Component.translatable("gui.minescore.mp3.eq_title"), left + 12, top + 84, UiTheme.c("device_dim"), false);
        if (amp.master() > cap() + 0.001f) {
            g.drawString(font, Component.translatable("gui.minescore.amp.held", cap), left + 80, top + 202, UiTheme.c("warn"), false);
        }
    }

    @Override
    protected String stateKey(AmplifierBlockEntity amp) {
        return role() + "/" + cap() + "/" + amp.redstoneControl(); // not the EQ itself: rebuilding mid-drag would drop the slider
    }
}
