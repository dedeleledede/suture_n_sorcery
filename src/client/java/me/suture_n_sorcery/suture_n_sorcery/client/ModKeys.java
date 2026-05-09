package me.suture_n_sorcery.suture_n_sorcery.client;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import me.suture_n_sorcery.suture_n_sorcery.client.screens.FeedingDebugLauncherScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class ModKeys{

    private static final KeyBinding.Category SNS_CATEGORY =
            KeyBinding.Category.create(
                    Identifier.of(Suture_n_sorcery.MOD_ID, "main")
            );

    private static KeyBinding OPEN_FEEDING_DEBUG;

    static void registerKeys(){
        OPEN_FEEDING_DEBUG =
                KeyBindingHelper.registerKeyBinding(
                        new KeyBinding(
                                "key.suture_n_sorcery.open_feeding_debug",
                                GLFW.GLFW_KEY_G,
                                SNS_CATEGORY
                        )
                );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_FEEDING_DEBUG.wasPressed()) { // consumes presses
                client.setScreen(new FeedingDebugLauncherScreen(client.currentScreen));
            }
        });
    }
}