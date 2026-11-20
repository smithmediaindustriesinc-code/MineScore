package com.minescore.item;

import com.minescore.block.PortBlock;
import com.minescore.wiring.Cable;
import com.minescore.wiring.CableGeometry;
import com.minescore.wiring.PortRef;
import com.minescore.wiring.WireNetwork;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Right-click a cable (or a jack) to remove it and get its wire back. */
public class WireCuttersItem extends Item {
    private static final double REACH = 6;
    private static final double TOLERANCE = 0.3;

    public WireCuttersItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel serverLevel) {
            cut(serverLevel, player, null);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null) {
            return InteractionResult.PASS;
        }
        if (context.getLevel() instanceof ServerLevel serverLevel) {
            cut(serverLevel, context.getPlayer(), context);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    private static void cut(ServerLevel level, Player player, @Nullable UseOnContext click) {
        WireNetwork network = WireNetwork.get(level);
        Cable cable = CableGeometry.pick(network.all(), player.getEyePosition(), player.getViewVector(1), REACH, TOLERANCE);
        if (cable == null && click != null) {
            BlockState state = level.getBlockState(click.getClickedPos());
            if (state.getBlock() instanceof PortBlock block) {
                cable = block.pick(state, click.getClickedPos(), player.getEyePosition(), player.getViewVector(1))
                        .map(port -> network.atPort(new PortRef(click.getClickedPos(), port.id())))
                        .filter(cables -> !cables.isEmpty())
                        .map(cables -> cables.get(0))
                        .orElse(null);
            }
        }
        if (cable == null) {
            player.displayClientMessage(Component.translatable("msg.minescore.cutters.none"), true);
            return;
        }
        network.remove(cable.id());
        com.minescore.audio.SynthSound.WIRE_CUT.play(level, click != null ? click.getClickedPos() : player.blockPosition().above());
        if (!player.getAbilities().instabuild) {
            ItemStack wire = new ItemStack(cable.gauge().item());
            if (!player.getInventory().add(wire)) {
                player.drop(wire, false);
            }
        }
        player.displayClientMessage(Component.translatable("msg.minescore.cutters.cut"), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.minescore.wire_cutters").withStyle(ChatFormatting.GRAY));
    }
}
