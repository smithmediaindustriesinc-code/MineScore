package com.minescore.mixin;

import com.minescore.block.RedstoneHub;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A comparator reading a MineScore hub asks it for the face it touches (SCOPE.md §6): a Channel Manager's
 * left and right, or an Amplifier's band groups, give different signals. Vanilla never says which face.
 */
@Mixin(ComparatorBlock.class)
abstract class ComparatorBlockMixin {
    @Inject(method = "getInputSignal", at = @At("HEAD"), cancellable = true)
    private void minescore$readHubFace(Level level, BlockPos pos, BlockState state, CallbackInfoReturnable<Integer> result) {
        Direction towardInput = state.getValue(HorizontalDirectionalBlock.FACING);
        if (level.getBlockEntity(pos.relative(towardInput)) instanceof RedstoneHub hub) {
            result.setReturnValue(hub.comparatorSignal(towardInput.getOpposite()));
        }
    }
}
