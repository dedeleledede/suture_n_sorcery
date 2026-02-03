package me.suture_n_sorcery.suture_n_sorcery.mixin;

import me.suture_n_sorcery.suture_n_sorcery.util.BleedingHolder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityBleedDataMixin {
    private static final String BLEED_KEY = "suture_n_sorcery_bleed_stored";

    @Inject(method = "writeCustomData", at = @At("TAIL"))
    private void suture_n_sorcery$writeCustom(WriteView view, CallbackInfo ci) {
        if ((Object) this instanceof BleedingHolder holder) {
            view.putFloat(BLEED_KEY, holder.suture_n_sorcery$getBleedStoredDamage());
        }
    }

    @Inject(method = "readCustomData", at = @At("TAIL"))
    private void suture_n_sorcery$readCustom(ReadView view, CallbackInfo ci) {
        if ((Object) this instanceof BleedingHolder holder) {
            holder.suture_n_sorcery$setBleedStoredDamage(view.getFloat(BLEED_KEY, 0.0f));
        }
    }
}
