package me.suture_n_sorcery.suture_n_sorcery.items;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public class HematicCatalyst extends Item {
    public HematicCatalyst(Settings settings) { super(settings); }

    public static final Identifier HEMATIC_CATALYST_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "HEMATIC_CATALYST");

    public static final RegistryKey<Item> HEMATIC_CATALYST_KEY =
            RegistryKey.of(RegistryKeys.ITEM, HEMATIC_CATALYST_ID);

    public static final Item HEMATIC_CATALYST = new HematicCatalyst(new Settings()
            .registryKey(HEMATIC_CATALYST_KEY)
            .maxCount(64)
    );

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        return ActionResult.PASS;
    }
}
