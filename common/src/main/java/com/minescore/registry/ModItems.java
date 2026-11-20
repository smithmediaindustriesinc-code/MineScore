package com.minescore.registry;

import com.minescore.item.ScoreItem;
import com.minescore.item.CardItem;
import com.minescore.item.CdItem;
import com.minescore.item.PresetChipItem;
import com.minescore.meu.CardType;
import java.util.EnumMap;
import java.util.Map;
import com.minescore.item.Mp3PlayerItem;
import com.minescore.item.WireCuttersItem;
import com.minescore.item.WireItem;
import com.minescore.platform.RegistryHelper;
import com.minescore.wiring.Gauge;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;

/** All standalone MineScore items. Most of their behaviour arrives with later milestones. */
public final class ModItems {
    /** Every MineScore item, block items included, in creative-tab order. Filled during {@link #register}. */
    static final List<Supplier<? extends Item>> ALL = new ArrayList<>();

    public static Supplier<Item> BLANK_CD;
    public static Supplier<Item> ENCODED_CD;
    public static Supplier<Item> MP3_PLAYER;
    public static Supplier<Item> SPEAKER_WIRE_12;
    public static Supplier<Item> SPEAKER_WIRE_16;
    public static Supplier<Item> SPEAKER_WIRE_20;
    public static Supplier<Item> WIRE_CUTTERS;
    public static Supplier<Item> BLANK_SCORE;
    public static Supplier<Item> SCORE;
    public static Supplier<Item> MANUAL;
    /** The six Multi-Effects Unit cards, by type. */
    public static final Map<CardType, Supplier<Item>> CARDS = new EnumMap<>(CardType.class);
    public static Supplier<Item> PRESET_CHIP;

    /** Undyed label colours. */
    public static final int BLANK_LABEL = 0xE2D8BE;
    public static final int ENCODED_LABEL = 0xBE323C;

    private ModItems() {}

    public static void register(RegistryHelper r) {
        BLANK_CD = item(r, "blank_cd", () -> new CdItem(false, BLANK_LABEL, new Item.Properties().stacksTo(16)));
        ENCODED_CD = item(r, "encoded_cd", () -> new CdItem(true, ENCODED_LABEL, new Item.Properties().stacksTo(1)));
        MP3_PLAYER = item(r, "mp3_player", () -> new Mp3PlayerItem(new Item.Properties().stacksTo(1)));
        SPEAKER_WIRE_12 = item(r, "speaker_wire_12", () -> new WireItem(Gauge.G12, new Item.Properties()));
        SPEAKER_WIRE_16 = item(r, "speaker_wire_16", () -> new WireItem(Gauge.G16, new Item.Properties()));
        SPEAKER_WIRE_20 = item(r, "speaker_wire_20", () -> new WireItem(Gauge.G20, new Item.Properties()));
        WIRE_CUTTERS = item(r, "wire_cutters", () -> new WireCuttersItem(new Item.Properties().stacksTo(1)));
        BLANK_SCORE = item(r, "blank_score", 64);
        SCORE = item(r, "score", () -> new ScoreItem(new Item.Properties().stacksTo(1)));
        MANUAL = item(r, "manual", () -> new com.minescore.item.ManualItem(new Item.Properties().stacksTo(1)));
        for (CardType type : CardType.values()) {
            CARDS.put(type, item(r, type.itemName(), () -> new CardItem(type, new Item.Properties().stacksTo(1))));
        }
        PRESET_CHIP = item(r, "preset_chip", () -> new PresetChipItem(new Item.Properties().stacksTo(1)));
        ALL.addAll(ModBlocks.BLOCK_ITEMS);
    }

    private static Supplier<Item> item(RegistryHelper r, String name, int maxStack) {
        return item(r, name, () -> new Item(new Item.Properties().stacksTo(maxStack)));
    }

    private static Supplier<Item> item(RegistryHelper r, String name, Supplier<Item> factory) {
        Supplier<Item> item = r.register(Registries.ITEM, name, factory);
        ALL.add(item);
        return item;
    }
}
