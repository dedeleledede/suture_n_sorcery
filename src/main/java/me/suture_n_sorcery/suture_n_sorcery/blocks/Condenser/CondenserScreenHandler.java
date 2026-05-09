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
        this(syncId, playerInventory, new SimpleInventory(CondenserBlockEntity.SLOT_COUNT), new ArrayPropertyDelegate(CondenserBlockEntity.PROPERTY_COUNT));
    }

    public CondenserScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate properties) {
        super(ModScreenHandlers.CONDENSER_SCREEN, syncId);
        this.inventory = inventory;
        this.properties = properties;

        // dirty gauze input
        this.addSlot(new InputSlot(inventory, CondenserBlockEntity.INPUT_SLOT, 80, 18));

        // clean gauze output
        this.addSlot(new OutputSlot(inventory, CondenserBlockEntity.OUTPUT_SLOT, 80, 54));

        // empty bucket input
        this.addSlot(new BucketSlot(inventory, CondenserBlockEntity.BUCKET_INPUT_SLOT, 43, 18));

        // concentrated blood bucket output
        this.addSlot(new OutputSlot(inventory, CondenserBlockEntity.BUCKET_OUTPUT_SLOT, 43, 54));

        // player inventory
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

        final int MACHINE_SLOTS = CondenserBlockEntity.SLOT_COUNT;
        final int PLAYER_END = this.slots.size();

        if (slotIndex < MACHINE_SLOTS) {
            if (!this.insertItem(stack, MACHINE_SLOTS, PLAYER_END, true)) return ItemStack.EMPTY;
            slot.onQuickTransfer(stack, original);
        } else {
            if (CondenserScreenHandler.PROCESSABLE_INPUTS.contains(stack.getItem())) {
                if (!this.insertItem(stack, CondenserBlockEntity.INPUT_SLOT, CondenserBlockEntity.INPUT_SLOT + 1, false)) return ItemStack.EMPTY;
            }
            else if (stack.isOf(Items.BUCKET)) {
                if (!this.insertItem(stack, CondenserBlockEntity.BUCKET_INPUT_SLOT, CondenserBlockEntity.BUCKET_INPUT_SLOT + 1, false)) return ItemStack.EMPTY;
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
    public int getProgress() { return properties.get(CondenserBlockEntity.PROP_PROGRESS); }
    public int getMaxProgress() { return properties.get(CondenserBlockEntity.PROP_MAX_PROGRESS); }
    public int getTankMl() { return properties.get(CondenserBlockEntity.PROP_TANK_ML); }

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
