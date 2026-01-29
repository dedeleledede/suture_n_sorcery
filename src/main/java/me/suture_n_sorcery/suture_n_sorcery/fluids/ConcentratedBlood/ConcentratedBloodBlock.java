package me.suture_n_sorcery.suture_n_sorcery.fluids.ConcentratedBlood;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ConcentratedBloodBlock {

    public static final Identifier CONCENTRATED_BLOOD_BLOCK_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "concentrated_blood");

    public static final RegistryKey<Block> CONCENTRATED_BLOOD_BLOCK_KEY =
            RegistryKey.of(RegistryKeys.BLOCK, CONCENTRATED_BLOOD_BLOCK_ID);

    public static final Block CONCENTRATED_BLOOD_BLOCK =
            new FluidBlock(ConcentratedBloodFluid.CONCENTRATED_BLOOD, AbstractBlock.Settings.copy(Blocks.WATER)
                    .registryKey(CONCENTRATED_BLOOD_BLOCK_KEY)) {};
}
