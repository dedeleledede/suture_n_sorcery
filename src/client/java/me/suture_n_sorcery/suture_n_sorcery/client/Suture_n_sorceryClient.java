package me.suture_n_sorcery.suture_n_sorcery.client;

import me.suture_n_sorcery.suture_n_sorcery.render.ModRender;
import net.fabricmc.api.ClientModInitializer;

public class Suture_n_sorceryClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ModRender.registerRender();
    }
}
