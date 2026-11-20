package com.minescore.forge;

import com.minescore.MineScore;
import com.minescore.power.Powered;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Forge Energy for MineScore's powered blocks (SCOPE.md §10): every {@link Powered} block entity gets an
 * energy capability on all sides that fills its buffer. MineScore only takes FE; it never gives any out.
 */
final class ForgeEnergy {
    private static final ResourceLocation ID = MineScore.id("energy");

    private ForgeEnergy() {}

    static void register() {
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, ForgeEnergy::attach);
    }

    private static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        if (!(event.getObject() instanceof Powered powered)) {
            return;
        }
        LazyOptional<IEnergyStorage> storage = LazyOptional.of(() -> new Storage(powered));
        event.addCapability(ID, new ICapabilityProvider() {
            @Override
            public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
                return ForgeCapabilities.ENERGY.orEmpty(capability, storage);
            }
        });
        event.addListener(storage::invalidate);
    }

    /** A Powered block's buffer as Forge sees it: fills while the block accepts power, never drains from outside. */
    private record Storage(Powered block) implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return block.acceptsEnergy() ? block.energy().receive(maxReceive, simulate) : 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return block.energy().stored();
        }

        @Override
        public int getMaxEnergyStored() {
            return block.energy().capacity();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return block.acceptsEnergy();
        }
    }
}
