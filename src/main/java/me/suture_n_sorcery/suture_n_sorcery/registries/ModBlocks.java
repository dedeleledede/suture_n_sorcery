package me.suture_n_sorcery.suture_n_sorcery.registries;

import me.suture_n_sorcery.suture_n_sorcery.blocks.Condenser.Condenser;
import me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom.RitualLoom;
import me.suture_n_sorcery.suture_n_sorcery.fluids.ConcentratedBlood.ConcentratedBloodBlock;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public class ModBlocks {
    public static void registerBlocks(){
        Registry.register(Registries.BLOCK, Condenser.CONDENSATOR_KEY, Condenser.CONDENSATOR);
        Registry.register(Registries.BLOCK, ConcentratedBloodBlock.CONCENTRATED_BLOOD_BLOCK_ID, ConcentratedBloodBlock.CONCENTRATED_BLOOD_BLOCK);
        Registry.register(Registries.BLOCK, RitualLoom.RITUAL_LOOM_KEY, RitualLoom.RITUAL_LOOM);
    }
}


