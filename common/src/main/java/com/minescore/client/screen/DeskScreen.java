package com.minescore.client.screen;

import com.minescore.client.UiTheme;
import com.minescore.MineScore;
import com.minescore.block.WritersDeskBlockEntity;
import com.minescore.item.ScoreItem;
import com.minescore.menu.DeskMenu;
import com.minescore.network.MineScoreNetwork;
import com.minescore.score.ScorePackets;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

/**
 * The Writer's Desk (SCOPE.md §9.2): the score slot, the copy slot and the editor button, in vanilla's
 * container style.
 */
public class DeskScreen extends AbstractContainerScreen<DeskMenu> {
    private static final ResourceLocation TEXTURE = MineScore.id("textures/gui/writers_desk.png");
    private Button editorButton, playButton, stopButton, loopButton;

    public DeskScreen(DeskMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 176;
        inventoryLabelY = DeskMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        Button editor = addRenderableWidget(Button.builder(Component.translatable("gui.minescore.desk.open_editor"), b -> {
            BlockPos desk = menu.pos();
            minecraft.player.closeContainer();
            MineScoreNetwork.sendToServer(new ScorePackets.Open(desk)); // the editor opens when the score arrives
        }).bounds(leftPos + 8, topPos + 64, 76, 16).build());
        editor.setTooltip(Tooltip.create(Component.translatable("gui.minescore.desk.editor_hint")));
        this.editorButton = editor;
        // as a source (SCOPE.md §9.2): Play renders the score as it is now and plays it
        playButton = addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
            MineScoreNetwork.sendToServer(new ScorePackets.Render(menu.pos(), ScorePackets.Purpose.DESK_PLAY));
            b.active = false; // until it has started (or failed)
        }).bounds(leftPos + 88, topPos + 64, 22, 16).tooltip(Tooltip.create(Component.translatable("gui.minescore.desk.play"))).build());
        stopButton = addRenderableWidget(Button.builder(Component.literal("■"), b ->
                MineScoreNetwork.sendToServer(new ScorePackets.DeskControl(menu.pos(), ScorePackets.DeskAction.STOP)))
                .bounds(leftPos + 112, topPos + 64, 22, 16).tooltip(Tooltip.create(Component.translatable("gui.minescore.desk.stop"))).build());
        loopButton = addRenderableWidget(Button.builder(Component.literal("⟲"), b ->
                MineScoreNetwork.sendToServer(new ScorePackets.DeskControl(menu.pos(), ScorePackets.DeskAction.LOOP)))
                .bounds(leftPos + 136, topPos + 64, 32, 16).tooltip(Tooltip.create(Component.translatable("gui.minescore.desk.loop"))).build());
    }

    private @Nullable WritersDeskBlockEntity desk() {
        return minecraft.level != null && minecraft.level.getBlockEntity(menu.pos()) instanceof WritersDeskBlockEntity desk ? desk : null;
    }

    private boolean wasPlaying;

    @Override
    protected void containerTick() {
        super.containerTick();
        boolean hasScore = menu.score().getItem() instanceof ScoreItem;
        if (editorButton != null) {
            editorButton.active = hasScore;
        }
        WritersDeskBlockEntity desk = desk();
        boolean playing = desk != null && desk.playing();
        if (playButton != null) {
            if (playing != wasPlaying || !hasScore) {
                playButton.active = hasScore; // Play again restarts it with the latest edits
            }
            wasPlaying = playing;
            stopButton.active = playing;
            boolean loop = desk != null && desk.looping();
            loopButton.setMessage(Component.literal(loop ? "⟲ ✔" : "⟲"));
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, UiTheme.c("label"), false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, UiTheme.c("label"), false);
        Component score = Component.translatable("gui.minescore.desk.score");
        g.drawString(font, score, DeskMenu.SCORE_X + 8 - font.width(score) / 2, DeskMenu.SLOT_Y - 11, UiTheme.c("label"), false);
        Component copy = Component.translatable("gui.minescore.desk.copy");
        g.drawString(font, copy, DeskMenu.COPY_X + 8 - font.width(copy) / 2, DeskMenu.SLOT_Y - 11, UiTheme.c("label"), false);
        g.drawString(font, "→", (DeskMenu.SCORE_X + DeskMenu.COPY_X) / 2 + 4, DeskMenu.SLOT_Y + 4, UiTheme.c("label"), false);
        WritersDeskBlockEntity desk = desk();
        if (desk != null && desk.playing()) {
            Component playing = Component.translatable("gui.minescore.desk.playing");
            g.drawString(font, playing, 172 - font.width(playing), 6, UiTheme.c("desk_playing"), false);
        }
        if (menu.score().getItem() instanceof ScoreItem) {
            Component name = menu.score().getHoverName();
            String text = font.plainSubstrByWidth(name.getString(), 120);
            g.drawString(font, Component.literal(text).withStyle(name.getStyle()), 88 - font.width(text) / 2, DeskMenu.SLOT_Y + 19, UiTheme.c("label"), false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
