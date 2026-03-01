package me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import me.suture_n_sorcery.suture_n_sorcery.registries.ModBlockEntities;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;
import org.jetbrains.annotations.Nullable;

public class RitualLoom extends Block implements BlockEntityProvider {

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new RitualLoomBlockEntity(pos, state);
    }

    public static final EnumProperty<RitualLoomPart> PART = EnumProperty.of("part", RitualLoomPart.class);

    public static final BooleanProperty STRINGS = BooleanProperty.of("strings");

    public static final BooleanProperty LEFT = BooleanProperty.of("left");

    public RitualLoom(Settings settings) {
        super(settings);
        this.setDefaultState(this.getDefaultState()
                .with(Properties.HORIZONTAL_FACING, Direction.NORTH)
                .with(PART, RitualLoomPart.MAIN)
                .with(LEFT, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(Properties.HORIZONTAL_FACING, PART, LEFT, STRINGS);

    }

    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
        World world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();

        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        Direction right = facing.rotateYCounterclockwise();
        Direction left  = facing.rotateYClockwise();

        BlockPos rightPos = pos.offset(right);
        BlockPos leftPos  = pos.offset(left);

        if (ctx.getPlayer() == null) return null;
        ShapeContext shapeContext = ShapeContext.of(ctx.getPlayer());

        BlockState mainState = this.getDefaultState()
                .with(Properties.HORIZONTAL_FACING, facing)
                .with(PART, RitualLoomPart.MAIN)
                .with(LEFT, false)
                .with(STRINGS, false);

        BlockState extState = mainState.with(PART, RitualLoomPart.EXTENSION);

        boolean canMainHere = world.getWorldBorder().contains(pos)
                && world.getBlockState(pos).canReplace(ctx)
                && world.canPlace(mainState, pos, shapeContext);

        boolean canExtRight = world.getWorldBorder().contains(rightPos)
                && world.getBlockState(rightPos).canReplace(ctx)
                && world.canPlace(extState, rightPos, shapeContext);

        if (canMainHere && canExtRight) {
            return mainState;
        }

        boolean canExtHere = world.getWorldBorder().contains(pos)
                && world.getBlockState(pos).canReplace(ctx)
                && world.canPlace(extState, pos, shapeContext);

        boolean canMainLeft = world.getWorldBorder().contains(leftPos)
                && world.getBlockState(leftPos).canReplace(ctx)
                && world.canPlace(mainState, leftPos, shapeContext);

        if (canExtHere && canMainLeft) {
            return extState;
        }

        return null;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);

        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        Direction right = facing.rotateYCounterclockwise();
        Direction left  = facing.rotateYClockwise();

        BlockPos otherPos;
        BlockState otherState;

        if (state.get(PART) == RitualLoomPart.MAIN) {
            otherPos = pos.offset(right);
            otherState = state.with(PART, RitualLoomPart.EXTENSION);
        } else {
            otherPos = pos.offset(left);
            otherState = state.with(PART, RitualLoomPart.MAIN);
        }

        if (!world.getWorldBorder().contains(otherPos)) return;
        if (!world.getBlockState(otherPos).isReplaceable()) return;

        // Collision/entity-safe check
        assert placer != null;
        ShapeContext shapeContext = ShapeContext.of(placer);
        if (!world.canPlace(otherState, otherPos, shapeContext)) return;

        world.setBlockState(otherPos, otherState, Block.NOTIFY_ALL);
    }

    private static final ThreadLocal<Boolean> SNS_BREAKING_OTHER_HALF =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (SNS_BREAKING_OTHER_HALF.get()) {
            return super.onBreak(world, pos, state, player);
        }

        RitualLoomPart part = state.get(PART);
        BlockPos otherPos = pos.offset(getLinkDirectionToOtherHalf(state));
        BlockState other = world.getBlockState(otherPos);

        if (other.isOf(this) && other.get(PART) != part) {
            SNS_BREAKING_OTHER_HALF.set(Boolean.TRUE);
            try {
                world.breakBlock(otherPos, false, player);
            } finally {
                SNS_BREAKING_OTHER_HALF.set(Boolean.FALSE);
            }
        }

        return super.onBreak(world, pos, state, player);
    }

    @Override
    protected BlockState getStateForNeighborUpdate(
            BlockState state,
            WorldView world,
            ScheduledTickView tickView,
            BlockPos pos,
            Direction direction,
            BlockPos neighborPos,
            BlockState neighborState,
            Random random
    ) {
        return super.getStateForNeighborUpdate(state, world, tickView, pos, direction, neighborPos, neighborState, random);
    }

    private static Direction getRight(BlockState state) {
        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        return facing.rotateYCounterclockwise();
    }

    private static Direction getLeft(BlockState state) {
        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        return facing.rotateYClockwise();
    }

    private static Direction getLinkDirectionToOtherHalf(BlockState state) {
        return state.get(PART) == RitualLoomPart.MAIN ? getRight(state) : getLeft(state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;

        BlockPos mainPos = pos;
        if (state.get(PART) == RitualLoomPart.EXTENSION) {
            mainPos = pos.offset(getLinkDirectionToOtherHalf(state)); // EXTENSION -> MAIN
        }

        BlockEntity be = world.getBlockEntity(mainPos);
        if (be instanceof RitualLoomBlockEntity factory) {
            player.openHandledScreen(factory);
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient()) return null;
        if (state.get(PART) != RitualLoomPart.MAIN) return null;

        if (type != ModBlockEntities.RITUAL_LOOM_BLOCK_ENTITY) return null;

        return (w, p, s, be) -> RitualLoomBlockEntity.tick(w, p, s, (RitualLoomBlockEntity) be);
    }

    //registry
    public static final Identifier RITUAL_LOOM_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "ritual_loom");

    public static final RegistryKey<Block> RITUAL_LOOM_KEY =
            RegistryKey.of(RegistryKeys.BLOCK, RITUAL_LOOM_ID);

    public static final Block RITUAL_LOOM =
            new RitualLoom(Settings.create()
                    .strength(1.8F)
                    .registryKey(RITUAL_LOOM_KEY)
                    .nonOpaque()
                    .sounds(BlockSoundGroup.WOOD)
            );
}
