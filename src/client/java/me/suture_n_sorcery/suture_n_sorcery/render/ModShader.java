package me.suture_n_sorcery.suture_n_sorcery.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

public class ModShader {
    public static RenderPipeline SHOCKWAVE;

    public static void registerShader(){
        SHOCKWAVE = RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
                        .withLocation(Identifier.of(Suture_n_sorcery.MOD_ID, "pipeline/shockwave_gui"))
                        .withVertexShader(Identifier.of(Suture_n_sorcery.MOD_ID, "core/shockwave"))
                        .withFragmentShader(Identifier.of(Suture_n_sorcery.MOD_ID, "core/shockwave"))
                        .withSampler("Sampler0")
                        .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                        .withUsePipelineDrawModeForGui(true)
                        .build()
        );
    }
}
