package me.suture_n_sorcery.suture_n_sorcery.items;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class BoneMarrow extends Item {
    public BoneMarrow(Settings settings) { super(settings); }

    public static final Identifier BONE_MARROW_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "bone_marrow");

    public static final RegistryKey<Item> BONE_MARROW_KEY =
            RegistryKey.of(RegistryKeys.ITEM, BONE_MARROW_ID);

    private static final FoodComponent BONE_MARROW_FOOD = new FoodComponent.Builder()
            .nutrition(7)
            .saturationModifier(0.9f)
            .alwaysEdible()
            .build();

    public static final Item BONE_MARROW = new BoneMarrow(new Settings()
            .registryKey(BONE_MARROW_KEY)
            .food(BONE_MARROW_FOOD)
            .maxCount(64)
    );
}
