package me.suture_n_sorcery.suture_n_sorcery.client.blood_sense;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import me.suture_n_sorcery.suture_n_sorcery.items.HematicCatalyst;
import me.suture_n_sorcery.suture_n_sorcery.network.BloodSenseRequestPayload;
import me.suture_n_sorcery.suture_n_sorcery.network.BloodSenseResponsePayload;
import me.suture_n_sorcery.suture_n_sorcery.network.HematicBondPayload;
import me.suture_n_sorcery.suture_n_sorcery.registries.ModParticles;
import me.suture_n_sorcery.suture_n_sorcery.render.ModShader;
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
import net.minecraft.client.render.RenderPhase;
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
import net.minecraft.world.BlockView;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class BloodSenseClient {

    private static final int DURATION_TICKS = 20 * 6;
    private static final int PULSE_TICKS = 38;
    private static final int FADE_OUT_TICKS = 36;
    private static final int TRACE_LIFETIME_TICKS = 20 * 60 * 12;
    private static final int TRACE_EXPIRY_FADE_TICKS = 20 * 30;
    private static final float MAX_RADIUS = 16f;
    private static final float TRACE_EDGE_FADE_BLOCKS = 2.4f;
    private static final float DETAILED_MARKER_DISTANCE = 11f;
    private static final float SPHERE_TEXTURE_TILES = 6f;
    private static final float INNER_SPHERE_SCALE = 0.36f;
    private static final float PILLAR_BODY_TEXTURE_ASPECT = 24f / 64f;
    private static final float PILLAR_CORE_TEXTURE_ASPECT = 24f / 64f;
    private static final Identifier SPHERE_TEXTURE = Identifier.of(Suture_n_sorcery.MOD_ID, "textures/effect/blood_sense_sphere.png");
    private static final Identifier SPHERE_TEXTURE_INNER = Identifier.of(Suture_n_sorcery.MOD_ID, "textures/effect/blood_sense_sphere_inner.png");
    private static final Identifier PILLAR_BODY_TEXTURE = Identifier.of(Suture_n_sorcery.MOD_ID, "textures/effect/blood_sense_pillar_body.png");
    private static final Identifier PILLAR_CORE_TEXTURE = Identifier.of(Suture_n_sorcery.MOD_ID, "textures/effect/blood_sense_pillar_core.png");
    private static final Identifier PILLAR_PULSE_TEXTURE = Identifier.of(Suture_n_sorcery.MOD_ID, "textures/effect/blood_sense_pillar_core.png");
    private static final Identifier GROUND_WOUND_TEXTURE = Identifier.of(Suture_n_sorcery.MOD_ID, "textures/effect/blood_sense_ground_wound.png");
    private static final Identifier RITUAL_THREAD_TEXTURE = Identifier.of(Suture_n_sorcery.MOD_ID, "textures/effect/blood_sense_thread.png");
    private static final ItemStack CATALYST_PLACEHOLDER = new ItemStack(HematicCatalyst.HEMATIC_CATALYST);
    private static final RenderPhase.TextureBase FRAMEBUFFER_TEXTURE = new RenderPhase.TextureBase(
            // bind the current world color buffer so the sphere can bend the scene behind it.
            () -> RenderSystem.setShaderTexture(0, outputView(MinecraftClient.getInstance())),
            () -> RenderSystem.setShaderTexture(0, null)
    ) {
    };

    private static int remainingTicks = 0;
    private static int fadeOutTicks = 0;
    private static float pulseJitter = 0.5f;
    private static final List<ClientTrace> visibleTraces = new ArrayList<>();

    private BloodSenseClient() {
    }

    public static void registerBloodSenseClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (remainingTicks > 0 && !client.isPaused()) {
                remainingTicks--;
                if (remainingTicks == 0) {
                    fadeOutTicks = FADE_OUT_TICKS;
                }
            } else if (fadeOutTicks > 0 && !client.isPaused()) {
                fadeOutTicks--;
            }
            if (!client.isPaused()) {
                spawnPillarParticles(client);
            }
            if (remainingTicks <= 0 && fadeOutTicks <= 0) visibleTraces.clear();
        });

        WorldRenderEvents.END_MAIN.register(BloodSenseClient::renderWorldSense);

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
        return remainingTicks > 0 || fadeOutTicks > 0;
    }

    public static float activeAmount() {
        return currentStrength(MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false));
    }

    private static void renderInventoryMarker(Screen screen, DrawContext context, int mouseX, int mouseY, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!HematicCatalyst.hasAbsorbedCatalyst(client.player)) return;

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
        fadeOutTicks = 0;
        pulseJitter = (System.nanoTime() & 1023L) / 1023f;
    }

    private static void syncHematicBond(MinecraftClient client, boolean absorbed) {
        if (client.player instanceof HematicBondHolder holder) {
            holder.suture_n_sorcery$setAbsorbedHematicCatalyst(absorbed);
        }
    }

    private static void renderWorldSense(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isActive() || client.world == null || client.player == null) return;

        float tickProgress = client.getRenderTickCounter().getTickProgress(false);
        float age = animatedAge(tickProgress);
        float open = openingAmount(age);
        float fade = fadeAmount(tickProgress);
        float strength = open * fade;
        float radius = MAX_RADIUS * open * fade;

        MatrixStack matrices = context.matrices();
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        Vec3d center = client.player.getLerpedPos(tickProgress).add(0.0, 1.0, 0.0);
        drawScreenRefraction(client, center, radius, strength);

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        MatrixStack.Entry entry = matrices.peek();

        RenderLayer outerSphereLayer = RenderLayer.getEnergySwirl(
                SPHERE_TEXTURE,
                age * 0.012f,
                age * 0.004f
        );

        RenderLayer innerSphereLayer = RenderLayer.getEntityTranslucent(SPHERE_TEXTURE_INNER);
        RenderLayer pillarBodyLayer = RenderLayer.getEntityTranslucent(PILLAR_BODY_TEXTURE);
        RenderLayer pillarCoreLayer = RenderLayer.getEntityTranslucent(PILLAR_CORE_TEXTURE);
        RenderLayer pillarPulseLayer = RenderLayer.getEntityTranslucent(PILLAR_PULSE_TEXTURE);
        RenderLayer groundWoundLayer = RenderLayer.getEntityTranslucent(GROUND_WOUND_TEXTURE);
        RenderLayer ritualThreadLayer = RenderLayer.getEntityTranslucent(RITUAL_THREAD_TEXTURE);
        RenderLayer sphereRefractionLayer = bloodSenseSphereRefractionLayer();
        updateTraceMarkers(client.player, radius, (int)age);

        drawLayer(sphereRefractionLayer, vertices ->
                drawRefractionSphere(entry, vertices, center, radius, strength)
        );

        drawLayer(innerSphereLayer, vertices ->
                drawInnerSphere(entry, vertices, center, radius, strength, age)
        );

        drawLayer(outerSphereLayer, vertices ->
                drawShaderSphere(entry, vertices, client.world, center, radius, strength)
        );

        drawLayer(groundWoundLayer, vertices -> drawGroundWounds(entry, vertices, client.world, strength, (int)age));
        drawLayer(pillarBodyLayer, vertices -> drawMarkerBodies(entry, vertices, camera, strength, (int)age));
        drawLayer(pillarCoreLayer, vertices -> drawMarkerCores(entry, vertices, camera, strength, (int)age));
        drawLayer(pillarPulseLayer, vertices -> drawMarkerPulses(entry, vertices, camera, strength, (int)age));
        drawLayer(ritualThreadLayer, vertices -> drawRitualThreads(entry, vertices, camera, strength, (int)age));

        matrices.pop();
    }

    private static RenderLayer bloodSenseRefractionLayer() {
        RenderLayer.MultiPhaseParameters parameters = RenderLayer.MultiPhaseParameters.builder()
                .texture(FRAMEBUFFER_TEXTURE)
                .lightmap(RenderPhase.ENABLE_LIGHTMAP)
                .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
                .target(RenderPhase.MAIN_TARGET)
                .build(false);
        return RenderLayer.of("suture_n_sorcery_blood_sense_refraction", 1536, false, true, ModShader.BLOOD_SENSE_REFRACTION, parameters);
    }

    private static RenderLayer bloodSenseSphereRefractionLayer() {
        RenderLayer.MultiPhaseParameters parameters = RenderLayer.MultiPhaseParameters.builder()
                .texture(FRAMEBUFFER_TEXTURE)
                .lightmap(RenderPhase.ENABLE_LIGHTMAP)
                .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
                .target(RenderPhase.MAIN_TARGET)
                .build(false);
        return RenderLayer.of("suture_n_sorcery_blood_sense_sphere_refraction", 1536, false, true, ModShader.BLOOD_SENSE_SPHERE_REFRACTION, parameters);
    }

    private static void drawScreenRefraction(MinecraftClient client, Vec3d center, float radius, float strength) {
        if (strength <= 0.001f) return;

        ScreenBubble bubble = projectBubble(client, center, radius);
        if (bubble == null) {
            bubble = new ScreenBubble(0.5f, 0.5f, 1.2f);
        }

        RenderLayer layer = bloodSenseRefractionLayer();
        int cx = Math.clamp((int)(bubble.x * 255f), 0, 255);
        int cy = Math.clamp((int)(bubble.y * 255f), 0, 255);
        int cr = Math.clamp((int)(bubble.radius * 255f), 0, 255);
        int ca = Math.clamp((int)(strength * 255f), 0, 255);

        drawLayer(layer, vertices -> {
            vertices.vertex(-1f, -1f, 0f).texture(0f, 0f).color(cx, cy, cr, ca);
            vertices.vertex(1f, -1f, 0f).texture(1f, 0f).color(cx, cy, cr, ca);
            vertices.vertex(1f, 1f, 0f).texture(1f, 1f).color(cx, cy, cr, ca);
            vertices.vertex(-1f, 1f, 0f).texture(0f, 1f).color(cx, cy, cr, ca);
        });
    }

    private static void drawLayer(RenderLayer layer, LayerDraw draw) {
        BufferBuilder buffer = Tessellator.getInstance().begin(layer.getDrawMode(), layer.getVertexFormat());
        draw.draw(buffer);
        BuiltBuffer built = buffer.endNullable();
        if (built != null) {
            layer.draw(built);
        }
    }

    private static GpuTextureView outputView(MinecraftClient client) {
        GpuTextureView override = RenderSystem.outputColorTextureOverride;
        return override != null ? override : client.getFramebuffer().getColorAttachmentView();
    }

    private static float currentStrength(float tickProgress) {
        float open = openingAmount(animatedAge(tickProgress));
        return open * fadeAmount(tickProgress);
    }

    private static float openingAmount(float age) {
        float firstPeak = 0.17f + pulseJitter * 0.035f;
        float recoil = 0.11f + pulseJitter * 0.025f;

        if (age < 5f) {
            return MathHelper.lerp(smoother(age / 5f), 0f, firstPeak);
        }
        if (age < 11f) {
            return MathHelper.lerp(smoother((age - 5f) / 6f), firstPeak, recoil);
        }

        return MathHelper.lerp(smoother(MathHelper.clamp((age - 11f) / (PULSE_TICKS - 11f), 0f, 1f)), recoil, 1f);
    }

    private static float animatedAge(float tickProgress) {
        if (remainingTicks > 0) {
            return DURATION_TICKS - remainingTicks + tickProgress;
        }
        return DURATION_TICKS + FADE_OUT_TICKS - fadeOutTicks + tickProgress;
    }

    private static float fadeAmount(float tickProgress) {
        if (remainingTicks > 0) return 1f;
        return smooth(MathHelper.clamp((fadeOutTicks - tickProgress) / (float)FADE_OUT_TICKS, 0f, 1f));
    }

    private static ScreenBubble projectBubble(MinecraftClient client, Vec3d center, float radius) {
        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        Vec3d relative = center.subtract(cameraPos);
        Vector3f view = new Vector3f((float)relative.x, (float)relative.y, (float)relative.z);
        Quaternionf inverseCamera = new Quaternionf(client.gameRenderer.getCamera().getRotation()).conjugate();
        view.rotate(inverseCamera);

        float depth = -view.z();
        if (depth <= 0.05f) return null;

        float aspect = client.getWindow().getFramebufferWidth() / (float)Math.max(1, client.getWindow().getFramebufferHeight());
        float fov = client.options.getFov().getValue();
        float focal = (float)(1.0 / Math.tan(Math.toRadians(fov) * 0.5));
        float ndcX = (view.x() * focal / aspect) / depth;
        float ndcY = (view.y() * focal) / depth;
        float screenRadius = MathHelper.clamp((radius * focal / depth) * 0.5f, 0f, 1.2f);

        return new ScreenBubble(
                MathHelper.clamp(ndcX * 0.5f + 0.5f, 0f, 1f),
                MathHelper.clamp(ndcY * 0.5f + 0.5f, 0f, 1f),
                screenRadius
        );
    }

    private static void drawShaderSphere(MatrixStack.Entry entry, VertexConsumer vertices, BlockView world, Vec3d center, float radius, float strength) {
        if (radius <= 0.35f) return;

        int alpha = Math.clamp((int)(92 * strength), 0, 112);
        int latitudeSteps = 12;
        int longitudeSteps = 32;

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

                addSphereVertex(entry, vertices, world, center, radius, theta0, phi0, (float) u0 * SPHERE_TEXTURE_TILES, (float) v0 * SPHERE_TEXTURE_TILES, alpha);
                addSphereVertex(entry, vertices, world, center, radius, theta0, phi1, (float) u1 * SPHERE_TEXTURE_TILES, (float) v0 * SPHERE_TEXTURE_TILES, alpha);
                addSphereVertex(entry, vertices, world, center, radius, theta1, phi1, (float) u1 * SPHERE_TEXTURE_TILES, (float) v1 * SPHERE_TEXTURE_TILES, alpha);
                addSphereVertex(entry, vertices, world, center, radius, theta1, phi0, (float) u0 * SPHERE_TEXTURE_TILES, (float) v1 * SPHERE_TEXTURE_TILES, alpha);
            }
        }
    }

    private static void drawInnerSphere(MatrixStack.Entry entry, VertexConsumer vertices, Vec3d center, float radius, float strength, float age) {
        float innerRadius = radius * INNER_SPHERE_SCALE;
        if (innerRadius <= 0.35f) return;

        int alpha = Math.clamp((int)(42 * strength), 0, 58);
        int latitudeSteps = 10;
        int longitudeSteps = 28;

        float uShift = age * 0.018f;
        float vShift = age * -0.007f;

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

                addInnerSphereVertex(entry, vertices, center, innerRadius, theta0, phi0, (float)u0 * SPHERE_TEXTURE_TILES + uShift, (float)v0 * SPHERE_TEXTURE_TILES + vShift, alpha);
                addInnerSphereVertex(entry, vertices, center, innerRadius, theta0, phi1, (float)u1 * SPHERE_TEXTURE_TILES + uShift, (float)v0 * SPHERE_TEXTURE_TILES + vShift, alpha);
                addInnerSphereVertex(entry, vertices, center, innerRadius, theta1, phi1, (float)u1 * SPHERE_TEXTURE_TILES + uShift, (float)v1 * SPHERE_TEXTURE_TILES + vShift, alpha);
                addInnerSphereVertex(entry, vertices, center, innerRadius, theta1, phi0, (float)u0 * SPHERE_TEXTURE_TILES + uShift, (float)v1 * SPHERE_TEXTURE_TILES + vShift, alpha);
            }
        }
    }

    private static void drawRefractionSphere(MatrixStack.Entry entry, VertexConsumer vertices, Vec3d center, float radius, float strength) {
        if (radius <= 0.35f) return;

        int alpha = Math.clamp((int)(255 * strength), 0, 255);
        int radiusByte = Math.clamp((int)((radius / MAX_RADIUS) * 255f), 0, 255);
        int latitudeSteps = 16;
        int longitudeSteps = 40;

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

                addRefractionSphereVertex(entry, vertices, center, radius, theta0, phi0, radiusByte, alpha);
                addRefractionSphereVertex(entry, vertices, center, radius, theta0, phi1, radiusByte, alpha);
                addRefractionSphereVertex(entry, vertices, center, radius, theta1, phi1, radiusByte, alpha);
                addRefractionSphereVertex(entry, vertices, center, radius, theta1, phi0, radiusByte, alpha);
            }
        }
    }

    private static void addRefractionSphereVertex(MatrixStack.Entry entry, VertexConsumer vertices, Vec3d center, float radius, double theta, double phi, int radiusByte, int alpha) {
        float nx = (float)(Math.cos(theta) * Math.cos(phi));
        float ny = (float)Math.sin(theta);
        float nz = (float)(Math.cos(theta) * Math.sin(phi));
        float x = (float)(center.x + nx * radius);
        float y = (float)(center.y + ny * radius);
        float z = (float)(center.z + nz * radius);

        vertices.vertex(entry, x, y, z)
                .color(radiusByte, 255, 255, alpha)
                .texture(0f, 0f)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(entry, nx, ny, nz);
    }

    private static void addSphereVertex(MatrixStack.Entry entry, VertexConsumer vertices, BlockView world, Vec3d center, float radius, double theta, double phi, float u, float v, int alpha) {
        float nx = (float)(Math.cos(theta) * Math.cos(phi));
        float ny = (float)Math.sin(theta);
        float nz = (float)(Math.cos(theta) * Math.sin(phi));

        float x = (float)(center.x + nx * radius);
        float y = (float)(center.y + ny * radius);
        float z = (float)(center.z + nz * radius);
        float contact = contactAmount(world, x, y, z, ny);
        int contactAlpha = Math.clamp((int)(alpha * (1.0f + contact * 1.25f)), 0, 255);
        int red = Math.clamp((int)(255 - contact * 15), 220, 255);
        int green = Math.clamp((int)(38 + contact * 28), 38, 78);
        int blue = Math.clamp((int)(54 + contact * 42), 54, 112);

        vertices.vertex(entry, x, y, z)
                .color(red, green, blue, contactAlpha)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(entry, nx, ny, nz);
    }

    private static void addInnerSphereVertex(MatrixStack.Entry entry, VertexConsumer vertices, Vec3d center, float radius, double theta, double phi, float u, float v, int alpha) {
        float nx = (float)(Math.cos(theta) * Math.cos(phi));
        float ny = (float)Math.sin(theta);
        float nz = (float)(Math.cos(theta) * Math.sin(phi));

        float x = (float)(center.x + nx * radius);
        float y = (float)(center.y + ny * radius);
        float z = (float)(center.z + nz * radius);

        vertices.vertex(entry, x, y, z)
                .color(255, 34, 54, alpha)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(entry, nx, ny, nz);
    }

    private static float contactAmount(BlockView world, float x, float y, float z, float normalY) {
        BlockPos below = BlockPos.ofFloored(x, y - 0.18f, z);
        BlockPos inside = BlockPos.ofFloored(x, y, z);
        boolean touchesBlock = world.getBlockState(below).isSolidBlock(world, below) || world.getBlockState(inside).isSolidBlock(world, inside);
        if (!touchesBlock) return 0f;

        float lowerHemisphere = MathHelper.clamp((-normalY + 0.35f) / 1.35f, 0f, 1f);
        return smooth(lowerHemisphere);
    }

    private static void updateTraceMarkers(ClientPlayerEntity player, float radius, int scanAge) {
        for (ClientTrace trace : visibleTraces) {
            double dx = (trace.pos.getX() + 0.5) - player.getX();
            double dz = (trace.pos.getZ() + 0.5) - player.getZ();
            float distance = MathHelper.sqrt((float)(dx * dx + dz * dz));
            trace.distance = distance;
            updateTraceVisibility(trace, distance, radius, scanAge);
        }
    }

    private static void drawMarkerBodies(MatrixStack.Entry entry, VertexConsumer vertices, Vec3d camera, float strength, int scanAge) {
        for (ClientTrace trace : visibleTraces) {
            if (trace.visibility <= 0.01f) continue;

            MarkerStyle style = markerStyle(trace, strength, scanAge);
            double x = trace.pos.getX() + 0.5;
            double y0 = trace.pos.getY() + 0.06;
            double z = trace.pos.getZ() + 0.5;
            drawPillarBillboardUv(entry, vertices, camera, x, y0, z, style.outerHalf, y0 + style.height, 0.024, bodyVOffset(scanAge, style), style.r, style.g, style.b, style.outerAlpha);
        }
    }

    private static void drawMarkerCores(MatrixStack.Entry entry, VertexConsumer vertices, Vec3d camera, float strength, int scanAge) {
        for (ClientTrace trace : visibleTraces) {
            if (trace.visibility <= 0.01f) continue;

            MarkerStyle style = markerStyle(trace, strength, scanAge);
            double x = trace.pos.getX() + 0.5;
            double y0 = trace.pos.getY() + 0.14;
            double z = trace.pos.getZ() + 0.5;
            drawPillarBillboardUv(entry, vertices, camera, x, y0, z, style.coreHalf, y0 + style.height, 0.048, coreVOffset(scanAge), style.r, Math.min(90, style.g + 28), Math.min(120, style.b + 32), style.coreAlpha);
        }
    }

    private static void drawMarkerPulses(MatrixStack.Entry entry, VertexConsumer vertices, Vec3d camera, float strength, int scanAge) {
        for (ClientTrace trace : visibleTraces) {
            if (trace.visibility <= 0.01f) continue;

            MarkerStyle style = markerStyle(trace, strength, scanAge);
            if (!style.distant) {
                double x = trace.pos.getX() + 0.5;
                double y0 = trace.pos.getY() + 0.06;
                double z = trace.pos.getZ() + 0.5;
                drawPulse(entry, vertices, camera, x, y0, z, style, scanAge);
            }
        }
    }

    private static void drawGroundWounds(MatrixStack.Entry entry, VertexConsumer vertices, BlockView world, float strength, int scanAge) {
        for (ClientTrace trace : visibleTraces) {
            if (trace.visibility <= 0.01f) continue;

            MarkerStyle style = markerStyle(trace, strength, scanAge);
            if (!style.distant && hasGroundWoundSurface(world, trace.pos)) {
                double x = trace.pos.getX() + 0.5;
                double y0 = trace.pos.getY() + 0.018;
                double z = trace.pos.getZ() + 0.5;
                drawGroundWound(entry, vertices, x, y0, z, style);
            }
        }
    }

    private static void drawRitualThreads(MatrixStack.Entry entry, VertexConsumer vertices, Vec3d camera, float strength, int scanAge) {
        for (ClientTrace trace : visibleTraces) {
            if (trace.visibility <= 0.01f) continue;

            MarkerStyle style = markerStyle(trace, strength, scanAge);
            if (!style.distant && trace.type == 1) {
                double x = trace.pos.getX() + 0.5;
                double y0 = trace.pos.getY() + 0.06;
                double z = trace.pos.getZ() + 0.5;
                drawRitualSpiral(entry, vertices, camera, x, y0, z, style, scanAge);
            }
        }
    }

    private static boolean hasGroundWoundSurface(BlockView world, BlockPos pos) {
        BlockPos below = pos.down();
        return world.getBlockState(below).isSolidBlock(world, below) || world.getBlockState(pos).isSolidBlock(world, pos);
    }

    private static MarkerStyle markerStyle(ClientTrace trace, float strength, int scanAge) {
        return markerStyle(trace, trace.distance, strength, scanAge);
    }

    private static MarkerStyle markerStyle(ClientTrace trace, float distance, float strength, int scanAge) {
        float mass = traceVisualMass(trace.strength);
        float age = MathHelper.clamp((trace.ageTicks + scanAge) / (float)TRACE_LIFETIME_TICKS, 0f, 1f);
        float freshness = 1f - age;
        boolean ritual = trace.type == 1;
        boolean distant = distance > DETAILED_MARKER_DISTANCE;
        float traceStrength = MathHelper.clamp((0.25f + mass * 0.75f) * strength * trace.visibility, 0f, 1.7f);

        int r = ritual ? 255 : Math.round(MathHelper.lerp(freshness, 128f, 235f));
        int g = ritual ? 42 : Math.round(MathHelper.lerp(freshness, 6f, 26f));
        int b = ritual ? 92 : Math.round(MathHelper.lerp(freshness, 18f, 44f));
        double height = (ritual ? 3.6 : 1.8) + mass * (ritual ? 5.4 : 4.8);
        height *= MathHelper.lerp(age, 1.0f, 0.62f);
        double bodyHalf = MathHelper.clamp((float)(height * PILLAR_BODY_TEXTURE_ASPECT * 0.26), distant ? 0.055f : 0.12f, distant ? 0.18f : 0.78f);
        double coreHalf = MathHelper.clamp((float)(height * PILLAR_CORE_TEXTURE_ASPECT * 0.16), distant ? 0.035f : 0.055f, distant ? 0.10f : 0.36f);

        int baseAlpha = Math.clamp((int)((ritual ? 125 : 105) * traceStrength * MathHelper.lerp(age, 1f, 0.58f)), 18, distant ? 105 : 210);
        int coreAlpha = Math.clamp((int)((ritual ? 190 : 155) * traceStrength * freshness), distant ? 18 : 35, distant ? 115 : 235);
        float pulseSpeed = ritual ? 0.19f : MathHelper.lerp(freshness, 0.045f, 0.135f);

        return new MarkerStyle(
                r, g, b,
                baseAlpha,
                coreAlpha,
                height,
                coreHalf,
                bodyHalf,
                pulseSpeed,
                mass,
                freshness,
                distant
        );
    }

    private static void drawPulse(MatrixStack.Entry entry, VertexConsumer vertices, Vec3d camera, double x, double y0, double z, MarkerStyle style, int scanAge) {
        double travel = ((scanAge * style.pulseSpeed) % 1.0);
        double bandHeight = Math.max(0.34, style.height * 0.16);
        double bandBottom = y0 + travel * Math.max(0.1, style.height - bandHeight);
        double bandTop = bandBottom + bandHeight;
        int alpha = Math.clamp((int)(style.coreAlpha * (0.62 + style.freshness * 0.38)), 25, 245);
        double half = Math.max(0.045, style.coreHalf * 0.48);

        drawPillarBillboard(entry, vertices, camera, x, bandBottom, z, half, bandTop, 0.054, 255, 72, 92, alpha);
    }

    private static void drawGroundWound(MatrixStack.Entry entry, VertexConsumer vertices, double x, double y, double z, MarkerStyle style) {
        double half = MathHelper.clamp((float)(style.outerHalf * (2.0 + style.mass * 0.9)), 0.38f, 1.55f);
        int alpha = Math.clamp((int)(style.outerAlpha * 1.35), 95, 245);

        addTexturedQuad(entry, vertices,
                x - half, y, z - half,
                x + half, y, z - half,
                x + half, y, z + half,
                x - half, y, z + half,
                255, 42, 58, alpha);
        double glowHalf = half * 1.32;
        addTexturedQuad(entry, vertices,
                x - glowHalf, y + 0.003, z - glowHalf,
                x + glowHalf, y + 0.003, z - glowHalf,
                x + glowHalf, y + 0.003, z + glowHalf,
                x - glowHalf, y + 0.003, z + glowHalf,
                255, 18, 34, Math.clamp((int)(alpha * 0.42), 35, 120));
    }

    private static void drawRitualSpiral(MatrixStack.Entry entry, VertexConsumer vertices, Vec3d camera, double x, double y0, double z, MarkerStyle style, int scanAge) {
        int segments = 28;
        double heightStep = style.height / segments;
        double spin = scanAge * 0.042;
        double offset = 0.086;

        for (int strand = 0; strand < 3; strand++) {
            double phase = strand * (Math.PI * 2.0 / 3.0);
            double strandRadius = style.outerHalf * (0.72 + strand * 0.16);
            int alpha = Math.clamp((int)(style.coreAlpha * (0.62 - strand * 0.08)), 36, 190);
            double half = Math.max(0.04, style.coreHalf * (0.24 - strand * 0.018));

            for (int i = 0; i < segments; i++) {
                double yA = y0 + i * heightStep + 0.08;
                double yB = yA + heightStep * 0.62;
                double a0 = spin + phase + i * 0.58;
                double a1 = spin + phase + (i + 0.48) * 0.58;

                drawThreadSegment(entry, vertices, camera,
                        x + Math.cos(a0) * strandRadius,
                        yA,
                        z + Math.sin(a0) * strandRadius,
                        x + Math.cos(a1) * strandRadius,
                        yB,
                        z + Math.sin(a1) * strandRadius,
                        half,
                        offset + strand * 0.012,
                        255, 82, 105, alpha);
            }
        }
    }

    private static float bodyVOffset(int scanAge, MarkerStyle style) {
        return (scanAge * (0.018f + style.freshness * 0.018f)) % 1f;
    }

    private static float coreVOffset(int scanAge) {
        return -((scanAge * 0.006f) % 1f);
    }

    private static float smooth(float value) {
        float clamped = MathHelper.clamp(value, 0f, 1f);
        return clamped * clamped * (3f - 2f * clamped);
    }

    private static float smoother(float value) {
        float clamped = MathHelper.clamp(value, 0f, 1f);
        return clamped * clamped * clamped * (clamped * (clamped * 6f - 15f) + 10f);
    }

    private static float traceVisualMass(int strength) {
        float value = Math.max(1f, strength);
        float max = 1800f;
        return MathHelper.clamp((float)(Math.log1p(value) / Math.log1p(max)), 0.18f, 1.0f);
    }

    private static void updateTraceVisibility(ClientTrace trace, float distance, float radius, int scanAge) {
        float edge = MathHelper.clamp((radius - distance + TRACE_EDGE_FADE_BLOCKS) / TRACE_EDGE_FADE_BLOCKS, 0f, 1f);
        float entryFade = smooth(edge);
        float durationFade = currentStrength(0f);

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

    private static void spawnPillarParticles(MinecraftClient client) {
        if (client.world == null || client.player == null || visibleTraces.isEmpty()) return;

        for (ClientTrace trace : visibleTraces) {
            if (trace.visibility <= 0.12f || client.world.random.nextFloat() > trace.visibility * 0.35f) continue;

            float mass = traceVisualMass(trace.strength);
            double height = 2.2 + mass * 5.8;
            double radius = 0.18 + mass * 0.42;
            double angle = client.world.random.nextDouble() * Math.PI * 2.0;
            double ring = radius * (0.45 + client.world.random.nextDouble() * 0.55);
            double x = trace.pos.getX() + 0.5 + Math.cos(angle) * ring;
            double y = trace.pos.getY() + 0.16 + client.world.random.nextDouble() * height;
            double z = trace.pos.getZ() + 0.5 + Math.sin(angle) * ring;
            double swirl = 0.012 + mass * 0.018;

            client.world.addParticleClient(
                    ModParticles.BLOOD_PARTICLE,
                    x, y, z,
                    -Math.sin(angle) * swirl,
                    0.012 + client.world.random.nextDouble() * 0.018,
                    Math.cos(angle) * swirl
            );
        }
    }

    private static void drawPillarBillboard(MatrixStack.Entry entry, VertexConsumer vertices, Vec3d camera, double x, double y0, double z, double half, double y1, double forwardOffset, int r, int g, int b, int a) {
        drawPillarBillboardUv(entry, vertices, camera, x, y0, z, half, y1, forwardOffset, 0f, r, g, b, a);
    }

    private static void drawPillarBillboardUv(MatrixStack.Entry entry, VertexConsumer vertices, Vec3d camera, double x, double y0, double z, double half, double y1, double forwardOffset, float vOffset, int r, int g, int b, int a) {
        double dx = camera.x - x;
        double dz = camera.z - z;
        double length = Math.sqrt(dx * dx + dz * dz);
        double rightX = length > 0.0001 ? dz / length : 1.0;
        double rightZ = length > 0.0001 ? -dx / length : 0.0;
        double forwardX = length > 0.0001 ? dx / length : 0.0;
        double forwardZ = length > 0.0001 ? dz / length : 1.0;
        double px = x + forwardX * forwardOffset;
        double pz = z + forwardZ * forwardOffset;

        addTexturedQuadUv(entry, vertices,
                px - rightX * half, y0, pz - rightZ * half,
                px + rightX * half, y0, pz + rightZ * half,
                px + rightX * half, y1, pz + rightZ * half,
                px - rightX * half, y1, pz - rightZ * half,
                0f, 1f + vOffset,
                1f, 1f + vOffset,
                1f, vOffset,
                0f, vOffset,
                r, g, b, a);
    }

    private static void drawTaperedPillarBillboard(MatrixStack.Entry entry, VertexConsumer vertices, Vec3d camera, double x, double y0, double z, double bottomHalf, double topHalf, double y1, double forwardOffset, float vOffset, int r, int g, int b, int a) {
        double dx = camera.x - x;
        double dz = camera.z - z;
        double length = Math.sqrt(dx * dx + dz * dz);
        double rightX = length > 0.0001 ? dz / length : 1.0;
        double rightZ = length > 0.0001 ? -dx / length : 0.0;
        double forwardX = length > 0.0001 ? dx / length : 0.0;
        double forwardZ = length > 0.0001 ? dz / length : 1.0;
        double px = x + forwardX * forwardOffset;
        double pz = z + forwardZ * forwardOffset;

        addTexturedQuadUv(entry, vertices,
                px - rightX * bottomHalf, y0, pz - rightZ * bottomHalf,
                px + rightX * bottomHalf, y0, pz + rightZ * bottomHalf,
                px + rightX * topHalf, y1, pz + rightZ * topHalf,
                px - rightX * topHalf, y1, pz - rightZ * topHalf,
                0f, 1f + vOffset,
                1f, 1f + vOffset,
                1f, vOffset,
                0f, vOffset,
                r, g, b, a);
    }

    private static void drawThreadSegment(MatrixStack.Entry entry, VertexConsumer vertices, Vec3d camera, double x0, double y0, double z0, double x1, double y1, double z1, double half, double forwardOffset, int r, int g, int b, int a) {
        double mx = (x0 + x1) * 0.5;
        double mz = (z0 + z1) * 0.5;
        double dx = camera.x - mx;
        double dz = camera.z - mz;
        double length = Math.sqrt(dx * dx + dz * dz);
        double rightX = length > 0.0001 ? dz / length : 1.0;
        double rightZ = length > 0.0001 ? -dx / length : 0.0;
        double forwardX = length > 0.0001 ? dx / length : 0.0;
        double forwardZ = length > 0.0001 ? dz / length : 1.0;
        double ox = forwardX * forwardOffset;
        double oz = forwardZ * forwardOffset;

        addTexturedQuad(entry, vertices,
                x0 + ox - rightX * half, y0, z0 + oz - rightZ * half,
                x0 + ox + rightX * half, y0, z0 + oz + rightZ * half,
                x1 + ox + rightX * half, y1, z1 + oz + rightZ * half,
                x1 + ox - rightX * half, y1, z1 + oz - rightZ * half,
                r, g, b, a);
    }

    private static void addTexturedQuad(MatrixStack.Entry entry, VertexConsumer vertices, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, int r, int g, int b, int a) {
        addTexturedQuadUv(entry, vertices, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, 0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f, r, g, b, a);
    }

    private static void addTexturedQuadUv(MatrixStack.Entry entry, VertexConsumer vertices, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, float u1, float v1, float u2, float v2, float u3, float v3, float u4, float v4, int r, int g, int b, int a) {
        vertices.vertex(entry, (float)x1, (float)y1, (float)z1).color(r, g, b, a).texture(u1, v1).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0f, 1f, 0f);
        vertices.vertex(entry, (float)x2, (float)y2, (float)z2).color(r, g, b, a).texture(u2, v2).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0f, 1f, 0f);
        vertices.vertex(entry, (float)x3, (float)y3, (float)z3).color(r, g, b, a).texture(u3, v3).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0f, 1f, 0f);
        vertices.vertex(entry, (float)x4, (float)y4, (float)z4).color(r, g, b, a).texture(u4, v4).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0f, 1f, 0f);
    }

    private static final class ClientTrace {
        private final int type;
        private final BlockPos pos;
        private final int strength;
        private final int ageTicks;
        private float visibility;
        private float distance;

        private ClientTrace(int type, BlockPos pos, int strength, int ageTicks) {
            this.type = type;
            this.pos = pos;
            this.strength = strength;
            this.ageTicks = ageTicks;
            this.visibility = 0f;
            this.distance = 0f;
        }
    }

    @FunctionalInterface
    private interface LayerDraw {
        void draw(VertexConsumer vertices);
    }

    private record ScreenBubble(float x, float y, float radius) {
    }

    private record MarkerStyle(
            int r,
            int g,
            int b,
            int outerAlpha,
            int coreAlpha,
            double height,
            double coreHalf,
            double outerHalf,
            float pulseSpeed,
            float mass,
            float freshness,
            boolean distant
    ) {
    }
}
