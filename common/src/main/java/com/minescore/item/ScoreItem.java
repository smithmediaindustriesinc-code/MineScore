package com.minescore.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * A Score (SCOPE.md §9.1): the item points at a score stored on the server, and shows its title (or
 * <i>Untitled Score</i> in italics), its composer, and its copy number.
 */
public class ScoreItem extends Item {
    private static final String TAG = "MineScoreScore";

    public ScoreItem(Properties properties) {
        super(properties);
    }

    /**
     * What a Score item says about its score.
     *
     * @param original the score it was copied from (itself if it's an original); copies are numbered from it
     * @param copy     0 for an original, n for Copy #n
     */
    public record Data(String id, String title, String composer, String original, int copy) {
        public static @Nullable Data read(ItemStack stack) {
            CompoundTag tag = stack.getTagElement(TAG);
            if (tag == null || !tag.contains("Id")) {
                return null;
            }
            String id = tag.getString("Id");
            return new Data(id, tag.getString("Title"), tag.getString("Composer"),
                    tag.contains("Original") ? tag.getString("Original") : id, tag.getInt("Copy"));
        }

        public void write(ItemStack stack) {
            CompoundTag tag = stack.getOrCreateTagElement(TAG);
            tag.putString("Id", id);
            tag.putString("Title", title);
            tag.putString("Composer", composer);
            tag.putString("Original", original);
            tag.putInt("Copy", copy);
        }

        public Data withTitles(String title, String composer) {
            return new Data(id, title, composer, original, copy);
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        Data data = Data.read(stack);
        if (data == null || data.title().isBlank()) {
            return Component.translatable("item.minescore.score.untitled").withStyle(ChatFormatting.ITALIC);
        }
        return Component.literal(data.title());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Data data = Data.read(stack);
        if (data == null) {
            tooltip.add(Component.translatable("tooltip.minescore.score.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!data.composer().isBlank()) {
            tooltip.add(Component.translatable("tooltip.minescore.score.composer", data.composer()).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add((data.copy() == 0 ? Component.translatable("tooltip.minescore.score.original")
                : Component.translatable("tooltip.minescore.score.copy", data.copy())).withStyle(ChatFormatting.DARK_AQUA));
    }
}
