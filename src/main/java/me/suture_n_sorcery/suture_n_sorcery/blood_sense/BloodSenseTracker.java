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
    private static final int MAX_TRACES_PER_WORLD = 512;
    private static final int PRUNE_INTERVAL_TICKS = 20 * 30;

    private static final int TRACE_MERGE_RADIUS = 4;
    private static final int MAX_TRACE_STRENGTH = 1800;

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
        int strength = 28 + Math.round(entity.getMaxHealth() * 2.5f);
        record(world, BloodSenseTraceType.DEATH, entity.getBlockPos(), strength);
    }

    public static void recordRitual(ServerWorld world, BlockPos pos, int strength) {
        record(world, BloodSenseTraceType.RITUAL, pos, Math.max(35, strength));
    }

    public static void recordDebug(ServerWorld world, BloodSenseTraceType type, BlockPos pos, int strength, int ageTicks) {
        prune(world);

        BloodSenseWorldState state = state(world);
        state.traces().add(new BloodSenseWorldState.StoredTrace(
                type.ordinal(),
                pos.toImmutable(),
                world.getTime() - Math.max(0, ageTicks),
                Math.min(MAX_TRACE_STRENGTH, Math.max(1, strength))
        ));

        if (state.traces().size() > MAX_TRACES_PER_WORLD) {
            state.traces().remove(0);
        }

        state.markDirty();
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

        int mergeIndex = findMergeTarget(traces, type, pos);
        long now = world.getTime();

        if (mergeIndex >= 0) {
            BloodSenseWorldState.StoredTrace old = traces.get(mergeIndex);

            int oldStrength = old.strength();
            int incomingStrength = Math.max(1, strength);
            int combinedStrength = Math.min(MAX_TRACE_STRENGTH, oldStrength + incomingStrength);

            BlockPos mergedPos = weightedMergePos(old.pos(), oldStrength, pos, incomingStrength);

            traces.set(mergeIndex, new BloodSenseWorldState.StoredTrace(
                    type.ordinal(),
                    mergedPos,
                    now,
                    combinedStrength
            ));
        } else {
            traces.add(new BloodSenseWorldState.StoredTrace(
                    type.ordinal(),
                    pos.toImmutable(),
                    now,
                    Math.min(MAX_TRACE_STRENGTH, Math.max(1, strength))
            ));
        }

        if (traces.size() > MAX_TRACES_PER_WORLD) {
            traces.sort(Comparator
                    .comparingInt(BloodSenseWorldState.StoredTrace::strength)
                    .thenComparingLong(BloodSenseWorldState.StoredTrace::createdTime));

            traces.subList(0, traces.size() - MAX_TRACES_PER_WORLD).clear();
        }

        state.markDirty();
    }

    private static int findMergeTarget(List<BloodSenseWorldState.StoredTrace> traces, BloodSenseTraceType type, BlockPos pos) {
        int bestIndex = -1;
        long bestDistance = Long.MAX_VALUE;
        long mergeRadiusSquared = (long) TRACE_MERGE_RADIUS * TRACE_MERGE_RADIUS;

        for (int i = 0; i < traces.size(); i++) {
            BloodSenseWorldState.StoredTrace trace = traces.get(i);
            if (trace.type() != type.ordinal()) continue;

            long distance = (long) trace.pos().getSquaredDistance(pos);
            if (distance <= mergeRadiusSquared && distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    private static BlockPos weightedMergePos(BlockPos a, int aStrength, BlockPos b, int bStrength) {
        int total = Math.max(1, aStrength + bStrength);

        int x = Math.round((a.getX() * aStrength + b.getX() * bStrength) / (float) total);
        int y = Math.round((a.getY() * aStrength + b.getY() * bStrength) / (float) total);
        int z = Math.round((a.getZ() * aStrength + b.getZ() * bStrength) / (float) total);

        return new BlockPos(x, y, z);
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
