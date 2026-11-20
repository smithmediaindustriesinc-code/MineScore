package com.minescore.meu;

import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * What one effect card is set to: which of its effects, every knob, and whether its slot is bypassed.
 * Kept on the card item, so the settings go wherever the card goes.
 */
public record CardSettings(CardType type, Effect effect, float[] values, boolean bypass) {
    private static final String TAG = "MineScoreCard";

    public CardSettings {
        if (effect.card() != type) {
            effect = type.defaultEffect();
        }
        float[] clean = effect.defaults();
        for (int i = 0; i < clean.length && i < values.length; i++) {
            clean[i] = effect.knobs().get(i).clamp(values[i]);
        }
        values = clean;
    }

    public static CardSettings defaults(CardType type) {
        return of(type.defaultEffect());
    }

    public static CardSettings of(Effect effect) {
        return new CardSettings(effect.card(), effect, effect.defaults(), false);
    }

    @Override
    public float[] values() {
        return values.clone();
    }

    public float value(int knob) {
        return knob >= 0 && knob < values.length ? values[knob] : 0;
    }

    public float value(String knob) {
        return value(effect.knob(knob));
    }

    public CardSettings withEffect(Effect next) {
        return new CardSettings(type, next, next.defaults(), bypass);
    }

    public CardSettings withValue(int knob, float value) {
        float[] next = values.clone();
        if (knob >= 0 && knob < next.length) {
            next[knob] = value;
        }
        return new CardSettings(type, effect, next, bypass);
    }

    public CardSettings withBypass(boolean on) {
        return new CardSettings(type, effect, values, on);
    }

    /** Changed from what a fresh card does: another effect, or any knob moved. */
    public boolean changed() {
        return effect != type.defaultEffect() || !Arrays.equals(values, effect.defaults());
    }

    // --- storage --------------------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type.id());
        tag.putString("Effect", effect.id());
        CompoundTag knobs = new CompoundTag();
        for (int i = 0; i < values.length; i++) {
            knobs.putFloat(effect.knobs().get(i).id(), values[i]);
        }
        tag.put("Knobs", knobs);
        tag.putBoolean("Bypass", bypass);
        return tag;
    }

    public static @Nullable CardSettings load(CompoundTag tag) {
        CardType type;
        try {
            type = CardType.valueOf(tag.getString("Type").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        Effect effect = Effect.byId(tag.getString("Effect"), type);
        float[] values = effect.defaults();
        CompoundTag knobs = tag.getCompound("Knobs");
        for (int i = 0; i < values.length; i++) {
            String id = effect.knobs().get(i).id();
            if (knobs.contains(id, Tag.TAG_FLOAT)) {
                values[i] = knobs.getFloat(id);
            }
        }
        return new CardSettings(type, effect, values, tag.getBoolean("Bypass"));
    }

    /** The settings on a card item (a fresh card's defaults if it has none yet). */
    public static CardSettings read(ItemStack stack, CardType type) {
        CompoundTag tag = stack.getTagElement(TAG);
        CardSettings settings = tag == null ? null : load(tag);
        return settings == null || settings.type != type ? defaults(type) : settings;
    }

    public void write(ItemStack stack) {
        stack.addTagElement(TAG, save());
    }

    public void writeBuf(FriendlyByteBuf buf) {
        buf.writeEnum(type);
        buf.writeEnum(effect);
        buf.writeVarInt(values.length);
        for (float v : values) {
            buf.writeFloat(v);
        }
        buf.writeBoolean(bypass);
    }

    public static CardSettings readBuf(FriendlyByteBuf buf) {
        CardType type = buf.readEnum(CardType.class);
        Effect effect = buf.readEnum(Effect.class);
        float[] values = new float[Math.min(16, buf.readVarInt())];
        for (int i = 0; i < values.length; i++) {
            values[i] = buf.readFloat();
        }
        return new CardSettings(type, effect, values, buf.readBoolean());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CardSettings other && type == other.type && effect == other.effect && bypass == other.bypass
                && Arrays.equals(values, other.values);
    }

    @Override
    public int hashCode() {
        return (type.hashCode() * 31 + effect.hashCode()) * 31 + Arrays.hashCode(values) + (bypass ? 1 : 0);
    }

    @Override
    public String toString() {
        return effect + Arrays.toString(values) + (bypass ? " (bypassed)" : "");
    }
}
