package me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom;

import me.suture_n_sorcery.suture_n_sorcery.items.ConcentratedBloodBucket;
import me.suture_n_sorcery.suture_n_sorcery.registries.ModScreenHandlers;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public class RitualLoomScreenHandler extends ScreenHandler {
    // Machine slot indices
    public static final int BUCKET_SLOT = 0;
    public static final int STRING_SLOT = 1;
    public static final int CORE_SLOT   = 2;

    public static final int CORE_X = 100;
    public static final int CORE_Y = 36;

    public static final int MACHINE_SLOT_COUNT = 3;

    // button ids (ScreenHandler clickButton)
    public static final int BTN_PRESSURIZE_START = 200;
    public static final int BTN_PRESSURIZE_STOP  = 201;

    private static final int PROP_COUNT = 13;

    private final Inventory inventory;
    private final PropertyDelegate properties;

    public RitualLoomScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory,
                new net.minecraft.inventory.SimpleInventory(MACHINE_SLOT_COUNT),
                new ArrayPropertyDelegate(PROP_COUNT)
        );
    }

    // Server constructor
    public RitualLoomScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate properties) {
        super(ModScreenHandlers.RITUAL_LOOM_SCREEN, syncId);
        checkSize(inventory, MACHINE_SLOT_COUNT);
        this.inventory = inventory;
        this.properties = properties;

        inventory.onOpen(playerInventory.player);

        // Bucket sloT
        this.addSlot(new Slot(inventory, BUCKET_SLOT, 6, 18) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return stack.isOf(ConcentratedBloodBucket.CONCENTRATED_BLOOD_BUCKET);
            }
        });

        // String slot
        this.addSlot(new Slot(inventory, STRING_SLOT, 26, 18) {
            @Override
            public boolean canInsert(ItemStack stack) {
                // block string feeding once core exists (keeps phase logic clean)
                if (!inventory.getStack(CORE_SLOT).isEmpty()) return false;
                return stack.isOf(Items.STRING);
            }
        });

        // CORE SLOT
        this.addSlot(new Slot(inventory, RitualLoomBlockEntity.CORE_SLOT, CORE_X, CORE_Y) {

            private boolean ready() {
                if (!this.getStack().isEmpty()) return true;

                // hide while pressurizing
                if (getPhase() == RitualLoomBlockEntity.PHASE_PRESSURIZING) return false;

                // hide until enough strings are saturated
                return getStrings() >= RitualLoomBlockEntity.REQUIRED_STRINGS;
            }

            @Override
            public boolean isEnabled() {
                return ready();
            }

            @Override
            public boolean canInsert(ItemStack stack) {
                if (!ready()) return false;
                if (!isCoreAllowed(stack)) return false;

                // accept stacks the slot will only take 1 due to max count = 1
                return stack.getCount() >= 1;
            }

            @Override public int getMaxItemCount() { return 1; }
            @Override public int getMaxItemCount(ItemStack stack) { return 1; }
        });

        // player inventory
        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);

        addProperties(properties);
    }

    private void addPlayerInventory(PlayerInventory inv) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
    }

    private void addPlayerHotbar(PlayerInventory inv) {
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, 142));
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        // if player closes while holding stop pressurizing
        if (inventory instanceof RitualLoomBlockEntity be) {
            be.setPressurizing(false);
        }
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id != BTN_PRESSURIZE_START && id != BTN_PRESSURIZE_STOP) {
            return super.onButtonClick(player, id);
        }

        // client must return true so the packet is sent
        if (player.getEntityWorld().isClient()) return true;

        if (inventory instanceof RitualLoomBlockEntity be) {
            be.setPressurizing(id == BTN_PRESSURIZE_START);
        }
        return true;
    }

    public static boolean isCoreAllowed(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return RitualLoomRitualHandler.get(stack) != null;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        ItemStack empty = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasStack()) return empty;

        ItemStack original = slot.getStack();
        ItemStack copy = original.copy();

        final int MACHINE_START = 0;
        final int MACHINE_END_EXCL = 3;

        // player inventory slots start right after machine slots
        final int PLAYER_END_EXCL = this.slots.size();

        if (slotIndex >= MACHINE_END_EXCL) {

            // Try core slot FIRST, but only move ONE item
            if (isCoreAllowed(original)
                    && getStrings() >= RitualLoomBlockEntity.REQUIRED_STRINGS
                    && getPhase() != RitualLoomBlockEntity.PHASE_PRESSURIZING
                    && !this.slots.get(RitualLoomBlockEntity.CORE_SLOT).hasStack()) {

                ItemStack one = original.copy();
                one.setCount(1);

                if (!player.getEntityWorld().isClient() && inventory instanceof RitualLoomBlockEntity be) {
                    if (!be.canInsertCore(one)) {
                        // do nothing
                    } else if (this.insertItem(one, RitualLoomBlockEntity.CORE_SLOT, RitualLoomBlockEntity.CORE_SLOT + 1, false)) {
                        original.decrement(1);
                        slot.markDirty();
                        if (original.isEmpty()) slot.setStack(ItemStack.EMPTY);
                        return copy;
                    }
                } else {
                    // client: try insert; server will correct
                    if (this.insertItem(one, RitualLoomBlockEntity.CORE_SLOT, RitualLoomBlockEntity.CORE_SLOT + 1, false)) {
                        original.decrement(1);
                        slot.markDirty();
                        if (original.isEmpty()) slot.setStack(ItemStack.EMPTY);
                        return copy;
                    }
                }
            }

            // let vanilla try other machine slots (bucket/string)
            if (!this.insertItem(original, MACHINE_START, MACHINE_END_EXCL, false)) {
                return empty;
            }

        } else {
            if (!this.insertItem(original, MACHINE_END_EXCL, PLAYER_END_EXCL, true)) {
                return empty;
            }
        }

        if (original.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }

        slot.onTakeItem(player, original);
        return copy;
    }

    // synced values PropertyDelegat
    public int getBloodMl()       { return properties.get(0); }
    public int getBloodMaxMl()    { return properties.get(1); }

    public int getPoleStrings()   { return properties.get(2); }
    public int getSaturatedStrings()  { return properties.get(3); }
    public int getStringsCap()    { return properties.get(4); }

    public int getPhase()         { return properties.get(5); }
    public int getSaturationTicks(){ return properties.get(6); } // now “convert ticks”
    public int getPressure()      { return properties.get(7); }
    public int getCoreNonce()     { return properties.get(8); }
    public int getStringNonce()   { return properties.get(9); }

    public int getRecipeRequiredStrings() { return properties.get(10); }
    public int getRecipePressTicks()      { return properties.get(11); }
    public int getRecipeBloodCostMl()     { return properties.get(12); }

    public int getStrings() { return getSaturatedStrings(); }

    public int getScaledBlood(int pixels) {
        int ml = getBloodMl();
        int cap = getBloodMaxMl();
        if (cap <= 0) return 0;
        return (ml * pixels) / cap;
    }
}
