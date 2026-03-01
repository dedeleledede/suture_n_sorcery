package me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class RitualLoomItem extends BlockItem {
    public RitualLoomItem(Item.Settings settings) {
        super(RitualLoom.RITUAL_LOOM, settings);
    }

    public static final Identifier RITUAL_LOOM_ITEM_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "ritual_loom");

    public static final RegistryKey<Item> RITUAL_LOOM_ITEM_KEY =
            RegistryKey.of(RegistryKeys.ITEM, RITUAL_LOOM_ITEM_ID);

    public static final BlockItem RITUAL_LOOM_ITEM =
            new RitualLoomItem(
                    new Item.Settings()
                            .registryKey(RITUAL_LOOM_ITEM_KEY)
                            .useBlockPrefixedTranslationKey()
            );
}
