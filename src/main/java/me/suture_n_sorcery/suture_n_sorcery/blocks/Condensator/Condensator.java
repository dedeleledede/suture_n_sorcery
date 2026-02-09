package me.suture_n_sorcery.suture_n_sorcery.blocks.Condensator;

import com.mojang.serialization.MapCodec;
import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.state.StateManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import me.suture_n_sorcery.suture_n_sorcery.registries.ModBlockEntities;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class Condensator extends HorizontalFacingBlock implements BlockEntityProvider {
    public static final MapCodec<Condensator> CODEC = Block.createCodec(Condensator::new);

    public Condensator(Settings settings) {
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
        return new CondensatorBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient()) return null;
        return validateTicker(type, CondensatorBlockEntity::tick);
    }

    @SuppressWarnings("unchecked")
    private static <E extends BlockEntity, A extends BlockEntity> @Nullable BlockEntityTicker<A> validateTicker(
            BlockEntityType<A> givenType, BlockEntityTicker<? super E> ticker) {
        return ModBlockEntities.CONDENSATOR_BLOCK_ENTITY == givenType ? (BlockEntityTicker<A>) ticker : null;
    }


    //registry
    public static final Identifier CONDENSATOR_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "condensator");

    public static final RegistryKey<Block> CONDENSATOR_KEY =
            RegistryKey.of(RegistryKeys.BLOCK, CONDENSATOR_ID);

    public static final Block CONDENSATOR =
            new Condensator(Settings.create()
                    .strength(1F)
                    .registryKey(CONDENSATOR_KEY)
                    .requiresTool()
                    .nonOpaque()
            );
}