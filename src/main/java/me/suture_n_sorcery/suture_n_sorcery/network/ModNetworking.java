package me.suture_n_sorcery.suture_n_sorcery.network;

import me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom.RitualLoomBlockEntity;
import me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom.RitualLoomScreenHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class ModNetworking {
    private static boolean initialized = false;

    private ModNetworking() {}

    public static void init() {
        if (initialized) return;
        initialized = true;

        // Register payload type so it can be decoded on both sides.
        PayloadTypeRegistry.playC2S().register(PressurizePayload.ID, PressurizePayload.CODEC);

        // Server receiver
        ServerPlayNetworking.registerGlobalReceiver(PressurizePayload.ID, (payload, context) -> context.server().execute(() -> {
            var player = context.player();
            BlockPos pos = payload.pos();

            // Basic anti-spoofing: must have the loom screen open for that exact position.
            if (!(player.currentScreenHandler instanceof RitualLoomScreenHandler handler)) return;

// must be able to use THIS handler (vanilla distance check + still-open inventory)
            if (!handler.canUse(player)) return;

// must be close to the target pos
            if (player.squaredDistanceTo(
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5
            ) > 64.0) return; // 8 blocks

            double dx = (pos.getX() + 0.5) - player.getX();
            double dy = (pos.getY() + 0.5) - player.getY();
            double dz = (pos.getZ() + 0.5) - player.getZ();
            if ((dx * dx + dy * dy + dz * dz) > 64.0) return;

            // AFTER (Yarn 1.21.10+build3)
            ServerWorld world = player.getEntityWorld();
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof RitualLoomBlockEntity loom) {
                loom.setPressurizing(payload.pressed());
            }
        }));
    }
}
