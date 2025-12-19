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

public class Condensator extends HorizontalFacingBlock {
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