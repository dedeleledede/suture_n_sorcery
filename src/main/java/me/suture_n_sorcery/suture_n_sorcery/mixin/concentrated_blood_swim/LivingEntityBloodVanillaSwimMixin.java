package me.suture_n_sorcery.suture_n_sorcery.mixin.concentrated_blood_swim;

import me.suture_n_sorcery.suture_n_sorcery.mixin.LivingEntityJumpingAccessor;
import me.suture_n_sorcery.suture_n_sorcery.tags.ModFluidTags;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityBloodVanillaSwimMixin {

    @Shadow private int jumpingCooldown;

    private static boolean sns$flying(LivingEntity e) {
        return (e instanceof PlayerEntity p) && p.getAbilities().flying;
    }

    private static boolean sns$inBlood(LivingEntity e) {
        return ((Entity)(Object)e).getFluidHeight(ModFluidTags.CONCENTRATED_BLOOD_SWIM) > 0.0D;
    }

    @Redirect(
            method = "travel",
            at = @At(value="INVOKE", target="Lnet/minecraft/entity/LivingEntity;isTouchingWater()Z")
    )
    private boolean sns$touchingWaterForTravel(LivingEntity self) {
        if (sns$flying(self)) return self.isTouchingWater();
        return self.isTouchingWater() || sns$inBlood(self);
    }

    @Redirect(
            method = "travelInFluid",
            at = @At(value="INVOKE", target="Lnet/minecraft/entity/LivingEntity;isTouchingWater()Z")
    )
    private boolean sns$touchingWaterForTravelInFluid(LivingEntity self) {
        if (sns$flying(self)) return self.isTouchingWater();
        return self.isTouchingWater() || sns$inBlood(self);
    }

    @Redirect(
            method = "tickMovement",
            at = @At(value="INVOKE", target="Lnet/minecraft/entity/LivingEntity;isTouchingWater()Z")
    )
    private boolean sns$touchingWaterForTickMovement(LivingEntity self) {
        if (sns$flying(self)) return self.isTouchingWater();
        return self.isTouchingWater() || sns$inBlood(self);
    }

    @Redirect(
            method = "tickMovement",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;getFluidHeight(Lnet/minecraft/registry/tag/TagKey;)D"
            )
    )
    private double sns$fluidHeightForJumpLogic(LivingEntity self, TagKey<Fluid> tag) {
        // call through Entity (avoids IDE confusion)
        double vanilla = ((Entity)(Object) self).getFluidHeight(tag);

        // substitute WATER height with BLOOD height when there is no water
        if (!sns$flying(self) && tag == FluidTags.WATER && vanilla <= 0.0D) {
            return ((Entity)(Object) self).getFluidHeight(ModFluidTags.CONCENTRATED_BLOOD_SWIM);
        }
        return vanilla;
    }
}
