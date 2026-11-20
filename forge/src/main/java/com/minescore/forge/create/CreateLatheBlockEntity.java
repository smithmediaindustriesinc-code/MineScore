package com.minescore.forge.create;

import com.minescore.block.LatheCore;
import com.minescore.block.LatheHost;
import com.minescore.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Create Lathe's block entity: a Create kinetic block entity (so it joins rotation networks and puts
 * stress on them) holding the same {@link LatheCore} as the plain Lathe.
 */
public class CreateLatheBlockEntity extends KineticBlockEntity implements LatheHost {
    private final LatheCore lathe = new LatheCore(this);

    public CreateLatheBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISC_CUTTING_LATHE.get(), pos, state);
    }

    @Override
    public LatheCore lathe() {
        return lathe;
    }

    @Override
    public boolean kinetic() {
        return true;
    }

    @Override
    public float rpm() {
        return isOverStressed() ? 0 : Math.abs(getSpeed());
    }

    /** Shows Create's rotation particles once placed, as its own kinetic blocks do. */
    void queueRotationIndicators() {
        effects.queueRotationIndicators();
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && !level.isClientSide) {
            lathe.serverTick();
        }
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        if (clientPacket) {
            lathe.saveForClient(tag);
        } else {
            lathe.save(tag);
        }
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        lathe.load(tag);
    }
}
