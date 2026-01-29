package me.suture_n_sorcery.suture_n_sorcery.registries;

import me.suture_n_sorcery.suture_n_sorcery.blocks.Condensator.Condensator;
import me.suture_n_sorcery.suture_n_sorcery.fluids.ConcentratedBlood.ConcentratedBloodBlock;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public class ModBlocks {
    public static void registerBlocks(){
        Registry.register(Registries.BLOCK, Condensator.CONDENSATOR_KEY, Condensator.CONDENSATOR);
        Registry.register(Registries.BLOCK, ConcentratedBloodBlock.CONCENTRATED_BLOOD_BLOCK_ID, ConcentratedBloodBlock.CONCENTRATED_BLOOD_BLOCK);
    }
}


