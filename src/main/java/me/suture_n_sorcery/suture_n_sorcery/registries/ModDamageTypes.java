package me.suture_n_sorcery.suture_n_sorcery.registries;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModDamageTypes {
    public static final RegistryKey<DamageType> BLEEDING =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Suture_n_sorcery.MOD_ID, "bleeding"));
    private ModDamageTypes() {}
}
