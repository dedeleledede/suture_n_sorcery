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
    private static final float SPHERE_TEXTURE_TILES = 6f;
    private static final float INNER_SPHERE_SCALE = 0.36f;
    private static final float PILLAR_TEXTURE_ASPECT = 26f / 64f;
    private static final Identifier SPHERE_TEXTURE = Identifier.of(Suture_n_sorcery.MOD_ID, "textures/effect/blood_sense_sphere.png");
    private static final Identifier PILLAR_TEXTURE = Identifier.of(Suture_n_sorcery.MOD_ID, "textures/effect/blood_sense_pillar.png");
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
        return remainingTicks > 0 || fadeOutTicks > 0;
    }

    public static float activeAmount() {
        return currentStrength(MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false));
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
        RenderLayer sphereLayer = RenderLayer.getEnergySwirl(SPHERE_TEXTURE, age * 0.012f, age * 0.004f);
        RenderLayer innerSphereLayer = RenderLayer.getDebugQuads();
        RenderLayer pillarLayer = RenderLayer.getEntityTranslucent(PILLAR_TEXTURE);
        RenderLayer sphereRefractionLayer = bloodSenseSphereRefractionLayer();
        drawLayer(sphereRefractionLayer, vertices -> drawRefractionSphere(entry, vertices, center, radius, strength));
        drawLayer(innerSphereLayer, vertices -> drawInnerSphere(entry, vertices, center, radius, strength));
        drawLayer(sphereLayer, vertices -> drawShaderSphere(entry, vertices, client.world, center, radius, strength));
        drawLayer(pillarLayer, vertices -> drawTracePillars(entry, vertices, client.player, radius, strength, (int)age));

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
        ScreenBubble bubble = projectBubble(client, center, radius);
        if (bubble == null || strength <= 0.001f) return;

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

    private static void drawInnerSphere(MatrixStack.Entry entry, VertexConsumer vertices, Vec3d center, float radius, float strength) {
        float innerRadius = radius * INNER_SPHERE_SCALE;
        if (innerRadius <= 0.35f) return;

        int alpha = Math.clamp((int)(18 * strength), 0, 28);
        int latitudeSteps = 10;
        int longitudeSteps = 28;

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

                addInnerSphereVertex(entry, vertices, center, innerRadius, theta0, phi0, (float) u0 * SPHERE_TEXTURE_TILES, (float) v0 * SPHERE_TEXTURE_TILES, alpha);
                addInnerSphereVertex(entry, vertices, center, innerRadius, theta0, phi1, (float) u1 * SPHERE_TEXTURE_TILES, (float) v0 * SPHERE_TEXTURE_TILES, alpha);
                addInnerSphereVertex(entry, vertices, center, innerRadius, theta1, phi1, (float) u1 * SPHERE_TEXTURE_TILES, (float) v1 * SPHERE_TEXTURE_TILES, alpha);
                addInnerSphereVertex(entry, vertices, center, innerRadius, theta1, phi0, (float) u0 * SPHERE_TEXTURE_TILES, (float) v1 * SPHERE_TEXTURE_TILES, alpha);
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
                .color(255, 18, 34, alpha);
    }

    private static float contactAmount(BlockView world, float x, float y, float z, float normalY) {
        BlockPos below = BlockPos.ofFloored(x, y - 0.18f, z);
        BlockPos inside = BlockPos.ofFloored(x, y, z);
        boolean touchesBlock = world.getBlockState(below).isSolidBlock(world, below) || world.getBlockState(inside).isSolidBlock(world, inside);
        if (!touchesBlock) return 0f;

        float lowerHemisphere = MathHelper.clamp((-normalY + 0.35f) / 1.35f, 0f, 1f);
        return smooth(lowerHemisphere);
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

            double height = y1 - y0;
            double textureHalf = MathHelper.clamp((float)(height * PILLAR_TEXTURE_ASPECT * 0.5), 0.32f, 1.25f);
            double inner = textureHalf * (0.34 + mass * 0.12);
            double outer = textureHalf * (0.78 + mass * 0.18);

            drawPillarBillboards(entry, vertices, x, y0, z, outer, y1, r, g, b, alpha / 2);
            drawPillarBillboards(entry, vertices, x, y0 + 0.16, z, inner, y1 + 0.36, 255, 55, 75, Math.clamp(alpha + 30, 120, 255));
        }
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

    private static void drawPillarBillboards(MatrixStack.Entry entry, VertexConsumer vertices, double x, double y0, double z, double half, double y1, int r, int g, int b, int a) {
        addTexturedQuad(entry, vertices, x - half, y0, z, x + half, y0, z, x + half, y1, z, x - half, y1, z, r, g, b, a);
        addTexturedQuad(entry, vertices, x, y0, z - half, x, y0, z + half, x, y1, z + half, x, y1, z - half, r, g, b, a);
    }

    private static void addTexturedQuad(MatrixStack.Entry entry, VertexConsumer vertices, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, int r, int g, int b, int a) {
        vertices.vertex(entry, (float)x1, (float)y1, (float)z1).color(r, g, b, a).texture(0f, 1f).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0f, 1f, 0f);
        vertices.vertex(entry, (float)x2, (float)y2, (float)z2).color(r, g, b, a).texture(1f, 1f).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0f, 1f, 0f);
        vertices.vertex(entry, (float)x3, (float)y3, (float)z3).color(r, g, b, a).texture(1f, 0f).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0f, 1f, 0f);
        vertices.vertex(entry, (float)x4, (float)y4, (float)z4).color(r, g, b, a).texture(0f, 0f).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0f, 1f, 0f);
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

    private record ScreenBubble(float x, float y, float radius) {
    }
}
