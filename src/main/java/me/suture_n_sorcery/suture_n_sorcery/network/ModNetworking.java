package me.suture_n_sorcery.suture_n_sorcery.network;

import me.suture_n_sorcery.suture_n_sorcery.blood_sense.BloodSenseTrace;
import me.suture_n_sorcery.suture_n_sorcery.blood_sense.BloodSenseTracker;
import me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom.RitualLoomBlockEntity;
import me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom.RitualLoomScreenHandler;
import me.suture_n_sorcery.suture_n_sorcery.items.HematicCatalyst;
import me.suture_n_sorcery.suture_n_sorcery.veinmaker.VeinmakerTrailTracker;
import me.suture_n_sorcery.suture_n_sorcery.veinmaker.VeinmakerTrailWorldState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ModNetworking {
    private static final double MAX_SCREEN_USE_DISTANCE_SQUARED = 64.0;
    private static final int BLOOD_SENSE_RADIUS = 48;
    private static final int MAX_BLOOD_SENSE_TRACES = 96;
    private static final int VEINMAKER_TRAIL_SYNC_RADIUS = 96;
    private static final int MAX_VEINMAKER_TRAIL_CELLS_PER_PACKET = 1024;
    private static final int JOIN_TRAIL_SYNC_DELAY_TICKS = 20;
    private static final Map<UUID, Integer> PENDING_JOIN_TRAIL_SYNCS = new HashMap<>();

    private static boolean initialized = false;

    private ModNetworking() {}

    public static void init() {
        if (initialized) return;
        initialized = true;

        registerPressurizePayload();
        registerBloodSensePayloads();
        registerHematicBondPayload();
        registerVeinmakerTrailPayload();
        registerPressurizeReceiver();
        registerBloodSenseReceiver();
        registerHematicBondSync();
        registerDelayedTrailSync();
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

    private static void registerVeinmakerTrailPayload() {
        PayloadTypeRegistry.playS2C().register(VeinmakerTrailPayload.ID, VeinmakerTrailPayload.CODEC);
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
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            syncHematicBond(handler.player);
            sendContainedBloodSenseUpdate(handler.player);
            sendVeinmakerTrails(handler.player);
            PENDING_JOIN_TRAIL_SYNCS.put(handler.player.getUuid(), JOIN_TRAIL_SYNC_DELAY_TICKS);
        });
    }

    private static void registerDelayedTrailSync() {
        ServerTickEvents.END_SERVER_TICK.register(server -> PENDING_JOIN_TRAIL_SYNCS.entrySet().removeIf(entry -> {
            int ticks = entry.getValue() - 1;
            if (ticks > 0) {
                entry.setValue(ticks);
                return false;
            }

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player != null) {
                sendContainedBloodSenseUpdate(player);
                sendVeinmakerTrails(player);
            }
            return true;
        }));
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

        sendBloodSenseUpdate(player, true);
    }

    public static void sendBloodSenseUpdate(ServerPlayerEntity player, boolean markActive) {
        if (!HematicCatalyst.hasAbsorbedCatalyst(player)) return;

        ServerWorld world = player.getEntityWorld();
        if (markActive) {
            BloodSenseTracker.markBloodSenseActive(world, player.getUuid());
        } else if (!BloodSenseTracker.isBloodSenseActive(world, player.getUuid())) {
            return;
        }
        List<BloodSenseResponsePayload.Trace> traces = BloodSenseTracker
                .recentTraces(world, player.getBlockPos(), BLOOD_SENSE_RADIUS)
                .stream()
                .limit(MAX_BLOOD_SENSE_TRACES)
                .map(trace -> toPayloadTrace(trace, world.getTime()))
                .toList();

        ServerPlayNetworking.send(player, new BloodSenseResponsePayload(traces, !markActive));
    }

    public static void sendContainedBloodSenseUpdate(ServerPlayerEntity player) {
        if (!HematicCatalyst.hasAbsorbedCatalyst(player)) return;

        ServerWorld world = player.getEntityWorld();
        List<BloodSenseResponsePayload.Trace> traces = BloodSenseTracker
                .recentTraces(world, player.getBlockPos(), BLOOD_SENSE_RADIUS)
                .stream()
                .filter(trace -> trace.state() == BloodSenseTracker.STATE_CONTAINED)
                .limit(MAX_BLOOD_SENSE_TRACES)
                .map(trace -> toPayloadTrace(trace, world.getTime()))
                .toList();

        ServerPlayNetworking.send(player, new BloodSenseResponsePayload(traces, true));
    }

    public static void sendVeinmakerTrailCells(ServerPlayerEntity player, List<VeinmakerTrailWorldState.PaintedCell> cells, boolean replace) {
        if (cells.isEmpty()) {
            if (replace) {
                ServerPlayNetworking.send(player, new VeinmakerTrailPayload(List.of(), true, VeinmakerTrailTracker.lifetimeTicks()));
            }
            return;
        }

        long now = player.getEntityWorld().getTime();
        List<VeinmakerTrailPayload.Cell> payloadCells = cells.stream()
                .map(cell -> new VeinmakerTrailPayload.Cell(
                        cell.pos(),
                        cell.side(),
                        cell.pixelX(),
                        cell.pixelY(),
                        (int)Math.max(0, now - cell.createdTime()),
                        cell.groupId(),
                        BloodSenseTracker.hasContainedTraceNear(player.getEntityWorld(), cell.pos(), 7)
                ))
                .toList();

        for (int start = 0; start < payloadCells.size(); start += MAX_VEINMAKER_TRAIL_CELLS_PER_PACKET) {
            int end = Math.min(start + MAX_VEINMAKER_TRAIL_CELLS_PER_PACKET, payloadCells.size());
            boolean replaceChunk = replace && start == 0;
            ServerPlayNetworking.send(player, new VeinmakerTrailPayload(
                    payloadCells.subList(start, end),
                    replaceChunk,
                    VeinmakerTrailTracker.lifetimeTicks()
            ));
        }
    }

    public static void sendVeinmakerTrails(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        sendVeinmakerTrailCells(
                player,
                VeinmakerTrailTracker.cellsNear(world, player.getBlockPos(), VEINMAKER_TRAIL_SYNC_RADIUS),
                true
        );
    }

    private static BloodSenseResponsePayload.Trace toPayloadTrace(BloodSenseTrace trace, long now) {
        BlockPos pos = trace.pos();
        return new BloodSenseResponsePayload.Trace(
                trace.type().ordinal(),
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                trace.strength(),
                trace.age(now),
                trace.state()
        );
    }

    public static void syncHematicBond(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new HematicBondPayload(HematicCatalyst.hasAbsorbedCatalyst(player)));
    }
}
