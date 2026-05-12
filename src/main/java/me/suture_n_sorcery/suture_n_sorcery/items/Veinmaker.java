package me.suture_n_sorcery.suture_n_sorcery.items;

import me.suture_n_sorcery.suture_n_sorcery.blood_sense.BloodSenseTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

public final class Veinmaker extends Item {
    private static final int CONTAINMENT_RADIUS = 4;

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

        if (!player.isCreative() && !consumeFixative(player)) {
            player.sendMessage(Text.literal("the veinmaker needs sanguine fixative"), true);
            return ActionResult.FAIL;
        }

        boolean contained = BloodSenseTracker.containNearest(world, context.getBlockPos(), CONTAINMENT_RADIUS);
        if (!contained) {
            player.sendMessage(Text.literal("the line finds no blood wound"), true);
            return ActionResult.FAIL;
        }

        player.sendMessage(Text.literal("blood wound contained"), true);
        return ActionResult.SUCCESS;
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
