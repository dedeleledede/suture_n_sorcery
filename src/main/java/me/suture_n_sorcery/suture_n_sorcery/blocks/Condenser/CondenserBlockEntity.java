package me.suture_n_sorcery.suture_n_sorcery.blocks.Condenser;

import me.suture_n_sorcery.suture_n_sorcery.items.DirtyGauze;
import me.suture_n_sorcery.suture_n_sorcery.items.Gauze;
import me.suture_n_sorcery.suture_n_sorcery.registries.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
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

import static me.suture_n_sorcery.suture_n_sorcery.items.ConcentratedBloodBucket.CONCENTRATED_BLOOD_BUCKET;

public class CondenserBlockEntity extends BlockEntity implements SidedInventory, NamedScreenHandlerFactory {

    public static final int TANK_CAPACITY_ML = 5000;
    public static final int BUCKET_AMOUNT_ML = 1000;

    public static final int ML_PER_DIRTY_GAUZE = 250;
    public static final int MAX_PROGRESS = 200;

    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int BUCKET_INPUT_SLOT = 2;
    public static final int BUCKET_OUTPUT_SLOT = 3;
    public static final int SLOT_COUNT = 4;

    public static final int PROP_PROGRESS = 0;
    public static final int PROP_MAX_PROGRESS = 1;
    public static final int PROP_TANK_ML = 2;
    public static final int PROPERTY_COUNT = 3;

    private static final String NBT_PROGRESS = "Progress";
    private static final String NBT_TANK_ML = "TankMl";

    private int progress = 0;
    private int tankMl = 0;

    public CondenserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONDENSATOR_BLOCK_ENTITY, pos, state);
    }

    // server tick entrypoint
    public static void tick(World world, BlockPos pos, BlockState state, CondenserBlockEntity be) {
        if (world.isClient()) return;
        be.tryFillBuckets();

        if (!be.canProcess()) {
            if (be.progress != 0) {
                be.progress = 0;
                be.markDirty();
            }
            return;
        }

        be.progress++;
        if (be.progress >= MAX_PROGRESS) {
            be.progress = 0;
            be.processOne();
        }

        be.markDirty();
    }

    private boolean canProcess() {
        ItemStack in = items.get(INPUT_SLOT);
        if (in.isEmpty() || !in.isOf(DirtyGauze.DIRTY_GAUZE)) return false;
        if (tankMl + ML_PER_DIRTY_GAUZE > TANK_CAPACITY_ML) return false;

        Item cleanItem = Registries.ITEM.get(Gauze.GAUZE_ID);
        if (cleanItem == Items.AIR) return false;

        ItemStack out = items.get(OUTPUT_SLOT);
        if (out.isEmpty()) return true;
        if (!out.isOf(cleanItem)) return false;
        return out.getCount() < out.getMaxCount();
    }

    private void processOne() {
        Item cleanItem = Registries.ITEM.get(Gauze.GAUZE_ID);
        if (cleanItem == Items.AIR) return;

        items.get(INPUT_SLOT).decrement(1);

        ItemStack out = items.get(OUTPUT_SLOT);
        if (out.isEmpty()) items.set(OUTPUT_SLOT, new ItemStack(cleanItem));
        else out.increment(1);

        tankMl = Math.min(TANK_CAPACITY_ML, tankMl + ML_PER_DIRTY_GAUZE);
    }

    private boolean canOutputTo(ItemStack toAdd) {
        ItemStack out = items.get(BUCKET_OUTPUT_SLOT);
        if (out.isEmpty()) return true;
        if (!ItemStack.areItemsEqual(out, toAdd)) return false;
        return out.getCount() < out.getMaxCount();
    }

    private void tryFillBuckets() {
        ItemStack inBuckets = items.get(BUCKET_INPUT_SLOT);
        if (!inBuckets.isOf(Items.BUCKET)) return;
        if (tankMl < BUCKET_AMOUNT_ML) return;

        ItemStack produced = new ItemStack(CONCENTRATED_BLOOD_BUCKET);

        if (!canOutputTo(produced)) return;

        inBuckets.decrement(1);
        items.set(BUCKET_INPUT_SLOT, inBuckets);

        ItemStack out = items.get(BUCKET_OUTPUT_SLOT);
        if (out.isEmpty()) items.set(BUCKET_OUTPUT_SLOT, produced);
        else {
            out.increment(1);
            items.set(3, out);
        }

        tankMl -= BUCKET_AMOUNT_ML;
        markDirty();
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.suture_n_sorcery.condenser");
    }

    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(SLOT_COUNT, ItemStack.EMPTY);

    private final PropertyDelegate properties = new PropertyDelegate() {
        @Override public int get(int index) {
            return switch (index) {
                case PROP_PROGRESS -> progress;
                case PROP_MAX_PROGRESS -> MAX_PROGRESS;
                case PROP_TANK_ML -> tankMl;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {}
        @Override public int size() { return PROPERTY_COUNT; }
    };

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new CondenserScreenHandler(syncId, playerInventory, this, properties);
    }

    // persistent state
    @Override
    public void writeData(WriteView view) {
        super.writeData(view);
        Inventories.writeData(view, items);
        view.putInt(NBT_PROGRESS, progress);
        view.putInt(NBT_TANK_ML, tankMl);
    }

    @Override
    public void readData(ReadView view) {
        super.readData(view);
        Inventories.readData(view, items);
        progress = view.getInt(NBT_PROGRESS, 0);
        tankMl = view.getInt(NBT_TANK_ML, 0);
    }


    // inventory access
    @Override public int size() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getStack(int slot) { return items.get(slot); }
    @Override public ItemStack removeStack(int slot, int amount) { return Inventories.splitStack(items, slot, amount); }
    @Override public ItemStack removeStack(int slot) { return Inventories.removeStack(items, slot); }
    @Override public void setStack(int slot, ItemStack stack) { items.set(slot, stack); markDirty(); }
    @Override public void clear() { items.clear(); markDirty(); }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return world != null
                && world.getBlockEntity(pos) == this
                && player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    private static final int[] UP = new int[]{INPUT_SLOT};
    private static final int[] DOWN = new int[]{OUTPUT_SLOT};
    private static final int[] SIDE = new int[]{INPUT_SLOT};

    @Override
    public int[] getAvailableSlots(Direction side) {
        if (side == Direction.UP) return UP;
        if (side == Direction.DOWN) return DOWN;
        return SIDE;
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        return slot == INPUT_SLOT && stack.isOf(DirtyGauze.DIRTY_GAUZE);
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return slot == OUTPUT_SLOT;
    }
}
