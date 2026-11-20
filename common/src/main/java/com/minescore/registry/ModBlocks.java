package com.minescore.registry;

import com.minescore.block.CableAnchorBlock;
import com.minescore.block.HubBlock;
import com.minescore.block.MeuBlock;
import com.minescore.block.SpeakerBlock;
import com.minescore.MineScore;
import com.minescore.block.WritersDeskBlock;
import com.minescore.block.MixerBlock;
import com.minescore.block.VinylDisplayBlock;
import com.minescore.block.VinylPlayerBlock;
import com.minescore.block.PlaceholderBlock;
import com.minescore.block.PortBlock;
import com.minescore.block.TweeterBlock;
import com.minescore.platform.RegistryHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * All MineScore blocks. Jacks and wiring work; everything else is still a placeholder, and the
 * multi-block Writer's Desk, Audio Mixer and Subwoofer get their full size in later milestones.
 */
public final class ModBlocks {
    /** Block items, in creative-tab order. Filled during {@link #register}. */
    static final List<Supplier<? extends Item>> BLOCK_ITEMS = new ArrayList<>();

    public static Supplier<Block> DISC_CUTTING_LATHE;
    public static Supplier<Block> AUDIO_MIXER;
    public static Supplier<Block> CHANNEL_MANAGER;
    public static Supplier<Block> AMPLIFIER;
    public static Supplier<Block> TWEETER_SPEAKER;
    public static Supplier<Block> MIDDLE_SPEAKER;
    public static Supplier<Block> SUBWOOFER;
    public static Supplier<Block> WRITERS_DESK;
    public static Supplier<Block> CABLE_ANCHOR;
    public static Supplier<Block> VINYL_PLAYER;
    public static Supplier<Block> VINYL_DISPLAY;
    public static Supplier<Block> MUSIC_CABINET;
    public static Supplier<Block> MULTI_EFFECTS_UNIT;

    private ModBlocks() {}

    public static void register(RegistryHelper r) {
        DISC_CUTTING_LATHE = block(r, "disc_cutting_lathe",
                () -> MineScore.platform().latheBlock(metal(), PlaceholderBlock.box(0, 0, 0, 16, 8, 16)));
        AUDIO_MIXER = block(r, "audio_mixer", () -> new MixerBlock(metal()));
        MULTI_EFFECTS_UNIT = block(r, "multi_effects_unit", () -> new MeuBlock(metal()));
        CHANNEL_MANAGER = block(r, "channel_manager", () -> new HubBlock(metal(), HubBlock.Type.CHANNEL_MANAGER));
        AMPLIFIER = block(r, "amplifier", () -> new HubBlock(metal(), HubBlock.Type.AMPLIFIER));
        TWEETER_SPEAKER = block(r, "tweeter_speaker", () -> new TweeterBlock(metal()));
        MIDDLE_SPEAKER = block(r, "middle_speaker",
                () -> new SpeakerBlock(metal(), PlaceholderBlock.box(2, 0, 2, 14, 12, 14)));
        SUBWOOFER = block(r, "subwoofer", () -> new SpeakerBlock(metal()));
        WRITERS_DESK = block(r, "writers_desk", () -> new WritersDeskBlock(wood()));
        CABLE_ANCHOR = block(r, "cable_anchor", () -> new CableAnchorBlock(
                BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.5f).sound(SoundType.CHAIN).noOcclusion()));
        VINYL_PLAYER = block(r, "vinyl_player",
                () -> new VinylPlayerBlock(wood(), PlaceholderBlock.box(0, 0, 0, 16, 8, 16)));
        VINYL_DISPLAY = block(r, "vinyl_display", () -> new VinylDisplayBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
                .strength(0.8f).sound(SoundType.WOOD).noOcclusion()));
        // Like a chest: an axe is quickest, but any tool gets it back.
        MUSIC_CABINET = block(r, "music_cabinet", () -> new com.minescore.block.MusicCabinetBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).noOcclusion()));
    }

    /** Mined with a pickaxe (see the mineable/pickaxe tag). */
    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f)
                .requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion();
    }

    /** Mined with an axe (see the mineable/axe tag). */
    private static BlockBehaviour.Properties wood() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f)
                .requiresCorrectToolForDrops().sound(SoundType.WOOD).noOcclusion();
    }

    private static Supplier<Block> block(RegistryHelper r, String name, Supplier<Block> factory) {
        Supplier<Block> block = r.register(Registries.BLOCK, name, factory);
        BLOCK_ITEMS.add(r.register(Registries.ITEM, name, () -> new BlockItem(block.get(), new Item.Properties())));
        return block;
    }
}
