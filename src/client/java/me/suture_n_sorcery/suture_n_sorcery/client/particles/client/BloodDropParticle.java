package me.suture_n_sorcery.suture_n_sorcery.client.particles.client;

import me.suture_n_sorcery.suture_n_sorcery.particles.BloodDropParticleEffect;
import me.suture_n_sorcery.suture_n_sorcery.registries.ModParticles;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

public class BloodDropParticle extends BillboardParticle {

    private static final int ATTACH_TICKS = 8;

    private final int targetEntityId;
    private Entity target;

    // normalized offsets coming from the effect (do NOT compute hitbox offsets in ctor)
    private final double nOx;
    private final double nOy;
    private final double nOz;

    private int phase = 0; // 0=attached, 1=falling
    private double droop = 0.0;

    protected BloodDropParticle(
            ClientWorld world,
            double x, double y, double z,
            double velocityX, double velocityY, double velocityZ,
            Sprite sprite,
            int targetEntityId,
            double nOx, double nOy, double nOz
    ) {
        super(world, x, y, z, velocityX, velocityY, velocityZ, sprite);

        this.targetEntityId = targetEntityId;
        this.nOx = nOx;
        this.nOy = nOy;
        this.nOz = nOz;

        int c = 0x8A0303;
        this.red = ((c >> 16) & 0xFF) / 255.0f;
        this.green = ((c >> 8) & 0xFF) / 255.0f;
        this.blue = (c & 0xFF) / 255.0f;
        this.alpha = 1.0f;

        this.collidesWithWorld = true;
        this.scale = 0.10f + this.random.nextFloat() * 0.05f;
        this.maxAge = 60;

        // while glued, we control motion manually
        this.gravityStrength = 0.0f;
        this.velocityX = 0.0;
        this.velocityY = 0.0;
        this.velocityZ = 0.0;
    }

    @Override
    protected RenderType getRenderType() {
        return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
    }

    @Override
    public void tick() {
        if (this.target == null) {
            this.target = this.world.getEntityById(this.targetEntityId);
        }

        // phase 0: glued to entity (smooth)
        if (this.phase == 0) {
            if (this.target != null && this.target.isAlive()) {
                double w = this.target.getWidth() * 0.9;
                double h = this.target.getHeight() * 0.85;

                double dx = this.nOx * w;
                double dz = this.nOz * w;

                // base Y along hitbox + droop downwards a bit each tick
                double baseY = this.target.getY() + (this.nOy * h);
                this.droop = MathHelper.clamp(this.droop - 0.01, -0.35, 0.10);

                // IMPORTANT for smooth render interpolation in this mapping
                this.lastX = this.x;
                this.lastY = this.y;
                this.lastZ = this.z;

                this.setPos(
                        this.target.getX() + dx,
                        baseY + this.droop,
                        this.target.getZ() + dz
                );
            }

            // no physics while attached
            this.velocityX = 0.0;
            this.velocityY = 0.0;
            this.velocityZ = 0.0;
            this.gravityStrength = 0.0f;

            // manual aging (don’t call super.tick() here)
            this.age++;
            if (this.age >= this.maxAge) {
                this.markDead();
                return;
            }

            if (this.age >= ATTACH_TICKS) {
                this.phase = 1;

                // detach -> start falling
                this.gravityStrength = 0.02f;
                double kick = 0.02;
                this.velocityX = (this.random.nextDouble() - 0.5) * kick;
                this.velocityZ = (this.random.nextDouble() - 0.5) * kick;
                this.velocityY = -0.02;
            }
            return;
        }

        // phase 1: falling (use base physics)
        this.gravityStrength = Math.min(0.14f, this.gravityStrength + 0.01f);
        super.tick();

        if (this.onGround) {
            // splat
            this.world.addParticleClient(
                    ModParticles.BLOOD_SPLAT,
                    this.x, this.y + 0.01, this.z,
                    0.0, 0.0, 0.0
            );
            this.markDead();
        }
    }

    public static class Factory implements ParticleFactory<BloodDropParticleEffect> {
        private final SpriteProvider sprites;

        public Factory(SpriteProvider sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable Particle createParticle(
                BloodDropParticleEffect params,
                ClientWorld world,
                double x, double y, double z,
                double velocityX, double velocityY, double velocityZ,
                Random random
        ) {
            Sprite sprite = this.sprites.getSprite(random);

            double ox = params.ox();
            double oy = params.oy();
            double oz = params.oz();

            return new BloodDropParticle(
                    world, x, y, z,
                    velocityX, velocityY, velocityZ,
                    sprite,
                    params.entityId(),
                    ox, oy, oz
            );
        }
    }
}
