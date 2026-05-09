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
    // machine slot indices
    public static final int BUCKET_SLOT = RitualLoomBlockEntity.BUCKET_SLOT;
    public static final int STRING_SLOT = RitualLoomBlockEntity.STRING_SLOT;
    public static final int CORE_SLOT   = RitualLoomBlockEntity.CORE_SLOT;

    public static final int CORE_X = 100;
    public static final int CORE_Y = 36;

    public static final int MACHINE_SLOT_COUNT = RitualLoomBlockEntity.MACHINE_SLOT_COUNT;
    private static final int MACHINE_START = 0;
    private static final int MACHINE_END_EXCL = MACHINE_START + MACHINE_SLOT_COUNT;

    // button ids sent through screenhandler clickbutton
    public static final int BTN_PRESSURIZE_START = 200;
    public static final int BTN_PRESSURIZE_STOP  = 201;

    private final Inventory inventory;
    private final PropertyDelegate properties;

    public RitualLoomScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory,
                new net.minecraft.inventory.SimpleInventory(MACHINE_SLOT_COUNT),
                new ArrayPropertyDelegate(RitualLoomBlockEntity.PROPERTY_COUNT)
        );
    }

    // server constructor
    public RitualLoomScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate properties) {
        super(ModScreenHandlers.RITUAL_LOOM_SCREEN, syncId);
        checkSize(inventory, MACHINE_SLOT_COUNT);
        this.inventory = inventory;
        this.properties = properties;

        inventory.onOpen(playerInventory.player);

        // bucket slot
        this.addSlot(new Slot(inventory, BUCKET_SLOT, 6, 18) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return stack.isOf(ConcentratedBloodBucket.CONCENTRATED_BLOOD_BUCKET);
            }
        });

        // string slot
        this.addSlot(new Slot(inventory, STRING_SLOT, 26, 18) {
            @Override
            public boolean canInsert(ItemStack stack) {
                // strings can only feed before a core is inserted
                if (!inventory.getStack(CORE_SLOT).isEmpty()) return false;
                return stack.isOf(Items.STRING);
            }
        });

        // core slot
        this.addSlot(new Slot(inventory, CORE_SLOT, CORE_X, CORE_Y) {

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

                // only one core can be processed at a time
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
        // closing the screen should release the press button
        if (inventory instanceof RitualLoomBlockEntity be) {
            be.setPressurizing(false);
        }
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id != BTN_PRESSURIZE_START && id != BTN_PRESSURIZE_STOP) {
            return super.onButtonClick(player, id);
        }

        // the client must accept the click so minecraft sends it to the server
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

    private boolean canQuickMoveCore(ItemStack stack) {
        return isCoreAllowed(stack)
                && getStrings() >= RitualLoomBlockEntity.REQUIRED_STRINGS
                && getPhase() != RitualLoomBlockEntity.PHASE_PRESSURIZING
                && !this.slots.get(CORE_SLOT).hasStack();
    }

    private boolean tryQuickMoveOneCore(PlayerEntity player, Slot slot, ItemStack original) {
        if (!canQuickMoveCore(original)) return false;

        ItemStack one = original.copy();
        one.setCount(1);

        if (!player.getEntityWorld().isClient() && inventory instanceof RitualLoomBlockEntity be && !be.canInsertCore(one)) {
            return false;
        }

        if (!this.insertItem(one, CORE_SLOT, CORE_SLOT + 1, false)) {
            return false;
        }

        original.decrement(1);
        slot.markDirty();
        if (original.isEmpty()) slot.setStack(ItemStack.EMPTY);
        return true;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        ItemStack empty = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasStack()) return empty;

        ItemStack original = slot.getStack();
        ItemStack copy = original.copy();

        // player inventory slots start right after machine slots
        final int PLAYER_END_EXCL = this.slots.size();

        if (slotIndex >= MACHINE_END_EXCL) {

            // try the core slot first, but only move one item
            if (tryQuickMoveOneCore(player, slot, original)) {
                return copy;
            }

            // let vanilla try the bucket and string slots afterward
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

    // synced values from the block entity property delegate
    public int getBloodMl()       { return properties.get(RitualLoomBlockEntity.PROP_BLOOD_ML); }
    public int getBloodMaxMl()    { return properties.get(RitualLoomBlockEntity.PROP_MAX_BLOOD_ML); }

    public int getPoleStrings()   { return properties.get(RitualLoomBlockEntity.PROP_POLE_STRINGS); }
    public int getSaturatedStrings()  { return properties.get(RitualLoomBlockEntity.PROP_SATURATED_STRINGS); }
    public int getStringsCap()    { return properties.get(RitualLoomBlockEntity.PROP_MAX_STRINGS); }

    public int getPhase()         { return properties.get(RitualLoomBlockEntity.PROP_PHASE); }
    public int getSaturationTicks(){ return properties.get(RitualLoomBlockEntity.PROP_SATURATION_TICKS); }
    public int getPressure()      { return properties.get(RitualLoomBlockEntity.PROP_PRESSURE); }
    public int getCoreNonce()     { return properties.get(RitualLoomBlockEntity.PROP_CORE_NONCE); }
    public int getStringNonce()   { return properties.get(RitualLoomBlockEntity.PROP_STRING_NONCE); }

    public int getRecipeRequiredStrings() { return properties.get(RitualLoomBlockEntity.PROP_ACTIVE_REQUIRED_STRINGS); }
    public int getRecipePressTicks()      { return properties.get(RitualLoomBlockEntity.PROP_ACTIVE_PRESS_TICKS); }
    public int getRecipeBloodCostMl()     { return properties.get(RitualLoomBlockEntity.PROP_ACTIVE_BLOOD_COST_ML); }

    public int getStrings() { return getSaturatedStrings(); }

    public int getScaledBlood(int pixels) {
        int ml = getBloodMl();
        int cap = getBloodMaxMl();
        if (cap <= 0) return 0;
        return (ml * pixels) / cap;
    }
}
