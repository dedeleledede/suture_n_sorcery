package me.suture_n_sorcery.suture_n_sorcery.blocks.Condenser;

import com.mojang.serialization.MapCodec;
import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import net.minecraft.block.*;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import me.suture_n_sorcery.suture_n_sorcery.registries.ModBlockEntities;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class Condenser extends HorizontalFacingBlock implements BlockEntityProvider, CondenserVoxel {
    public static final MapCodec<Condenser> CODEC = Block.createCodec(Condenser::new);

    public Condenser(Settings settings) {
        super(settings);
        this.setDefaultState(this.getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                 PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof NamedScreenHandlerFactory factory) {
            player.openHandledScreen(factory);
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    // --- BlockEntityProvider ---
    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CondenserBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient()) return null;
        return validateTicker(type, CondenserBlockEntity::tick);
    }

    @SuppressWarnings("unchecked")
    private static <E extends BlockEntity, A extends BlockEntity> @Nullable BlockEntityTicker<A> validateTicker(
            BlockEntityType<A> givenType, BlockEntityTicker<? super E> ticker) {
        return ModBlockEntities.CONDENSATOR_BLOCK_ENTITY == givenType ? (BlockEntityTicker<A>) ticker : null;
    }

    // block hitbox

    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 14, 16);

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getRaycastShape(BlockState state, BlockView world, BlockPos pos) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
        return SHAPE;

        // renderizar faces de vizinhos por dentro
        // return VoxelShapes.empty();
    }

    @Override
    public int getOpacity(BlockState state, BlockView world, BlockPos pos) {
        return 0;
    }

    @Override
    public boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
        return true;
    }

    @Override
    protected float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos) {
        return 1.0F;
    }

    //registry
    public static final Identifier CONDENSATOR_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "condensator");

    public static final RegistryKey<Block> CONDENSATOR_KEY =
            RegistryKey.of(RegistryKeys.BLOCK, CONDENSATOR_ID);

    public static final Block CONDENSATOR =
            new Condenser(Settings.create()
                    .strength(1F)
                    .registryKey(CONDENSATOR_KEY)
                    .requiresTool()
                    .sounds(BlockSoundGroup.STONE)
            );
}