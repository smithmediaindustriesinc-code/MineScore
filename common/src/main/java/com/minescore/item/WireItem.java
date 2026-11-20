package com.minescore.item;

import com.minescore.MineScoreSettings;
import com.minescore.block.CableAnchorBlock;
import com.minescore.block.PortBlock;
import com.minescore.wiring.Cable;
import com.minescore.wiring.Gauge;
import com.minescore.wiring.LevelWireWorld;
import com.minescore.wiring.PendingCable;
import com.minescore.wiring.Port;
import com.minescore.wiring.PortRef;
import com.minescore.wiring.WireWorld;
import com.minescore.wiring.WiringRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Speaker wire. Right-click a jack, optionally some Cable Anchors, then another jack. Speakers take a
 * plain click with their own gauge; hub-to-hub links take a Shift-click with any gauge.
 * Shift-right-click the air to give up on a cable.
 */
public class WireItem extends Item {
    private final Gauge gauge;

    public WireItem(Gauge gauge, Properties properties) {
        super(properties);
        this.gauge = gauge;
    }

    public Gauge gauge() {
        return gauge;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockState state = level.getBlockState(context.getClickedPos());
        boolean ours = state.getBlock() instanceof PortBlock || state.getBlock() instanceof CableAnchorBlock;
        Player player = context.getPlayer();
        if (!ours || player == null) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel) {
            click(serverLevel, player, context.getItemInHand(), context.getClickedPos(), state, context.getClickLocation());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && PendingCable.read(stack) != null) {
            if (!level.isClientSide) {
                PendingCable.clear(stack);
                tell(player, Component.translatable("msg.minescore.wire.cancelled"));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    private void click(ServerLevel level, Player player, ItemStack stack, BlockPos pos, BlockState state, Vec3 hit) {
        PendingCable pending = PendingCable.read(stack);
        if (pending != null && !pending.dimension().equals(level.dimension())) {
            PendingCable.clear(stack);
            tell(player, Component.translatable("msg.minescore.wire.other_dimension"));
            return;
        }

        if (state.getBlock() instanceof CableAnchorBlock) {
            if (pending == null) {
                tell(player, Component.translatable("msg.minescore.wire.anchor_needs_cable"));
            } else if (pending.anchors().contains(pos)) {
                tell(player, Component.translatable("msg.minescore.wire.anchor_used"));
            } else {
                Vec3[] ends = CableAnchorBlock.ends(state, pos);
                warnIfFar(player, pending.lastPoint());
                pending = pending.withAnchor(pos, ends[0], ends[1]);
                pending.write(stack);
                tell(player, Component.translatable("msg.minescore.wire.anchored", pending.anchors().size()));
            }
            return;
        }

        PortBlock block = (PortBlock) state.getBlock();
        Optional<Port> port = block.pick(state, pos, player.getEyePosition(), player.getViewVector(1));
        if (port.isEmpty()) {
            tell(player, Component.translatable("msg.minescore.wire.click_jack"));
            return;
        }
        PortRef ref = new PortRef(pos.immutable(), port.get().id());

        if (pending == null) {
            new PendingCable(level.dimension(), ref, List.of(), List.of(block.attachPoint(state, pos, port.get()))).write(stack);
            com.minescore.audio.SynthSound.JACK_PLUG.play(level, pos);
            tell(player, Component.translatable("msg.minescore.wire.started", state.getBlock().getName(),
                    Component.translatable(port.get().translationKey())));
            return;
        }
        if (pending.start().equals(ref)) {
            PendingCable.clear(stack);
            com.minescore.audio.SynthSound.JACK_UNPLUG.play(level, pos);
            tell(player, Component.translatable("msg.minescore.wire.cancelled"));
            return;
        }

        LevelWireWorld world = new LevelWireWorld(level);
        WiringRules.Result result = WiringRules.check(world, pending.start(), ref, gauge, player.isShiftKeyDown());
        if (result instanceof WiringRules.Error error) {
            tell(player, error.message().copy().withStyle(ChatFormatting.RED));
            return;
        }
        WiringRules.Ok ok = (WiringRules.Ok) result;
        List<Vec3> laid = new ArrayList<>(pending.points());
        laid.add(block.attachPoint(state, pos, port.get()));
        warnIfFar(player, pending.lastPoint());
        world.network().add(Cable.create(ok, gauge, pending.anchors(), laid));

        PendingCable.clear(stack);
        com.minescore.audio.SynthSound.JACK_PLUG.play(level, pos);
        if (level.getBlockEntity(ok.from().ref().pos()) instanceof com.minescore.block.SpeakerBlockEntity
                || level.getBlockEntity(ok.to().ref().pos()) instanceof com.minescore.block.SpeakerBlockEntity) {
            com.minescore.ModAdvancements.award(player, "plugged_in");
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        tell(player, Component.translatable("msg.minescore.wire.connected", describe(level, ok.from()), describe(level, ok.to())));
    }

    private static Component describe(Level level, WireWorld.Endpoint end) {
        return Component.translatable("msg.minescore.wire.jack", level.getBlockState(end.ref().pos()).getBlock().getName(),
                Component.translatable(end.port().translationKey()));
    }

    /** Warns when the run from the previous connection is longer than speakers can be heard. */
    private static void warnIfFar(Player player, Vec3 previous) {
        double distance = previous.distanceTo(player.getEyePosition());
        if (distance > MineScoreSettings.hearingRange() && com.minescore.config.MineScoreConfig.DISTANCE_WARNING.bool()) {
            player.displayClientMessage(Component.translatable("msg.minescore.wire.too_far", (int) distance,
                    (int) MineScoreSettings.hearingRange()).withStyle(ChatFormatting.GOLD), false);
        }
    }

    private static void tell(Player player, Component message) {
        player.displayClientMessage(message, true);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return PendingCable.read(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.minescore.speaker_wire").withStyle(ChatFormatting.GRAY));
        if (PendingCable.read(stack) != null) {
            tooltip.add(Component.translatable("tooltip.minescore.speaker_wire.pending").withStyle(ChatFormatting.YELLOW));
        }
    }
}
