package me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom;

import me.suture_n_sorcery.suture_n_sorcery.items.ConcentratedBloodBucket;
import me.suture_n_sorcery.suture_n_sorcery.registries.ModBlockEntities;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

public class RitualLoomBlockEntity extends BlockEntity implements SidedInventory, NamedScreenHandlerFactory {

    // slots
    public static final int BUCKET_SLOT = 0;
    public static final int STRING_SLOT = 1;
    public static final int CORE_SLOT   = 2;
    public static final int MACHINE_SLOT_COUNT = 3;

    private static final int[] ALL_SLOTS = new int[]{BUCKET_SLOT, STRING_SLOT, CORE_SLOT};

    public static final int MAX_BLOOD_ML = 10000;
    public static final int BUCKET_ML = 1000;

    public static final int MAX_STRINGS  = 64;
    public static final int REQUIRED_STRINGS = RitualLoomRitualHandler.minRequiredStrings();

    // synced phase ids used by screens and handlers
    public static final int PHASE_IDLE          = RitualLoomPhase.IDLE.id();
    public static final int PHASE_SATURATING    = RitualLoomPhase.SATURATING.id();
    public static final int PHASE_SATURATED     = RitualLoomPhase.SATURATED.id();
    public static final int PHASE_CORE_INSERTED = RitualLoomPhase.CORE_INSERTED.id();
    public static final int PHASE_PRESSURIZING  = RitualLoomPhase.PRESSURIZING.id();
    public static final int PHASE_COMPLETE      = RitualLoomPhase.COMPLETE.id();
    public static final int PHASE_FAILED        = RitualLoomPhase.FAILED.id();

    // saturation tuning
    private static final int BLOOD_PER_STRING = 100;
    private static final int TICKS_PER_STRING = 15;

    public static final int PROP_BLOOD_ML = 0;
    public static final int PROP_MAX_BLOOD_ML = 1;
    public static final int PROP_POLE_STRINGS = 2;
    public static final int PROP_SATURATED_STRINGS = 3;
    public static final int PROP_MAX_STRINGS = 4;
    public static final int PROP_PHASE = 5;
    public static final int PROP_SATURATION_TICKS = 6;
    public static final int PROP_PRESSURE = 7;
    public static final int PROP_CORE_NONCE = 8;
    public static final int PROP_STRING_NONCE = 9;
    public static final int PROP_ACTIVE_REQUIRED_STRINGS = 10;
    public static final int PROP_ACTIVE_PRESS_TICKS = 11;
    public static final int PROP_ACTIVE_BLOOD_COST_ML = 12;
    public static final int PROPERTY_COUNT = 13;

    private static final String NBT_BLOOD_ML = "BloodMl";
    private static final String NBT_POLE_STRINGS = "PoleStrings";
    private static final String NBT_SATURATED_STRINGS = "Strings";
    private static final String NBT_PHASE = "Phase";
    private static final String NBT_SATURATION_TICKS = "SaturationTicks";
    private static final String NBT_PRESSURE = "Pressure";
    private static final String NBT_CORE_NONCE = "CoreNonce";
    private static final String NBT_STRING_NONCE = "StringNonce";
    private static final String NBT_PRESSURIZING = "Pressurizing";
    private static final String NBT_CORE_IS_OUTPUT = "CoreIsOutput";

    // meters
    private int bloodMl = 0;
    private int poleStrings = 0;
    private int saturatedStrings = 0;

    // ritual progress
    private RitualLoomPhase phase = RitualLoomPhase.IDLE;
    private int pressure = 0;
    private boolean pressurizing = false;

    private int saturationTicks = 0;

    // fx triggers
    private int coreNonce = 0;
    private int stringNonce = 0;
    private boolean lastCorePresent = false;

    private boolean coreIsOutput = false;

    // Recipe-active config (synced to screen)
    private int activeRequiredStrings = 0;
    private int activePressTicks = 0;
    private int activeBloodCostMl = 0;

    // Press progress
    private int pressTicksElapsed = 0;
    private int pressCostCarry = 0;

