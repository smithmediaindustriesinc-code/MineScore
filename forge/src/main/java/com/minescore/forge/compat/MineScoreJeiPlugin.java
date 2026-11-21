package com.minescore.forge.compat;

import com.minescore.MineScore;
import com.minescore.registry.ModBlocks;
import com.minescore.registry.ModItems;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * JEI (SCOPE.md §16, M12 stage 5): a Disc Cutting Lathe category with the four things it does, and notes on
 * the items that aren't made in a recipe at all. Loaded only when JEI is installed.
 */
@JeiPlugin
public class MineScoreJeiPlugin implements IModPlugin {
    static final RecipeType<LatheJob> LATHE = RecipeType.create(MineScore.MOD_ID, "lathe", LatheJob.class);

    /** One of the Lathe's jobs: what goes in its two input slots, what comes out, and a line saying how. */
    public record LatheJob(ItemStack input, @Nullable ItemStack extra, ItemStack output, String how) {}

    @Override
    public ResourceLocation getPluginUid() {
        return MineScore.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new LatheCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        ItemStack blank = new ItemStack(ModItems.BLANK_CD.get()), record = new ItemStack(ModItems.ENCODED_CD.get());
        ItemStack score = new ItemStack(ModItems.SCORE.get()), mp3 = new ItemStack(ModItems.MP3_PLAYER.get());
        registration.addRecipes(LATHE, List.of(
                new LatheJob(blank, null, record, "jei.minescore.lathe.cut"),
                new LatheJob(score, blank, record, "jei.minescore.lathe.score"),
                new LatheJob(record, blank, record, "jei.minescore.lathe.copy"),
                new LatheJob(record, mp3, mp3, "jei.minescore.lathe.load")));
        for (var item : List.of(ModItems.MANUAL, ModItems.BLANK_SCORE, ModItems.SCORE, ModItems.SPEAKER_WIRE_12, ModItems.SPEAKER_WIRE_16,
                ModItems.SPEAKER_WIRE_20)) {
            String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.get()).getPath();
            registration.addIngredientInfo(new ItemStack(item.get()), VanillaTypes.ITEM_STACK, Component.translatable("jei.minescore.info." + path));
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.DISC_CUTTING_LATHE.get()), LATHE);
    }

    /** Two input slots, an arrow, the result, and the note underneath. */
    static final class LatheCategory implements IRecipeCategory<LatheJob> {
        private static final int W = 150, H = 52;
        private final IDrawable background, icon, slot;

        LatheCategory(IGuiHelper gui) {
            background = gui.createBlankDrawable(W, H);
            icon = gui.createDrawableItemStack(new ItemStack(ModBlocks.DISC_CUTTING_LATHE.get()));
            slot = gui.getSlotDrawable();
        }

        @Override
        public RecipeType<LatheJob> getRecipeType() {
            return LATHE;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("block.minescore.disc_cutting_lathe");
        }

        @Override
        @SuppressWarnings("removal")
        public IDrawable getBackground() {
            return background;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, LatheJob job, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 30, 2).addItemStack(job.input()).setBackground(slot, -1, -1);
            if (job.extra() != null) {
                builder.addSlot(RecipeIngredientRole.INPUT, 50, 2).addItemStack(job.extra()).setBackground(slot, -1, -1);
            }
            builder.addSlot(RecipeIngredientRole.OUTPUT, 104, 2).addItemStack(job.output()).setBackground(slot, -1, -1);
        }

        @Override
        public void draw(LatheJob job, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
            var font = Minecraft.getInstance().font;
            g.drawString(font, "→", 82, 6, 0xFF606060, false);
            int y = 24;
            for (FormattedCharSequence line : font.split(Component.translatable(job.how()), W)) {
                g.drawString(font, line, W / 2 - font.width(line) / 2, y, 0xFF404040, false);
                y += 9;
            }
        }
    }
}
