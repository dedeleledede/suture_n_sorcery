package me.suture_n_sorcery.suture_n_sorcery.registries;

import me.suture_n_sorcery.suture_n_sorcery.blocks.Condenser.Condenser;
import me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom.RitualLoom;
import me.suture_n_sorcery.suture_n_sorcery.fluids.ConcentratedBlood.ConcentratedBloodBlock;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

public final class ModBlocks {
    private ModBlocks() {
    }

    public static void registerBlocks() {
        register(Condenser.CONDENSATOR_KEY, Condenser.CONDENSATOR);
        register(ConcentratedBloodBlock.CONCENTRATED_BLOOD_BLOCK_ID, ConcentratedBloodBlock.CONCENTRATED_BLOOD_BLOCK);
        register(RitualLoom.RITUAL_LOOM_KEY, RitualLoom.RITUAL_LOOM);
    }

    private static void register(RegistryKey<Block> key, Block block) {
        Registry.register(Registries.BLOCK, key, block);
    }

    private static void register(Identifier id, Block block) {
        Registry.register(Registries.BLOCK, id, block);
    }
}