    // pressurization
    private static final int MAX_PRESSURE = 1000;

    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(MACHINE_SLOT_COUNT, ItemStack.EMPTY);

    // properties for screen
    private final PropertyDelegate properties = new PropertyDelegate() {
        @Override public int get(int index) {
            return switch (index) {
                case PROP_BLOOD_ML -> bloodMl;
                case PROP_MAX_BLOOD_ML -> MAX_BLOOD_ML;
                case PROP_POLE_STRINGS -> poleStrings;
                case PROP_SATURATED_STRINGS -> saturatedStrings;
                case PROP_MAX_STRINGS -> MAX_STRINGS;
                case PROP_PHASE -> phase.id();
                case PROP_SATURATION_TICKS -> saturationTicks;
                case PROP_PRESSURE -> pressure;
                case PROP_CORE_NONCE -> coreNonce;
                case PROP_STRING_NONCE -> stringNonce;
                case PROP_ACTIVE_REQUIRED_STRINGS -> activeRequiredStrings;
                case PROP_ACTIVE_PRESS_TICKS -> activePressTicks;
                case PROP_ACTIVE_BLOOD_COST_ML -> activeBloodCostMl;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case PROP_BLOOD_ML -> bloodMl = value;
                case PROP_POLE_STRINGS -> poleStrings = value;
                case PROP_SATURATED_STRINGS -> saturatedStrings = value;
                case PROP_PHASE -> phase = RitualLoomPhase.fromId(value);
                case PROP_SATURATION_TICKS -> saturationTicks = value;
                case PROP_PRESSURE -> pressure = value;
                case PROP_CORE_NONCE -> coreNonce = value;
                case PROP_STRING_NONCE -> stringNonce = value;
                case PROP_ACTIVE_REQUIRED_STRINGS -> activeRequiredStrings = value;
                case PROP_ACTIVE_PRESS_TICKS -> activePressTicks = value;
                case PROP_ACTIVE_BLOOD_COST_ML -> activeBloodCostMl = value;
                default -> { }
            }
        }
        @Override public int size() { return PROPERTY_COUNT; }
    };

