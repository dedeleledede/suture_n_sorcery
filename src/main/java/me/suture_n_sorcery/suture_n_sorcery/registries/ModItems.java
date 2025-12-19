package me.suture_n_sorcery.suture_n_sorcery.registries;

import me.suture_n_sorcery.suture_n_sorcery.blocks.Condensator.CondensatorItem;
import me.suture_n_sorcery.suture_n_sorcery.items.ConcentratedBloodBucket;
import me.suture_n_sorcery.suture_n_sorcery.items.DirtyGauze;
import me.suture_n_sorcery.suture_n_sorcery.items.Gauze;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public class ModItems{
    public static void registerItems(){

        //HAND ITEMS

        Registry.register(Registries.ITEM, Gauze.GAUZE_KEY,  Gauze.GAUZE);
        Registry.register(Registries.ITEM, DirtyGauze.DIRTY_GAUZE_KEY, DirtyGauze.DIRTY_GAUZE);
        Registry.register(Registries.ITEM, ConcentratedBloodBucket.CONCENTRATED_BLOOD_BUCKET_KEY, ConcentratedBloodBucket.CONCENTRATED_BLOOD_BUCKET);

        // BLOCK ITEMS

        Registry.register(Registries.ITEM, CondensatorItem.CONDENSATOR_ITEM_KEY, CondensatorItem.CONDENSATOR_ITEM);
    }
}
