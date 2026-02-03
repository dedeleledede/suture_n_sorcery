package me.suture_n_sorcery.suture_n_sorcery.tags;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public final class ModEntityTypeTags {
    public static final TagKey<EntityType<?>> BLEEDABLE =
            TagKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Suture_n_sorcery.MOD_ID, "bleedable"));
    private ModEntityTypeTags() {}
}
