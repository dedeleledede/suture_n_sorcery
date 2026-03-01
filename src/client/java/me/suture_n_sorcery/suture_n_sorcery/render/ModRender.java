package me.suture_n_sorcery.suture_n_sorcery.render;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import me.suture_n_sorcery.suture_n_sorcery.blocks.Condenser.Condenser;
import me.suture_n_sorcery.suture_n_sorcery.client.particles.client.BloodParticle;
import me.suture_n_sorcery.suture_n_sorcery.client.particles.client.BloodSplatParticle;
import me.suture_n_sorcery.suture_n_sorcery.fluids.ConcentratedBlood.ConcentratedBloodFluid;
import me.suture_n_sorcery.suture_n_sorcery.client.particles.client.BloodDropParticle;
import me.suture_n_sorcery.suture_n_sorcery.registries.ModParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.util.Identifier;

public class ModRender {
    public static void registerRender() {

        // CONDENSATOR

        BlockRenderLayerMap.putBlocks(
                BlockRenderLayer.CUTOUT,
                Condenser.CONDENSATOR
        );

        BlockRenderLayerMap.putBlocks(
                BlockRenderLayer.CUTOUT_MIPPED,
                Condenser.CONDENSATOR
        );

        // CONCENTRATED BLOOD

        FluidRenderHandlerRegistry.INSTANCE.register(
                ConcentratedBloodFluid.CONCENTRATED_BLOOD,
                ConcentratedBloodFluid.FLOWING_CONCENTRATED_BLOOD,
                new SimpleFluidRenderHandler(
                        Identifier.of(Suture_n_sorcery.MOD_ID, "block/concentrated_blood_still"),
                        Identifier.of(Suture_n_sorcery.MOD_ID, "block/concentrated_blood_flow"),
                        0x8A0303 // tint (RGB)
                )
        );

        BlockRenderLayerMap.putFluids(
                BlockRenderLayer.SOLID,
                ConcentratedBloodFluid.CONCENTRATED_BLOOD,
                ConcentratedBloodFluid.FLOWING_CONCENTRATED_BLOOD
        );

        ParticleFactoryRegistry.getInstance().register(ModParticles.BLOOD_DROP, BloodDropParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.BLOOD_PARTICLE, BloodParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.BLOOD_SPLAT, BloodSplatParticle.Factory::new);}
    }