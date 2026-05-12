package me.suture_n_sorcery.suture_n_sorcery.network;

import me.suture_n_sorcery.suture_n_sorcery.blood_sense.BloodSenseTrace;
import me.suture_n_sorcery.suture_n_sorcery.blood_sense.BloodSenseTraceType;
import me.suture_n_sorcery.suture_n_sorcery.blood_sense.BloodSenseTracker;
import me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom.RitualLoomBlockEntity;
import me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom.RitualLoomScreenHandler;
import me.suture_n_sorcery.suture_n_sorcery.items.HematicCatalyst;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public final class ModNetworking {
    private static final double MAX_SCREEN_USE_DISTANCE_SQUARED = 64.0;
    private static final int BLOOD_SENSE_RADIUS = 48;
    private static final int MAX_BLOOD_SENSE_TRACES = 96;

    private static boolean initialized = false;

    private ModNetworking() {}

    public static void init() {
        if (initialized) return;
        initialized = true;

        registerPressurizePayload();
        registerBloodSensePayloads();
        registerHematicBondPayload();
        registerPressurizeReceiver();
        registerBloodSenseReceiver();
        registerHematicBondSync();
    }

    private static void registerPressurizePayload() {
        PayloadTypeRegistry.playC2S().register(PressurizePayload.ID, PressurizePayload.CODEC);
    }

    private static void registerBloodSensePayloads() {
        PayloadTypeRegistry.playC2S().register(BloodSenseRequestPayload.ID, BloodSenseRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BloodSenseResponsePayload.ID, BloodSenseResponsePayload.CODEC);
    }

    private static void registerHematicBondPayload() {
        PayloadTypeRegistry.playS2C().register(HematicBondPayload.ID, HematicBondPayload.CODEC);
    }

    private static void registerPressurizeReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(PressurizePayload.ID, (payload, context) ->
                context.server().execute(() -> handlePressurize(payload, context))
        );
    }

    private static void registerBloodSenseReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(BloodSenseRequestPayload.ID, (payload, context) ->
                context.server().execute(() -> handleBloodSense(context.player()))
        );
    }

    private static void registerHematicBondSync() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                syncHematicBond(handler.player)
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

    private static void handleBloodSense(ServerPlayerEntity player) {
        if (!HematicCatalyst.hasAbsorbedCatalyst(player)) return;

        ServerWorld world = player.getEntityWorld();
        BloodSenseTracker.markBloodSenseActive(world, player.getUuid());
        List<BloodSenseResponsePayload.Trace> traces = BloodSenseTracker
                .recentTraces(world, player.getBlockPos(), BLOOD_SENSE_RADIUS)
                .stream()
                .limit(MAX_BLOOD_SENSE_TRACES)
                .map(trace -> toPayloadTrace(trace, world.getTime()))
                .toList();

        ServerPlayNetworking.send(player, new BloodSenseResponsePayload(traces));
    }

    private static BloodSenseResponsePayload.Trace toPayloadTrace(BloodSenseTrace trace, long now) {
        BlockPos pos = trace.pos();
        int type = trace.type() == BloodSenseTraceType.RITUAL ? 1 : 0;
        return new BloodSenseResponsePayload.Trace(
                type,
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                trace.strength(),
                trace.age(now)
        );
    }

    public static void syncHematicBond(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new HematicBondPayload(HematicCatalyst.hasAbsorbedCatalyst(player)));
    }
}
