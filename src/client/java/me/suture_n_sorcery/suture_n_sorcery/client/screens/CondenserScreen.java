package me.suture_n_sorcery.suture_n_sorcery.client.screens;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import me.suture_n_sorcery.suture_n_sorcery.blocks.Condenser.CondenserScreenHandler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class CondenserScreen extends HandledScreen<CondenserScreenHandler> {

    private static final Identifier GUI = Identifier.of(Suture_n_sorcery.MOD_ID, "textures/gui/condenser.png");

    private static final int TANK_X = 11;
    private static final int TANK_Y = 18;
    private static final int TANK_W = 24;
    private static final int TANK_H = 52;

    private static final int ARROW_X = 79;
    private static final int ARROW_Y = 37;
    private static final int ARROW_W = 18;
    private static final int ARROW_H = 15;

    private static final int BLOOD_FRAME_W = 16;
    private static final int BLOOD_FRAME_H = 16;
    private static final int BLOOD_FRAME_COUNT = 19;

    private static final Identifier BLOOD_SPRITESHEET =
            Identifier.of(Suture_n_sorcery.MOD_ID, "textures/block/concentrated_blood_still.png");

    private static final Identifier PROGRESS =
            Identifier.of(Suture_n_sorcery.MOD_ID, "textures/gui/condensate_progress.png");

    public CondenserScreen(CondenserScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        this.titleX = 8;
        this.titleY = 6;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        final int x = this.x;
        final int y = this.y;

        // Background GUI
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, GUI,
                x, y,
                0f, 0f,
                backgroundWidth, backgroundHeight,
                256, 256
        );

        drawProgressArrow(ctx, x, y);
        drawTank(ctx, x, y);
    }


    private void drawProgressArrow(DrawContext ctx, int x, int y) {
        int filled = handler.getScaledProgress(ARROW_H); // 0..14
        if (filled <= 0) return;

        int px = x + ARROW_X;
        int py = y + ARROW_Y;

        // top > bottom progress
        ctx.enableScissor(px, py, px + ARROW_W, py + filled);

        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, PROGRESS,
                px, py,
                0f, 0f,
                ARROW_W, ARROW_H,
                ARROW_W, ARROW_H
        );
        ctx.disableScissor();
    }


    private void drawTank(DrawContext ctx, int x, int y) {
        int filled = handler.getScaledTank(TANK_H); // 0..52
        if (filled <= 0) return;

        int tankX = x + TANK_X;
        int tankY = y + TANK_Y;

        int clipTop = tankY + (TANK_H - filled);
        ctx.enableScissor(tankX, clipTop, tankX + TANK_W, tankY + TANK_H);

        int tick = (int) (System.currentTimeMillis() / 100L);
        int frame = Math.floorMod(tick, BLOOD_FRAME_COUNT);
        float vFrame = frame * (float) BLOOD_FRAME_H;

        for (int yOff = 0; yOff < TANK_H; yOff += BLOOD_FRAME_H) {
            for (int xOff = 0; xOff < TANK_W; xOff += BLOOD_FRAME_W) {
                int drawW = Math.min(BLOOD_FRAME_W, TANK_W - xOff);
                int drawH = Math.min(BLOOD_FRAME_H, TANK_H - yOff);

                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, BLOOD_SPRITESHEET,
                        tankX + xOff, tankY + yOff,
                        0f, vFrame,
                        drawW, drawH,
                        BLOOD_FRAME_W, BLOOD_FRAME_H * BLOOD_FRAME_COUNT
                );
            }
        }
        ctx.disableScissor();
    }

    @Override
    protected void drawMouseoverTooltip(DrawContext ctx, int mouseX, int mouseY) {
        super.drawMouseoverTooltip(ctx, mouseX, mouseY);

        if (this.focusedSlot == null && this.isPointWithinBounds(TANK_X, TANK_Y, TANK_W, TANK_H, mouseX, mouseY)) {
            ctx.drawTooltip(this.textRenderer,
                    Text.literal(handler.getTankMl() + " / 5000 ml"),
                    mouseX, mouseY
            );
        }
    }
}
