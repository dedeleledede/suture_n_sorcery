package me.suture_n_sorcery.suture_n_sorcery.client;

import me.suture_n_sorcery.suture_n_sorcery.client.screens.CondenserScreen;
import me.suture_n_sorcery.suture_n_sorcery.registries.ModScreenHandlers;
import me.suture_n_sorcery.suture_n_sorcery.render.ModRender;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class Suture_n_sorceryClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        ModRender.registerRender();
        HandledScreens.register(ModScreenHandlers.CONDENSATOR_SCREEN, CondenserScreen::new);
    }
}
