package me.suture_n_sorcery.suture_n_sorcery.client.blood_sense;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import me.suture_n_sorcery.suture_n_sorcery.items.HematicCatalyst;
import me.suture_n_sorcery.suture_n_sorcery.mixin.client.DrawContextInvoker;
import me.suture_n_sorcery.suture_n_sorcery.network.BloodSenseRequestPayload;
import me.suture_n_sorcery.suture_n_sorcery.network.BloodSenseResponsePayload;
import me.suture_n_sorcery.suture_n_sorcery.render.ModShader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public final class BloodSenseClient {

    private static final int DURATION_TICKS = 20 * 6;
    private static final int PULSE_TICKS = 38;
    private static final float MAX_RADIUS = 16f;
    private static final ItemStack CATALYST_PLACEHOLDER = new ItemStack(HematicCatalyst.HEMATIC_CATALYST);

    private static int remainingTicks = 0;
    private static final List<ClientTrace> visibleTraces = new ArrayList<>();

    private BloodSenseClient() {
    }

    public static void registerBloodSenseClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (remainingTicks > 0 && !client.isPaused()) {
                remainingTicks--;
            }
            if (remainingTicks <= 0) visibleTraces.clear();
        });

        WorldRenderEvents.BEFORE_TRANSLUCENT.register(BloodSenseClient::renderWorldSense);
        HudRenderCallback.EVENT.register((context, tickCounter) ->
                renderRefraction(context, tickCounter.getTickProgress(false))
        );

        ClientPlayNetworking.registerGlobalReceiver(BloodSenseResponsePayload.ID, (payload, context) ->
                context.client().execute(() -> activate(payload.traces()))
        );

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof InventoryScreen) {
                ScreenEvents.afterRender(screen).register(BloodSenseClient::renderInventoryMarker);
            }
        });
    }

    public static void activate() {
        if (ClientPlayNetworking.canSend(BloodSenseRequestPayload.ID)) {
            ClientPlayNetworking.send(new BloodSenseRequestPayload());
        }
    }

    public static boolean isActive() {
        return remainingTicks > 0;
    }

    public static float activeAmount() {
        return Math.min(1f, remainingTicks / (float)DURATION_TICKS);
    }

    private static void renderInventoryMarker(Screen screen, DrawContext context, int mouseX, int mouseY, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !HematicCatalyst.hasAbsorbedCatalyst(client.player)) return;

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

    private static void activate(List<BloodSenseResponsePayload.Trace> traces) {
        visibleTraces.clear();
        for (BloodSenseResponsePayload.Trace trace : traces) {
            visibleTraces.add(new ClientTrace(
                    trace.type(),
                    new BlockPos(trace.x(), trace.y(), trace.z()),
                    trace.strength(),
                    trace.ageTicks()
            ));
        }
        remainingTicks = DURATION_TICKS;
    }

    private static void renderWorldSense(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (remainingTicks <= 0 || client.world == null || client.player == null) return;

        int age = DURATION_TICKS - remainingTicks;
        float pulse01 = Math.min(1f, age / (float)PULSE_TICKS);
        float radius = MAX_RADIUS * smooth(pulse01);
        float tickProgress = client.getRenderTickCounter().getTickProgress(false);
        float strength = Math.min(1f, (remainingTicks + tickProgress) / (float)DURATION_TICKS);

        MatrixStack matrices = context.matrices();
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        Vec3d center = client.player.getLerpedPos(tickProgress).add(0.0, 1.0, 0.0);

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        MatrixStack.Entry entry = matrices.peek();
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
        drawPulseSphere(entry, lines, center, radius, strength, age);
        drawTracePillars(entry, context, client.player, radius, strength);

        matrices.pop();
    }

    private static void drawPulseSphere(MatrixStack.Entry entry, VertexConsumer lines, Vec3d center, float radius, float strength, int age) {
        if (radius <= 0.25f) return;

        int alpha = Math.clamp((int)(230 * strength), 80, 230);
        float shimmer = 0.08f + 0.05f * MathHelper.sin(age * 0.45f);
        float inner = Math.max(0.25f, radius - shimmer);
        float outer = radius + shimmer;

        drawCircle(entry, lines, center, radius, 0, 230, 24, 38, alpha);
        drawCircle(entry, lines, center, radius, 1, 230, 24, 38, alpha);
        drawCircle(entry, lines, center, radius, 2, 230, 24, 38, alpha);

        drawCircle(entry, lines, center, outer, 0, 255, 80, 86, Math.clamp(alpha / 2, 45, 120));
        drawCircle(entry, lines, center, inner, 0, 110, 20, 32, Math.clamp(alpha / 2, 45, 120));
        drawCircle(entry, lines, center.add(0.0, radius * 0.34, 0.0), radius * 0.78f, 0, 255, 42, 56, Math.clamp(alpha / 3, 35, 95));
        drawCircle(entry, lines, center.add(0.0, -radius * 0.34, 0.0), radius * 0.78f, 0, 255, 42, 56, Math.clamp(alpha / 3, 35, 95));
    }

    private static void drawCircle(MatrixStack.Entry entry, VertexConsumer lines, Vec3d center, float radius, int plane, int r, int g, int b, int a) {
        int segments = 96;
        double lastX = 0.0;
        double lastY = 0.0;
        double lastZ = 0.0;

        for (int i = 0; i <= segments; i++) {
            double angle = (Math.PI * 2.0 * i) / segments;
            double ca = Math.cos(angle) * radius;
            double sa = Math.sin(angle) * radius;

            double x = center.x + (plane == 0 ? ca : plane == 1 ? ca : 0.0);
            double y = center.y + (plane == 0 ? 0.0 : plane == 1 ? sa : ca);
            double z = center.z + (plane == 0 ? sa : plane == 1 ? 0.0 : sa);

            if (i > 0) {
                addLine(entry, lines, lastX, lastY, lastZ, x, y, z, r, g, b, a);
            }

            lastX = x;
            lastY = y;
            lastZ = z;
        }
    }

    private static void drawTracePillars(MatrixStack.Entry entry, WorldRenderContext context, ClientPlayerEntity player, float radius, float strength) {
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());

        for (ClientTrace trace : visibleTraces) {
            double dx = (trace.pos.getX() + 0.5) - player.getX();
            double dz = (trace.pos.getZ() + 0.5) - player.getZ();
            if ((dx * dx) + (dz * dz) > radius * radius) continue;

            float traceStrength = MathHelper.clamp(trace.strength / 100f, 0.25f, 1f) * strength;
            int alpha = Math.clamp((int)(165 * traceStrength), 70, 180);
            int r = trace.type == 1 ? 240 : 210;
            int g = trace.type == 1 ? 42 : 10;
            int b = trace.type == 1 ? 82 : 28;

            double x = trace.pos.getX() + 0.5;
            double y0 = trace.pos.getY() + 0.08;
            double y1 = y0 + 2.5 + traceStrength * 3.0;
            double z = trace.pos.getZ() + 0.5;
            double half = 0.18 + traceStrength * 0.22;

            drawPillarLines(entry, lines, x, y0, z, half, y1, r, g, b, alpha);
            addLine(entry, lines, x, y0, z, x, y1 + 0.6, z, 255, 54, 70, Math.clamp(alpha + 65, 120, 230));
        }
    }

    private static void drawPillarLines(MatrixStack.Entry entry, VertexConsumer lines, double x, double y0, double z, double half, double y1, int r, int g, int b, int a) {
        addLine(entry, lines, x - half, y0, z - half, x - half, y1, z - half, r, g, b, a);
        addLine(entry, lines, x + half, y0, z - half, x + half, y1, z - half, r, g, b, a);
        addLine(entry, lines, x - half, y0, z + half, x - half, y1, z + half, r, g, b, a);
        addLine(entry, lines, x + half, y0, z + half, x + half, y1, z + half, r, g, b, a);
        addLine(entry, lines, x - half, y1, z - half, x + half, y1, z - half, r, g, b, a);
        addLine(entry, lines, x + half, y1, z - half, x + half, y1, z + half, r, g, b, a);
        addLine(entry, lines, x + half, y1, z + half, x - half, y1, z + half, r, g, b, a);
        addLine(entry, lines, x - half, y1, z + half, x - half, y1, z - half, r, g, b, a);
    }

    private static void addLine(MatrixStack.Entry entry, VertexConsumer lines, double x1, double y1, double z1, double x2, double y2, double z2, int r, int g, int b, int a) {
        float nx = (float)(x2 - x1);
        float ny = (float)(y2 - y1);
        float nz = (float)(z2 - z1);
        float len = MathHelper.sqrt(nx * nx + ny * ny + nz * nz);
        if (len <= 0.0001f) return;

        nx /= len;
        ny /= len;
        nz /= len;

        lines.vertex(entry, (float)x1, (float)y1, (float)z1).color(r, g, b, a).normal(entry, nx, ny, nz);
        lines.vertex(entry, (float)x2, (float)y2, (float)z2).color(r, g, b, a).normal(entry, nx, ny, nz);
    }

    private static void renderRefraction(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (remainingTicks <= 0 || client.world == null || client.player == null) return;
        if (client.currentScreen != null) return;

        GpuTextureView srcView = outputView(client);
        if (srcView == null) return;

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        if (width <= 0 || height <= 0) return;

        int age = DURATION_TICKS - remainingTicks;
        float pulse01 = Math.min(1f, (age + tickDelta) / (float)PULSE_TICKS);
        float radius = Math.min(1f, 0.64f * smooth(pulse01));
        float strength = Math.min(1f, (remainingTicks + tickDelta) / (float)DURATION_TICKS);

        int pr = 128;
        int pg = 128;
        int pb = Math.clamp((int)(radius * 255f), 0, 255);
        int pa = Math.clamp((int)(strength * 235f), 0, 235);
        int packed = (pa << 24) | (pr << 16) | (pg << 8) | pb;

        ((DrawContextInvoker) context).sns$drawTexturedQuad(
                ModShader.BLOOD_SENSE_REFRACTION,
                srcView,
                0, 0, width, height,
                0f, 1f, 0f, 1f,
                packed
        );
    }

    private static GpuTextureView outputView(MinecraftClient client) {
        var override = RenderSystem.outputColorTextureOverride;
        return override != null ? override : client.getFramebuffer().getColorAttachmentView();
    }

    private static float smooth(float value) {
        float clamped = MathHelper.clamp(value, 0f, 1f);
        return clamped * clamped * (3f - 2f * clamped);
    }

    private record ClientTrace(int type, BlockPos pos, int strength, int ageTicks) {
    }
}
