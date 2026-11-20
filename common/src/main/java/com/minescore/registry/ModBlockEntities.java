package com.minescore.registry;

import com.minescore.block.AmplifierBlockEntity;
import com.minescore.block.ChannelManagerBlockEntity;
import com.minescore.block.MeuBlockEntity;
import com.minescore.block.SpeakerBlockEntity;
import com.minescore.block.MixerBlockEntity;
import com.minescore.block.VinylDisplayBlockEntity;
import com.minescore.block.VinylPlayerBlockEntity;
import com.minescore.block.WritersDeskBlockEntity;
import com.minescore.platform.PlatformHelper;
import com.minescore.platform.RegistryHelper;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
    /** The plain {@code LatheBlockEntity}, or with Create installed the loader's kinetic one. */
    public static Supplier<BlockEntityType<BlockEntity>> DISC_CUTTING_LATHE;
    public static Supplier<BlockEntityType<MixerBlockEntity>> AUDIO_MIXER;
    public static Supplier<BlockEntityType<VinylPlayerBlockEntity>> VINYL_PLAYER;
    public static Supplier<BlockEntityType<VinylDisplayBlockEntity>> VINYL_DISPLAY;
    public static Supplier<BlockEntityType<com.minescore.block.MusicCabinetBlockEntity>> MUSIC_CABINET;
    public static Supplier<BlockEntityType<SpeakerBlockEntity>> SPEAKER;
    public static Supplier<BlockEntityType<MeuBlockEntity>> MULTI_EFFECTS_UNIT;
    public static Supplier<BlockEntityType<AmplifierBlockEntity>> AMPLIFIER;
    public static Supplier<BlockEntityType<ChannelManagerBlockEntity>> CHANNEL_MANAGER;
    public static Supplier<BlockEntityType<WritersDeskBlockEntity>> WRITERS_DESK;

    private ModBlockEntities() {}

    public static void register(RegistryHelper r, PlatformHelper platform) {
        DISC_CUTTING_LATHE = r.register(Registries.BLOCK_ENTITY_TYPE, "disc_cutting_lathe",
                () -> platform.blockEntityType(platform.latheBlockEntity(), ModBlocks.DISC_CUTTING_LATHE.get()));
        AUDIO_MIXER = r.register(Registries.BLOCK_ENTITY_TYPE, "audio_mixer",
                () -> platform.blockEntityType(MixerBlockEntity::new, ModBlocks.AUDIO_MIXER.get()));
        VINYL_PLAYER = r.register(Registries.BLOCK_ENTITY_TYPE, "vinyl_player",
                () -> platform.blockEntityType(VinylPlayerBlockEntity::new, ModBlocks.VINYL_PLAYER.get()));
        VINYL_DISPLAY = r.register(Registries.BLOCK_ENTITY_TYPE, "vinyl_display",
                () -> platform.blockEntityType(VinylDisplayBlockEntity::new, ModBlocks.VINYL_DISPLAY.get()));
        MUSIC_CABINET = r.register(Registries.BLOCK_ENTITY_TYPE, "music_cabinet",
                () -> platform.blockEntityType(com.minescore.block.MusicCabinetBlockEntity::new, ModBlocks.MUSIC_CABINET.get()));
        MULTI_EFFECTS_UNIT = r.register(Registries.BLOCK_ENTITY_TYPE, "multi_effects_unit",
                () -> platform.blockEntityType(MeuBlockEntity::new, ModBlocks.MULTI_EFFECTS_UNIT.get()));
        SPEAKER = r.register(Registries.BLOCK_ENTITY_TYPE, "speaker", () -> platform.blockEntityType(SpeakerBlockEntity::new,
                ModBlocks.TWEETER_SPEAKER.get(), ModBlocks.MIDDLE_SPEAKER.get(), ModBlocks.SUBWOOFER.get()));
        AMPLIFIER = r.register(Registries.BLOCK_ENTITY_TYPE, "amplifier",
                () -> platform.blockEntityType(AmplifierBlockEntity::new, ModBlocks.AMPLIFIER.get()));
        WRITERS_DESK = r.register(Registries.BLOCK_ENTITY_TYPE, "writers_desk",
                () -> platform.blockEntityType(WritersDeskBlockEntity::new, ModBlocks.WRITERS_DESK.get()));
        CHANNEL_MANAGER = r.register(Registries.BLOCK_ENTITY_TYPE, "channel_manager",
                () -> platform.blockEntityType(ChannelManagerBlockEntity::new, ModBlocks.CHANNEL_MANAGER.get()));
    }
}
