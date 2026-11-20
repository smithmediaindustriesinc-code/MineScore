package com.minescore.forge;

import com.minescore.MineScore;
import com.minescore.forge.client.MineScoreForgeClient;
import com.minescore.forge.dev.DevPower;
import com.minescore.forge.platform.ForgeChunkLoader;
import com.minescore.forge.platform.ForgeNetworkHelper;
import com.minescore.forge.create.CreateLathe;
import com.minescore.forge.platform.ForgePlatformHelper;
import com.minescore.forge.platform.ForgeRegistryHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(MineScore.MOD_ID)
public final class MineScoreForge {
    public MineScoreForge(FMLJavaModLoadingContext context) {
        ForgeEnergy.register();
        if (!FMLEnvironment.production) {
            DevPower.register(context.getModEventBus()); // an endless FE source, for testing in the dev game only
        }
        MineScore.init(new ForgeRegistryHelper(context.getModEventBus()), new ForgeNetworkHelper(), new ForgeChunkLoader(),
                new ForgePlatformHelper());

        // The One Probe asks for its providers by inter-mod message; its classes are only touched if it's installed.
        context.getModEventBus().addListener((net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent event) -> {
            if (net.minecraftforge.fml.ModList.get().isLoaded("theoneprobe")) {
                net.minecraftforge.fml.InterModComms.sendTo("theoneprobe", "getTheOneProbe",
                        () -> new com.minescore.forge.compat.TheOneProbeCompat());
            }
        });
        MinecraftForge.EVENT_BUS.addListener(MineScoreForge::onLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(MineScoreForge::onLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(MineScoreForge::onRespawn);
        MinecraftForge.EVENT_BUS.addListener(MineScoreForge::onChangedDimension);
        MinecraftForge.EVENT_BUS.addListener(MineScoreForge::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(MineScoreForge::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(MineScoreForge::onServerStopped);

        if (ForgePlatformHelper.createLoaded()) {
            context.getModEventBus().addListener((FMLCommonSetupEvent event) -> event.enqueueWork(CreateLathe::registerStress));
        }

        if (FMLEnvironment.dist == Dist.CLIENT) {
            MineScoreForgeClient.init(context);
        }
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        entered(event.getEntity());
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MineScore.onPlayerLoggedOut(player);
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        entered(event.getEntity());
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        entered(event.getEntity());
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        MineScore.registerCommands(event.getDispatcher());
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            MineScore.onServerTick(event.getServer());
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        MineScore.onServerStopped(event.getServer());
    }

    private static void entered(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            MineScore.onPlayerEnteredLevel(serverPlayer);
        }
    }
}
