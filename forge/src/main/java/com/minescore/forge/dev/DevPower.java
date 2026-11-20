package com.minescore.forge.dev;

import com.minescore.MineScore;
import com.minescore.registry.ModCreativeTabs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Development only: a Creative Power Source that hands out endless Forge Energy, pushing it into every block
 * next to it and letting anything pull as much as it likes. It is only registered in the dev game, so it
 * never ships in the built mod. It's there to test the power system (M7) before anything else makes FE.
 */
public final class DevPower {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MineScore.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MineScore.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MineScore.MOD_ID);

    static final RegistryObject<Block> SOURCE = BLOCKS.register("creative_power_source", () -> new SourceBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_MAGENTA).strength(1.5f).sound(SoundType.METAL).lightLevel(s -> 7)));
    static final RegistryObject<Item> SOURCE_ITEM = ITEMS.register("creative_power_source",
            () -> new BlockItem(SOURCE.get(), new Item.Properties()));
    static final RegistryObject<BlockEntityType<SourceBlockEntity>> SOURCE_ENTITY = BLOCK_ENTITIES.register("creative_power_source",
            () -> BlockEntityType.Builder.of(SourceBlockEntity::new, SOURCE.get()).build(null));

    private DevPower() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(DevPower::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() == ModCreativeTabs.MAIN.get()) {
            event.accept(SOURCE_ITEM);
        }
    }

    /** Endless energy: always full, gives whatever is asked, takes nothing. */
    private static final IEnergyStorage ENDLESS = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return maxExtract;
        }

        @Override
        public int getEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public int getMaxEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    };

    static final class SourceBlock extends Block implements EntityBlock {
        SourceBlock(Properties properties) {
            super(properties);
        }

        @Override
        public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new SourceBlockEntity(pos, state);
        }

        @Override
        public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return level.isClientSide || type != SOURCE_ENTITY.get() ? null : (l, p, s, be) -> ((SourceBlockEntity) be).push();
        }
    }

    static final class SourceBlockEntity extends BlockEntity {
        private final LazyOptional<IEnergyStorage> energy = LazyOptional.of(() -> ENDLESS);

        SourceBlockEntity(BlockPos pos, BlockState state) {
            super(SOURCE_ENTITY.get(), pos, state);
        }

        /** Fills every neighbour that takes FE, every tick. */
        void push() {
            if (level == null) {
                return;
            }
            for (Direction side : Direction.values()) {
                BlockEntity neighbour = level.getBlockEntity(worldPosition.relative(side));
                if (neighbour != null) {
                    neighbour.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).ifPresent(target -> {
                        if (target.canReceive()) {
                            target.receiveEnergy(Integer.MAX_VALUE, false);
                        }
                    });
                }
            }
        }

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
            return capability == ForgeCapabilities.ENERGY ? energy.cast() : super.getCapability(capability, side);
        }

        @Override
        public void invalidateCaps() {
            super.invalidateCaps();
            energy.invalidate();
        }
    }
}
