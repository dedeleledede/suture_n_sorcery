package me.suture_n_sorcery.suture_n_sorcery.items;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ConcentratedBloodBucket extends Item {
    public ConcentratedBloodBucket(Settings settings) { super(settings); }

    public static final Identifier CONCENTRATED_BLOOD_BUCKET_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "concentrated_blood_bucket");

    public static final RegistryKey<Item> CONCENTRATED_BLOOD_BUCKET_KEY =
            RegistryKey.of(RegistryKeys.ITEM, CONCENTRATED_BLOOD_BUCKET_ID);

    public static final Item CONCENTRATED_BLOOD_BUCKET = new ConcentratedBloodBucket(new Item
            .Settings()
            .registryKey(CONCENTRATED_BLOOD_BUCKET_KEY)
            .maxCount(1)
    );

}
