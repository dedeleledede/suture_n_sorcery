package me.suture_n_sorcery.suture_n_sorcery.items;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import me.suture_n_sorcery.suture_n_sorcery.status_effects.Bleeding;
import me.suture_n_sorcery.suture_n_sorcery.util.BleedingHolder;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.Objects;

public class Gauze extends Item {
    public Gauze(Settings settings) { super(settings); }

    public static final Identifier GAUZE_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "gauze");

    public static final RegistryKey<Item> GAUZE_KEY =
            RegistryKey.of(RegistryKeys.ITEM, GAUZE_ID);

    public static final Item GAUZE = new Gauze(new Item.Settings()
            .registryKey(GAUZE_KEY)
            .maxCount(16)
    );

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!world.isClient()
                && user.hasStatusEffect(Bleeding.entry())
                && user instanceof BleedingHolder holder) {

            float stored = holder.suture_n_sorcery$getBleedStoredDamage();
            float newStored = Math.max(0.0f, stored - 10.0f);
            holder.suture_n_sorcery$setBleedStoredDamage(newStored);

            int amp = Objects.requireNonNull(user.getStatusEffect(Bleeding.entry())).getAmplifier();

            if (newStored <= 0.0f || amp <= 0) {
                user.removeStatusEffect(Bleeding.entry());
            } else {
                // optional: sync icon level immediately to remaining stored damage (not “-1”, real tier)
                StatusEffectInstance inst = user.getStatusEffect(Bleeding.entry());
                int tier = Bleeding.tierForDamage(newStored);
                if (inst != null && tier > 0 && inst.getAmplifier() != (tier - 1)) {
                    user.addStatusEffect(new StatusEffectInstance(
                            Bleeding.entry(),
                            inst.getDuration(),
                            tier - 1,
                            false, false, true
                    ));
                }
            }

            if (!user.getAbilities().creativeMode) {
                stack.decrement(1);
                ItemStack dirty = new ItemStack(DirtyGauze.DIRTY_GAUZE);
                if (!user.getInventory().insertStack(dirty)) user.dropItem(dirty, false);
            }

            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }
}
