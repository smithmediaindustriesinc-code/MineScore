package com.minescore.menu;

import com.minescore.block.WritersDeskBlockEntity;
import com.minescore.item.ScoreItem;
import com.minescore.registry.ModItems;
import com.minescore.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The Writer's Desk's menu: the score slot, the copy slot, and the player's inventory (176 × 176). */
public class DeskMenu extends AbstractContainerMenu {
    public static final int SCORE_X = 44, COPY_X = 116, SLOT_Y = 35;
    public static final int INVENTORY_X = 8, INVENTORY_Y = 94;

    private final Container container;
    private final BlockPos pos;

    public DeskMenu(int id, Inventory inventory, Container container, BlockPos pos) {
        super(ModMenus.WRITERS_DESK.get(), id);
        this.container = container;
        this.pos = pos;
        addSlot(new Slot(container, WritersDeskBlockEntity.SCORE, SCORE_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.BLANK_SCORE.get()) || stack.getItem() instanceof ScoreItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(container, WritersDeskBlockEntity.COPY, COPY_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.BLANK_SCORE.get());
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
        }
    }

    public static DeskMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf extra) {
        return new DeskMenu(id, inventory, new SimpleContainer(2), extra.readBlockPos());
    }

    public BlockPos pos() {
        return pos;
    }

    public ItemStack score() {
        return container.getItem(WritersDeskBlockEntity.SCORE);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index <= WritersDeskBlockEntity.COPY) {
            if (!moveItemStackTo(stack, 2, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof ScoreItem || (stack.is(ModItems.BLANK_SCORE.get()) && !slots.get(0).hasItem())) {
            if (!moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.BLANK_SCORE.get())) {
            if (!moveItemStackTo(stack, 1, 2, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getCenter()) <= 64;
    }
}
