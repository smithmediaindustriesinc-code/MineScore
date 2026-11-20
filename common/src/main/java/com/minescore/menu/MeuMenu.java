package com.minescore.menu;

import com.minescore.item.CardItem;
import com.minescore.item.PresetChipItem;
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

/**
 * The Multi-Effects Unit's menu: six card slots (one of each card), the Preset Chip slot, and the player's
 * inventory. Positions match the screen's 304 × 240 layout.
 */
public class MeuMenu extends AbstractContainerMenu {
    public static final int CARDS = 6;
    public static final int CHIP = 6;
    public static final int WIDTH = 304, HEIGHT = 240;
    public static final int CARD_Y = 18, CHIP_X = 179, CHIP_Y = 182;
    public static final int INVENTORY_X = 8, INVENTORY_Y = 160;

    private final Container container;
    private final BlockPos pos;
    private final net.minecraft.world.entity.player.Player owner;

    public static int cardX(int slot) {
        return 8 + slot * 20;
    }

    public MeuMenu(int id, Inventory inventory, Container container, BlockPos pos) {
        super(ModMenus.MULTI_EFFECTS_UNIT.get(), id);
        this.container = container;
        this.pos = pos;
        this.owner = inventory.player;
        for (int i = 0; i < CARDS; i++) {
            int index = i;
            addSlot(new Slot(container, i, cardX(i), CARD_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof CardItem card && !holdsElsewhere(card, index);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        addSlot(new Slot(container, CHIP, CHIP_X, CHIP_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof PresetChipItem;
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

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        boolean full = true;
        for (int i = 0; i < CARDS; i++) {
            full &= !container.getItem(i).isEmpty();
        }
        if (full && owner != null) {
            com.minescore.ModAdvancements.award(owner, "pedalboard");
        }
    }

    /** A unit takes one card of each kind. */
    private boolean holdsElsewhere(CardItem card, int except) {
        for (int i = 0; i < CARDS; i++) {
            if (i != except && container.getItem(i).getItem() instanceof CardItem other && other.type() == card.type()) {
                return true;
            }
        }
        return false;
    }

    public static MeuMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf extra) {
        return new MeuMenu(id, inventory, new SimpleContainer(CARDS + 1), extra.readBlockPos());
    }

    public BlockPos pos() {
        return pos;
    }

    public ItemStack card(int slot) {
        return container.getItem(slot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index <= CHIP) {
            if (!moveItemStackTo(stack, CHIP + 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof CardItem) {
            if (!moveItemStackTo(stack, 0, CARDS, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof PresetChipItem) {
            if (!moveItemStackTo(stack, CHIP, CHIP + 1, false)) {
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
        return container.stillValid(player) && player.distanceToSqr(pos.getCenter()) <= 64;
    }
}
