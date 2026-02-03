package me.suture_n_sorcery.suture_n_sorcery.registries;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import me.suture_n_sorcery.suture_n_sorcery.particles.BloodDropParticleEffect;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModParticles {

    public static final SimpleParticleType BLOOD_PARTICLE = FabricParticleTypes.simple(); // old spray
    public static final SimpleParticleType BLOOD_SPLAT = FabricParticleTypes.simple();    // ground decal
    public static final ParticleType<BloodDropParticleEffect> BLOOD_DROP =
            FabricParticleTypes.complex(BloodDropParticleEffect.CODEC, BloodDropParticleEffect.PACKET_CODEC); // glued drip

    public static void registerParticles() {
        Registry.register(Registries.PARTICLE_TYPE, Identifier.of(Suture_n_sorcery.MOD_ID, "blood_particle"), BLOOD_PARTICLE);
        Registry.register(Registries.PARTICLE_TYPE, Identifier.of(Suture_n_sorcery.MOD_ID, "blood_splat"), BLOOD_SPLAT);
        Registry.register(Registries.PARTICLE_TYPE, Identifier.of(Suture_n_sorcery.MOD_ID, "blood_drop"), BLOOD_DROP);
    }
}