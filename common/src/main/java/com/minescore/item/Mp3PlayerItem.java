package com.minescore.item;

import com.minescore.block.PortBlock;
import com.minescore.client.ClientHooks;
import com.minescore.mp3.Bluetooth;
import com.minescore.mp3.Mp3State;
import com.minescore.wiring.BlockKind;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The MP3 Player. Right-click the air to open it; right-click a speaker to play through that speaker
 * (Bluetooth), again to stop. Songs are loaded from Vinyl Records at the Disc Cutting Lathe.
 */
public class Mp3PlayerItem extends Item {
    public Mp3PlayerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            var id = Mp3State.idOf(stack);
            if (id != null) {
                ClientHooks.get().openMp3(id);
            }
        } else if (!Mp3State.has(stack)) {
            new Mp3State().write(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockState state = level.getBlockState(context.getClickedPos());
        if (!(state.getBlock() instanceof PortBlock block) || block.layout().kind() != BlockKind.SPEAKER) {
            return InteractionResult.PASS;
        }
        if (context.getPlayer() instanceof ServerPlayer player) {
            ItemStack stack = context.getItemInHand();
            if (!Mp3State.has(stack)) {
                new Mp3State().write(stack);
            }
            Bluetooth.toggle(player, stack, context.getClickedPos());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Gives every MP3 Player its own state (and id) as soon as it's in an inventory. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && !Mp3State.has(stack)) {
            new Mp3State().write(stack);
        }
    }

    /** Its data changes as songs play; don't make the held item bob every time (Forge hook). */
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Mp3State state = Mp3State.has(stack) ? Mp3State.read(stack) : new Mp3State();
        tooltip.add(Component.translatable("tooltip.minescore.mp3.songs", state.library.size()).withStyle(ChatFormatting.GRAY));
        state.current().ifPresent(song -> tooltip.add(Component.translatable(
                state.playing ? "tooltip.minescore.mp3.playing" : "tooltip.minescore.mp3.paused", song.title())
                .withStyle(ChatFormatting.DARK_AQUA)));
        if (state.link != null) {
            tooltip.add(Component.translatable("tooltip.minescore.mp3.linked").withStyle(ChatFormatting.BLUE));
        }
        tooltip.add(Component.translatable("tooltip.minescore.mp3.help").withStyle(ChatFormatting.DARK_GRAY));
    }
}
