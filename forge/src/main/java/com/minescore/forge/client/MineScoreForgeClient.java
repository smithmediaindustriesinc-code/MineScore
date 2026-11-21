package com.minescore.forge.client;

import com.minescore.MineScore;
import com.minescore.client.CableRenderer;
import com.minescore.client.CdAppearance;
import com.minescore.client.ClientCables;
import com.minescore.client.ClientHooks;
import com.minescore.client.ClientLocate;
import com.minescore.client.ClientPlayback;
import com.minescore.client.ClientSongs;
import com.minescore.client.ModKeys;
import com.minescore.client.screen.ConfigScreen;
import com.minescore.config.MineScoreConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.fml.ModLoadingContext;
import org.lwjgl.glfw.GLFW;
import com.minescore.client.audio.AudioEngine;
import com.minescore.client.render.LatheRenderer;
import com.minescore.client.render.MusicCabinetRenderer;
import com.minescore.client.render.VinylDisplayRenderer;
import com.minescore.client.render.VinylPlayerRenderer;
import com.minescore.client.render.MeuRenderer;
import com.minescore.client.screen.LatheScreen;
import com.minescore.client.screen.MeuScreen;
import com.minescore.registry.ModBlockEntities;
import com.minescore.registry.ModBlocks;
import com.minescore.registry.ModItems;
import com.minescore.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.sound.SoundEngineLoadEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Client-only Forge glue. Only loaded on the physical client. */
public final class MineScoreForgeClient {
    /** The config menu's key (Ctrl+M) and the extra hotkeys, unbound until a player picks keys (SCOPE.md §10). */
    private static final String CATEGORY = "key.categories.minescore";

    private MineScoreForgeClient() {}

