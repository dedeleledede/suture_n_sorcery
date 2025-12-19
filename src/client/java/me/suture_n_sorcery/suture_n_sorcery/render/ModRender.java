package me.suture_n_sorcery.suture_n_sorcery.render;

import me.suture_n_sorcery.suture_n_sorcery.blocks.Condensator.Condensator;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.client.render.BlockRenderLayer;

public class ModRender {
    public static void registerRender() {
        BlockRenderLayerMap.putBlocks(
                BlockRenderLayer.CUTOUT,
                Condensator.CONDENSATOR
        );

        BlockRenderLayerMap.putBlocks(
                BlockRenderLayer.CUTOUT_MIPPED,
                Condensator.CONDENSATOR
        );
    }
}
