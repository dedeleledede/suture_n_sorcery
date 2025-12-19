package me.suture_n_sorcery.suture_n_sorcery.blocks.Condensator;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class CondensatorItem extends BlockItem {
    public CondensatorItem(Item.Settings settings) { super(Condensator.CONDENSATOR, settings); }

    public static final Identifier CONDENSATOR_ITEM_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "condensator");

    public static final RegistryKey<Item> CONDENSATOR_ITEM_KEY =
            RegistryKey.of(RegistryKeys.ITEM, CONDENSATOR_ITEM_ID);

    public static final BlockItem CONDENSATOR_ITEM =
            new CondensatorItem(
                    new Item.Settings()
                            .registryKey(CONDENSATOR_ITEM_KEY)
                            .useBlockPrefixedTranslationKey()
            );
}
