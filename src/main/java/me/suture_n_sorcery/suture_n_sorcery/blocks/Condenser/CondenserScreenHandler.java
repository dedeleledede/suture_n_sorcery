package me.suture_n_sorcery.suture_n_sorcery.blocks.Condenser;

import me.suture_n_sorcery.suture_n_sorcery.registries.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.Set;

public class CondenserScreenHandler extends ScreenHandler {

    // 0 = progress, 1 = maxProgress, 2 = tankMl
    private final PropertyDelegate properties;
    private final Inventory inventory;

    public static final Set<Item> PROCESSABLE_INPUTS = Set.of(
            me.suture_n_sorcery.suture_n_sorcery.items.DirtyGauze.DIRTY_GAUZE
    );

        private static final class InputSlot extends Slot {
            public InputSlot(Inventory inv, int index, int x, int y) { super(inv, index, x, y); }
            @Override public boolean canInsert(ItemStack stack) {
                return PROCESSABLE_INPUTS.contains(stack.getItem());
            }
        }

        private static final class OutputSlot extends Slot {
            public OutputSlot(Inventory inv, int index, int x, int y) { super(inv, index, x, y); }
            @Override public boolean canInsert(ItemStack stack) {
                return false;
            }
        }

        private static final class BucketSlot extends Slot {
            public BucketSlot(Inventory inv, int index, int x, int y) { super(inv, index, x, y); }
            @Override public boolean canInsert(ItemStack stack) { return stack.isOf(Items.BUCKET); }
        }

    public CondenserScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(CondenserBlockEntity.slot), new ArrayPropertyDelegate(3));
    }

    public CondenserScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate properties) {
        super(ModScreenHandlers.CONDENSER_SCREEN, syncId);
        this.inventory = inventory;
        this.properties = properties;

        // INPUT slot
        this.addSlot(new InputSlot(inventory, 0, 80, 18));

        // OUTPUT slot
        this.addSlot(new OutputSlot(inventory, 1, 80, 54));

        // BUCKET slot
        this.addSlot(new BucketSlot(inventory, 2, 43, 18));

        // OUTPUT BUCKET slot
        this.addSlot(new OutputSlot(inventory, 3, 43, 54));

        // Player inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        this.addProperties(properties);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasStack()) return ItemStack.EMPTY;

        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();

        final int MACHINE_SLOTS = CondenserBlockEntity.slot;
        final int PLAYER_END = this.slots.size();

        if (slotIndex < MACHINE_SLOTS) {
            if (!this.insertItem(stack, MACHINE_SLOTS, PLAYER_END, true)) return ItemStack.EMPTY;
            slot.onQuickTransfer(stack, original);
        } else {
            if (CondenserScreenHandler.PROCESSABLE_INPUTS.contains(stack.getItem())) {
                if (!this.insertItem(stack, 0, 1, false)) return ItemStack.EMPTY;
            }
            else if (stack.isOf(Items.BUCKET)) {
                if (!this.insertItem(stack, 2, 3, false)) return ItemStack.EMPTY;
            }
            else {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }
        return original;
    }


    @Override
    public boolean canUse(PlayerEntity player) {
        return inventory.canPlayerUse(player);
    }
    public int getProgress() { return properties.get(0); }
    public int getMaxProgress() { return properties.get(1); }
    public int getTankMl() { return properties.get(2); }

    public int getScaledProgress(int pixels) {
        int p = getProgress();
        int max = getMaxProgress();
        if (max <= 0 || p <= 0) return 0;
        return p * pixels / max;
    }

    public int getScaledTank(int pixels) {
        int ml = getTankMl();
        if (ml <= 0) return 0;
        return ml * pixels / CondenserBlockEntity.TANK_CAPACITY_ML;
    }
}
