// BloodDropParticleEffect.java (common)
package me.suture_n_sorcery.suture_n_sorcery.particles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.suture_n_sorcery.suture_n_sorcery.registries.ModParticles;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;

public record BloodDropParticleEffect(int entityId, int variant, float ox, float oy, float oz) implements ParticleEffect {

    public static final MapCodec<BloodDropParticleEffect> CODEC =
            RecordCodecBuilder.mapCodec(i -> i.group(
                    Codec.INT.fieldOf("entity_id").forGetter(BloodDropParticleEffect::entityId),
                    Codec.INT.fieldOf("variant").forGetter(BloodDropParticleEffect::variant),
                    Codec.FLOAT.fieldOf("ox").forGetter(BloodDropParticleEffect::ox),
                    Codec.FLOAT.fieldOf("oy").forGetter(BloodDropParticleEffect::oy),
                    Codec.FLOAT.fieldOf("oz").forGetter(BloodDropParticleEffect::oz)
            ).apply(i, BloodDropParticleEffect::new));

    public static final PacketCodec<RegistryByteBuf, BloodDropParticleEffect> PACKET_CODEC =
            PacketCodec.tuple(
                    PacketCodecs.VAR_INT, BloodDropParticleEffect::entityId,
                    PacketCodecs.VAR_INT, BloodDropParticleEffect::variant,
                    PacketCodecs.FLOAT, BloodDropParticleEffect::ox,
                    PacketCodecs.FLOAT, BloodDropParticleEffect::oy,
                    PacketCodecs.FLOAT, BloodDropParticleEffect::oz,
                    BloodDropParticleEffect::new
            );

    @Override
    public ParticleType<?> getType() { return ModParticles.BLOOD_DROP; }
}
