package me.suture_n_sorcery.suture_n_sorcery.mixin.bleeding;

import me.suture_n_sorcery.suture_n_sorcery.status_effects.Bleeding;
import me.suture_n_sorcery.suture_n_sorcery.tags.ModEntityTypeTags;
import me.suture_n_sorcery.suture_n_sorcery.util.BleedingHolder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.ItemTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.server.world.ServerWorld;

@Mixin(LivingEntity.class)
public abstract class LivingEntityBleedingMixin implements BleedingHolder {

    @Unique private float suture_n_sorcery$bleedStoredDamage = 0.0f;
    @Unique private float suture_n_sorcery$bleedPreHealth = 0.0f;

    @Override
    public float suture_n_sorcery$getBleedStoredDamage() {
        return this.suture_n_sorcery$bleedStoredDamage;
    }

    @Override
    public void suture_n_sorcery$setBleedStoredDamage(float value) {
        this.suture_n_sorcery$bleedStoredDamage = Math.max(0.0f, value);
    }

    @Inject(method = "damage", at = @At("HEAD"))
    private void suture_n_sorcery$capturePreHealth(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        this.suture_n_sorcery$bleedPreHealth = self.getHealth();
    }

    @Inject(method = "damage", at = @At("TAIL"))
    private void suture_n_sorcery$convertSharpDamageToBleed(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;

        if (self.getEntityWorld().isClient()) return;
        if (!cir.getReturnValue()) return;              // damage not applied
        if (self.getHealth() <= 0.0f) return;           // dead > dont convert
        if (!suture_n_sorcery$isSharp(source)) return;

        float healthLost = this.suture_n_sorcery$bleedPreHealth - self.getHealth();
        if (healthLost < 5.0f) return;

        boolean alreadyBleeding = self.hasStatusEffect(Bleeding.entry());
        int hitTier = Bleeding.tierForDamage(healthLost);
        if (!alreadyBleeding && hitTier == 0) return; // no effect => no storage

        if (!(self.getType().isIn(ModEntityTypeTags.BLEEDABLE) || self instanceof PlayerEntity)) return;

// Convert immediate damage into stored DoT
        self.setHealth(this.suture_n_sorcery$bleedPreHealth);
        this.suture_n_sorcery$addBleedStoredDamage(healthLost);

// Ensure the effect exists if it didn’t already
        if (!alreadyBleeding) {
            self.addStatusEffect(new StatusEffectInstance(
                    Bleeding.entry(),
                    Bleeding.durationForTierTicks(hitTier),
                    hitTier - 1,
                    false, false, true
            ));
        }


        int tier = Bleeding.tierForDamage(this.suture_n_sorcery$getBleedStoredDamage());
        if (tier == 0) return;

        int durationTicks = Bleeding.durationForTierTicks(tier);

        self.addStatusEffect(new StatusEffectInstance(
                Bleeding.entry(),
                durationTicks,
                tier - 1,
                false,
                false,
                true
        ));
    }

    @Unique
    private static boolean suture_n_sorcery$isSharp(DamageSource source) {
        Entity direct = source.getSource();
        if (direct instanceof PersistentProjectileEntity proj) {
            EntityType<?> type = proj.getType();
            return type == EntityType.ARROW || type == EntityType.SPECTRAL_ARROW || type == EntityType.TRIDENT;
        }

        Entity attacker = source.getAttacker();
        if (attacker instanceof LivingEntity living) {
            ItemStack stack = living.getMainHandStack();
            return stack.isIn(ItemTags.SWORDS) || stack.isIn(ItemTags.AXES) || stack.isOf(Items.TRIDENT);
        }
        return false;
    }
}
