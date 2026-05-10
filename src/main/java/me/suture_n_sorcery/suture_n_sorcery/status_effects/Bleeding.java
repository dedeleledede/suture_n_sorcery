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
    private static final float TIER_TWO_DAMAGE = 10.0f;
    private static final float TIER_THREE_DAMAGE = 15.0f;
    private static final float TIER_FOUR_DAMAGE = 20.0f;
    private static final int MAX_TIER = 4;
    private static final int MIN_INTERVAL_TICKS = 10;
    private static final int TICKS_PER_SECOND = 20;
    private static final int ATTACHED_DROP_MIN_COUNT = 6;
    private static final int ATTACHED_DROP_RANDOM_COUNT = 2;

    public Bleeding() {
        super(StatusEffectCategory.HARMFUL, 0x780606);
    }

    public static final Identifier BLEEDING_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "bleeding");

    public static RegistryEntry.Reference<StatusEffect> entry() {
        return Registries.STATUS_EFFECT.getEntry(BLEEDING_ID)
                .orElseThrow(() -> new IllegalStateException("Bleeding effect not registered! " + BLEEDING_ID));
    }

    // stored damage controls both bleed strength and total duration
    public static int tierForDamage(float storedDamage) {
        if (storedDamage <= 0.0f) return 0;
        if (storedDamage >= TIER_FOUR_DAMAGE) return 4;
        if (storedDamage >= TIER_THREE_DAMAGE) return 3;
        if (storedDamage >= TIER_TWO_DAMAGE) return 2;
        return 1;
    }

    public static int durationForTierTicks(int tier) {
        return switch (tier) {
            case 1 -> 14 * TICKS_PER_SECOND;
            case 2 -> 12 * TICKS_PER_SECOND;
            case 3 -> 10 * TICKS_PER_SECOND;
            case 4 -> 8 * TICKS_PER_SECOND;
            default -> 0;
        };
    }

    public static int intervalTicksForTier(int tier) {
        return switch (tier) {
            case 1 -> 25;
            case 2 -> 21;
            case 3 -> 15;
            case 4 -> 12;
            default -> TICKS_PER_SECOND;
        };
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        int tier = Math.min(MAX_TIER, amplifier + 1);
        int interval = Math.max(MIN_INTERVAL_TICKS, intervalTicksForTier(tier));
        return (duration - 1) % interval == 0;
    }

    @Override
    public boolean applyUpdateEffect(ServerWorld world, LivingEntity entity, int amplifier) {
        if (!(entity instanceof BleedingHolder holder)) return false;

        float stored = holder.suture_n_sorcery$getBleedStoredDamage();
        int wantedTier = tierForDamage(stored);
        if (stored <= 0.0f) {
            entity.removeStatusEffect(entry());
            return false;
        }

        StatusEffectInstance inst = entity.getStatusEffect(entry());
        if (inst == null) return false;

        int currentTier = Math.min(MAX_TIER, amplifier + 1);
        if (wantedTier != currentTier) {
            entity.addStatusEffect(new StatusEffectInstance(entry(), inst.getDuration(), wantedTier - 1, false, false, true));
            currentTier = wantedTier;
        }

        int interval = Math.max(MIN_INTERVAL_TICKS, intervalTicksForTier(currentTier));
        int duration = inst.getDuration();
        int ticksLeft = Math.max(1, (duration + interval - 1) / interval);
        float dmg = stored / ticksLeft;

        boolean creative = entity instanceof net.minecraft.entity.player.PlayerEntity p && p.getAbilities().creativeMode;

        if (creative) {
            return true;
        }

        var src = world.getDamageSources().create(ModDamageTypes.BLEEDING);

        boolean applied = entity.damage(world, src, dmg);

        if (applied) {
            holder.suture_n_sorcery$setBleedStoredDamage(stored - dmg);

            int count = ATTACHED_DROP_MIN_COUNT + world.random.nextInt(ATTACHED_DROP_RANDOM_COUNT);
            for (int i = 0; i < count; i++) {
                float ox = (float) (world.random.nextDouble() - 0.5);
                float oy = (float) world.random.nextDouble();
                float oz = (float) (world.random.nextDouble() - 0.5);

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
