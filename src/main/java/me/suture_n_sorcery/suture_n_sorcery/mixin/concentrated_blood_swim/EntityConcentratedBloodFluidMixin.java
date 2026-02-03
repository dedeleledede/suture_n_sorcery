package me.suture_n_sorcery.suture_n_sorcery.mixin.concentrated_blood_swim;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import me.suture_n_sorcery.suture_n_sorcery.tags.ModFluidTags;
import me.suture_n_sorcery.suture_n_sorcery.util.BloodFluidData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.tag.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityConcentratedBloodFluidMixin implements BloodFluidData {

    @Shadow protected Object2DoubleMap<TagKey<Fluid>> fluidHeight;
    @Shadow public abstract boolean updateMovementInFluid(TagKey<Fluid> tag, double speed);

    @Unique private boolean sns$inBlood;
    @Unique private boolean sns$jumpingInput;

    // Run AFTER vanilla has cleared/rebuilt fluidHeight this tick
    @Inject(method = "baseTick", at = @At("TAIL"))
    private void sns$updateBloodFluidState(CallbackInfo ci) {
        Entity e = (Entity)(Object)this;

        // no viscosity while creative-flying
        if (e instanceof PlayerEntity p && p.getAbilities().flying) {
            this.sns$inBlood = false;
            this.fluidHeight.put(ModFluidTags.CONCENTRATED_BLOOD_SWIM, 0.0D);
            this.sns$jumpingInput = false;
            return;
        }

        // keep/tune later; main point is correct timing
        this.sns$inBlood = this.updateMovementInFluid(ModFluidTags.CONCENTRATED_BLOOD_SWIM, 0.014D);

        if (!this.sns$inBlood) {
            this.fluidHeight.put(ModFluidTags.CONCENTRATED_BLOOD_SWIM, 0.0D);
            this.sns$jumpingInput = false;
        }
    }


    @Override public boolean sns$isInBlood() { return sns$inBlood; }
    @Override public void sns$setJumpingInput(boolean v) { sns$jumpingInput = v; }
    @Override public boolean sns$isJumpingInput() { return sns$jumpingInput; }
}