    public static void init(FMLJavaModLoadingContext context) {
        ConfigScreen.setUp();
        ModKeys.binder = (key, input, ctrl, shift, alt) -> key.setKeyModifierAndCode(
                ctrl ? KeyModifier.CONTROL : shift ? KeyModifier.SHIFT : alt ? KeyModifier.ALT : KeyModifier.NONE, input);
        // in the Mods list too
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new ConfigScreen(parent)));
        context.getModEventBus().addListener(MineScoreForgeClient::onRegisterKeys);
        context.getModEventBus().addListener(MineScoreForgeClient::onAddPacks);
        context.getModEventBus().addListener(MineScoreForgeClient::onReloadListeners);
        ClientHooks.set(ClientSongs.INSTANCE);

        MinecraftForge.EVENT_BUS.addListener(MineScoreForgeClient::onRenderLevel);
        MinecraftForge.EVENT_BUS.addListener(MineScoreForgeClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(MineScoreForgeClient::onLoggingOut);
        context.getModEventBus().addListener(MineScoreForgeClient::onSoundEngineLoad);
        context.getModEventBus().addListener(MineScoreForgeClient::onClientSetup);
        context.getModEventBus().addListener(MineScoreForgeClient::onItemColors);
        context.getModEventBus().addListener(MineScoreForgeClient::onBlockColors);
        context.getModEventBus().addListener(MineScoreForgeClient::onRegisterRenderers);
        context.getModEventBus().addListener(MineScoreForgeClient::onRegisterParticles);
        context.getModEventBus().addListener(MineScoreForgeClient::onRegisterOverlays);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.DISC_CUTTING_LATHE.get(), LatheScreen::new);
            MenuScreens.register(ModMenus.MULTI_EFFECTS_UNIT.get(), MeuScreen::new);
            MenuScreens.register(ModMenus.WRITERS_DESK.get(), com.minescore.client.screen.DeskScreen::new);
            for (Item cd : new Item[] {ModItems.BLANK_CD.get(), ModItems.ENCODED_CD.get()}) {
                ItemProperties.register(cd, MineScore.id("trim"), (stack, level, entity, seed) -> CdAppearance.trimProperty(stack));
            }
        });
    }

    /** The Vinyl Display's label (tint 0) takes the colour of the record in it. */
    private static void onBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> level != null && pos != null
                && level.getBlockEntity(pos) instanceof com.minescore.block.VinylDisplayBlockEntity display
                ? display.labelColor() : com.minescore.block.VinylDisplayBlockEntity.DEFAULT_LABEL, ModBlocks.VINYL_DISPLAY.get());
        // Each record sleeve in a Music Cabinet (tint = its place) takes its record's dye.
        event.register((state, level, pos, tintIndex) -> level != null && pos != null
                && level.getBlockEntity(pos) instanceof com.minescore.block.MusicCabinetBlockEntity cabinet
                ? cabinet.sleeveColor(tintIndex) : ModItems.ENCODED_LABEL, ModBlocks.MUSIC_CABINET.get());
    }

    private static void onItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(CdAppearance::color, ModItems.BLANK_CD.get(), ModItems.ENCODED_CD.get());
        event.register((stack, tintIndex) -> MusicCabinetRenderer.ITEM_SLEEVES[tintIndex % MusicCabinetRenderer.ITEM_SLEEVES.length],
                ModBlocks.MUSIC_CABINET.get());
    }

    /** The wiring HUD, just above the hotbar. */
    private static void onRegisterOverlays(net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) {
        event.registerAbove(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.HOTBAR.id(), "wiring",
                (gui, g, partialTick, width, height) -> com.minescore.client.WiringHud.render(g, width, height));
    }

    private static void onRegisterParticles(net.minecraftforge.client.event.RegisterParticleProvidersEvent event) {
        record Kind(java.util.function.Supplier<net.minecraft.core.particles.SimpleParticleType> type,
                    com.minescore.client.render.MineScoreParticle.Kind kind) {}
        for (Kind k : new Kind[] {new Kind(com.minescore.registry.ModParticles.NOTE, com.minescore.client.render.MineScoreParticle.Kind.NOTE),
                new Kind(com.minescore.registry.ModParticles.SHAVING, com.minescore.client.render.MineScoreParticle.Kind.SHAVING),
                new Kind(com.minescore.registry.ModParticles.SHARD, com.minescore.client.render.MineScoreParticle.Kind.SHARD),
                new Kind(com.minescore.registry.ModParticles.SPARK, com.minescore.client.render.MineScoreParticle.Kind.SPARK),
                new Kind(com.minescore.registry.ModParticles.SMOKE, com.minescore.client.render.MineScoreParticle.Kind.SMOKE)}) {
            event.registerSpriteSet(k.type().get(), sprites -> com.minescore.client.render.MineScoreParticle.provider(k.kind(), sprites));
        }
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.DISC_CUTTING_LATHE.get(), LatheRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.VINYL_PLAYER.get(), VinylPlayerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.VINYL_DISPLAY.get(), VinylDisplayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MUSIC_CABINET.get(), MusicCabinetRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SPEAKER.get(), com.minescore.client.render.SpeakerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MULTI_EFFECTS_UNIT.get(), MeuRenderer::new);
    }

    private static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            CableRenderer.render(event.getPoseStack(), event.getCamera(), event.getFrustum(), event.getPartialTick());
        }
    }

    /** The built-in style packs (SCOPE.md §13.1), listed in the Resource Packs screen and off until picked. */
    private static void onAddPacks(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        for (String id : new String[] {"minescore_modern", "minescore_retro", "minescore_16x", "minescore_sounds_studio",
                "minescore_sounds_retro"}) {
            java.nio.file.Path root = packRoot(id);
            if (root == null) {
                MineScore.LOGGER.warn("The built-in pack {} is missing", id);
                continue;
            }
            Pack pack = Pack.readMetaAndCreate("minescore/" + id, Component.translatable("pack.minescore." + id), false,
                    name -> new PathPackResources(name, root, true), PackType.CLIENT_RESOURCES, Pack.Position.TOP, PackSource.BUILT_IN);
            if (pack != null) {
                event.addRepositorySource(packs -> packs.accept(pack));
            }
        }
    }

    /** Where a built-in pack's files are: in the mod's jar, or (in a dev run) wherever the common module's resources are. */
    private static java.nio.file.Path packRoot(String id) {
        java.nio.file.Path root = ModList.get().getModFileById(MineScore.MOD_ID).getFile().findResource("resourcepacks", id);
        if (java.nio.file.Files.exists(root.resolve("pack.mcmeta"))) {
            return root;
        }
        try {
            java.net.URL meta = MineScore.class.getResource("/resourcepacks/" + id + "/pack.mcmeta");
            return meta == null ? null : java.nio.file.Path.of(meta.toURI()).getParent();
        } catch (Exception e) {
            return null;
        }
    }

    private static void onReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) com.minescore.client.UiTheme::reload);
        event.registerReloadListener((ResourceManagerReloadListener) com.minescore.client.audio.SynthSounds::reload);
        event.registerReloadListener((ResourceManagerReloadListener) resources -> com.minescore.client.render.SpeakerRenderer.reload());
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        KeyMapping config = new KeyMapping("key.minescore.config", KeyConflictContext.IN_GAME, KeyModifier.CONTROL,
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY);
        event.register(config);
        ModKeys.add(config, "config");
        for (String id : new String[] {"invisible_wires", "room_sound", "through_blocks"}) {
            KeyMapping key = new KeyMapping("key.minescore." + id, KeyConflictContext.IN_GAME, InputConstants.UNKNOWN, CATEGORY);
            event.register(key);
            ModKeys.add(key, id);
        }
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ModKeys.tick();
            AudioEngine.tick();
            ClientSongs.INSTANCE.tick();
            ClientLocate.tick();
        }
    }

    private static void onSoundEngineLoad(SoundEngineLoadEvent event) {
        AudioEngine.onSoundEngineReloaded();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        AudioEngine.stopAll();
        ClientCables.clear();
        ClientPlayback.clear();
        ClientSongs.INSTANCE.reset();
        MineScoreConfig.reloadServerSide(); // back to this computer's own server settings
    }
}
