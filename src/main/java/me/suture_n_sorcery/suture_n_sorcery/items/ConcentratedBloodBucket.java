package me.suture_n_sorcery.suture_n_sorcery.items;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import me.suture_n_sorcery.suture_n_sorcery.fluids.ConcentratedBlood.ConcentratedBloodFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.BucketItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ConcentratedBloodBucket extends BucketItem {
    public ConcentratedBloodBucket(Fluid fluid, Settings settings) { super(fluid, settings); }

    public static final Identifier CONCENTRATED_BLOOD_BUCKET_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "concentrated_blood_bucket");

    public static final RegistryKey<Item> CONCENTRATED_BLOOD_BUCKET_KEY =
            RegistryKey.of(RegistryKeys.ITEM, CONCENTRATED_BLOOD_BUCKET_ID);

    public static final Item CONCENTRATED_BLOOD_BUCKET =
            new ConcentratedBloodBucket(
                ConcentratedBloodFluid.CONCENTRATED_BLOOD,
                new Item.Settings()
                        .registryKey(CONCENTRATED_BLOOD_BUCKET_KEY)
                        .maxCount(1)
                        .recipeRemainder(Items.BUCKET)
            );
}
