package me.suture_n_sorcery.suture_n_sorcery.status_effects;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import me.suture_n_sorcery.suture_n_sorcery.particles.BloodDropParticleEffect;
import me.suture_n_sorcery.suture_n_sorcery.registries.ModDamageTypes;
import me.suture_n_sorcery.suture_n_sorcery.registries.ModParticles;
import me.suture_n_sorcery.suture_n_sorcery.util.BleedingHolder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

public class Bleeding extends StatusEffect {
    public Bleeding() {
        super(StatusEffectCategory.HARMFUL, 0x780606);
    }

    public static final Identifier BLEEDING_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "bleeding");

    public static RegistryEntry.Reference<StatusEffect> entry() {
        return Registries.STATUS_EFFECT.getEntry(BLEEDING_ID)
                .orElseThrow(() -> new IllegalStateException("Bleeding effect not registered! " + BLEEDING_ID));
    }

    // damage thresholds -> tiers 1..4
    public static int tierForDamage(float storedDamage) {
        if (storedDamage <= 0.0f) return 0;
        if (storedDamage >= 20.0f) return 4;
        if (storedDamage >= 15.0f) return 3;
        if (storedDamage >= 10.0f) return 2;
        return 1; // 0 < stored < 10 => Bleeding 1
    }

    // Your durations:
    // 5 dmg  -> bleeding 1 -> 14s
    // 10 dmg -> bleeding 2 -> 12s
    // 15 dmg -> bleeding 3 -> 10s
    // 20 dmg -> bleeding 4 ->  8s
    public static int durationForTierTicks(int tier) {
        return switch (tier) {
            case 1 -> 14 * 20;
            case 2 -> 12 * 20;
            case 3 -> 10 * 20;
            case 4 ->  8 * 20;
            default -> 0;
        };
    }

    // Faster ticks at higher tier (feel free to tweak)
    public static int intervalTicksForTier(int tier) {
        return switch (tier) {
            case 1 -> 25;
            case 2 -> 21;
            case 3 -> 15;
            case 4 ->  12;
            default -> 20;
        };
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        int tier = Math.min(4, amplifier + 1);
        int interval = Math.max(10, intervalTicksForTier(tier));
        return (duration - 1) % interval == 0;
    }

    @Override
    public boolean applyUpdateEffect(ServerWorld world, LivingEntity entity, int amplifier) {
        if (!(entity instanceof BleedingHolder holder)) return false;

        float stored = holder.suture_n_sorcery$getBleedStoredDamage();
        int wantedTier = tierForDamage(stored);
        if (stored <= 0.0f) { entity.removeStatusEffect(entry()); return false; }

        StatusEffectInstance inst = entity.getStatusEffect(entry());
        if (inst == null) return false;

        int currentTier = Math.min(4, amplifier + 1);
        if (wantedTier != currentTier) {
            entity.addStatusEffect(new StatusEffectInstance(entry(), inst.getDuration(), wantedTier - 1, false, false, true));
            currentTier = wantedTier;
        }

        int interval = Math.max(10, intervalTicksForTier(currentTier));
        int duration = inst.getDuration();
        int ticksLeft = Math.max(1, (duration + interval - 1) / interval); // ceil
        float dmg = stored / ticksLeft;

        boolean creative = entity instanceof net.minecraft.entity.player.PlayerEntity p && p.getAbilities().creativeMode;

        if (creative) {
            return true;
        }

        var src = world.getDamageSources().create(ModDamageTypes.BLEEDING);

        boolean applied = entity.damage(world, src, dmg);

        if (applied) {
            holder.suture_n_sorcery$setBleedStoredDamage(stored - dmg);

            int count = 6 + world.random.nextInt(2);
            for (int i = 0; i < count; i++) {
                float ox = (float) (world.random.nextDouble() - 0.5); // -0.5..0.5
                float oy = (float) (world.random.nextDouble());       // 0..1
                float oz = (float) (world.random.nextDouble() - 0.5); // -0.5..0.5

                world.spawnParticles(
                        new BloodDropParticleEffect(entity.getId(), 0, ox, oy, oz),
                        entity.getX(), entity.getY(), entity.getZ(),
                        1, 0, 0, 0, 0
                );
            }

            world.spawnParticles(
                    ModParticles.BLOOD_PARTICLE,
                    entity.getX(),
                    entity.getBodyY(0.55),
                    entity.getZ(),
                    2 + (currentTier * 3),
                    0.25, 0.35, 0.25,
                    0.02
            );
        } else {
            entity.addStatusEffect(new StatusEffectInstance(
                    entry(),
                    duration + interval,
                    inst.getAmplifier(),
                    false,
                    false,
                    true
            ));
        }
        return true;
    }
}
