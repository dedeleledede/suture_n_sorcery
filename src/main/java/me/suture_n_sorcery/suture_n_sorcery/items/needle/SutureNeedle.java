package me.suture_n_sorcery.suture_n_sorcery.items.needle;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import me.suture_n_sorcery.suture_n_sorcery.items.HematicCatalyst;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public final class SutureNeedle extends Item {
    public SutureNeedle(Settings settings) { super(settings); }

    public static final Identifier SUTURE_NEEDLE_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "suture_needle");

    public static final RegistryKey<Item> SUTURE_NEEDLE_KEY =
            RegistryKey.of(RegistryKeys.ITEM, SUTURE_NEEDLE_ID);

    public static final Item SUTURE_NEEDLE = new SutureNeedle(new Settings()
            .registryKey(SUTURE_NEEDLE_KEY)
            .maxCount(1)
    );

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        Hand other = (hand == Hand.MAIN_HAND) ? Hand.OFF_HAND : Hand.MAIN_HAND;
        return HematicCatalyst.useNeedleOnCatalyst(world, user, hand, other);
    }
}
