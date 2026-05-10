package me.suture_n_sorcery.suture_n_sorcery.client.blood_sense;

import me.suture_n_sorcery.suture_n_sorcery.items.HematicCatalyst;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;

public final class BloodSenseClient {

    private static final int DURATION_TICKS = 20 * 6;
    private static final ItemStack CATALYST_PLACEHOLDER = new ItemStack(HematicCatalyst.HEMATIC_CATALYST);

    private static int remainingTicks = 0;

    private BloodSenseClient() {
    }

    public static void registerBloodSenseClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (remainingTicks > 0 && !client.isPaused()) {
                remainingTicks--;
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof InventoryScreen) {
                ScreenEvents.afterRender(screen).register(BloodSenseClient::renderInventoryMarker);
            }
        });
    }

    public static void activate() {
        remainingTicks = DURATION_TICKS;
    }

    public static boolean isActive() {
        return remainingTicks > 0;
    }

    public static float activeAmount() {
        return Math.min(1f, remainingTicks / (float)DURATION_TICKS);
    }

    private static void renderInventoryMarker(Screen screen, DrawContext context, int mouseX, int mouseY, float tickDelta) {
        int left = (screen.width - 176) / 2;
        int top = (screen.height - 166) / 2;
        int x = left + 124;
        int y = top + 58;

        int frame = isActive() ? 0x99B80E1A : 0x66301518;
        int fill = isActive() ? 0x40220A0D : 0x24110A0C;

        context.fill(x - 2, y - 2, x + 18, y + 18, frame);
        context.fill(x - 1, y - 1, x + 17, y + 17, fill);
        context.drawItem(CATALYST_PLACEHOLDER, x, y);
    }
}
