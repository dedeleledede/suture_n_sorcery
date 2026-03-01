package me.suture_n_sorcery.suture_n_sorcery.client.audio;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.util.concurrent.atomic.AtomicReference;

public final class PressControllerInstance extends AbstractSoundInstance
        implements TickableSoundInstance {

    private final int pressTicks;
    private final int successTick;
    private final int fadeLenTicks;

    private int age = 0;
    private boolean done = false;
    private boolean fading = false;
    private int fadeAge = 0;
    private boolean successPlayed = false;
    private static final AtomicReference<PressControllerInstance> CURRENT =
            new AtomicReference<>();

    private static volatile boolean FADE_CURRENT = false;
    private final Identifier successId;

    public static void requestFadeCurrent() {
        FADE_CURRENT = true;
    }

    public PressControllerInstance(Identifier pressId,
                                   Identifier successId,
                                   int pressTicks,
                                   float successBeforeFadeSeconds,
                                   float fadeOutLenSeconds) {

        super(SoundEvent.of(pressId), SoundCategory.BLOCKS, Random.create());

        this.successId = successId;

        this.pressTicks = Math.max(1, pressTicks);
        this.successTick = Math.max(0, this.pressTicks - Math.round(successBeforeFadeSeconds * 20f));
        this.fadeLenTicks = Math.max(1, Math.round(fadeOutLenSeconds * 20f));

        this.repeat = true;
        this.repeatDelay = 0;
        this.relative = true;
        this.attenuationType = SoundInstance.AttenuationType.NONE;

        this.volume = 0.1f;
        this.pitch = 1f;

        CURRENT.set(this);
        FADE_CURRENT = false; // important: clear any stale request
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public void tick() {
        if (done) return;

        age++;

        if (!fading && FADE_CURRENT && CURRENT.get() == this) {
            fading = true;
            fadeAge = 0;
            repeat = false;
            FADE_CURRENT = false;
        }

        if (!fading) {
            float t = Math.min(1f, age / 6f);
            float env = smoothstep01(t);
            volume = 0.01f + (1.0f - 0.01f) * env;

            if (!successPlayed && age >= successTick) {
                successPlayed = true;
                playOneShot(successId, 0.9f);
            }

            if (age >= pressTicks) {
                fading = true;
                fadeAge = 0;
                repeat = false;
            }
        } else {
            fadeAge++;
            float t = Math.min(1f, fadeAge / (float)fadeLenTicks);
            volume = 1f - (t * t * t);

            if (t >= 1f) {
                done = true;
                if (CURRENT.get() == this) CURRENT.set(null);
            }
        }
    }

    private void playOneShot(Identifier id, float vol) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;

        mc.getSoundManager().play(PositionedSoundInstance.master(
                SoundEvent.of(id),
                vol
        ));
    }

    private static float smoothstep01(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }
}