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

    // ---------------- DEBUG (server-only, player-only, 1 line / second) ----------------
    @Unique private int sns$jumpDbgCd = 0;

    @Inject(
            method = "tickMovement",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiler/Profiler;push(Ljava/lang/String;)V",
                    args = "ldc=jump",
                    shift = At.Shift.AFTER
            )
    )
    private void sns$debugSwimDecision(CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (self.getEntityWorld().isClient()) return;
        if (!(self instanceof net.minecraft.server.network.ServerPlayerEntity)) return;

        if (sns$jumpDbgCd-- > 0) return;
        sns$jumpDbgCd = 20;

        Entity e = (Entity)(Object)self;

        boolean jumpKey = ((LivingEntityJumpingAccessor) self).sns$isJumping();
        double bloodH = e.getFluidHeight(ModFluidTags.CONCENTRATED_BLOOD_SWIM);
        double waterH = e.getFluidHeight(FluidTags.WATER);
        double lavaH  = e.getFluidHeight(FluidTags.LAVA);

        boolean touchingWaterEffective = self.isTouchingWater() || (bloodH > 0.0D);
        double gEffective = self.isInLava() ? lavaH : (waterH > 0.0D ? waterH : bloodH);

        double swimH = self.getSwimHeight();
        boolean wouldSwimUp = touchingWaterEffective && gEffective > swimH && !self.isOnGround() && jumpKey;

        System.out.println(
                "jump=" + jumpKey
                        + " shouldSwim=" + self.shouldSwimInFluids()
                        + " touchingWater(raw)=" + self.isTouchingWater()
                        + " touchingWater(eff)=" + touchingWaterEffective
                        + " onGround=" + self.isOnGround()
                        + " cooldown=" + this.jumpingCooldown
                        + " swimH=" + swimH
                        + " bloodH=" + bloodH
                        + " waterH=" + waterH
                        + " lavaH=" + lavaH
                        + " gEff=" + gEffective
                        + " wouldSwimUp=" + wouldSwimUp
        );
    }
}
