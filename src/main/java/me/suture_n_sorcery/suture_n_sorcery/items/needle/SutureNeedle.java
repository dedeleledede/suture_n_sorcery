package me.suture_n_sorcery.suture_n_sorcery.items.needle;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import me.suture_n_sorcery.suture_n_sorcery.items.HematicCatalyst;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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

        ItemStack needle = user.getStackInHand(hand);
        ItemStack catalyst = user.getStackInHand(other);

        if (!catalyst.isOf(HematicCatalyst.HEMATIC_CATALYST)) return ActionResult.PASS;
        if (HematicCatalyst.isReady(catalyst)) return ActionResult.PASS;
        if (!HematicCatalyst.hasEnoughHealthToFeed(user)) return ActionResult.FAIL;

        if (!world.isClient()) return ActionResult.SUCCESS;

        int pct = HematicCatalyst.feedingGrowthPercent(catalyst);
        int catalystHandOrdinal = (other == Hand.MAIN_HAND) ? 0 : 1;

        try {
            Class<?> bridge = Class.forName("me.suture_n_sorcery.suture_n_sorcery.client.items.SutureNeedleClientBridge");

            bridge.getMethod("openFeedingMiniGame", int.class, int.class)
                    .invoke(null, pct, catalystHandOrdinal);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to open feeding minigame", e);
        }

        return ActionResult.SUCCESS;
    }
}
