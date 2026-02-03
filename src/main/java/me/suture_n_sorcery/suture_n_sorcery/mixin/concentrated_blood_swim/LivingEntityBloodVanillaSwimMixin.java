package me.suture_n_sorcery.suture_n_sorcery.mixin.concentrated_blood_swim;

import me.suture_n_sorcery.suture_n_sorcery.tags.ModFluidTags;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityBloodVanillaSwimMixin {

    @Shadow
    protected boolean jumping;


    @Unique
    private static boolean sns$flying(LivingEntity self) {
        return (self instanceof PlayerEntity p) && p.getAbilities().flying;
    }

    @Unique
    private static boolean sns$inBlood(LivingEntity self) {
        return self.getFluidHeight(ModFluidTags.CONCENTRATED_BLOOD_SWIM) > 0.0D;
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
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;getFluidHeight(Lnet/minecraft/registry/tag/TagKey;)D"
            )
    )
    private double sns$fluidHeightForTravelInFluid(LivingEntity self, TagKey<Fluid> tag) {
        double vanilla = self.getFluidHeight(tag);

        if (!sns$flying(self) && tag == FluidTags.WATER && vanilla <= 0.0D) {
            return self.getFluidHeight(ModFluidTags.CONCENTRATED_BLOOD_SWIM);
        }
        return vanilla;
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
        double vanilla = self.getFluidHeight(tag);

        if (!sns$flying(self) && tag == FluidTags.WATER && vanilla <= 0.0D) {
            return self.getFluidHeight(ModFluidTags.CONCENTRATED_BLOOD_SWIM);
        }
        return vanilla;
    }

    @Redirect(
            method = "travelInFluid",
            at = @At(value="INVOKE", target="Lnet/minecraft/entity/LivingEntity;isTouchingWater()Z")
    )
    private boolean sns$touchingWaterForTravelInFluid(LivingEntity self) {
        if (sns$flying(self)) return self.isTouchingWater();
        return self.isTouchingWater() || sns$inBlood(self);
    }

    @Inject(method = "travelInFluid", at = @At("TAIL"))
    private void sns$noPassiveFloatInBlood(Vec3d movementInput, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;

        // only affect players (mobs keep normal buoyancy)
        if (!(self instanceof PlayerEntity)) return;

        // ignore creative flight
        if (sns$flying(self)) return;

        // only in blood, not in real water
        if (self.getFluidHeight(ModFluidTags.CONCENTRATED_BLOOD_SWIM) <= 0.0D) return;
        if (self.getFluidHeight(FluidTags.WATER) > 0.0D) return;

        // if not pressing jump, kill passive upward buoyancy
        if (!this.jumping) {
            Vec3d v = self.getVelocity();
            if (v.y > 0.0D) {
                self.setVelocity(v.x, 0.0D, v.z);
            }
        }
    }
}
