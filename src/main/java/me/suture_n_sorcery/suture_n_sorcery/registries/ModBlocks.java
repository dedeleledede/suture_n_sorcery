package me.suture_n_sorcery.suture_n_sorcery.registries;

import me.suture_n_sorcery.suture_n_sorcery.blocks.Condensator.Condensator;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public class ModBlocks {
    public static void registerBlocks(){
        Registry.register(Registries.BLOCK, Condensator.CONDENSATOR_KEY, Condensator.CONDENSATOR);
    }
}


