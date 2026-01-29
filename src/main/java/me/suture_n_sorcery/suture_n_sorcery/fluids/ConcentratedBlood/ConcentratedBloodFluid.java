package me.suture_n_sorcery.suture_n_sorcery.fluids.ConcentratedBlood;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import me.suture_n_sorcery.suture_n_sorcery.items.ConcentratedBloodBucket;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Item;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

public class ConcentratedBloodFluid extends FlowableFluid {

    public static final Identifier CONCENTRATED_BLOOD_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "concentrated_blood");

    public static final Identifier FLOWING_CONCENTRATED_BLOOD_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "flowing_concentrated_blood");

    public static final FlowableFluid CONCENTRATED_BLOOD = new ConcentratedBloodFluid.Still();

    public static final FlowableFluid FLOWING_CONCENTRATED_BLOOD = new ConcentratedBloodFluid.Flowing();

    public Fluid getStill(){
        return CONCENTRATED_BLOOD;
    }

    @Override
    public boolean matchesType(Fluid fluid) {
        return fluid == CONCENTRATED_BLOOD || fluid == FLOWING_CONCENTRATED_BLOOD;
    }

    @Override
    public Fluid getFlowing() {
        return FLOWING_CONCENTRATED_BLOOD;
    }

    @Override
    public Item getBucketItem(){
        return ConcentratedBloodBucket.CONCENTRATED_BLOOD_BUCKET;
    }

    @Override
    protected boolean isInfinite(ServerWorld world) {
        return false;
    }

    @Override
    protected void beforeBreakingBlock(WorldAccess world, BlockPos pos, BlockState state) {
        BlockEntity be = state.hasBlockEntity() ? world.getBlockEntity(pos) : null;
        Block.dropStacks(state, world, pos, be);
    }

    @Override
    protected int getLevelDecreasePerBlock(WorldView world) {
        return 3;
    }

    @Override
    public int getLevel(FluidState state) {
        return state.get(LEVEL);
    }

    @Override
    public int getTickRate(WorldView world) {
        return 25;
    }

    @Override
    protected float getBlastResistance() {
        return 10.0F;
    }

    @Override
    protected int getMaxFlowDistance(WorldView world) {
        return 2;
    }

    @Override
    protected void onEntityCollision(net.minecraft.world.World world, BlockPos pos, net.minecraft.entity.Entity entity, net.minecraft.entity.EntityCollisionHandler handler) {
        // horizontal drag
        entity.setVelocity(entity.getVelocity().multiply(0.35, 1.0, 0.35));

        entity.velocityDirty = true;
    }

    @Override
    protected boolean canBeReplacedWith(FluidState state, BlockView world, BlockPos pos, Fluid fluid, Direction direction) {
        return direction == Direction.DOWN && !fluid.matchesType(getStill());
    }

    @Override
    protected BlockState toBlockState(FluidState state) {
        return ConcentratedBloodBlock.CONCENTRATED_BLOOD_BLOCK.getDefaultState() //concentrated blood block dps
                .with(Properties.LEVEL_15, getBlockStateLevel(state));
    }

    @Override
    public boolean isStill(FluidState state) {
        return false;
    }

    public static class Flowing extends ConcentratedBloodFluid {
        @Override
        protected void appendProperties(StateManager.Builder<Fluid, FluidState> builder) {
            super.appendProperties(builder);
            builder.add(LEVEL);
        }
    }

    public static class Still extends ConcentratedBloodFluid {
        @Override
        public int getLevel(FluidState state) {
            return 8;
        }

        @Override
        public boolean isStill(FluidState state) {
            return true;
        }
    }

    public ConcentratedBloodFluid()
	{}
}
