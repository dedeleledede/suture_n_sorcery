package me.suture_n_sorcery.suture_n_sorcery.client.gui_text;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;

public final class BloodGuiText {
    private BloodGuiText() {
    }

    public static void drawReadingPanel(DrawContext context, TextRenderer renderer, int x, int y, List<ReadingLine> lines, float intensity) {
        if (lines.isEmpty()) return;

        int width = 0;
        for (ReadingLine line : lines) {
            width = Math.max(width, renderer.getWidth(line.text()));
        }

        int panelWidth = width + 18;
        int panelHeight = lines.size() * 11 + 10;
        int frame = alphaColor(0x6C2A070B, intensity, 0.8f);
        int fill = alphaColor(0x5A070203, intensity, 0.72f);

        context.fill(x - 1, y - 1, x + panelWidth + 1, y + panelHeight + 1, frame);
        context.fill(x, y, x + panelWidth, y + panelHeight, fill);

        int lineY = y + 6;
        for (ReadingLine line : lines) {
            drawReading(context, renderer, line.text(), x + 8, lineY, line.intensity() * intensity);
            lineY += 11;
        }
    }

    public static void drawReading(DrawContext context, TextRenderer renderer, String text, int x, int y, float intensity) {
        int red = Math.clamp((int)(145 + 90 * intensity), 145, 235);
        int dark = Math.clamp((int)(28 + 18 * intensity), 20, 46);
        int color = 0xFF000000 | red << 16 | dark << 8 | (dark + 8);

        context.drawText(renderer, Text.literal(text), x + 1, y + 1, 0xAA120408, false);
        context.drawText(renderer, Text.literal(text), x, y, color, false);
    }

    private static int alphaColor(int color, float intensity, float alphaScale) {
        int alpha = Math.clamp((int)(((color >>> 24) & 255) * Math.clamp(intensity, 0f, 1f) * alphaScale), 0, 255);
        return alpha << 24 | (color & 0x00FFFFFF);
    }

    public record ReadingLine(String text, float intensity) {
    }
}
