package com.minescore.item;

import com.minescore.meu.CardSettings;
import com.minescore.meu.Preset;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** A Preset Chip: holds one Multi-Effects Unit preset, written and read in the unit's chip slot. */
public class PresetChipItem extends Item {
    public PresetChipItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        Preset preset = Preset.read(stack);
        return preset == null ? super.getName(stack) : Component.translatable("item.minescore.preset_chip.named", preset.name());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return Preset.read(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Preset preset = Preset.read(stack);
        if (preset == null) {
            tooltip.add(Component.translatable("tooltip.minescore.chip.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        for (CardSettings card : preset.cards()) {
            tooltip.add(Component.translatable("tooltip.minescore.chip.card", Component.translatable("item.minescore." + card.type().itemName()),
                    Component.translatable("effect.minescore." + card.effect().id())).withStyle(card.bypass() ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY));
        }
    }
}
