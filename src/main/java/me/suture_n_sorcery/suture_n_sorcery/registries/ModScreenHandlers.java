package me.suture_n_sorcery.suture_n_sorcery.registries;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import me.suture_n_sorcery.suture_n_sorcery.blocks.Condenser.CondenserScreenHandler;
import me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom.RitualLoomScreenHandler;
import me.suture_n_sorcery.suture_n_sorcery.network.ModNetworking;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public final class ModScreenHandlers {
    public static ScreenHandlerType<CondenserScreenHandler> CONDENSER_SCREEN;
    public static ScreenHandlerType<RitualLoomScreenHandler> RITUAL_LOOM_SCREEN;

    public static void registerScreenHandlers(){
        CONDENSER_SCREEN = Registry.register(
                Registries.SCREEN_HANDLER,
                Identifier.of(Suture_n_sorcery.MOD_ID, "condenser"),
                new ScreenHandlerType<>(CondenserScreenHandler::new, FeatureSet.empty())
        );

        RITUAL_LOOM_SCREEN = Registry.register(
                Registries.SCREEN_HANDLER,
                Identifier.of(Suture_n_sorcery.MOD_ID, "ritual_loom"),
                new ScreenHandlerType<>(RitualLoomScreenHandler::new, FeatureSet.empty())
        );

        // Networking used by the ritual loom UI (press-and-hold "PRESSURIZE")
        ModNetworking.init();
    }
}