package com.minescore.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Blank and Vinyl Records. Both can be dyed like leather armour (the dye colours the label) and trimmed
 * at a smithing table. A Vinyl Record shows its song's title as its name (and plays in the Vinyl Player, M4).
 */
public class CdItem extends Item implements DyeableLeatherItem {
    private final boolean encoded;
    private final int defaultLabel;

    public CdItem(boolean encoded, int defaultLabel, Properties properties) {
        super(properties);
        this.encoded = encoded;
        this.defaultLabel = defaultLabel;
    }

    @Override
    public int getColor(ItemStack stack) {
        return hasCustomColor(stack) ? DyeableLeatherItem.super.getColor(stack) : defaultLabel;
    }

    /** A record both dyed and trimmed earns Custom Pressing. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && level.getGameTime() % 20 == 0 && entity instanceof net.minecraft.world.entity.player.Player player
                && hasCustomColor(stack) && stack.getTag() != null && stack.getTag().contains("Trim")) {
            com.minescore.ModAdvancements.award(player, "custom_pressing");
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        CdData data = encoded ? CdData.read(stack) : null;
        return data != null ? Component.literal(data.title()) : super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CdData data = encoded ? CdData.read(stack) : null;
        if (data == null) {
            tooltip.add(Component.translatable(encoded ? "tooltip.minescore.cd.unreadable" : "tooltip.minescore.blank_cd")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.minescore.cd.length", data.length()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.minescore.cd.cut_by", data.cutBy()).withStyle(ChatFormatting.GRAY));
        tooltip.add((data.copy() == 0
                ? Component.translatable("tooltip.minescore.cd.original")
                : Component.translatable("tooltip.minescore.cd.copy", data.copy())).withStyle(ChatFormatting.DARK_AQUA));
    }
}
