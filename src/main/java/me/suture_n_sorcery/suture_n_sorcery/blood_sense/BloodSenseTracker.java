package me.suture_n_sorcery.suture_n_sorcery.blood_sense;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Comparator;
import java.util.List;

public final class BloodSenseTracker {

    private static final int TRACE_LIFETIME_TICKS = 20 * 60 * 12;
    private static final int MAX_TRACES_PER_WORLD = 256;
    private static final int PRUNE_INTERVAL_TICKS = 20 * 30;

    private BloodSenseTracker() {
    }

    public static void registerBloodSenseEvents() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity.getEntityWorld() instanceof ServerWorld world) {
                recordDeath(world, entity);
            }
        });

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % PRUNE_INTERVAL_TICKS == 0) {
                prune(world);
            }
        });
    }

    public static void recordDeath(ServerWorld world, LivingEntity entity) {
        int strength = Math.min(100, 28 + Math.round(entity.getMaxHealth() * 2.5f));
        record(world, BloodSenseTraceType.DEATH, entity.getBlockPos(), strength);
    }

    public static void recordRitual(ServerWorld world, BlockPos pos, int strength) {
        record(world, BloodSenseTraceType.RITUAL, pos, Math.max(35, Math.min(100, strength)));
    }

    public static List<BloodSenseTrace> recentTraces(ServerWorld world, BlockPos center, int radius) {
        prune(world);

        long radiusSquared = (long)radius * radius;
        return state(world).traces().stream()
                .map(trace -> new BloodSenseTrace(
                        BloodSenseTraceType.byId(trace.type()),
                        world.getRegistryKey(),
                        trace.pos(),
                        trace.createdTime(),
                        trace.strength()
                ))
                .filter(trace -> trace.pos().getSquaredDistance(center) <= radiusSquared)
                .sorted(Comparator.comparingInt(trace -> trace.pos().getManhattanDistance(center)))
                .toList();
    }

    private static void record(ServerWorld world, BloodSenseTraceType type, BlockPos pos, int strength) {
        prune(world);

        BloodSenseWorldState state = state(world);
        List<BloodSenseWorldState.StoredTrace> traces = state.traces();
        traces.add(new BloodSenseWorldState.StoredTrace(type.ordinal(), pos.toImmutable(), world.getTime(), strength));

        if (traces.size() > MAX_TRACES_PER_WORLD) {
            traces.subList(0, traces.size() - MAX_TRACES_PER_WORLD).clear();
        }

        state.markDirty();
    }

    private static void prune(ServerWorld world) {
        BloodSenseWorldState state = state(world);
        List<BloodSenseWorldState.StoredTrace> traces = state.traces();

        long now = world.getTime();
        boolean removed = traces.removeIf(trace -> now - trace.createdTime() > TRACE_LIFETIME_TICKS);
        if (removed) {
            state.markDirty();
        }
    }

    private static BloodSenseWorldState state(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(BloodSenseWorldState.TYPE);
    }
}
