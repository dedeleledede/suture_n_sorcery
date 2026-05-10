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
    private static final int BLOOD_COLOR = 0x8A0303;
    private static final double AIR_DAMPING = 0.85;
    private static final double GROUND_DAMPING = 0.4;

    protected BloodParticle(
            ClientWorld world,
            double x, double y, double z,
            double vx, double vy, double vz,
            SpriteProvider sprites
    ) {
        super(world, x, y, z, vx, vy, vz, sprites.getSprite(world.random));

        this.red = ((BLOOD_COLOR >> 16) & 0xFF) / 255.0f;
        this.green = ((BLOOD_COLOR >> 8) & 0xFF) / 255.0f;
        this.blue = (BLOOD_COLOR & 0xFF) / 255.0f;
        this.alpha = 1.0f;

        this.collidesWithWorld = true;
        this.gravityStrength = 0.10f;

        this.scale = 0.08f + this.random.nextFloat() * 0.05f;
        this.maxAge = 16 + this.random.nextInt(10);

        this.zRotation = this.random.nextFloat() * ((float) Math.PI * 2.0f);
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

        this.velocityX *= AIR_DAMPING;
        this.velocityY *= AIR_DAMPING;
        this.velocityZ *= AIR_DAMPING;

        if (this.onGround) {
            this.velocityX *= GROUND_DAMPING;
            this.velocityZ *= GROUND_DAMPING;
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
