package me.suture_n_sorcery.suture_n_sorcery.blocks.Condenser;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

public interface CondenserVoxel {

    VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos);

    int getOpacity(BlockState state, BlockView world, BlockPos pos);

    boolean isTransparent(BlockState state, BlockView world, BlockPos pos);
}
