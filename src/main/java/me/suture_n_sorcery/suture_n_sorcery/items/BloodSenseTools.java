package me.suture_n_sorcery.suture_n_sorcery.items;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class BloodSenseTools {
    private BloodSenseTools() {
    }

    public static final Identifier VEINMAKER_ID = Identifier.of(Suture_n_sorcery.MOD_ID, "veinmaker");
    public static final RegistryKey<Item> VEINMAKER_KEY = RegistryKey.of(RegistryKeys.ITEM, VEINMAKER_ID);
    public static final Item VEINMAKER = new Item(new Item.Settings()
            .registryKey(VEINMAKER_KEY)
            .maxCount(1)
    );

    public static final Identifier SANGUINE_FIXATIVE_ID = Identifier.of(Suture_n_sorcery.MOD_ID, "sanguine_fixative");
    public static final RegistryKey<Item> SANGUINE_FIXATIVE_KEY = RegistryKey.of(RegistryKeys.ITEM, SANGUINE_FIXATIVE_ID);
    public static final Item SANGUINE_FIXATIVE = new Item(new Item.Settings()
            .registryKey(SANGUINE_FIXATIVE_KEY)
            .maxCount(16)
    );

    public static final Identifier ECHO_ASH_ID = Identifier.of(Suture_n_sorcery.MOD_ID, "echo_ash");
    public static final RegistryKey<Item> ECHO_ASH_KEY = RegistryKey.of(RegistryKeys.ITEM, ECHO_ASH_ID);
    public static final Item ECHO_ASH = new Item(new Item.Settings()
            .registryKey(ECHO_ASH_KEY)
            .maxCount(64)
    );

    public static final Identifier VIAL_ID = Identifier.of(Suture_n_sorcery.MOD_ID, "vial");
    public static final RegistryKey<Item> VIAL_KEY = RegistryKey.of(RegistryKeys.ITEM, VIAL_ID);
    public static final Item VIAL = new Item(new Item.Settings()
            .registryKey(VIAL_KEY)
            .maxCount(16)
    );

    public static final Identifier BOTTLED_BLOOD_ID = Identifier.of(Suture_n_sorcery.MOD_ID, "bottled_blood");
    public static final RegistryKey<Item> BOTTLED_BLOOD_KEY = RegistryKey.of(RegistryKeys.ITEM, BOTTLED_BLOOD_ID);
    public static final Item BOTTLED_BLOOD = new Item(new Item.Settings()
            .registryKey(BOTTLED_BLOOD_KEY)
            .maxCount(16)
    );
}
