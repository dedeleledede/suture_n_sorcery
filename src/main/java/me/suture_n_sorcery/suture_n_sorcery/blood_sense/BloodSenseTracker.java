package me.suture_n_sorcery.suture_n_sorcery.blood_sense;

import me.suture_n_sorcery.suture_n_sorcery.items.BloodSenseTools;
import net.minecraft.entity.ItemEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BloodSenseTracker {

    private static final int TRACE_LIFETIME_TICKS = 20 * 60 * 12;
    private static final int CONTAINED_TRACE_LIFETIME_TICKS = 20 * 60 * 20;
    private static final int SPENT_TRACE_LIFETIME_TICKS = 20 * 60 * 2;
    private static final int MAX_TRACES_PER_WORLD = 512;
    private static final int PRUNE_INTERVAL_TICKS = 20 * 30;

    private static final int TRACE_MERGE_RADIUS = 4;
    private static final int MAX_TRACE_STRENGTH = 1800;
    private static final int ACTIVE_SENSE_GRACE_TICKS = 20 * 8;
    private static final int ECHO_ASH_RADIUS = 16;
    private static final float ECHO_ASH_DROP_CHANCE = 0.45f;
    private static final Map<UUID, Long> ACTIVE_BLOOD_SENSE = new HashMap<>();
    public static final int STATE_HIDDEN = 0;
    public static final int STATE_CONTAINED = 3;
    public static final int STATE_DRAINED = 4;
    public static final int STATE_MUTATED = 5;

    private BloodSenseTracker() {
    }

    public static void registerBloodSenseEvents() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity.getEntityWorld() instanceof ServerWorld world) {
                recordDeath(world, entity);
                tryDropEchoAsh(world, entity, damageSource);
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
        record(world, deathTraceType(entity), entity.getBlockPos(), strength);
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
                Math.min(MAX_TRACE_STRENGTH, Math.max(1, strength)),
                STATE_HIDDEN
        ));

        if (state.traces().size() > MAX_TRACES_PER_WORLD) {
            state.traces().remove(0);
        }

        state.markDirty();
    }

    public static void markBloodSenseActive(ServerWorld world, UUID playerUuid) {
        ACTIVE_BLOOD_SENSE.put(playerUuid, world.getTime() + ACTIVE_SENSE_GRACE_TICKS);
    }

    public static boolean isBloodSenseActive(ServerWorld world, UUID playerUuid) {
        return ACTIVE_BLOOD_SENSE.getOrDefault(playerUuid, 0L) >= world.getTime();
    }

    public static boolean containNearest(ServerWorld world, BlockPos center, int radius) {
        prune(world);

        BloodSenseWorldState state = state(world);
        List<BloodSenseWorldState.StoredTrace> traces = state.traces();
        int bestIndex = -1;
        long bestDistance = Long.MAX_VALUE;
        long radiusSquared = (long) radius * radius;

        for (int i = 0; i < traces.size(); i++) {
            BloodSenseWorldState.StoredTrace trace = traces.get(i);
            if (trace.state() != STATE_HIDDEN) continue;

            long distance = (long) trace.pos().getSquaredDistance(center);
            if (distance <= radiusSquared && distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }

        if (bestIndex < 0) return false;

        BloodSenseWorldState.StoredTrace trace = traces.get(bestIndex);
        traces.set(bestIndex, new BloodSenseWorldState.StoredTrace(
                trace.type(),
                trace.pos(),
                world.getTime(),
                trace.strength(),
                STATE_CONTAINED
        ));
        state.markDirty();
        return true;
    }

    public static ContainmentResult containLoop(ServerWorld world, PlayerEntity player, List<BlockPos> points) {
        prune(world);

        if (points.size() < 6 || !isClosed(points)) return ContainmentResult.OPEN;

        double area = Math.abs(loopArea(points));
        if (area < 1.5) return ContainmentResult.TOO_SMALL;
        if (area > 80.0) return ContainmentResult.TOO_LARGE;

        BloodSenseWorldState state = state(world);
        List<BloodSenseWorldState.StoredTrace> traces = state.traces();
        int containedIndex = -1;

        for (int i = 0; i < traces.size(); i++) {
            BloodSenseWorldState.StoredTrace trace = traces.get(i);
            if (trace.state() != STATE_HIDDEN) continue;
            if (!pointInsideLoop(trace.pos(), points)) continue;
            if (player.squaredDistanceTo(trace.pos().toCenterPos()) > 100.0) return ContainmentResult.TOO_FAR;
            if (containedIndex >= 0) return ContainmentResult.MULTIPLE;

            containedIndex = i;
        }

        if (containedIndex < 0) return ContainmentResult.NONE;

        BloodSenseWorldState.StoredTrace trace = traces.get(containedIndex);
        traces.set(containedIndex, new BloodSenseWorldState.StoredTrace(
                trace.type(),
                trace.pos(),
                world.getTime(),
                trace.strength(),
                STATE_CONTAINED
        ));
        state.markDirty();
        return ContainmentResult.CONTAINED;
    }

    public static BloodSenseTrace operateNearestContained(ServerWorld world, BlockPos center, int radius, int nextState) {
        prune(world);

        BloodSenseWorldState state = state(world);
        List<BloodSenseWorldState.StoredTrace> traces = state.traces();
        int bestIndex = -1;
        long bestDistance = Long.MAX_VALUE;
        long radiusSquared = (long) radius * radius;

        for (int i = 0; i < traces.size(); i++) {
            BloodSenseWorldState.StoredTrace trace = traces.get(i);
            if (trace.state() != STATE_CONTAINED) continue;

            long distance = (long) trace.pos().getSquaredDistance(center);
            if (distance <= radiusSquared && distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }

        if (bestIndex < 0) return null;

        BloodSenseWorldState.StoredTrace trace = traces.get(bestIndex);
        int spentStrength = nextState == STATE_MUTATED
                ? Math.min(MAX_TRACE_STRENGTH, Math.max(1, trace.strength() + 30))
                : Math.max(1, trace.strength() / 2);

        // spent traces stay readable briefly so the player can see the operation resolve.
        BloodSenseWorldState.StoredTrace updated = new BloodSenseWorldState.StoredTrace(
                trace.type(),
                trace.pos(),
                world.getTime(),
                spentStrength,
                nextState
        );
        traces.set(bestIndex, updated);
        state.markDirty();

        return new BloodSenseTrace(
                BloodSenseTraceType.byId(updated.type()),
                world.getRegistryKey(),
                updated.pos(),
                updated.createdTime(),
                updated.strength(),
                updated.state()
        );
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
                        trace.strength(),
                        trace.state()
                ))
                .filter(trace -> trace.pos().getSquaredDistance(center) <= radiusSquared)
                .sorted(Comparator.comparingInt(trace -> trace.pos().getManhattanDistance(center)))
                .toList();
    }

    public static boolean hasActiveTraceNear(ServerWorld world, BlockPos center, int radius) {
        prune(world);

        long radiusSquared = (long) radius * radius;
        return state(world).traces().stream()
                .anyMatch(trace -> trace.state() != STATE_DRAINED
                        && trace.state() != STATE_MUTATED
                        && trace.pos().getSquaredDistance(center) <= radiusSquared);
    }

    public static boolean hasContainedTraceNear(ServerWorld world, BlockPos center, int radius) {
        prune(world);

        long radiusSquared = (long) radius * radius;
        return state(world).traces().stream()
                .anyMatch(trace -> trace.state() == STATE_CONTAINED
                        && trace.pos().getSquaredDistance(center) <= radiusSquared);
    }

    public static boolean releaseContainedNear(ServerWorld world, BlockPos center, int radius) {
        BloodSenseWorldState state = state(world);
        List<BloodSenseWorldState.StoredTrace> traces = state.traces();
        long radiusSquared = (long) radius * radius;
        boolean released = false;

        for (int i = 0; i < traces.size(); i++) {
            BloodSenseWorldState.StoredTrace trace = traces.get(i);
            if (trace.state() != STATE_CONTAINED) continue;
            if (trace.pos().getSquaredDistance(center) > radiusSquared) continue;

            traces.set(i, new BloodSenseWorldState.StoredTrace(
                    trace.type(),
                    trace.pos(),
                    trace.createdTime(),
                    trace.strength(),
                    STATE_HIDDEN
            ));
            released = true;
        }

        if (released) {
            state.markDirty();
        }
        return released;
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
                    combinedStrength,
                    STATE_HIDDEN
            ));
        } else {
            traces.add(new BloodSenseWorldState.StoredTrace(
                    type.ordinal(),
                    pos.toImmutable(),
                    now,
                    Math.min(MAX_TRACE_STRENGTH, Math.max(1, strength)),
                    STATE_HIDDEN
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

    private static BloodSenseTraceType deathTraceType(LivingEntity entity) {
        if (entity.getType().isIn(EntityTypeTags.UNDEAD)) return BloodSenseTraceType.ROT;
        if (entity.getBlockY() <= 0) return BloodSenseTraceType.DEEP;
        return BloodSenseTraceType.DEATH;
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

    private static boolean isClosed(List<BlockPos> points) {
        BlockPos first = points.getFirst();
        BlockPos last = points.getLast();
        double dx = first.getX() - last.getX();
        double dz = first.getZ() - last.getZ();
        return dx * dx + dz * dz <= 2.25;
    }

    private static double loopArea(List<BlockPos> points) {
        double sum = 0.0;
        for (int i = 0; i < points.size(); i++) {
            BlockPos a = points.get(i);
            BlockPos b = points.get((i + 1) % points.size());
            sum += a.getX() * b.getZ() - b.getX() * a.getZ();
        }
        return sum * 0.5;
    }

    private static boolean pointInsideLoop(BlockPos point, List<BlockPos> loop) {
        double x = point.getX() + 0.5;
        double z = point.getZ() + 0.5;
        boolean inside = false;

        for (int i = 0, j = loop.size() - 1; i < loop.size(); j = i++) {
            BlockPos a = loop.get(i);
            BlockPos b = loop.get(j);
            double zi = a.getZ() + 0.5;
            double zj = b.getZ() + 0.5;
            double xi = a.getX() + 0.5;
            double xj = b.getX() + 0.5;

            boolean crosses = (zi > z) != (zj > z);
            if (crosses && x < (xj - xi) * (z - zi) / (zj - zi) + xi) {
                inside = !inside;
            }
        }

        return inside;
    }

    private static void prune(ServerWorld world) {
        BloodSenseWorldState state = state(world);
        List<BloodSenseWorldState.StoredTrace> traces = state.traces();

        long now = world.getTime();
        boolean removed = traces.removeIf(trace -> {
            int lifetime = switch (trace.state()) {
                case STATE_CONTAINED -> CONTAINED_TRACE_LIFETIME_TICKS;
                case STATE_DRAINED, STATE_MUTATED -> SPENT_TRACE_LIFETIME_TICKS;
                default -> TRACE_LIFETIME_TICKS;
            };
            return now - trace.createdTime() >= lifetime;
        });
        ACTIVE_BLOOD_SENSE.entrySet().removeIf(entry -> entry.getValue() < now);
        if (removed) {
            state.markDirty();
        }
    }

    private static void tryDropEchoAsh(ServerWorld world, LivingEntity entity, DamageSource source) {
        if (entity instanceof PlayerEntity || !isFireDeath(entity, source)) return;

        long now = world.getTime();
        long radiusSquared = (long)ECHO_ASH_RADIUS * ECHO_ASH_RADIUS;
        boolean witnessed = world.getPlayers(player ->
                player.squaredDistanceTo(entity) <= radiusSquared
                        && ACTIVE_BLOOD_SENSE.getOrDefault(player.getUuid(), 0L) >= now
        ).size() > 0;

        if (!witnessed) return;
        if (world.random.nextFloat() > ECHO_ASH_DROP_CHANCE) return;

        ItemEntity drop = new ItemEntity(
                world,
                entity.getX(),
                entity.getY() + 0.35,
                entity.getZ(),
                new ItemStack(BloodSenseTools.ECHO_ASH)
        );
        world.spawnEntity(drop);
    }

    private static boolean isFireDeath(LivingEntity entity, DamageSource source) {
        return entity.getFireTicks() > 0
                || entity.isOnFire()
                || source.isOf(DamageTypes.IN_FIRE)
                || source.isOf(DamageTypes.ON_FIRE)
                || source.isOf(DamageTypes.LAVA)
                || source.isOf(DamageTypes.CAMPFIRE)
                || source.isOf(DamageTypes.HOT_FLOOR);
    }

    private static BloodSenseWorldState state(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(BloodSenseWorldState.TYPE);
    }

    public enum ContainmentResult {
        CONTAINED,
        OPEN,
        TOO_SMALL,
        TOO_LARGE,
        MULTIPLE,
        NONE,
        TOO_FAR
    }
}
