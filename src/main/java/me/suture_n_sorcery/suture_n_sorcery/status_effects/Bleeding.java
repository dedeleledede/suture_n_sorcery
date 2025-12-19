package me.suture_n_sorcery.suture_n_sorcery.status_effects;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

public class Bleeding extends StatusEffect {
    public Bleeding() { super (StatusEffectCategory.HARMFUL, 0x780606); }

    public static final Identifier BLEEDING_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "bleeding");

    public static RegistryEntry.Reference<StatusEffect> entry() {
        return Registries.STATUS_EFFECT.getEntry(BLEEDING_ID)
                .orElseThrow(() -> new IllegalStateException("Bleeding effect not registered!" + BLEEDING_ID));
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return duration % 10 == 0;
    }


}
