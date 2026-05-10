package me.suture_n_sorcery.suture_n_sorcery.registries;

import me.suture_n_sorcery.suture_n_sorcery.fluids.ConcentratedBlood.ConcentratedBloodFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModFluids {
    private ModFluids() {
    }

    public static void registerFluids() {
        register(ConcentratedBloodFluid.CONCENTRATED_BLOOD_ID, ConcentratedBloodFluid.CONCENTRATED_BLOOD);
        register(ConcentratedBloodFluid.FLOWING_CONCENTRATED_BLOOD_ID, ConcentratedBloodFluid.FLOWING_CONCENTRATED_BLOOD);
    }

    private static void register(Identifier id, Fluid fluid) {
        Registry.register(Registries.FLUID, id, fluid);
    }
}
