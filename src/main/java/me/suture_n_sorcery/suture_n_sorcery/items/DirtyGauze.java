package me.suture_n_sorcery.suture_n_sorcery.items;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class DirtyGauze extends Item {
    public DirtyGauze(Settings settings) { super (settings); }

    public static final Identifier DIRTY_GAUZE_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "dirty_gauze");

    public static final RegistryKey<Item> DIRTY_GAUZE_KEY =
            RegistryKey.of(RegistryKeys.ITEM, DIRTY_GAUZE_ID);

    public static final Item DIRTY_GAUZE = new DirtyGauze(new Settings()
            .registryKey(DIRTY_GAUZE_KEY)
            .maxCount(16)
    );

}
