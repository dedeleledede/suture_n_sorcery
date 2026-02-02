package me.suture_n_sorcery.suture_n_sorcery.mixin.concentrated_blood_swim;

import me.suture_n_sorcery.suture_n_sorcery.tags.ModFluidTags;
import net.minecraft.entity.LivingEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class LivingEntityBloodBreathingRedirectMixin {

    @Redirect(
            method = "baseTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;isSubmergedIn(Lnet/minecraft/registry/tag/TagKey;)Z"
            )
    )
    private boolean sns$bloodCountsAsWaterForAir(LivingEntity self, TagKey<Fluid> tag) {
        // let vanilla handle all non-water checks normally
        boolean vanilla = self.isSubmergedIn(tag);
        if (tag != FluidTags.WATER) return vanilla;

        // if actually in real water, keep vanilla true
        if (vanilla) return true;

        // only on server (air logic matters there)
        if (!(self.getEntityWorld() instanceof ServerWorld world)) return false;

        // eyes-in-blood check (slightly below eye to avoid surface false negatives)
        BlockPos eyePos = BlockPos.ofFloored(self.getX(), self.getEyeY() - 0.10D, self.getZ());
        return world.getFluidState(eyePos).isIn(ModFluidTags.CONCENTRATED_BLOOD_SWIM);
    }
}
