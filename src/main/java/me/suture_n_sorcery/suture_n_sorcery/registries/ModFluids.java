package me.suture_n_sorcery.suture_n_sorcery.registries;

import me.suture_n_sorcery.suture_n_sorcery.fluids.ConcentratedBlood.ConcentratedBloodFluid;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public class ModFluids {
    public static void registerFluids(){
        Registry.register(Registries.FLUID, ConcentratedBloodFluid.CONCENTRATED_BLOOD_ID, ConcentratedBloodFluid.CONCENTRATED_BLOOD);
        Registry.register(Registries.FLUID, ConcentratedBloodFluid.FLOWING_CONCENTRATED_BLOOD_ID, ConcentratedBloodFluid.FLOWING_CONCENTRATED_BLOOD);
    }
}
