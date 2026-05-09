package me.suture_n_sorcery.suture_n_sorcery.client.items;

import me.suture_n_sorcery.suture_n_sorcery.client.screens.FeedingMiniGameScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

public final class SutureNeedleClientBridge {
    private SutureNeedleClientBridge() {
    }

    public static void openFeedingMiniGame(int pct, int catalystHandOrdinal) {
        MinecraftClient client = MinecraftClient.getInstance();
        Screen parent = client.currentScreen;

        client.setScreen(new FeedingMiniGameScreen(parent, pct, catalystHandOrdinal));
    }
}