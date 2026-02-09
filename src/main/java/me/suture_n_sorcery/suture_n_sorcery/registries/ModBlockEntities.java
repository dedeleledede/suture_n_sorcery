package me.suture_n_sorcery.suture_n_sorcery.registries;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import me.suture_n_sorcery.suture_n_sorcery.blocks.Condensator.Condensator;
import me.suture_n_sorcery.suture_n_sorcery.blocks.Condensator.CondensatorBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModBlockEntities {

    public static BlockEntityType<CondensatorBlockEntity> CONDENSATOR_BLOCK_ENTITY;

    public static void registerBlockEntities() {
        if (CONDENSATOR_BLOCK_ENTITY != null) return; // prevents double-register

        CONDENSATOR_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(Suture_n_sorcery.MOD_ID, "condensator"),
                FabricBlockEntityTypeBuilder.create(CondensatorBlockEntity::new, Condensator.CONDENSATOR).build()
        );
    }
}
