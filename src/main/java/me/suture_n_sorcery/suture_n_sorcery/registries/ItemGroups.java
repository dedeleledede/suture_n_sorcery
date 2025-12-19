package me.suture_n_sorcery.suture_n_sorcery.registries;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import me.suture_n_sorcery.suture_n_sorcery.blocks.Condensator.Condensator;
import me.suture_n_sorcery.suture_n_sorcery.blocks.Condensator.CondensatorItem;
import me.suture_n_sorcery.suture_n_sorcery.items.ConcentratedBloodBucket;
import me.suture_n_sorcery.suture_n_sorcery.items.DirtyGauze;
import me.suture_n_sorcery.suture_n_sorcery.items.Gauze;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ItemGroups {
    public static final ItemGroup SUTURE_N_SORCERY = FabricItemGroup.builder()
            .icon(() -> new ItemStack(Gauze.GAUZE))
            .displayName(Text.translatable("itemGroup.suture_n_sorcery"))
            .entries((displayContext, entries) -> {
                entries.add(new ItemStack(Gauze.GAUZE));
                entries.add(new ItemStack(DirtyGauze.DIRTY_GAUZE));
                entries.add(new ItemStack(CondensatorItem.CONDENSATOR_ITEM));
                entries.add(new ItemStack(ConcentratedBloodBucket.CONCENTRATED_BLOOD_BUCKET));
             })
            .build();


    public static void registerItemGroups(){
        Registry.register(
                Registries.ITEM_GROUP,
                Identifier.of(Suture_n_sorcery.MOD_ID, "item_group"),
                SUTURE_N_SORCERY
        );
    }
}
