package me.suture_n_sorcery.suture_n_sorcery.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DrawContext.class)
public interface DrawContextInvoker {
    @Invoker("drawTexturedQuad")
    void sns$drawTexturedQuad(
            RenderPipeline pipeline,
            GpuTextureView view,
            int x1, int y1, int x2, int y2,
            float u1, float u2, float v1, float v2,
            int color
    );
}