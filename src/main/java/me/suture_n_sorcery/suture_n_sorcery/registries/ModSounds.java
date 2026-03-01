package me.suture_n_sorcery.suture_n_sorcery.registries;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class ModSounds {

    public static final Identifier RITUAL_LOOM_PRESSURIZE_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "ritual_loom_pressurize");
    public static final Identifier RITUAL_LOOM_SUCCESS_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "ritual_loom_success");

    public static final SoundEvent RITUAL_LOOM_PRESSURIZE =
            SoundEvent.of(RITUAL_LOOM_PRESSURIZE_ID);
    public static final SoundEvent RITUAL_LOOM_SUCCESS =
            SoundEvent.of(RITUAL_LOOM_SUCCESS_ID);

    public static void registerSounds() {
        Registry.register(Registries.SOUND_EVENT, RITUAL_LOOM_PRESSURIZE_ID, RITUAL_LOOM_PRESSURIZE);
        Registry.register(Registries.SOUND_EVENT, RITUAL_LOOM_SUCCESS_ID, RITUAL_LOOM_SUCCESS);
    }
}
