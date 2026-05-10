package me.suture_n_sorcery.suture_n_sorcery.items.needle;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public final class NeedleStacks {
    private NeedleStacks() {}

    public static ItemStack convertKeepingDamage(ItemStack from, ItemConvertible toItem) {
        // keep stack data when the needle swaps item forms
        ItemStack out = from.copyComponentsToNewStack(toItem, from.getCount());

        out.setDamage(from.getDamage());
        return out;
    }

    public static void setInHand(PlayerEntity player, Hand hand, ItemStack stack) {
        player.setStackInHand(hand, stack);
    }

    public static EquipmentSlot slotForHand(Hand hand) {
        return (hand == Hand.MAIN_HAND) ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }
}