    public RitualLoomBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RITUAL_LOOM_BLOCK_ENTITY, pos, state);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.suture_n_sorcery.ritual_loom");
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory playerInventory, PlayerEntity player) {
        return new RitualLoomScreenHandler(syncId, playerInventory, this, properties);
    }

    // valid cores
    public boolean canInsertCore(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (pressurizing) return false;
        if (!getStack(CORE_SLOT).isEmpty()) return false;
        if (stack.getCount() < 1) return false;
        var def = RitualLoomRitualHandler.get(stack);
        if (def == null) return false;

        return saturatedStrings >= def.requiredStrings();
    }

    // called by screen handler button click(client > server) to start/stop pressurize
    public void setPressurizing(boolean pressed) {
        if (world == null || world.isClient()) return;

        boolean corePresent = !getStack(CORE_SLOT).isEmpty();
        if (!corePresent) {
            pressurizing = false;
            if (phase == RitualLoomPhase.PRESSURIZING)
                phase = idleOrSaturated(saturatedStrings);
        } else if (pressed) {
            if (phase == RitualLoomPhase.SATURATED) phase = RitualLoomPhase.CORE_INSERTED;
            if (phase != RitualLoomPhase.CORE_INSERTED && phase != RitualLoomPhase.PRESSURIZING) {
                pressurizing = false;
                sync();
                return;
            }
            pressurizing = true;
            phase = RitualLoomPhase.PRESSURIZING;
        } else {
            pressurizing = false;
            if (phase == RitualLoomPhase.PRESSURIZING) phase = RitualLoomPhase.CORE_INSERTED;
        }

        sync();
    }

    private void sync() {
        markDirty();
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    private void resetPressProgress() {
        pressTicksElapsed = 0;
        pressCostCarry = 0;
        pressure = 0;
    }

    private void stopPressurizing() {
        pressurizing = false;
        resetPressProgress();
    }

    private static RitualLoomPhase idleOrSaturated(int saturatedStrings) {
        return saturatedStrings >= REQUIRED_STRINGS ? RitualLoomPhase.SATURATED : RitualLoomPhase.IDLE;
    }

    private boolean updateActiveRecipeParams(boolean coreEmpty) {
        int oldReq = activeRequiredStrings;
        int oldPress = activePressTicks;
        int oldBlood = activeBloodCostMl;

        if (!coreEmpty && !coreIsOutput) {
            var def = RitualLoomRitualHandler.get(getStack(CORE_SLOT));
            if (def != null) {
                activeRequiredStrings = def.requiredStrings();
                activePressTicks = def.pressTicks();
                activeBloodCostMl = def.bloodMlCost();
            } else {
                clearActiveRecipeParams();
            }
        } else {
            clearActiveRecipeParams();
        }

        return activeRequiredStrings != oldReq || activePressTicks != oldPress || activeBloodCostMl != oldBlood;
    }

    private void clearActiveRecipeParams() {
        activeRequiredStrings = 0;
        activePressTicks = 0;
        activeBloodCostMl = 0;
    }

    private boolean handleCorePresenceChange(boolean corePresent) {
        if (corePresent == lastCorePresent) return false;

        lastCorePresent = corePresent;
        coreIsOutput = false;         // inserted = input core

        if (corePresent) {
            resetPressProgress();
            phase = RitualLoomPhase.CORE_INSERTED;
        } else {
            stopPressurizing();
            phase = idleOrSaturated(saturatedStrings);
        }

        return true;
    }

    private boolean loadStringFromSlot() {
        if (poleStrings >= MAX_STRINGS) return false;

        ItemStack stack = getStack(STRING_SLOT);
        if (stack.isEmpty() || !stack.isOf(Items.STRING)) return false;

        poleStrings++;

        stack.decrement(1);
        if (stack.isEmpty()) {
            setStack(STRING_SLOT, ItemStack.EMPTY);
        }

        return true;
    }

    private boolean saturateStrings(boolean coreEmpty) {
        if (coreEmpty && poleStrings > 0 && saturatedStrings < MAX_STRINGS) {
            final int baseCost = BLOOD_PER_STRING / TICKS_PER_STRING;
            final int remainder = BLOOD_PER_STRING - (baseCost * TICKS_PER_STRING);

            if (phase != RitualLoomPhase.SATURATING && saturatedStrings < REQUIRED_STRINGS) {
                phase = RitualLoomPhase.SATURATING;
                saturationTicks = 0;
            }

            int cost = baseCost + ((saturationTicks + 1 >= TICKS_PER_STRING) ? remainder : 0);

            if (bloodMl >= cost) {
                bloodMl -= cost;
                saturationTicks++;

                if (saturationTicks >= TICKS_PER_STRING) {
                    saturationTicks = 0;
                    poleStrings--;
                    saturatedStrings++;
                    stringNonce++;

                    if (saturatedStrings >= REQUIRED_STRINGS && getStack(CORE_SLOT).isEmpty()) {
                        phase = RitualLoomPhase.SATURATED;
                    }
                }
                return true;
            }

            if (phase == RitualLoomPhase.SATURATING) {
                saturationTicks = 0;
                phase = idleOrSaturated(saturatedStrings);
                return true;
            }

            return false;
        }

        if (phase == RitualLoomPhase.SATURATING && coreEmpty) {
            phase = idleOrSaturated(saturatedStrings);
            saturationTicks = 0;
            return true;
        }

        return false;
    }

    private boolean pressurizeTick(boolean coreEmpty) {
        if (!pressurizing) return false;

        if (coreEmpty || coreIsOutput) {
            stopPressurizing();
            phase = idleOrSaturated(saturatedStrings);
            return true;
        }

        var def = RitualLoomRitualHandler.get(getStack(CORE_SLOT));
        if (def == null) {
            stopPressurizing();
            phase = RitualLoomPhase.FAILED;
            return true;
        }

        if (saturatedStrings < def.requiredStrings()) {
            stopPressurizing();
            phase = RitualLoomPhase.CORE_INSERTED;
            return true;
        }

        int totalTicks = Math.max(1, def.pressTicks());
        int totalCost = Math.max(0, def.bloodMlCost());
        int cost = nextPressBloodCost(totalTicks, totalCost);

        if (bloodMl < cost) {
            stopPressurizing();
            phase = RitualLoomPhase.FAILED;
            return true;
        }

        bloodMl -= cost;
        pressTicksElapsed++;
        pressure = Math.min(MAX_PRESSURE, (pressTicksElapsed * MAX_PRESSURE) / totalTicks);
        phase = RitualLoomPhase.PRESSURIZING;

        if (pressTicksElapsed >= totalTicks) {
            completePressurization(def);
        }

        return true;
    }

    private int nextPressBloodCost(int totalTicks, int totalCost) {
        int baseCost = totalCost / totalTicks;
        int remainder = totalCost - baseCost * totalTicks;

        int cost = baseCost;
        pressCostCarry += remainder;
        if (pressCostCarry >= totalTicks) {
            cost += 1;
            pressCostCarry -= totalTicks;
        }

        return cost;
    }

    private void completePressurization(RitualLoomRitualHandler.RitualDef def) {
        stopPressurizing();

        saturatedStrings = Math.max(0, saturatedStrings - def.requiredStrings());

        setStack(CORE_SLOT, def.result().copy());
        coreIsOutput = true;

        coreNonce++;          // FX trigger: craft complete
        phase = RitualLoomPhase.COMPLETE;
    }

    private boolean absorbBloodBucket() {
        ItemStack bucket = getStack(BUCKET_SLOT);
        if (bloodMl + BUCKET_ML > MAX_BLOOD_ML || !bucket.isOf(ConcentratedBloodBucket.CONCENTRATED_BLOOD_BUCKET)) {
            return false;
        }

        bloodMl += BUCKET_ML;
        setStack(BUCKET_SLOT, new ItemStack(Items.BUCKET));
        return true;
    }

    private boolean maintainCorePhase(boolean coreEmpty) {
        if (!coreEmpty && !pressurizing && !coreIsOutput) {
            if (phase != RitualLoomPhase.CORE_INSERTED && phase != RitualLoomPhase.FAILED) {
                phase = RitualLoomPhase.CORE_INSERTED;
                return true;
            }
        }

        if (coreEmpty && !pressurizing) {
            if (phase == RitualLoomPhase.CORE_INSERTED || phase == RitualLoomPhase.PRESSURIZING) {
                pressure = 0;
                phase = idleOrSaturated(saturatedStrings);
                return true;
            } else if (phase != RitualLoomPhase.SATURATING) {
                RitualLoomPhase desired = idleOrSaturated(saturatedStrings);
                if (phase != desired) {
                    phase = desired;
                    return true;
                }
            }
        }

        return false;
    }

    private boolean recoverTerminalPhase() {
        if (phase == RitualLoomPhase.COMPLETE) {
            if (getStack(CORE_SLOT).isEmpty()) {
                coreIsOutput = false;
                RitualLoomPhase desired = idleOrSaturated(saturatedStrings);
                if (phase != desired) {
                    phase = desired;
                    return true;
                }
            }
        } else if (phase == RitualLoomPhase.FAILED) {
            RitualLoomPhase desired = getStack(CORE_SLOT).isEmpty()
                    ? idleOrSaturated(saturatedStrings)
                    : RitualLoomPhase.CORE_INSERTED;

            if (phase != desired) {
                phase = desired;
                return true;
            }
        }

        return false;
    }

    private boolean decayPressure() {
        if (pressurizing || pressure <= 0) return false;

        pressure = Math.max(0, pressure - 15);
        return true;
    }

    private void syncChangedState(World world, BlockPos pos, BlockState state) {
        markDirty();
        world.updateListeners(pos, state, state, 3);

        if (state.contains(RitualLoom.STRINGS)) {
            boolean show = saturatedStrings > 0;
            if (state.get(RitualLoom.STRINGS) != show) {
                world.setBlockState(pos, state.with(RitualLoom.STRINGS, show), 3);
            }
        }
    }

    // ticking
    public static void tick(World world, BlockPos pos, BlockState state, RitualLoomBlockEntity be) {
        if (world.isClient()) return;

        boolean changed = false;
        boolean corePresent = !be.getStack(CORE_SLOT).isEmpty();
        boolean coreEmpty = !corePresent;

        if (be.decayPressure()) {
            changed = true;
        }

        if (be.updateActiveRecipeParams(coreEmpty)) {
            changed = true;
        }

        if (be.handleCorePresenceChange(corePresent)) {
            changed = true;
        }

        if (be.absorbBloodBucket()) {
            changed = true;
        }

        if (be.maintainCorePhase(coreEmpty)) {
            changed = true;
        }

        if (coreEmpty && be.loadStringFromSlot()) {
            changed = true;
        }

        if (be.saturateStrings(coreEmpty)) {
            changed = true;
        }

        if (be.pressurizeTick(coreEmpty)) {
            changed = true;
        }

        if (be.recoverTerminalPhase()) {
            changed = true;
        }

        if (changed) {
            be.syncChangedState(world, pos, state);
        }
    }

    // nbt
    @Override
    public void writeData(WriteView view) {
        super.writeData(view);
        Inventories.writeData(view, inventory);
        view.putInt(NBT_BLOOD_ML, bloodMl);
        view.putInt(NBT_POLE_STRINGS, poleStrings);
        view.putInt(NBT_SATURATED_STRINGS, saturatedStrings);
        view.putInt(NBT_PHASE, phase.id());
        view.putInt(NBT_SATURATION_TICKS, saturationTicks);
        view.putInt(NBT_PRESSURE, pressure);
        view.putInt(NBT_CORE_NONCE, coreNonce);
        view.putInt(NBT_STRING_NONCE, stringNonce);
        view.putBoolean(NBT_PRESSURIZING, pressurizing);
        view.putBoolean(NBT_CORE_IS_OUTPUT, coreIsOutput);
    }

    @Override
    public void readData(ReadView view) {
        super.readData(view);
        Inventories.readData(view, inventory);
        bloodMl = view.getInt(NBT_BLOOD_ML, 0);
        poleStrings = view.getInt(NBT_POLE_STRINGS, 0);
        saturatedStrings = view.getInt(NBT_SATURATED_STRINGS, 0);
        phase = RitualLoomPhase.fromId(view.getInt(NBT_PHASE, PHASE_IDLE));
        saturationTicks = view.getInt(NBT_SATURATION_TICKS, 0);
        pressure = view.getInt(NBT_PRESSURE, 0);
        coreNonce = view.getInt(NBT_CORE_NONCE, 0);
        stringNonce = view.getInt(NBT_STRING_NONCE, 0);
        pressurizing = view.getBoolean(NBT_PRESSURIZING, false);
        coreIsOutput = view.getBoolean(NBT_CORE_IS_OUTPUT, false);
    }

    // sidedinv / inv
    @Override public int size() { return inventory.size(); }
    @Override public boolean isEmpty() { return inventory.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getStack(int slot) { return inventory.get(slot); }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack res = Inventories.splitStack(inventory, slot, amount);
        if (!res.isEmpty()) markDirty();
        return res;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack res = Inventories.removeStack(inventory, slot);
        if (!res.isEmpty()) markDirty();
        return res;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        inventory.set(slot, stack);
        markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        if (world == null) return false;
        if (world.getBlockEntity(pos) != this) return false;
        return player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override public void clear() { inventory.clear(); markDirty(); }

    @Override public int[] getAvailableSlots(Direction side) { return ALL_SLOTS; }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        if (slot == BUCKET_SLOT) return stack.isOf(ConcentratedBloodBucket.CONCENTRATED_BLOOD_BUCKET) && bloodMl < MAX_BLOOD_ML;
        if (slot == STRING_SLOT) return stack.isOf(Items.STRING) && saturatedStrings < MAX_STRINGS && getStack(CORE_SLOT).isEmpty();
        if (slot == CORE_SLOT) return canInsertCore(stack);
        return false;
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        // lock the core during pressurize
        if (slot == CORE_SLOT) return phase != RitualLoomPhase.PRESSURIZING;
        return true;
    }
}
