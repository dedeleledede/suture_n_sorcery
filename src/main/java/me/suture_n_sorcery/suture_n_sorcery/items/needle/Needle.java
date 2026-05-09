package me.suture_n_sorcery.suture_n_sorcery.items.needle;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class Needle extends Item {
    public Needle(Settings settings) { super(settings); }

    public static final Identifier NEEDLE_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "needle");

    public static final RegistryKey<Item> NEEDLE_KEY =
            RegistryKey.of(RegistryKeys.ITEM, NEEDLE_ID);

    public static final Item NEEDLE = new Needle(new Settings()
            .registryKey(NEEDLE_KEY)
            .maxCount(1)
    );
}