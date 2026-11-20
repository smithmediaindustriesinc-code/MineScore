package com.minescore.client.screen;

import com.minescore.client.UiTheme;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** A slider (0–1 inside) that reports its value when let go of, and every few steps while dragged. */
final class ValueSlider extends AbstractSliderButton {
    private final DoubleFunction<Component> label;
    private final DoubleConsumer onChange;
    private double sent;

    ValueSlider(int x, int y, int w, double value, DoubleFunction<Component> label, DoubleConsumer onChange) {
        this(x, y, w, 18, value, label, onChange);
    }

    ValueSlider(int x, int y, int w, int h, double value, DoubleFunction<Component> label, DoubleConsumer onChange) {
        super(x, y, w, h, label.apply(Mth.clamp(value, 0, 1)), Mth.clamp(value, 0, 1));
        this.label = label;
        this.onChange = onChange;
        this.sent = this.value;
    }

    @Override
    protected void updateMessage() {
        setMessage(label.apply(value));
    }

    @Override
    protected void applyValue() {
        if (Math.abs(value - sent) >= 0.02) {
            sent = value;
            onChange.accept(value);
        }
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        super.onRelease(mouseX, mouseY);
        sent = value;
        onChange.accept(value);
    }

    /** Draws a thin scrollbar for a list showing {@code visible} of {@code total} rows from {@code offset}. */
    static void scrollbar(GuiGraphics g, int x, int y, int height, int offset, int visible, int total) {
        if (total <= visible) {
            return;
        }
        g.fill(x, y, x + 3, y + height, UiTheme.c("device_track"));
        int thumb = Math.max(8, height * visible / total);
        int top = y + (height - thumb) * offset / Math.max(1, total - visible);
        g.fill(x, top, x + 3, top + thumb, UiTheme.c("device_thumb"));
    }
}
