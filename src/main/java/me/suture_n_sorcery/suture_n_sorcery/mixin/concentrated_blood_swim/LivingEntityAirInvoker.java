package me.suture_n_sorcery.suture_n_sorcery.mixin.concentrated_blood_swim;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAirInvoker {

    @Invoker("getNextAirUnderwater")
    int sns$getNextAirUnderwater(int air);

    @Invoker("getNextAirOnLand")
    int sns$getNextAirOnLand(int air);

    @Invoker("shouldDrown")
    boolean sns$shouldDrown();
}