package me.suture_n_sorcery.suture_n_sorcery.registries;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import me.suture_n_sorcery.suture_n_sorcery.blocks.Condenser.Condenser;
import me.suture_n_sorcery.suture_n_sorcery.blocks.Condenser.CondenserBlockEntity;
import me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom.RitualLoom;
import me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom.RitualLoomBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModBlockEntities {

    public static BlockEntityType<RitualLoomBlockEntity> RITUAL_LOOM_BLOCK_ENTITY;
    public static BlockEntityType<CondenserBlockEntity> CONDENSATOR_BLOCK_ENTITY;

    public static void registerBlockEntities() {
        CONDENSATOR_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(Suture_n_sorcery.MOD_ID, "condensator"),
                FabricBlockEntityTypeBuilder.create(CondenserBlockEntity::new, Condenser.CONDENSATOR).build()
        );

        RITUAL_LOOM_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(Suture_n_sorcery.MOD_ID, "ritual_loom"),
                FabricBlockEntityTypeBuilder.create(RitualLoomBlockEntity::new, RitualLoom.RITUAL_LOOM).build()
        );
    }
}
