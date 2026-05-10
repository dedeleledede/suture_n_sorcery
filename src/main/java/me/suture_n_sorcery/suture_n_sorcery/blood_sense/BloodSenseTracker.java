package me.suture_n_sorcery.suture_n_sorcery.blood_sense;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BloodSenseTracker {

    private static final int TRACE_LIFETIME_TICKS = 20 * 60 * 12;
    private static final int MAX_TRACES_PER_WORLD = 256;

    private static final Map<RegistryKey<World>, List<BloodSenseTrace>> TRACES = new HashMap<>();

    private BloodSenseTracker() {
    }

    public static void registerBloodSenseEvents() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity.getEntityWorld() instanceof ServerWorld world) {
                recordDeath(world, entity);
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
        return TRACES.getOrDefault(world.getRegistryKey(), List.of()).stream()
                .filter(trace -> trace.pos().getSquaredDistance(center) <= radiusSquared)
                .sorted(Comparator.comparingInt(trace -> trace.pos().getManhattanDistance(center)))
                .toList();
    }

    private static void record(ServerWorld world, BloodSenseTraceType type, BlockPos pos, int strength) {
        prune(world);

        List<BloodSenseTrace> traces = TRACES.computeIfAbsent(world.getRegistryKey(), key -> new ArrayList<>());
        traces.add(new BloodSenseTrace(type, world.getRegistryKey(), pos.toImmutable(), world.getTime(), strength));

        if (traces.size() > MAX_TRACES_PER_WORLD) {
            traces.subList(0, traces.size() - MAX_TRACES_PER_WORLD).clear();
        }
    }

    private static void prune(ServerWorld world) {
        List<BloodSenseTrace> traces = TRACES.get(world.getRegistryKey());
        if (traces == null) return;

        long now = world.getTime();
        traces.removeIf(trace -> trace.age(now) > TRACE_LIFETIME_TICKS);
        if (traces.isEmpty()) {
            TRACES.remove(world.getRegistryKey());
        }
    }
}
