package com.minescore.item;

import com.minescore.meu.CardSettings;
import com.minescore.meu.CardType;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** A Multi-Effects Unit effect card. It keeps its settings, so they travel with it. */
public class CardItem extends Item {
    private final CardType type;

    public CardItem(CardType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public CardType type() {
        return type;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CardSettings settings = CardSettings.read(stack, type);
        tooltip.add(Component.translatable("effect.minescore." + settings.effect().id()).withStyle(ChatFormatting.GRAY));
        if (settings.bypass()) {
            tooltip.add(Component.translatable("tooltip.minescore.card.bypassed").withStyle(ChatFormatting.DARK_GRAY));
        } else if (settings.changed()) {
            tooltip.add(Component.translatable("tooltip.minescore.card.set").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
