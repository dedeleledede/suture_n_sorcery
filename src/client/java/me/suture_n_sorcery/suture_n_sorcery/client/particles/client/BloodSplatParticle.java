package me.suture_n_sorcery.suture_n_sorcery.client.particles.client;

import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

public class BloodSplatParticle extends BillboardParticle {

    private static final int SPLAT_TICKS = 18;

    protected BloodSplatParticle(ClientWorld world, double x, double y, double z, SpriteProvider sprites) {
        super(world, x, y, z, 0, 0, 0, sprites.getSprite(world.random));

        int c = 0x8A0303;
        this.red = ((c >> 16) & 0xFF) / 255.0f;
        this.green = ((c >> 8) & 0xFF) / 255.0f;
        this.blue = (c & 0xFF) / 255.0f;
        this.alpha = 1.0f;

        this.collidesWithWorld = false;
        this.gravityStrength = 0.0f;
        this.velocityX = 0.0;
        this.velocityY = 0.0;
        this.velocityZ = 0.0;

        this.scale = 0.14f + this.random.nextFloat() * 0.06f;
        this.maxAge = SPLAT_TICKS;
    }

    @Override
    protected RenderType getRenderType() {
        return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        this.alpha = 1.0f - (this.age / (float) this.maxAge);
    }

    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider sprites;

        public Factory(SpriteProvider sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable Particle createParticle(
                SimpleParticleType params,
                ClientWorld world,
                double x, double y, double z,
                double velocityX, double velocityY, double velocityZ,
                Random random
        ) {
            return new BloodSplatParticle(world, x, y, z, this.sprites);
        }
    }
}
