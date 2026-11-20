package com.minescore.registry;

import com.minescore.platform.RegistryHelper;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public final class ModCreativeTabs {
    public static Supplier<CreativeModeTab> MAIN;

    private ModCreativeTabs() {}

    /** The tab's order: discs and players, the system from source to speakers, wiring, then scores. */
    private static List<Supplier<? extends ItemLike>> order() {
        List<Supplier<? extends ItemLike>> order = new java.util.ArrayList<>(List.of(ModItems.BLANK_CD, ModItems.ENCODED_CD,
                ModBlocks.DISC_CUTTING_LATHE, ModBlocks.VINYL_PLAYER, ModBlocks.VINYL_DISPLAY, ModBlocks.MUSIC_CABINET,
                ModItems.MP3_PLAYER, ModBlocks.AUDIO_MIXER,
                ModBlocks.MULTI_EFFECTS_UNIT));
        order.addAll(ModItems.CARDS.values());
        order.add(ModItems.PRESET_CHIP);
        order.addAll(List.of(ModBlocks.CHANNEL_MANAGER, ModBlocks.AMPLIFIER, ModBlocks.SUBWOOFER, ModBlocks.MIDDLE_SPEAKER,
                ModBlocks.TWEETER_SPEAKER, ModItems.SPEAKER_WIRE_12, ModItems.SPEAKER_WIRE_16, ModItems.SPEAKER_WIRE_20,
                ModBlocks.CABLE_ANCHOR, ModItems.WIRE_CUTTERS, ModBlocks.WRITERS_DESK, ModItems.BLANK_SCORE, ModItems.SCORE,
                ModItems.MANUAL));
        return order;
    }

    public static void register(RegistryHelper r) {
        MAIN = r.register(Registries.CREATIVE_MODE_TAB, "main", () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                .title(Component.translatable("itemGroup.minescore"))
                .icon(() -> new ItemStack(ModItems.MP3_PLAYER.get()))
                .displayItems((params, output) -> order().forEach(item -> output.accept(item.get())))
                .build());
    }
}
