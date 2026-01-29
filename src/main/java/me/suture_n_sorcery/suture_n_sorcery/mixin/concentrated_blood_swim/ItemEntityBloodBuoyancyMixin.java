package me.suture_n_sorcery.suture_n_sorcery.mixin.concentrated_blood_swim;

import me.suture_n_sorcery.suture_n_sorcery.util.BloodFluidData;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityBloodBuoyancyMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void sns$itemFloatInBlood(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (!((BloodFluidData) self).sns$isInBlood()) return;

        Vec3d v = self.getVelocity();

        double y = Math.min(v.y + 0.02D, 0.08D);
        self.setVelocity(v.x * 0.85D, y, v.z * 0.85D);
    }
}
