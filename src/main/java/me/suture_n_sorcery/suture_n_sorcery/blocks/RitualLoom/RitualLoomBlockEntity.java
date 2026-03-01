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

    private static final int[] ALL_SLOTS = new int[]{BUCKET_SLOT, STRING_SLOT, CORE_SLOT};

    public static final int MAX_BLOOD_ML = 10000;
    public static final int BUCKET_ML = 1000;

    public static final int MAX_STRINGS  = 64;
    public static final int REQUIRED_STRINGS = RitualLoomRitualHandler.minRequiredStrings();

    // states
    public static final int PHASE_IDLE          = 0; // not enough saturated strings yet
    public static final int PHASE_SATURATING    = 1; // actively saturating one string
    public static final int PHASE_SATURATED     = 2; // ready for core insertion (strings >= REQUIRED_STRINGS)
    public static final int PHASE_CORE_INSERTED = 3; // core present, pressurize button visible
    public static final int PHASE_PRESSURIZING  = 4; // holding pressurize
    public static final int PHASE_COMPLETE      = 5; // finished this cycle (brief)
    public static final int PHASE_FAILED        = 6; // failed this cycle (brief)

    // saturation tuning
    private static final int BLOOD_PER_STRING = 100;
    private static final int TICKS_PER_STRING = 15;

    // meters
    private int bloodMl = 0;
    private int poleStrings = 0;
    private int saturatedStrings = 0;

    // ritual progress
    private int phase = PHASE_IDLE;
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

    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(3, ItemStack.EMPTY);

    // properties for screen
    private final PropertyDelegate properties = new PropertyDelegate() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> bloodMl;
                case 1 -> MAX_BLOOD_ML;
                case 2 -> poleStrings;
                case 3 -> saturatedStrings;
                case 4 -> MAX_STRINGS;
                case 5 -> phase;
                case 6 -> saturationTicks;
                case 7 -> pressure;
                case 8 -> coreNonce;
                case 9 -> stringNonce;
                case 10 -> activeRequiredStrings;
                case 11 -> activePressTicks;
                case 12 -> activeBloodCostMl;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> bloodMl = value;
                case 2 -> poleStrings = value;
                case 3 -> saturatedStrings = value;
                case 5 -> phase = value;
                case 6 -> saturationTicks = value;
                case 7 -> pressure = value;
                case 8 -> coreNonce = value;
                case 9 -> stringNonce = value;
                case 10 -> activeRequiredStrings = value;
                case 11 -> activePressTicks = value;
                case 12 -> activeBloodCostMl = value;
                default -> { }
            }
        }
        @Override public int size() { return 13; }
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
            if (phase == PHASE_PRESSURIZING)
                phase = (saturatedStrings >= REQUIRED_STRINGS) ? PHASE_SATURATED : PHASE_IDLE;
        } else if (pressed) {
            if (phase == PHASE_SATURATED) phase = PHASE_CORE_INSERTED;
            if (phase != PHASE_CORE_INSERTED && phase != PHASE_PRESSURIZING) {
                pressurizing = false;
                sync();
                return;
            }
            pressurizing = true;
            phase = PHASE_PRESSURIZING;
        } else {
            pressurizing = false;
            if (phase == PHASE_PRESSURIZING) phase = PHASE_CORE_INSERTED;
        }

        sync();
    }

    private void sync() {
        markDirty();
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    // ticking
    public static void tick(World world, BlockPos pos, BlockState state, RitualLoomBlockEntity be) {
        if (world.isClient()) return;

        boolean changed = false;
        boolean corePresent = !be.getStack(CORE_SLOT).isEmpty();
        boolean coreEmpty = !corePresent;

        // passive lowering pressurize
        if (!be.pressurizing && be.pressure > 0) {
            be.pressure = Math.max(0, be.pressure - 15);
            changed = true;
        }

        // update active recipe params (synced to client)
        int oldReq = be.activeRequiredStrings;
        int oldPress = be.activePressTicks;
        int oldBlood = be.activeBloodCostMl;

        if (!coreEmpty && !be.coreIsOutput) {
            var def = RitualLoomRitualHandler.get(be.getStack(CORE_SLOT));
            if (def != null) {
                be.activeRequiredStrings = def.requiredStrings();
                be.activePressTicks = def.pressTicks();
                be.activeBloodCostMl = def.bloodMlCost();
            } else {
                be.activeRequiredStrings = 0;
                be.activePressTicks = 0;
                be.activeBloodCostMl = 0;
            }
        } else {
            be.activeRequiredStrings = 0;
            be.activePressTicks = 0;
            be.activeBloodCostMl = 0;
        }

        if (be.activeRequiredStrings != oldReq || be.activePressTicks != oldPress || be.activeBloodCostMl != oldBlood) {
            changed = true;
        }

        // detect core transitions for ripple
        if (corePresent != be.lastCorePresent) {
            be.lastCorePresent = corePresent;

            be.coreIsOutput = false;         // inserted = input core
            if (corePresent) {
                be.pressTicksElapsed = 0;
                be.pressCostCarry = 0;
                be.pressure = 0;

                be.phase = PHASE_CORE_INSERTED;
            } else {
                be.pressurizing = false;
                be.pressTicksElapsed = 0;
                be.pressCostCarry = 0;
                be.pressure = 0;

                int minReq = RitualLoomRitualHandler.minRequiredStrings();
                be.phase = (be.saturatedStrings >= minReq) ? PHASE_SATURATED : PHASE_IDLE;
            }

            changed = true;
        }

        ItemStack bucket = be.getStack(BUCKET_SLOT);
        if (be.bloodMl + BUCKET_ML <= MAX_BLOOD_ML && bucket.isOf(ConcentratedBloodBucket.CONCENTRATED_BLOOD_BUCKET)) {
            be.bloodMl += BUCKET_ML;
            be.setStack(BUCKET_SLOT, new ItemStack(Items.BUCKET));
            changed = true;
        }

        if (!coreEmpty && !be.pressurizing && !be.coreIsOutput) {
            if (be.phase != PHASE_CORE_INSERTED && be.phase != PHASE_FAILED) {
                be.phase = PHASE_CORE_INSERTED;
                changed = true;
            }
        }

        if (coreEmpty && !be.pressurizing) {
            if (be.phase == PHASE_CORE_INSERTED || be.phase == PHASE_PRESSURIZING) {
                be.pressure = 0;
                be.phase = (be.saturatedStrings >= REQUIRED_STRINGS) ? PHASE_SATURATED : PHASE_IDLE;
                changed = true;
            } else if (be.phase != PHASE_SATURATING) {
                int desired = (be.saturatedStrings >= REQUIRED_STRINGS) ? PHASE_SATURATED : PHASE_IDLE;
                if (be.phase != desired) { be.phase = desired; changed = true; }
            }
        }

        if (coreEmpty && be.poleStrings < MAX_STRINGS) {
            ItemStack stack = be.getStack(STRING_SLOT);

            if (!stack.isEmpty() && stack.isOf(Items.STRING)) {
                be.poleStrings++;

                stack.decrement(1);
                if (stack.isEmpty()) {
                    be.setStack(STRING_SLOT, ItemStack.EMPTY);
                }

                changed = true;
            }
        }

        if (coreEmpty && be.poleStrings > 0 && be.saturatedStrings < MAX_STRINGS) {

            final int baseCost = BLOOD_PER_STRING / TICKS_PER_STRING;
            final int remainder = BLOOD_PER_STRING - (baseCost * TICKS_PER_STRING);

            if (be.phase != PHASE_SATURATING && be.saturatedStrings < REQUIRED_STRINGS) {
                be.phase = PHASE_SATURATING;
                be.saturationTicks = 0;
                changed = true;
            }

            int cost = baseCost + ((be.saturationTicks + 1 >= TICKS_PER_STRING) ? remainder : 0);

            if (be.bloodMl >= cost) {
                be.bloodMl -= cost;
                be.saturationTicks++;

                if (be.saturationTicks >= TICKS_PER_STRING) {
                    be.saturationTicks = 0;
                    be.poleStrings--;
                    be.saturatedStrings++;
                    be.stringNonce++;

                    if (be.saturatedStrings >= REQUIRED_STRINGS && be.getStack(CORE_SLOT).isEmpty()) {
                        be.phase = PHASE_SATURATED;
                    }
                }
                changed = true;

            } else {
                // not enough blood to keep converting
                if (be.phase == PHASE_SATURATING) {
                    be.saturationTicks = 0;
                    be.phase = (be.saturatedStrings >= REQUIRED_STRINGS) ? PHASE_SATURATED : PHASE_IDLE;
                    changed = true;
                }
            }

        } else {
            // not converting
            if (be.phase == PHASE_SATURATING && coreEmpty) {
                be.phase = (be.saturatedStrings >= REQUIRED_STRINGS) ? PHASE_SATURATED : PHASE_IDLE;
                be.saturationTicks = 0;
                changed = true;
            }
        }

        if (be.pressurizing) {
            if (coreEmpty || be.coreIsOutput) {
                be.pressurizing = false;
                be.pressTicksElapsed = 0;
                be.pressCostCarry = 0;
                be.pressure = 0;

                int minReq = RitualLoomRitualHandler.minRequiredStrings();
                be.phase = (be.saturatedStrings >= minReq) ? PHASE_SATURATED : PHASE_IDLE;
                changed = true;

            } else {
                var def = RitualLoomRitualHandler.get(be.getStack(CORE_SLOT));
                if (def == null) {
                    be.pressurizing = false;
                    be.pressTicksElapsed = 0;
                    be.pressCostCarry = 0;
                    be.pressure = 0;
                    be.phase = PHASE_FAILED;
                    changed = true;

                } else if (be.saturatedStrings < def.requiredStrings()) {
                    be.pressurizing = false;
                    be.pressTicksElapsed = 0;
                    be.pressCostCarry = 0;
                    be.pressure = 0;
                    be.phase = PHASE_CORE_INSERTED;
                    changed = true;

                } else {
                    int totalTicks = Math.max(1, def.pressTicks());
                    int totalCost = Math.max(0, def.bloodMlCost());

                    int baseCost = totalCost / totalTicks;
                    int rem = totalCost - baseCost * totalTicks;

                    int cost = baseCost;
                    be.pressCostCarry += rem;
                    if (be.pressCostCarry >= totalTicks) {
                        cost += 1;
                        be.pressCostCarry -= totalTicks;
                    }

                    if (be.bloodMl < cost) {
                        be.pressurizing = false;
                        be.pressTicksElapsed = 0;
                        be.pressCostCarry = 0;
                        be.pressure = 0;
                        be.phase = PHASE_FAILED;

                    } else {
                        be.bloodMl -= cost;
                        be.pressTicksElapsed++;
                        be.pressure = Math.min(MAX_PRESSURE, (be.pressTicksElapsed * MAX_PRESSURE) / totalTicks);
                        be.phase = PHASE_PRESSURIZING;

                        if (be.pressTicksElapsed >= totalTicks) {
                            be.pressurizing = false;
                            be.pressTicksElapsed = 0;
                            be.pressCostCarry = 0;
                            be.pressure = 0;

                            be.saturatedStrings = Math.max(0, be.saturatedStrings - def.requiredStrings());

                            be.setStack(CORE_SLOT, def.result().copy());
                            be.coreIsOutput = true;

                            be.coreNonce++;          // FX trigger: craft complete
                            be.phase = PHASE_COMPLETE;
                        }

                    }
                    changed = true;
                }
            }
        }

        if (be.phase == PHASE_COMPLETE) {
            if (be.getStack(CORE_SLOT).isEmpty()) {
                be.coreIsOutput = false;
                int minReq = RitualLoomRitualHandler.minRequiredStrings();
                int desired = (be.saturatedStrings >= minReq) ? PHASE_SATURATED : PHASE_IDLE;
                if (be.phase != desired) { be.phase = desired; changed = true; }
            }
        } else if (be.phase == PHASE_FAILED) {
            int minReq = RitualLoomRitualHandler.minRequiredStrings();
            int desired = (be.getStack(CORE_SLOT).isEmpty() && be.saturatedStrings >= minReq) ? PHASE_SATURATED :
                    (be.getStack(CORE_SLOT).isEmpty() ? PHASE_IDLE : PHASE_CORE_INSERTED);

            if (be.phase != desired) {
                be.phase = desired;
                changed = true;
            }
        }

        if (changed) {
            be.markDirty();
            world.updateListeners(pos, state, state, 3);

            if (state.contains(RitualLoom.STRINGS)) {
                boolean show = be.saturatedStrings > 0;
                if (state.get(RitualLoom.STRINGS) != show) {
                    world.setBlockState(pos, state.with(RitualLoom.STRINGS, show), 3);
                }
            }
        }
    }

    // nbt
    @Override
    public void writeData(WriteView view) {
        super.writeData(view);
        Inventories.writeData(view, inventory);
        view.putInt("BloodMl", bloodMl);
        view.putInt("PoleStrings", poleStrings);
        view.putInt("Strings", saturatedStrings);
        view.putInt("Phase", phase);
        view.putInt("SaturationTicks", saturationTicks);
        view.putInt("Pressure", pressure);
        view.putInt("CoreNonce", coreNonce);
        view.putInt("StringNonce", stringNonce);
        view.putBoolean("Pressurizing", pressurizing);
        view.putBoolean("CoreIsOutput", coreIsOutput);
    }

    @Override
    public void readData(ReadView view) {
        super.readData(view);
        Inventories.readData(view, inventory);
        bloodMl = view.getInt("BloodMl", 0);
        poleStrings = view.getInt("PoleStrings", 0);
        saturatedStrings = view.getInt("Strings", 0);
        phase = view.getInt("Phase", PHASE_IDLE);
        saturationTicks = view.getInt("SaturationTicks", 0);
        pressure = view.getInt("Pressure", 0);
        coreNonce = view.getInt("CoreNonce", 0);
        stringNonce = view.getInt("StringNonce", 0);
        pressurizing = view.getBoolean("Pressurizing", false);
        coreIsOutput = view.getBoolean("CoreIsOutput", false);
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
        if (slot == CORE_SLOT) return phase != PHASE_PRESSURIZING;
        return true;
    }
}
