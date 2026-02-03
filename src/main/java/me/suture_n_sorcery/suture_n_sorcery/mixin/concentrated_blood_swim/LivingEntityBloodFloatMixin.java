package me.suture_n_sorcery.suture_n_sorcery.mixin.concentrated_blood_swim;

import me.suture_n_sorcery.suture_n_sorcery.tags.ModFluidTags;
import me.suture_n_sorcery.suture_n_sorcery.util.BloodFluidData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityBloodFloatMixin {

    @Inject(method = "tickMovement", at = @At("TAIL"))
    private void sns$bloodBuoyancy(CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;

        if (self instanceof net.minecraft.entity.player.PlayerEntity) return;

        if (!((BloodFluidData)self).sns$isInBlood()) return;
        if (self.isTouchingWater() || self.isInLava()) return;

        double h = self.getFluidHeight(ModFluidTags.CONCENTRATED_BLOOD_SWIM);
        if (h <= 0.0D) return;

        Vec3d v = self.getVelocity();

        double up = 0.04D;
        double maxUp = 0.10D;
        double minDown = -0.02D;

        double newY = v.y;

        if (newY < minDown) newY = minDown;

        if (h > 0.3D) newY = Math.min(newY + up, maxUp);

        self.setVelocity(v.x, newY, v.z);
    }
}
