package me.suture_n_sorcery.suture_n_sorcery.client.screens;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class FeedingDebugLauncherScreen extends Screen {
    private final Screen parent;

    public FeedingDebugLauncherScreen(Screen parent) {
        super(Text.literal("Feeding Debug"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int bw = 200;
        int bh = 20;

        this.addDrawableChild(
                ButtonWidget
                        .builder(Text.literal("Open Feeding Minigame"), btn -> {
                            assert this.client != null;
                            this.client.setScreen(new FeedingMiniGameScreen(this, 0, 0));
                        })
                        .dimensions(this.width / 2 - bw / 2, this.height / 2 - 10, bw, bh)
                        .build()
        );

        this.addDrawableChild(
                ButtonWidget
                        .builder(Text.literal("Close"), btn -> close())
                        .dimensions(this.width / 2 - bw / 2, this.height / 2 + 14, bw, bh)
                        .build()
        );
    }

    @Override
    public void close() {
        assert this.client != null;
        this.client.setScreen(parent);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xAA000000); // dark background
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}