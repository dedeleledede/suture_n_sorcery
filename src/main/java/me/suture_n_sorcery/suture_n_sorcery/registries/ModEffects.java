package me.suture_n_sorcery.suture_n_sorcery.registries;

import me.suture_n_sorcery.suture_n_sorcery.status_effects.Bleeding;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEffects {
    public static void registerEffects(){
        Registry.register(Registries.STATUS_EFFECT, Bleeding.BLEEDING_ID, new Bleeding());
    }
}