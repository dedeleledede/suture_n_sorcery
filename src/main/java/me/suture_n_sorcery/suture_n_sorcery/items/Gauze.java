package me.suture_n_sorcery.suture_n_sorcery.items;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import me.suture_n_sorcery.suture_n_sorcery.registries.ModEffects;
import me.suture_n_sorcery.suture_n_sorcery.status_effects.Bleeding;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public class Gauze extends Item {
    public Gauze(Settings settings) { super (settings); }

    public static final Identifier GAUZE_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "gauze");

    public static final RegistryKey<Item> GAUZE_KEY =
            RegistryKey.of(RegistryKeys.ITEM, GAUZE_ID);

    public static final Item GAUZE = new Gauze(new Item
            .Settings()
            .registryKey(GAUZE_KEY)
            .maxCount(16)
    );

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if(!world.isClient() && user.hasStatusEffect(Bleeding.entry())){

            int amp = user.getStatusEffect(Bleeding.entry()).getAmplifier();
            int dur = user.getStatusEffect(Bleeding.entry()).getDuration();

            if (amp <= 0) {
                user.removeStatusEffect(Bleeding.entry());
            } else {
                user.removeStatusEffect(Bleeding.entry());
                user.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        Bleeding.entry(),
                        dur,
                        amp - 1
                ));
            }

            if(!user.getAbilities().creativeMode){
                stack.decrement(1);
                user.giveItemStack(new ItemStack(DirtyGauze.DIRTY_GAUZE));
            }

            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }
}
