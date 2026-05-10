package me.suture_n_sorcery.suture_n_sorcery.mixin.bleeding;

import me.suture_n_sorcery.suture_n_sorcery.util.BleedingHolder;
import me.suture_n_sorcery.suture_n_sorcery.util.HematicBondHolder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityBleedDataMixin {

    @Unique
    private static final String BLEED_KEY = "suture_n_sorcery_bleed_stored";
    @Unique
    private static final String ABSORBED_HEMATIC_CATALYST_KEY = "suture_n_sorcery_absorbed_hematic_catalyst";

    @Inject(method = "writeCustomData", at = @At("TAIL"))
    private void suture_n_sorcery$writeCustom(WriteView view, CallbackInfo ci) {
        if ((Object) this instanceof BleedingHolder holder) {
            view.putFloat(BLEED_KEY, holder.suture_n_sorcery$getBleedStoredDamage());
        }
        if ((Object) this instanceof HematicBondHolder holder) {
            view.putBoolean(ABSORBED_HEMATIC_CATALYST_KEY, holder.suture_n_sorcery$hasAbsorbedHematicCatalyst());
        }
    }

    @Inject(method = "readCustomData", at = @At("TAIL"))
    private void suture_n_sorcery$readCustom(ReadView view, CallbackInfo ci) {
        if ((Object) this instanceof BleedingHolder holder) {
            holder.suture_n_sorcery$setBleedStoredDamage(view.getFloat(BLEED_KEY, 0.0f));
        }
        if ((Object) this instanceof HematicBondHolder holder) {
            holder.suture_n_sorcery$setAbsorbedHematicCatalyst(view.getBoolean(ABSORBED_HEMATIC_CATALYST_KEY, false));
        }
    }
}
