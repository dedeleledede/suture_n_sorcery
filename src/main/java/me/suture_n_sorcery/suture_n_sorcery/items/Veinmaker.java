package me.suture_n_sorcery.suture_n_sorcery.items;

import me.suture_n_sorcery.suture_n_sorcery.blood_sense.BloodSenseTracker;
import me.suture_n_sorcery.suture_n_sorcery.blood_sense.BloodSenseTrace;
import me.suture_n_sorcery.suture_n_sorcery.blood_sense.BloodSenseTraceType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public final class Veinmaker extends Item {
    private static final int CONTAINMENT_RADIUS = 4;
    private static final int OPERATION_RADIUS = 4;

    public Veinmaker(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (!(context.getWorld() instanceof ServerWorld world)) return ActionResult.SUCCESS;

        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;

        if (!HematicCatalyst.hasAbsorbedCatalyst(player)) {
            player.sendMessage(Text.literal("the veinmaker has nothing to read"), true);
            return ActionResult.FAIL;
        }

        if (!BloodSenseTracker.isBloodSenseActive(world, player.getUuid())) {
            player.sendMessage(Text.literal("blood sense must be active"), true);
            return ActionResult.FAIL;
        }

        ItemStack offering = player.getStackInHand(Hand.OFF_HAND);
        if (isValidOffering(offering, context.getStack())) {
            if (!consumeFixativeForAction(player)) return ActionResult.FAIL;

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

            if (!player.isCreative()) {
                offering.decrement(1);
            }
            player.sendMessage(Text.literal("the blood wound takes the offering"), true);
            return ActionResult.SUCCESS;
        }

        if (!consumeFixativeForAction(player)) return ActionResult.FAIL;

        BloodSenseTrace drained = BloodSenseTracker.operateNearestContained(
                world,
                context.getBlockPos(),
                OPERATION_RADIUS,
                BloodSenseTracker.STATE_DRAINED
        );
        if (drained != null) {
            ItemStack output = outputFor(drained);
            if (!player.giveItemStack(output)) {
                player.dropItem(output, false);
            }
            player.sendMessage(Text.literal("the veinmaker draws blood memory"), true);
            return ActionResult.SUCCESS;
        }

        boolean contained = BloodSenseTracker.containNearest(world, context.getBlockPos(), CONTAINMENT_RADIUS);
        if (!contained) {
            player.sendMessage(Text.literal("the line finds no blood wound"), true);
            return ActionResult.FAIL;
        }

        player.sendMessage(Text.literal("blood wound contained"), true);
        return ActionResult.SUCCESS;
    }

    private static boolean consumeFixativeForAction(PlayerEntity player) {
        if (player.isCreative()) return true;

        if (consumeFixative(player)) return true;

        player.sendMessage(Text.literal("the veinmaker needs sanguine fixative"), true);
        return false;
    }

    private static boolean isValidOffering(ItemStack offering, ItemStack veinmakerStack) {
        return !offering.isEmpty()
                && offering != veinmakerStack
                && !offering.isOf(BloodSenseTools.VEINMAKER)
                && !offering.isOf(BloodSenseTools.SANGUINE_FIXATIVE);
    }

    private static ItemStack outputFor(BloodSenseTrace trace) {
        int count = trace.type() == BloodSenseTraceType.RITUAL ? 2 : 1;
        return new ItemStack(BloodSenseTools.BOTTLED_BLOOD, count);
    }

    private static boolean consumeFixative(PlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isOf(BloodSenseTools.SANGUINE_FIXATIVE)) continue;

            stack.decrement(1);
            player.getInventory().markDirty();
            return true;
        }
        return false;
    }
}
