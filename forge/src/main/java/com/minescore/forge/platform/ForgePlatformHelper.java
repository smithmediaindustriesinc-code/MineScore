package com.minescore.forge.platform;

import com.minescore.block.LatheBlock;
import com.minescore.forge.create.CreateLathe;
import com.minescore.platform.PlatformHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import java.util.function.BiFunction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkHooks;

public final class ForgePlatformHelper implements PlatformHelper {
    @Override
    public <M extends AbstractContainerMenu> MenuType<M> menuType(MenuFactory<M> factory) {
        return IForgeMenuType.create(factory::create);
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider provider, BlockPos pos) {
        NetworkHooks.openScreen(player, provider, buf -> buf.writeBlockPos(pos));
    }

    /** Create is installed: the Lathe takes rotation. Its classes are only loaded then. */
    public static boolean createLoaded() {
        return ModList.get().isLoaded("create");
    }

    @Override
    public LatheBlock latheBlock(BlockBehaviour.Properties properties, VoxelShape shape) {
        return createLoaded() ? CreateLathe.block(properties, shape) : PlatformHelper.super.latheBlock(properties, shape);
    }

    @Override
    public BiFunction<BlockPos, BlockState, BlockEntity> latheBlockEntity() {
        return createLoaded() ? CreateLathe.blockEntity() : PlatformHelper.super.latheBlockEntity();
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> blockEntityType(BiFunction<BlockPos, BlockState, T> factory, Block... blocks) {
        return BlockEntityType.Builder.of(factory::apply, blocks).build(null);
    }

    @Override
    public void registerTrigger(net.minecraft.advancements.CriterionTrigger<?> trigger) {
        net.minecraft.advancements.CriteriaTriggers.register(trigger);
    }
}
