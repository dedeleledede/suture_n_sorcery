package me.suture_n_sorcery.suture_n_sorcery.mixin.client;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import me.suture_n_sorcery.suture_n_sorcery.client.audio.PressControllerInstance;
import me.suture_n_sorcery.suture_n_sorcery.client.audio.PressurizeArm;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.util.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundSystem.class)
public abstract class SoundSystemSeekMixin {

    @Unique
    private static final Identifier PRESS_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "ritual_loom_pressurize");

    @Unique
    private static final Identifier SUCCESS_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "ritual_loom_success");

    @Unique private static final float FADE_START = 8.33f;
    @Unique private static final float TRACK_LEN = 9.50f;
    @Unique private static final float SUCCESS_CUE = 8.10f;

    @Unique private static final float SUCCESS_BEFORE_FADE = (FADE_START - SUCCESS_CUE); // 0.23s
    @Unique private static final float FADE_OUT_LEN = (TRACK_LEN - FADE_START);          // 1.17s

    @Unique private static boolean sns$internalPlay = false;

    @Inject(
            method = "play(Lnet/minecraft/client/sound/SoundInstance;)Lnet/minecraft/client/sound/SoundSystem$PlayResult;",
            at = @At("HEAD"),
            cancellable = true
    )

    private void sns$replacePressurize(SoundInstance sound, CallbackInfoReturnable<SoundSystem.PlayResult> cir) {

        if (sns$internalPlay) return;
        if (sound == null) return;

        if (!PRESS_ID.equals(sound.getId())) return;

        int pressTicks = PressurizeArm.take();

        // if not armed, let vanilla play
        if (pressTicks <= 0) return;

        sns$internalPlay = true;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.getSoundManager() != null) {
                mc.getSoundManager().play(new PressControllerInstance(
                        PRESS_ID, SUCCESS_ID, pressTicks, SUCCESS_BEFORE_FADE, FADE_OUT_LEN
                ));
            }
        } finally {
            sns$internalPlay = false;
        }

        cir.setReturnValue(SoundSystem.PlayResult.STARTED);
    }
}