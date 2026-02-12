package me.suture_n_sorcery.suture_n_sorcery.registries;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import me.suture_n_sorcery.suture_n_sorcery.blocks.Condenser.CondenserScreenHandler;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public final class ModScreenHandlers {
    public static ScreenHandlerType<CondenserScreenHandler> CONDENSATOR_SCREEN;

    public static void registerScreenHandlers(){
        if (CONDENSATOR_SCREEN != null) return;

        CONDENSATOR_SCREEN = Registry.register(
                Registries.SCREEN_HANDLER,
                Identifier.of(Suture_n_sorcery.MOD_ID, "condensator"),
                new ScreenHandlerType<>(CondenserScreenHandler::new, FeatureSet.empty())
            );
    }
}