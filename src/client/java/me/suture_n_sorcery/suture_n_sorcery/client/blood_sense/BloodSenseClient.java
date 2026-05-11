package me.suture_n_sorcery.suture_n_sorcery.client.blood_sense;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import me.suture_n_sorcery.suture_n_sorcery.items.HematicCatalyst;
import me.suture_n_sorcery.suture_n_sorcery.network.BloodSenseRequestPayload;
import me.suture_n_sorcery.suture_n_sorcery.network.BloodSenseResponsePayload;
import me.suture_n_sorcery.suture_n_sorcery.network.HematicBondPayload;
import me.suture_n_sorcery.suture_n_sorcery.util.HematicBondHolder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public final class BloodSenseClient {

    private static final int DURATION_TICKS = 20 * 6;
    private static final int PULSE_TICKS = 38;
    private static final int TRACE_LIFETIME_TICKS = 20 * 60 * 12;
    private static final int TRACE_EXPIRY_FADE_TICKS = 20 * 30;
    private static final float MAX_RADIUS = 16f;
    private static final float TRACE_EDGE_FADE_BLOCKS = 2.4f;
    private static final Identifier SPHERE_TEXTURE = Identifier.of(Suture_n_sorcery.MOD_ID, "textures/effect/blood_sense_sphere.png");
    private static final Identifier PILLAR_TEXTURE = Identifier.of(Suture_n_sorcery.MOD_ID, "textures/effect/blood_trace_pillar.png");
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

        WorldRenderEvents.AFTER_ENTITIES.register(BloodSenseClient::renderWorldSense);

        ClientPlayNetworking.registerGlobalReceiver(BloodSenseResponsePayload.ID, (payload, context) ->
                context.client().execute(() -> activate(payload.traces()))
        );
        ClientPlayNetworking.registerGlobalReceiver(HematicBondPayload.ID, (payload, context) ->
                context.client().execute(() -> syncHematicBond(context.client(), payload.absorbed()))
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

    private static void syncHematicBond(MinecraftClient client, boolean absorbed) {
        if (client.player instanceof HematicBondHolder holder) {
            holder.suture_n_sorcery$setAbsorbedHematicCatalyst(absorbed);
        }
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
        RenderLayer sphereLayer = RenderLayer.getEnergySwirl(SPHERE_TEXTURE, age * 0.012f, age * 0.004f);
        RenderLayer pillarLayer = RenderLayer.getEnergySwirl(PILLAR_TEXTURE, age * 0.006f, age * 0.018f);
        drawLayer(sphereLayer, vertices -> drawShaderSphere(entry, vertices, center, radius, strength, age));
        drawLayer(pillarLayer, vertices -> drawTracePillars(entry, vertices, client.player, radius, strength, age));

        matrices.pop();
    }

    private static void drawLayer(RenderLayer layer, LayerDraw draw) {
        BufferBuilder buffer = Tessellator.getInstance().begin(layer.getDrawMode(), layer.getVertexFormat());
        draw.draw(buffer);
        BuiltBuffer built = buffer.endNullable();
        if (built != null) {
            layer.draw(built);
        }
    }

    private static void drawShaderSphere(MatrixStack.Entry entry, VertexConsumer vertices, Vec3d center, float radius, float strength, int age) {
        if (radius <= 0.35f) return;

        int alpha = Math.clamp((int)(92 * strength), 24, 92);
        int latitudeSteps = 12;
        int longitudeSteps = 32;
        float wobble = 1.0f + 0.018f * MathHelper.sin(age * 0.38f);

        for (int lat = 0; lat < latitudeSteps; lat++) {
            double v0 = lat / (double) latitudeSteps;
            double v1 = (lat + 1) / (double) latitudeSteps;
            double theta0 = -Math.PI / 2.0 + Math.PI * v0;
            double theta1 = -Math.PI / 2.0 + Math.PI * v1;

            for (int lon = 0; lon < longitudeSteps; lon++) {
                double u0 = lon / (double) longitudeSteps;
                double u1 = (lon + 1) / (double) longitudeSteps;
                double phi0 = Math.PI * 2.0 * u0;
                double phi1 = Math.PI * 2.0 * u1;

                addSphereVertex(entry, vertices, center, radius * wobble, theta0, phi0, (float) u0, (float) v0, alpha, age);
                addSphereVertex(entry, vertices, center, radius * wobble, theta0, phi1, (float) u1, (float) v0, alpha, age);
                addSphereVertex(entry, vertices, center, radius * wobble, theta1, phi1, (float) u1, (float) v1, alpha, age);
                addSphereVertex(entry, vertices, center, radius * wobble, theta1, phi0, (float) u0, (float) v1, alpha, age);
            }
        }
    }

    private static void addSphereVertex(MatrixStack.Entry entry, VertexConsumer vertices, Vec3d center, float radius, double theta, double phi, float u, float v, int alpha, int age) {
        float nx = (float)(Math.cos(theta) * Math.cos(phi));
        float ny = (float)Math.sin(theta);
        float nz = (float)(Math.cos(theta) * Math.sin(phi));

        float waveA = MathHelper.sin((float)(phi * 5.0 + theta * 2.0 + age * 0.22f));
        float waveB = MathHelper.sin((float)(phi * -3.0 + theta * 7.0 + age * 0.13f));
        float warpedRadius = radius * (1.0f + waveA * 0.026f + waveB * 0.014f);

        float x = (float)(center.x + nx * warpedRadius);
        float y = (float)(center.y + ny * warpedRadius);
        float z = (float)(center.z + nz * warpedRadius);

        vertices.vertex(entry, x, y, z)
                .color(255, 38, 54, alpha)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(entry, nx, ny, nz);
    }

    private static void drawTracePillars(MatrixStack.Entry entry, VertexConsumer vertices, ClientPlayerEntity player, float radius, float strength, int scanAge) {
        for (ClientTrace trace : visibleTraces) {
            double dx = (trace.pos.getX() + 0.5) - player.getX();
            double dz = (trace.pos.getZ() + 0.5) - player.getZ();
            float distance = MathHelper.sqrt((float)(dx * dx + dz * dz));
            updateTraceVisibility(trace, distance, radius, scanAge);
            if (trace.visibility <= 0.01f) continue;

            float mass = traceVisualMass(trace.strength);
            float traceStrength = MathHelper.clamp((0.28f + mass * 0.72f) * strength * trace.visibility, 0f, 1.6f);

            int alpha = Math.clamp((int)(85 + 145 * traceStrength), 70, 240);
            int r = trace.type == 1 ? 255 : 220;
            int g = trace.type == 1 ? 48 : 16;
            int b = trace.type == 1 ? 96 : 34;

            double x = trace.pos.getX() + 0.5;
            double y0 = trace.pos.getY() + 0.06;
            double y1 = y0 + 2.2 + mass * 5.8;
            double z = trace.pos.getZ() + 0.5;

            double inner = 0.10 + mass * 0.20;
            double outer = 0.26 + mass * 0.42;

            drawPillarBillboards(entry, vertices, x, y0, z, outer, y1, r, g, b, alpha / 2);
            drawPillarBillboards(entry, vertices, x, y0 + 0.16, z, inner, y1 + 0.36, 255, 55, 75, Math.clamp(alpha + 30, 120, 255));
            drawPillarCap(entry, vertices, x, y1 + 0.18, z, outer * 0.9, 255, 75, 92, Math.clamp(alpha + 15, 100, 255));
        }
    }

    private static float smooth(float value) {
        float clamped = MathHelper.clamp(value, 0f, 1f);
        return clamped * clamped * (3f - 2f * clamped);
    }

    private static float traceVisualMass(int strength) {
        float value = Math.max(1f, strength);
        float max = 1800f;
        return MathHelper.clamp((float)(Math.log1p(value) / Math.log1p(max)), 0.18f, 1.0f);
    }

    private static void updateTraceVisibility(ClientTrace trace, float distance, float radius, int scanAge) {
        float edge = MathHelper.clamp((radius - distance + TRACE_EDGE_FADE_BLOCKS) / TRACE_EDGE_FADE_BLOCKS, 0f, 1f);
        float entryFade = smooth(edge);
        float durationFade = smooth(MathHelper.clamp(remainingTicks / 20f, 0f, 1f));

        int clientAge = trace.ageTicks + scanAge;
        float expiryFade = 1f;
        int expiryStart = TRACE_LIFETIME_TICKS - TRACE_EXPIRY_FADE_TICKS;
        if (clientAge > expiryStart) {
            expiryFade = 1f - smooth(MathHelper.clamp((clientAge - expiryStart) / (float)TRACE_EXPIRY_FADE_TICKS, 0f, 1f));
        }

        float target = entryFade * durationFade * expiryFade;
        float speed = target > trace.visibility ? 0.22f : 0.14f;
        trace.visibility += (target - trace.visibility) * speed;
        if (trace.visibility < 0.004f) trace.visibility = 0f;
    }

    private static void drawPillarBillboards(MatrixStack.Entry entry, VertexConsumer vertices, double x, double y0, double z, double half, double y1, int r, int g, int b, int a) {
        addTexturedQuad(entry, vertices, x - half, y0, z, x + half, y0, z, x + half, y1, z, x - half, y1, z, r, g, b, a, 0f);
        addTexturedQuad(entry, vertices, x, y0, z - half, x, y0, z + half, x, y1, z + half, x, y1, z - half, r, g, b, a, 1f);
    }

    private static void drawPillarCap(MatrixStack.Entry entry, VertexConsumer vertices, double x, double y, double z, double half, int r, int g, int b, int a) {
        addTexturedQuad(entry, vertices, x - half, y, z - half, x + half, y, z - half, x + half, y, z + half, x - half, y, z + half, r, g, b, a, 2f);
    }

    private static void addTexturedQuad(MatrixStack.Entry entry, VertexConsumer vertices, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, int r, int g, int b, int a, float uOffset) {
        vertices.vertex(entry, (float)x1, (float)y1, (float)z1).color(r, g, b, a).texture(uOffset, 0f).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0f, 1f, 0f);
        vertices.vertex(entry, (float)x2, (float)y2, (float)z2).color(r, g, b, a).texture(uOffset + 1f, 0f).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0f, 1f, 0f);
        vertices.vertex(entry, (float)x3, (float)y3, (float)z3).color(r, g, b, a).texture(uOffset + 1f, 1f).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0f, 1f, 0f);
        vertices.vertex(entry, (float)x4, (float)y4, (float)z4).color(r, g, b, a).texture(uOffset, 1f).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0f, 1f, 0f);
    }

    private static final class ClientTrace {
        private final int type;
        private final BlockPos pos;
        private final int strength;
        private final int ageTicks;
        private float visibility;

        private ClientTrace(int type, BlockPos pos, int strength, int ageTicks) {
            this.type = type;
            this.pos = pos;
            this.strength = strength;
            this.ageTicks = ageTicks;
            this.visibility = 0f;
        }
    }

    @FunctionalInterface
    private interface LayerDraw {
        void draw(VertexConsumer vertices);
    }
}
