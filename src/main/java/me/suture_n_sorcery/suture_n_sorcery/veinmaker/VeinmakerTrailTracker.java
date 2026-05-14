package me.suture_n_sorcery.suture_n_sorcery.veinmaker;

import me.suture_n_sorcery.suture_n_sorcery.blood_sense.BloodSenseTracker;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class VeinmakerTrailTracker {
    private static final int MAX_CELLS_PER_WORLD = 8192;
    private static final int BRUSH_RADIUS_PIXELS = 1;
    private static final int TRAIL_LIFETIME_TICKS = 20 * 60 * 10;
    public static final int UNCONTAINED_TRAIL_LIFETIME_TICKS = 20 * 15;
    private static final int ACTIVE_PILLAR_RADIUS = 7;
    private static final int PRUNE_INTERVAL_TICKS = 20;

    private static boolean initialized = false;
    private static final Set<RegistryKey<World>> FORCE_PRUNE_WORLDS = new HashSet<>();

    private VeinmakerTrailTracker() {
    }

    public static void registerTrailEvents() {
        if (initialized) return;
        initialized = true;

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            boolean forced = FORCE_PRUNE_WORLDS.remove(world.getRegistryKey());
            if (forced || world.getTime() % PRUNE_INTERVAL_TICKS == 0) {
                prune(world);
            }
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerWorld serverWorld) {
                FORCE_PRUNE_WORLDS.add(serverWorld.getRegistryKey());
            }
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world instanceof ServerWorld serverWorld) {
                FORCE_PRUNE_WORLDS.add(serverWorld.getRegistryKey());
            }
            return ActionResult.PASS;
        });
    }

    public static List<VeinmakerTrailWorldState.PaintedCell> paint(ServerWorld world, BlockPos pos, Direction side, Vec3d hitPos, int groupId) {
        prune(world);
        return paintStamp(world, pos, side, hitPos, groupId);
    }

    private static List<VeinmakerTrailWorldState.PaintedCell> paintStamp(ServerWorld world, BlockPos pos, Direction side, Vec3d hitPos, int groupId) {
        VeinmakerTrailWorldState state = state(world);
        List<VeinmakerTrailWorldState.PaintedCell> added = new ArrayList<>();
        Pixel pixel = pixelOnFace(pos, side, hitPos);
        long now = world.getTime();

        for (int dx = -BRUSH_RADIUS_PIXELS; dx <= BRUSH_RADIUS_PIXELS; dx++) {
            for (int dy = -BRUSH_RADIUS_PIXELS; dy <= BRUSH_RADIUS_PIXELS; dy++) {
                int x = Math.clamp(pixel.x + dx, 0, 15);
                int y = Math.clamp(pixel.y + dy, 0, 15);
                VeinmakerTrailWorldState.PaintedCell cell = new VeinmakerTrailWorldState.PaintedCell(
                        pos.toImmutable(),
                        side.ordinal(),
                        x,
                        y,
                        now,
                        groupId
                );
                upsert(state.cells(), cell);
                added.add(cell);
            }
        }

        if (state.cells().size() > MAX_CELLS_PER_WORLD) {
            state.cells().subList(0, state.cells().size() - MAX_CELLS_PER_WORLD).clear();
        }
        state.markDirty();
        return added;
    }

    public static List<VeinmakerTrailWorldState.PaintedCell> paintLine(ServerWorld world, BlockPos fromPos, Vec3d fromHit, BlockPos toPos, Vec3d toHit, Direction side, int groupId) {
        prune(world);
        List<VeinmakerTrailWorldState.PaintedCell> added = new ArrayList<>();
        double distance = fromHit.distanceTo(toHit);
        int steps = Math.max(1, (int)Math.ceil(distance * 32.0));

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3d hit = fromHit.lerp(toHit, t);
            BlockPos pos = i == 0 ? fromPos.toImmutable() : i == steps ? toPos.toImmutable() : blockBehindFace(hit, side);
            added.addAll(paintStamp(world, pos, side, hit, groupId));
        }

        return added;
    }

    private static BlockPos blockBehindFace(Vec3d hit, Direction side) {
        return BlockPos.ofFloored(
                hit.x - side.getOffsetX() * 0.01,
                hit.y - side.getOffsetY() * 0.01,
                hit.z - side.getOffsetZ() * 0.01
        );
    }

    public static List<VeinmakerTrailWorldState.PaintedCell> cellsNear(ServerWorld world, BlockPos center, int radius) {
        prune(world);

        long radiusSquared = (long) radius * radius;
        return state(world).cells().stream()
                .filter(cell -> cell.pos().getSquaredDistance(center) <= radiusSquared)
                .toList();
    }

    public static int lifetimeTicks() {
        return TRAIL_LIFETIME_TICKS;
    }

    private static void prune(ServerWorld world) {
        VeinmakerTrailWorldState state = state(world);
        long now = world.getTime();
        Set<Integer> brokenGroups = new HashSet<>();
        List<BlockPos> brokenCells = new ArrayList<>();

        for (VeinmakerTrailWorldState.PaintedCell cell : state.cells()) {
            if (hasOpenSurface(world, cell)) continue;

            brokenCells.add(cell.pos());
            if (cell.groupId() != 0) {
                brokenGroups.add(cell.groupId());
            }
        }

        boolean removed = state.cells().removeIf(cell -> {
            if (brokenGroups.contains(cell.groupId())) return true;
            boolean contained = BloodSenseTracker.hasContainedTraceNear(world, cell.pos(), ACTIVE_PILLAR_RADIUS);
            int lifetime = contained ? TRAIL_LIFETIME_TICKS : UNCONTAINED_TRAIL_LIFETIME_TICKS;
            return now - cell.createdTime() >= lifetime
                    || !hasOpenSurface(world, cell)
                    || (!contained && !BloodSenseTracker.hasActiveTraceNear(world, cell.pos(), ACTIVE_PILLAR_RADIUS));
        });

        for (BlockPos pos : brokenCells) {
            if (BloodSenseTracker.releaseContainedNear(world, pos, ACTIVE_PILLAR_RADIUS)) {
                notifyBrokenContainment(world, pos);
            }
        }

        if (removed) {
            state.markDirty();
        }
    }

    private static boolean hasOpenSurface(ServerWorld world, VeinmakerTrailWorldState.PaintedCell cell) {
        Direction side = Direction.values()[Math.floorMod(cell.side(), Direction.values().length)];
        BlockPos pos = cell.pos();
        return !world.getBlockState(pos).isAir()
                && world.getBlockState(pos.offset(side)).isAir();
    }

    private static void notifyBrokenContainment(ServerWorld world, BlockPos pos) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(pos.toCenterPos()) <= 144.0) {
                player.sendMessage(Text.literal("the containment line breaks"), true);
            }
        }
    }

    private static void upsert(List<VeinmakerTrailWorldState.PaintedCell> cells, VeinmakerTrailWorldState.PaintedCell next) {
        for (int i = 0; i < cells.size(); i++) {
            VeinmakerTrailWorldState.PaintedCell old = cells.get(i);
            if (old.side() == next.side()
                    && old.pixelX() == next.pixelX()
                    && old.pixelY() == next.pixelY()
                    && old.pos().equals(next.pos())) {
                cells.set(i, next);
                return;
            }
        }
        cells.add(next);
    }

    private static Pixel pixelOnFace(BlockPos pos, Direction side, Vec3d hitPos) {
        double localX = hitPos.x - pos.getX();
        double localY = hitPos.y - pos.getY();
        double localZ = hitPos.z - pos.getZ();

        double u = switch (side) {
            case UP, DOWN -> localX;
            case NORTH, SOUTH -> localX;
            case EAST, WEST -> localZ;
        };
        double v = switch (side) {
            case UP, DOWN -> localZ;
            case NORTH, SOUTH, EAST, WEST -> 1.0 - localY;
        };

        return new Pixel(
                Math.clamp((int)Math.floor(u * 16.0), 0, 15),
                Math.clamp((int)Math.floor(v * 16.0), 0, 15)
        );
    }

    private static VeinmakerTrailWorldState state(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(VeinmakerTrailWorldState.TYPE);
    }

    private record Pixel(int x, int y) {
    }
}
