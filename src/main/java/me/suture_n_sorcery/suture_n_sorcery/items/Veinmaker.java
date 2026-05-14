package me.suture_n_sorcery.suture_n_sorcery.items;

import me.suture_n_sorcery.suture_n_sorcery.blood_sense.BloodSenseTracker;
import me.suture_n_sorcery.suture_n_sorcery.blood_sense.BloodSenseTrace;
import me.suture_n_sorcery.suture_n_sorcery.blood_sense.BloodSenseTraceType;
import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import me.suture_n_sorcery.suture_n_sorcery.network.ModNetworking;
import me.suture_n_sorcery.suture_n_sorcery.veinmaker.VeinmakerTrailTracker;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Veinmaker extends Item {
    private static final int CONTAINMENT_RADIUS = 4;
    private static final int OPERATION_RADIUS = 4;
    private static final int MAX_USE_TICKS = 72000;
    private static final int MAX_LINE_POINTS = 80;
    private static final int MAX_FIXATIVE_CHARGES = 8;
    private static final int CHARGES_PER_FIXATIVE = 4;
    private static final String FIXATIVE_CHARGES_KEY = "suture_n_sorcery_fixative_charges";
    private static final Identifier EMPTY_MODEL = Identifier.of(Suture_n_sorcery.MOD_ID, "veinmaker_empty");
    private static final Identifier LOADED_MODEL = Identifier.of(Suture_n_sorcery.MOD_ID, "veinmaker");
    private static final Map<UUID, DrawnLine> ACTIVE_LINES = new HashMap<>();

    public Veinmaker(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (!(context.getWorld() instanceof ServerWorld world)) return ActionResult.SUCCESS;

        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;
        if (tryRefill(player, context.getHand(), context.getStack())) return ActionResult.SUCCESS;
        if (player.isSneaking()) {
            return beginLine(world, player, context.getHand());
        }

        if (!HematicCatalyst.hasAbsorbedCatalyst(player)) {
            player.sendMessage(Text.literal("the veinmaker has nothing to read"), true);
            return ActionResult.FAIL;
        }

        ItemStack offering = player.getStackInHand(Hand.OFF_HAND);
        if (isValidOffering(offering, context.getStack())) {
            if (!hasFixativeForAction(player, context.getStack())) return ActionResult.FAIL;

            BloodSenseTrace mutated = BloodSenseTracker.operateNearestContained(
                    world,
                    context.getBlockPos(),
                    OPERATION_RADIUS,
                    BloodSenseTracker.STATE_MUTATED
            );
            if (mutated == null) {
                player.sendMessage(Text.literal("feed a contained blood wound"), true);
                return ActionResult.FAIL;
            }

            consumeFixative(context.getStack());
            if (!player.isCreative()) {
                offering.decrement(1);
            }
            syncBloodSense(player);
            player.sendMessage(Text.literal("the blood wound takes the offering"), true);
            return ActionResult.SUCCESS;
        }

        if (!hasFixativeForAction(player, context.getStack())) return ActionResult.FAIL;

        BloodSenseTrace drained = BloodSenseTracker.operateNearestContained(
                world,
                context.getBlockPos(),
                OPERATION_RADIUS,
                BloodSenseTracker.STATE_DRAINED
        );
        if (drained != null) {
            consumeFixative(context.getStack());
            ItemStack output = outputFor(drained);
            if (!player.giveItemStack(output)) {
                player.dropItem(output, false);
            }
            syncBloodSense(player);
            player.sendMessage(Text.literal("the veinmaker draws blood memory"), true);
            return ActionResult.SUCCESS;
        }

        boolean contained = BloodSenseTracker.containNearest(world, context.getBlockPos(), CONTAINMENT_RADIUS);
        if (!contained) {
            player.sendMessage(Text.literal("the line finds no blood wound"), true);
            return ActionResult.FAIL;
        }

        consumeFixative(context.getStack());
        syncBloodSense(player);
        player.sendMessage(Text.literal("blood wound contained"), true);
        return ActionResult.SUCCESS;
    }

    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        if (!(world instanceof ServerWorld serverWorld)) return ActionResult.SUCCESS;
        ItemStack stack = player.getStackInHand(hand);
        if (tryRefill(player, hand, stack)) return ActionResult.SUCCESS;
        return beginLine(serverWorld, player, hand);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!(world instanceof ServerWorld serverWorld) || !(user instanceof PlayerEntity player)) return;

        DrawnLine line = ACTIVE_LINES.get(player.getUuid());
        if (line == null) return;

        BlockHitResult hit = tracedSurface(player);
        if (hit == null) return;

        BlockPos pos = hit.getBlockPos().toImmutable();
        Vec3d hitPos = hit.getPos();
        List<me.suture_n_sorcery.suture_n_sorcery.veinmaker.VeinmakerTrailWorldState.PaintedCell> painted = line.paint(serverWorld, pos, hitPos, hit.getSide());
        syncTrailPixels(player, painted);
    }

    @Override
    public boolean onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!(world instanceof ServerWorld serverWorld) || !(user instanceof PlayerEntity player)) return false;

        DrawnLine line = ACTIVE_LINES.remove(player.getUuid());
        if (line == null) return false;

        if (!hasFixativeForAction(player, stack)) return true;

        BloodSenseTracker.ContainmentResult result = BloodSenseTracker.containLoop(serverWorld, player, line.points());
        if (result == BloodSenseTracker.ContainmentResult.CONTAINED) {
            consumeFixative(stack);
            syncBloodSense(player);
            if (player instanceof ServerPlayerEntity serverPlayer) {
                ModNetworking.sendVeinmakerTrails(serverPlayer);
            }
            player.sendMessage(Text.literal("blood wound contained"), true);
            return true;
        }

        player.sendMessage(Text.literal(containmentFailure(result)), true);
        return true;
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return MAX_USE_TICKS;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, TooltipDisplayComponent displayComponent, Consumer<Text> textConsumer, TooltipType type) {
        int charges = fixativeCharges(stack);
        String contents = charges <= 0 ? "empty" : "sanguine fixative " + charges + "/" + MAX_FIXATIVE_CHARGES;
        textConsumer.accept(Text.literal("contents: " + contents));
    }

    private static ActionResult beginLine(ServerWorld world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!HematicCatalyst.hasAbsorbedCatalyst(player)) {
            player.sendMessage(Text.literal("the veinmaker has nothing to read"), true);
            return ActionResult.FAIL;
        }

        if (!hasFixativeForAction(player, stack)) return ActionResult.FAIL;

        DrawnLine line = new DrawnLine(nextGroupId(world, player));
        BlockHitResult hit = tracedSurface(player);
        if (hit != null) {
            line.seed(hit.getBlockPos().toImmutable(), hit.getPos(), hit.getSide());
        }
        ACTIVE_LINES.put(player.getUuid(), line);
        player.setCurrentHand(hand);
        return ActionResult.CONSUME;
    }

    private static boolean tryRefill(PlayerEntity player, Hand hand, ItemStack veinmaker) {
        Hand otherHand = hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
        ItemStack fixative = player.getStackInHand(otherHand);
        if (!fixative.isOf(BloodSenseTools.SANGUINE_FIXATIVE)) return false;

        int charges = fixativeCharges(veinmaker);
        if (charges >= MAX_FIXATIVE_CHARGES) {
            player.sendMessage(Text.literal("the veinmaker is already full"), true);
            return true;
        }

        setFixativeCharges(veinmaker, Math.min(MAX_FIXATIVE_CHARGES, charges + CHARGES_PER_FIXATIVE));
        if (!player.isCreative()) {
            fixative.decrement(1);
        }
        player.sendMessage(Text.literal("the veinmaker drinks the fixative"), true);
        return true;
    }

    private static BlockHitResult tracedSurface(PlayerEntity player) {
        HitResult hit = player.raycast(5.5, 0f, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) return null;
        return blockHit;
    }

    private static boolean hasFixativeForAction(PlayerEntity player, ItemStack veinmaker) {
        if (player.isCreative()) return true;

        if (fixativeCharges(veinmaker) > 0) return true;

        player.sendMessage(Text.literal("the veinmaker needs sanguine fixative"), true);
        return false;
    }

    private static boolean isValidOffering(ItemStack offering, ItemStack veinmakerStack) {
        return !offering.isEmpty()
                && offering != veinmakerStack
                && !offering.isOf(BloodSenseTools.VEINMAKER)
                && !offering.isOf(BloodSenseTools.SANGUINE_FIXATIVE);
    }

    private static int nextGroupId(ServerWorld world, PlayerEntity player) {
        int id = (int)(world.getTime() ^ player.getUuid().getMostSignificantBits() ^ player.getUuid().getLeastSignificantBits());
        return id == 0 ? 1 : id;
    }

    private static ItemStack outputFor(BloodSenseTrace trace) {
        int count = switch (trace.type()) {
            case RITUAL -> 2;
            case DEEP -> 3;
            default -> 1;
        };
        return new ItemStack(BloodSenseTools.BOTTLED_BLOOD, count);
    }

    private static String containmentFailure(BloodSenseTracker.ContainmentResult result) {
        return switch (result) {
            case OPEN -> "the line never closes";
            case TOO_SMALL -> "the line is too tight to bind";
            case TOO_LARGE -> "the line thins and collapses";
            case MULTIPLE -> "conflicting blood wounds pull against the line";
            case TOO_FAR -> "the blood wound is too far from your hand";
            case NONE -> "the line finds no blood wound";
            case CONTAINED -> "blood wound contained";
        };
    }

    private static boolean consumeFixative(ItemStack veinmaker) {
        int charges = fixativeCharges(veinmaker);
        if (charges <= 0) return false;

        setFixativeCharges(veinmaker, charges - 1);
        return true;
    }

    private static int fixativeCharges(ItemStack stack) {
        NbtComponent data = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        return Math.clamp(data.copyNbt().getInt(FIXATIVE_CHARGES_KEY, 0), 0, MAX_FIXATIVE_CHARGES);
    }

    private static void setFixativeCharges(ItemStack stack, int charges) {
        int clamped = Math.clamp(charges, 0, MAX_FIXATIVE_CHARGES);
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.putInt(FIXATIVE_CHARGES_KEY, clamped));
        stack.set(DataComponentTypes.ITEM_MODEL, clamped > 0 ? LOADED_MODEL : EMPTY_MODEL);
    }

    private static void syncTrailPixels(PlayerEntity player, List<me.suture_n_sorcery.suture_n_sorcery.veinmaker.VeinmakerTrailWorldState.PaintedCell> painted) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        ModNetworking.sendVeinmakerTrailCells(serverPlayer, painted, false);
    }

    private static void syncBloodSense(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            ModNetworking.sendBloodSenseUpdate(serverPlayer, false);
        }
    }

    private static final class DrawnLine {
        private final List<BlockPos> points = new ArrayList<>();
        private final int groupId;
        private BlockPos lastPos;
        private Vec3d lastHit;
        private net.minecraft.util.math.Direction lastSide;

        private DrawnLine(int groupId) {
            this.groupId = groupId;
        }

        private void seed(BlockPos pos, Vec3d hitPos, net.minecraft.util.math.Direction side) {
            samplePoint(pos);
            lastPos = pos;
            lastHit = hitPos;
            lastSide = side;
        }

        private List<me.suture_n_sorcery.suture_n_sorcery.veinmaker.VeinmakerTrailWorldState.PaintedCell> paint(ServerWorld world, BlockPos pos, Vec3d hitPos, net.minecraft.util.math.Direction side) {
            samplePoint(pos);
            List<me.suture_n_sorcery.suture_n_sorcery.veinmaker.VeinmakerTrailWorldState.PaintedCell> painted;
            if (lastPos != null && lastHit != null && side == lastSide) {
                painted = VeinmakerTrailTracker.paintLine(world, lastPos, lastHit, pos, hitPos, side, groupId);
            } else {
                painted = VeinmakerTrailTracker.paint(world, pos, side, hitPos, groupId);
            }
            lastPos = pos;
            lastHit = hitPos;
            lastSide = side;
            return painted;
        }

        private void samplePoint(BlockPos pos) {
            if (points.size() < MAX_LINE_POINTS && (points.isEmpty() || points.getLast().getSquaredDistance(pos) >= 0.85)) {
                points.add(pos);
            }
        }

        private List<BlockPos> points() {
            return points;
        }
    }
}
