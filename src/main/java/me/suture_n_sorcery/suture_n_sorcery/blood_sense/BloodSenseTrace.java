package me.suture_n_sorcery.suture_n_sorcery.blood_sense;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public record BloodSenseTrace(
        BloodSenseTraceType type,
        RegistryKey<World> worldKey,
        BlockPos pos,
        long createdTime,
        int strength,
        int state
) {
    public int age(long now) {
        return (int)Math.max(0, now - createdTime);
    }

    public float intensity(long now, int lifetimeTicks) {
        if (lifetimeTicks <= 0) return 0f;
        float age01 = Math.min(1f, age(now) / (float)lifetimeTicks);
        return Math.max(0f, strength / 100f) * (1f - age01);
    }
}
