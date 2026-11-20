package com.minescore.client;

import com.minescore.item.CdItem;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;

/** How a record looks: its label takes the dye colour, its trim overlay the trim material's colour. */
public final class CdAppearance {
    /** Must match tools/textures.py TRIM_PATTERNS and the item model overrides. */
    private static final List<String> PATTERNS = List.of("sentry", "dune", "coast", "wild", "ward", "eye", "vex", "tide", "snout",
            "rib", "spire", "wayfinder", "shaper", "silence", "raiser", "host");

    private CdAppearance() {}

    /** Item tint: layer 1 is the label, layer 2 the trim. */
    public static int color(ItemStack stack, int tintIndex) {
        if (tintIndex == 1 && stack.getItem() instanceof CdItem cd) {
            return cd.getColor(stack) | 0xFF000000;
        }
        if (tintIndex == 2) {
            return trim(stack)
                    .map(t -> t.material().value().description().getStyle().getColor())
                    .map(TextColor::getValue)
                    .orElse(0xFFFFFF) | 0xFF000000;
        }
        return 0xFFFFFFFF;
    }

    /** The "minescore:trim" item property: 0 untrimmed, (pattern index + 1) / 16 otherwise. */
    public static float trimProperty(ItemStack stack) {
        return trim(stack)
                .map(t -> t.pattern().unwrapKey().map(k -> PATTERNS.indexOf(k.location().getPath())).orElse(-1))
                .map(index -> (Math.max(index, 0) + 1) / 16f)
                .orElse(0f);
    }

    private static Optional<ArmorTrim> trim(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || stack.getTag() == null) {
            return Optional.empty();
        }
        return ArmorTrim.getTrim(mc.level.registryAccess(), stack);
    }
}
