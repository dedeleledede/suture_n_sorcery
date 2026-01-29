package me.suture_n_sorcery.suture_n_sorcery.mixin.client;

import me.suture_n_sorcery.suture_n_sorcery.fluids.ConcentratedBlood.ConcentratedBloodFluid;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(FogRenderer.class)
public class FogRendererMixin {

    // Fog color 0x8A0303
    @Unique
    private static final Vector4f FOG_COLOR = new Vector4f(0.5411765f, 0.011764706f, 0.011764706f, 1.0f);

    @Unique private static boolean sutureNS$inBloodFog = false;

    @Inject(
            method = "applyFog(Lnet/minecraft/client/render/Camera;IZLnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
            at = @At("HEAD")
    )
    private void sutureNS$markBloodFog(Camera camera, int viewDistance, boolean thick, RenderTickCounter tickCounter, float skyDarkness, ClientWorld world, CallbackInfoReturnable<Vector4f> cir) {
        sutureNS$inBloodFog = sutureNS$isCameraInBlood(camera, world);
    }

    @ModifyArgs(
            method = "applyFog(Lnet/minecraft/client/render/Camera;IZLnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"
            )
    )
    private void sutureNS$overrideFogUpload(Args args) {
        if (!sutureNS$inBloodFog) return;

        args.set(2, FOG_COLOR);

        // Fog density
        float start = 0.0f;
        float end   = 1.0f;

        args.set(3, start); // environmentalStart
        args.set(4, end);   // environmentalEnd
        args.set(5, start); // renderDistanceStart
        args.set(6, end);   // renderDistanceEnd

        // sky/cloud fog
        args.set(7, end); // skyEnd
        args.set(8, end); // cloudEnd
    }

    @Unique
    private static boolean sutureNS$isCameraInBlood(Camera camera, ClientWorld world) {
        BlockPos pos = BlockPos.ofFloored(camera.getPos());
        var fs = world.getFluidState(pos);
        if (!fs.getFluid().matchesType(ConcentratedBloodFluid.CONCENTRATED_BLOOD)) return false;

        float pad = 0.2F; // adjust
        float h = fs.getHeight(world, pos);          // 0..1
        double localY = camera.getPos().y - pos.getY(); // 0..1 inside the block
        return localY < h + pad;
    }

    @Inject(
            method="applyFog(Lnet/minecraft/client/render/Camera;IZLnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
            at=@At("RETURN"),
            cancellable=true
    )
    private void sutureNS$overrideReturn(Camera camera, int viewDistance, boolean thick,
                                         RenderTickCounter tickCounter, float skyDarkness,
                                         ClientWorld world, CallbackInfoReturnable<Vector4f> cir) {
        if (!sutureNS$inBloodFog) return;
        cir.setReturnValue(new Vector4f(FOG_COLOR));
    }
}