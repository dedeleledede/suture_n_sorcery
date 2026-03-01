package me.suture_n_sorcery.suture_n_sorcery.registries;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import me.suture_n_sorcery.suture_n_sorcery.blocks.Condenser.CondenserItem;
import me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom.RitualLoomItem;
import me.suture_n_sorcery.suture_n_sorcery.items.*;
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
                entries.add(new ItemStack(CondenserItem.CONDENSATOR_ITEM));
                entries.add(new ItemStack(ConcentratedBloodBucket.CONCENTRATED_BLOOD_BUCKET));
                entries.add(new ItemStack(RitualLoomItem.RITUAL_LOOM_ITEM));
                entries.add(new ItemStack(HematicCatalyzer.HEMATIC_CATALYZER));
                entries.add(new ItemStack(BoneMarrow.BONE_MARROW));
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
