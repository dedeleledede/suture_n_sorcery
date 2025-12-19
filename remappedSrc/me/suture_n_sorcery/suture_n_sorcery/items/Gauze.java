package me.suture_n_sorcery.suture_n_sorcery.items;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class Gauze extends Item {
    public Gauze(net.minecraft.item.Item.Settings properties) { super (properties); }

    public static final Identifier GAUZE_ID = Identifier.of(Suture_n_sorcery.MOD_ID, "gauze");

    public static final RegistryKey<Item> GAUZE_KEY = RegistryKey.of(RegistryKeys.ITEM, GAUZE_ID);

    public final Item GAUZE =
            Registry.register(
                    Registries.ITEM,
                    GAUZE_ID,
                    new Gauze(
                            new Item.Settings().maxCount(16)
                    )
            );

}
