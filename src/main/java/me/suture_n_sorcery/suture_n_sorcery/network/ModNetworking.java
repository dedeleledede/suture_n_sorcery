package me.suture_n_sorcery.suture_n_sorcery.network;

import me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom.RitualLoomBlockEntity;
import me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom.RitualLoomScreenHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class ModNetworking {
    private static final double MAX_SCREEN_USE_DISTANCE_SQUARED = 64.0;

    private static boolean initialized = false;

    private ModNetworking() {}

    public static void init() {
        if (initialized) return;
        initialized = true;

        registerPressurizePayload();
        registerPressurizeReceiver();
    }

    private static void registerPressurizePayload() {
        PayloadTypeRegistry.playC2S().register(PressurizePayload.ID, PressurizePayload.CODEC);
    }

    private static void registerPressurizeReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(PressurizePayload.ID, (payload, context) ->
                context.server().execute(() -> handlePressurize(payload, context))
        );
    }

    private static void handlePressurize(PressurizePayload payload, ServerPlayNetworking.Context context) {
        var player = context.player();
        BlockPos pos = payload.pos();

        if (!(player.currentScreenHandler instanceof RitualLoomScreenHandler handler)) return;
        if (!canPressurize(handler, player, pos)) return;

        ServerWorld world = player.getEntityWorld();
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof RitualLoomBlockEntity loom) {
            loom.setPressurizing(payload.pressed());
        }
    }

    private static boolean canPressurize(RitualLoomScreenHandler handler, ServerPlayerEntity player, BlockPos pos) {
        if (!handler.canUse(player)) return false;

        // the payload position must match something the open screen can still reach
        return player.squaredDistanceTo(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
        ) <= MAX_SCREEN_USE_DISTANCE_SQUARED;
    }
}
