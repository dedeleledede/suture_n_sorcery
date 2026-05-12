package me.suture_n_sorcery.suture_n_sorcery.client.gui_text;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class BloodGuiText {
    private BloodGuiText() {
    }

    public static void drawReading(DrawContext context, TextRenderer renderer, String text, int x, int y, float intensity) {
        int red = Math.clamp((int)(145 + 90 * intensity), 145, 235);
        int dark = Math.clamp((int)(28 + 18 * intensity), 20, 46);
        int color = 0xFF000000 | red << 16 | dark << 8 | (dark + 8);

        context.drawText(renderer, Text.literal(text), x + 1, y + 1, 0xAA120408, false);
        context.drawText(renderer, Text.literal(text), x, y, color, false);
    }
}
