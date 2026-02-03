package me.suture_n_sorcery.suture_n_sorcery.client.particles.client;

import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

public class BloodParticle extends BillboardParticle {

    protected BloodParticle(
            ClientWorld world,
            double x, double y, double z,
            double vx, double vy, double vz,
            SpriteProvider sprites
    ) {
        super(world, x, y, z, vx, vy, vz, sprites.getSprite(world.random));

        int c = 0x8A0303;
        this.red = ((c >> 16) & 0xFF) / 255.0f;
        this.green = ((c >> 8) & 0xFF) / 255.0f;
        this.blue = (c & 0xFF) / 255.0f;
        this.alpha = 1.0f;

        this.collidesWithWorld = true;
        this.gravityStrength = 0.10f;

        this.scale = 0.08f + this.random.nextFloat() * 0.05f;
        this.maxAge = 16 + this.random.nextInt(10);

        this.zRotation = this.random.nextFloat() * ((float)Math.PI * 2.0f);
        this.lastZRotation = this.zRotation;

        this.velocityY = vy + 0.06;
    }

    @Override
    protected RenderType getRenderType() {
        return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();

        // damping similar to your old behavior
        this.velocityX *= 0.85;
        this.velocityY *= 0.85;
        this.velocityZ *= 0.85;

        if (this.onGround) {
            this.velocityX *= 0.4;
            this.velocityZ *= 0.4;
        }

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
            return new BloodParticle(world, x, y, z, velocityX, velocityY, velocityZ, this.sprites);
        }
    }
}
