package com.minescore.menu;

import com.minescore.registry.ModItems;
import com.minescore.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The Disc Cutting Lathe's menu. Slot and inventory positions match the screen's 256 × 222 layout. */
public class LatheMenu extends AbstractContainerMenu {
    public static final int INPUT_X = 136, INPUT_Y = 50;
    public static final int EXTRA_X = 136, EXTRA_Y = 76;
    public static final int OUTPUT_X = 202, OUTPUT_Y = 63;
    public static final int INVENTORY_X = 48, INVENTORY_Y = 140;

    private final Container container;
    private final ContainerData data;
    private final BlockPos pos;

    public LatheMenu(int id, Inventory inventory, Container container, ContainerData data, BlockPos pos) {
        super(ModMenus.DISC_CUTTING_LATHE.get(), id);
        this.container = container;
        this.data = data;
        this.pos = pos;
        addSlot(new Slot(container, 0, INPUT_X, INPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.BLANK_CD.get()) || stack.is(ModItems.ENCODED_CD.get()) || stack.is(ModItems.SCORE.get());
            }
        });
        addSlot(new Slot(container, 1, EXTRA_X, EXTRA_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.BLANK_CD.get()) || stack.is(ModItems.MP3_PLAYER.get());
            }
        });
        addSlot(new Slot(container, 2, OUTPUT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
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
        addDataSlots(data);
    }

    /** Client side, built from the position the server sent. */
    public static LatheMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf extra) {
        return new LatheMenu(id, inventory, new SimpleContainer(3), new SimpleContainerData(4), extra.readBlockPos());
    }

    public BlockPos pos() {
        return pos;
    }

    /** Cut progress from 0 to 1, or -1 when idle. */
    public float progress() {
        int total = data.get(1);
        int done = data.get(0);
        return total > 0 ? Math.min(1, (float) done / total) : -1;
    }

    /** FE stored in the Lathe. */
    public int energy() {
        return data.get(2);
    }

    /** Its job is waiting for power. */
    public boolean waitingForPower() {
        return (data.get(3) & 1) != 0;
    }

    /** It takes Create rotation. */
    public boolean kinetic() {
        return (data.get(3) & 4) != 0;
    }

    /** The server runs in Energy mode (in None mode nothing needs power). */
    public boolean energyMode() {
        return (data.get(3) & 2) != 0;
    }

    public ItemStack input() {
        return container.getItem(0);
    }

    public ItemStack extra() {
        return container.getItem(1);
    }

    public ItemStack output() {
        return container.getItem(2);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < 3) {
            if (!moveItemStackTo(stack, 3, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.ENCODED_CD.get()) || stack.is(ModItems.SCORE.get())) {
            if (!moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.MP3_PLAYER.get())) {
            if (!moveItemStackTo(stack, 1, 2, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.BLANK_CD.get())) {
            // Fill the cutting slot first; with a Vinyl Record there, the copy slot.
            if (!moveItemStackTo(stack, 0, 1, false) && !moveItemStackTo(stack, 1, 2, false)) {
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
