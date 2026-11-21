package com.minescore.forge.compat;

import com.minescore.BlockInfo;
import com.minescore.MineScore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/** Jade (SCOPE.md §16, M12 stage 5): MineScore's {@link BlockInfo} lines under the block's name. Loaded only with Jade. */
@WailaPlugin
public class MineScoreJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Info.INSTANCE, Block.class);
    }

    enum Info implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!BuiltInRegistries.BLOCK.getKey(accessor.getBlock()).getNamespace().equals(MineScore.MOD_ID)) {
                return;
            }
            for (Component line : BlockInfo.lines(accessor.getLevel(), accessor.getPosition(), accessor.getBlockState())) {
                tooltip.add(line);
            }
        }

        @Override
        public ResourceLocation getUid() {
            return MineScore.id("info");
        }
    }
}
