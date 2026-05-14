package me.suture_n_sorcery.suture_n_sorcery.client.blood_sense;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import me.suture_n_sorcery.suture_n_sorcery.client.gui_text.BloodGuiText;
import me.suture_n_sorcery.suture_n_sorcery.items.HematicCatalyst;
import me.suture_n_sorcery.suture_n_sorcery.network.BloodSenseRequestPayload;
import me.suture_n_sorcery.suture_n_sorcery.network.BloodSenseResponsePayload;
import me.suture_n_sorcery.suture_n_sorcery.network.HematicBondPayload;
import me.suture_n_sorcery.suture_n_sorcery.network.VeinmakerTrailPayload;
import me.suture_n_sorcery.suture_n_sorcery.registries.ModParticles;
import me.suture_n_sorcery.suture_n_sorcery.render.ModShader;
import me.suture_n_sorcery.suture_n_sorcery.util.HematicBondHolder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class BloodSenseClient {

    private static final int DURATION_TICKS = 20 * 12;
    private static final int PULSE_TICKS = 38;
    private static final int FADE_OUT_TICKS = 36;
    private static final int TRACE_LIFETIME_TICKS = 20 * 60 * 12;
    private static final int TRACE_EXPIRY_FADE_TICKS = 20 * 30;
    private static final int UNCONTAINED_TRAIL_LIFETIME_TICKS = 20 * 15;
    private static final float MAX_RADIUS = 16f;
    private static final float TRACE_EDGE_FADE_BLOCKS = 2.4f;
    private static final float DETAILED_MARKER_DISTANCE = 11f;
    private static final float READING_TARGET_RADIUS = 0.92f;
    private static final float READING_TARGET_RANGE = 18f;
    private static final double TYPE_READING_RANGE_SQUARED = 25.0;
    private static final float SPHERE_TEXTURE_TILES = 6f;
    private static final float INNER_SPHERE_SCALE = 0.36f;
    private static final float PILLAR_BODY_TEXTURE_ASPECT = 24f / 64f;
    private static final float PILLAR_CORE_TEXTURE_ASPECT = 24f / 64f;
    private static final Identifier SPHERE_TEXTURE = Identifier.of(Suture_n_sorcery.MOD_ID, "textures/effect/blood_sense_sphere.png");
    private static final Identifier SPHERE_TEXTURE_INNER = Identifier.of(Suture_n_sorcery.MOD_ID, "textures/effect/blood_sense_sphere_inner.png");
    private static final Identifier PILLAR_BODY_TEXTURE = Identifier.of(Suture_n_sorcery.MOD_ID, "textures/effect/blood_sense_pillar_body.png");
    private static final Identifier PILLAR_CORE_TEXTURE = Identifier.of(Suture_n_sorcery.MOD_ID, "textures/effect/blood_sense_pillar_core.png");
    private static final Identifier PILLAR_PULSE_TEXTURE = Identifier.of(Suture_n_sorcery.MOD_ID, "textures/effect/blood_sense_pillar_pulse.png");
    private static final Identifier GROUND_WOUND_TEXTURE = Identifier.of(Suture_n_sorcery.MOD_ID, "textures/effect/blood_sense_ground_wound.png");
    private static final Identifier SANGUINE_FIXATIVE_TRAIL_TEXTURE = Identifier.of(Suture_n_sorcery.MOD_ID, "textures/effect/sanguine_fixative_trail.png");
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
    private static int markerAnimationTicks = 0;
    private static float pulseJitter = 0.5f;
    private static final List<ClientTrace> visibleTraces = new ArrayList<>();
    private static final List<PaintedCell> paintedCells = new ArrayList<>();

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
                markerAnimationTicks++;
                spawnPillarParticles(client);
                paintedCells.removeIf(cell -> --cell.life <= 0 || !hasOpenPaintedSurface(cell.pos, Direction.values()[Math.floorMod(cell.side, Direction.values().length)]));
            }
            if (remainingTicks <= 0 && fadeOutTicks <= 0) {
                visibleTraces.removeIf(trace -> trace.state != 3);
            }
        });

        WorldRenderEvents.END_MAIN.register(BloodSenseClient::renderWorldSense);
        HudRenderCallback.EVENT.register((context, tickCounter) -> renderBloodReadings(context));

        ClientPlayNetworking.registerGlobalReceiver(BloodSenseResponsePayload.ID, (payload, context) ->
                context.client().execute(() -> handleBloodSenseResponse(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(HematicBondPayload.ID, (payload, context) ->
                context.client().execute(() -> syncHematicBond(context.client(), payload.absorbed()))
        );
        ClientPlayNetworking.registerGlobalReceiver(VeinmakerTrailPayload.ID, (payload, context) ->
                context.client().execute(() -> applyPaintedCells(payload))
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

    private static void renderBloodReadings(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isActive() || client.player == null || visibleTraces.isEmpty()) return;

        ClientTrace trace = targetedReadableTrace(client);
        if (trace == null) return;

        float tickProgress = client.getRenderTickCounter().getTickProgress(false);
        int ageTicks = trace.ageTicks + (int)animatedAge(tickProgress);
        float intensity = MathHelper.clamp(trace.visibility * activeAmount(), 0.15f, 1f);
        List<BloodGuiText.ReadingLine> lines = new ArrayList<>();
        lines.add(new BloodGuiText.ReadingLine(readingName(trace, ageTicks), intensity));
        lines.add(new BloodGuiText.ReadingLine("intensity " + Math.round(traceVisualMass(trace.strength) * 100f) + "%", intensity * 0.82f));

        String state = readingState(trace.state);
        if (!state.isEmpty()) {
            lines.add(new BloodGuiText.ReadingLine(state, intensity * 0.9f));
        }

        int x = client.getWindow().getScaledWidth() / 2 + 10;
        int y = client.getWindow().getScaledHeight() / 2 + 14;
        BloodGuiText.drawReadingPanel(context, client.textRenderer, x, y, lines, intensity);
    }

    private static ClientTrace targetedReadableTrace(MinecraftClient client) {
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        Vec3d look = Vec3d.fromPolar(client.gameRenderer.getCamera().getPitch(), client.gameRenderer.getCamera().getYaw()).normalize();
        ClientTrace target = null;
        double bestAlongRay = Double.MAX_VALUE;

        for (ClientTrace trace : visibleTraces) {
            if (trace.visibility <= 0.08f) continue;
            if (client.player.squaredDistanceTo(trace.pos.toCenterPos()) > TYPE_READING_RANGE_SQUARED) continue;

            double alongRay = rayPillarIntersection(camera, look, trace);
            if (alongRay < 0.5 || alongRay > READING_TARGET_RANGE || alongRay >= bestAlongRay) continue;

            target = trace;
            bestAlongRay = alongRay;
        }

        return target;
    }

    private static double rayPillarIntersection(Vec3d origin, Vec3d direction, ClientTrace trace) {
        float mass = traceVisualMass(trace.strength);
        double height = MathHelper.clamp(1.9f + mass * 5.7f, 2.0f, 7.6f);
        double radius = MathHelper.clamp(0.34f + mass * 0.44f, 0.36f, 0.9f);
        double cx = trace.pos.getX() + 0.5;
        double cz = trace.pos.getZ() + 0.5;
        double minY = trace.pos.getY() - 0.1;
        double maxY = trace.pos.getY() + height;

        double ox = origin.x - cx;
        double oz = origin.z - cz;
        double a = direction.x * direction.x + direction.z * direction.z;
        if (a < 0.00001) return Double.MAX_VALUE;

        double b = 2.0 * (ox * direction.x + oz * direction.z);
        double c = ox * ox + oz * oz - radius * radius;
        double discriminant = b * b - 4.0 * a * c;
        if (discriminant < 0.0) return Double.MAX_VALUE;

        double root = Math.sqrt(discriminant);
        double t0 = (-b - root) / (2.0 * a);
        double t1 = (-b + root) / (2.0 * a);
        double t = t0 > 0.0 ? t0 : t1;
        if (t <= 0.0) return Double.MAX_VALUE;

        double y = origin.y + direction.y * t;
        return y >= minY && y <= maxY ? t : Double.MAX_VALUE;
    }

    private static double markerReadingHeight(ClientTrace trace) {
        float mass = traceVisualMass(trace.strength);
        return MathHelper.clamp(1.0f + mass * 2.6f, 1.0f, 3.4f);
    }

    private static String readingName(ClientTrace trace, int ageTicks) {
        boolean fresh = ageTicks < 20 * 60;
        return switch (trace.type) {
            case 1 -> fresh ? "fresh ritual blood" : "ritual residue";
            case 2 -> fresh ? "fresh rot blood" : "rot residue";
            case 3 -> fresh ? "deep blood pressure" : "deep blood residue";
            default -> fresh ? "death residue" : "old death blood";
        };
    }

    private static String readingState(int state) {
        return switch (state) {
            case 3 -> "contained";
            case 4 -> "drained";
            case 5 -> "mutated";
            default -> "";
        };
    }

    private static void handleBloodSenseResponse(BloodSenseResponsePayload payload) {
        setVisibleTraces(payload.traces());
        if (payload.refreshOnly()) return;

        remainingTicks = DURATION_TICKS;
        fadeOutTicks = 0;
        pulseJitter = (System.nanoTime() & 1023L) / 1023f;
    }

    private static void setVisibleTraces(List<BloodSenseResponsePayload.Trace> traces) {
        visibleTraces.clear();
        for (BloodSenseResponsePayload.Trace trace : traces) {
            visibleTraces.add(new ClientTrace(
                    trace.type(),
                    new BlockPos(trace.x(), trace.y(), trace.z()),
                    trace.strength(),
                    trace.ageTicks(),
                    trace.state()
            ));
        }
    }

    private static void applyPaintedCells(VeinmakerTrailPayload payload) {
        if (payload.replace()) {
            paintedCells.clear();
        }

        for (VeinmakerTrailPayload.Cell cell : payload.cells()) {
            int lifetime = cell.contained() ? payload.lifetimeTicks() : UNCONTAINED_TRAIL_LIFETIME_TICKS;
            int life = Math.max(1, lifetime - cell.ageTicks());
            upsertPaintedCell(new PaintedCell(cell.pos(), cell.side(), cell.pixelX(), cell.pixelY(), life));
        }

        if (paintedCells.size() > 8192) {
            paintedCells.subList(0, paintedCells.size() - 8192).clear();
        }
    }

    private static void upsertPaintedCell(PaintedCell next) {
        for (int i = 0; i < paintedCells.size(); i++) {
            if (paintedCells.get(i).samePixel(next)) {
                paintedCells.set(i, next);
                return;
            }
        }
        paintedCells.add(next);
    }

    private static void syncHematicBond(MinecraftClient client, boolean absorbed) {
        if (client.player instanceof HematicBondHolder holder) {
            holder.suture_n_sorcery$setAbsorbedHematicCatalyst(absorbed);
        }
    }

    private static void renderWorldSense(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        float tickProgress = client.getRenderTickCounter().getTickProgress(false);
        renderVeinmakerLines(context, client);
        boolean active = isActive();
        if (!active && visibleTraces.stream().noneMatch(trace -> trace.state == 3)) return;

        float age = animatedAge(tickProgress);
        float open = active ? openingAmount(age) : 1f;
        float fade = active ? fadeAmount(tickProgress) : 1f;
        float strength = active ? open * fade : 0.68f;
        float radius = active ? MAX_RADIUS * open * fade : MAX_RADIUS;

        MatrixStack matrices = context.matrices();
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        Vec3d center = client.player.getLerpedPos(tickProgress).add(0.0, 1.0, 0.0);
        if (active) {
            drawScreenRefraction(client, center, radius, strength);
        }

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        MatrixStack.Entry entry = matrices.peek();

        RenderLayer innerSphereLayer = RenderLayer.getEntityTranslucent(SPHERE_TEXTURE_INNER);
        RenderLayer pillarBodyLayer = RenderLayer.getEntityTranslucent(PILLAR_BODY_TEXTURE);
        RenderLayer pillarCoreLayer = RenderLayer.getEntityTranslucent(PILLAR_CORE_TEXTURE);
        RenderLayer pillarPulseLayer = RenderLayer.getEntityTranslucent(PILLAR_PULSE_TEXTURE);
        RenderLayer groundWoundLayer = RenderLayer.getEntityTranslucent(GROUND_WOUND_TEXTURE);
        RenderLayer ritualThreadLayer = RenderLayer.getEntityTranslucent(RITUAL_THREAD_TEXTURE);
        RenderLayer sphereRefractionLayer = bloodSenseSphereRefractionLayer();
        int markerAge = markerAnimationTicks + (int)tickProgress;
        updateTraceMarkers(client.player, radius, active ? (int)age : markerAge);

        if (active) {
            RenderLayer outerSphereLayer = RenderLayer.getEnergySwirl(
                    SPHERE_TEXTURE,
                    age * 0.012f,
                    age * 0.004f
            );

            drawLayer(sphereRefractionLayer, vertices ->
                    drawRefractionSphere(entry, vertices, center, radius, strength)
            );

            drawLayer(innerSphereLayer, vertices ->
                    drawInnerSphere(entry, vertices, center, radius, strength, age)
            );

            drawLayer(outerSphereLayer, vertices ->
                    drawShaderSphere(entry, vertices, client.world, center, radius, strength)
            );
        }

        drawLayer(groundWoundLayer, vertices -> drawGroundWounds(entry, vertices, client.world, strength, markerAge));
        drawLayer(pillarBodyLayer, vertices -> drawMarkerBodies(entry, vertices, camera, strength, markerAge));
        drawLayer(pillarCoreLayer, vertices -> drawMarkerCores(entry, vertices, camera, strength, markerAge));
        drawLayer(pillarPulseLayer, vertices -> drawMarkerPulses(entry, vertices, camera, strength, markerAge));
        drawLayer(ritualThreadLayer, vertices -> drawRitualThreads(entry, vertices, camera, strength, markerAge));

        matrices.pop();
    }

    private static void renderVeinmakerLines(WorldRenderContext context, MinecraftClient client) {
        if (paintedCells.isEmpty()) return;

        MatrixStack matrices = context.matrices();
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        MatrixStack.Entry entry = matrices.peek();
        RenderLayer layer = RenderLayer.getEntityTranslucent(SANGUINE_FIXATIVE_TRAIL_TEXTURE);
        drawLayer(layer, vertices -> {
            for (PaintedCell cell : paintedCells) {
                drawPaintedCell(entry, vertices, cell);
            }
        });

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

        int alpha = Math.clamp((int)(38 * strength), 0, 54);
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

        int alpha = Math.clamp((int)(26 * strength), 0, 38);
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
            if (trace.state == 3) continue;

            MarkerStyle style = markerStyle(trace, strength, scanAge);
            double x = trace.pos.getX() + 0.5;
            double y0 = trace.pos.getY() + 0.06;
            double z = trace.pos.getZ() + 0.5;
            drawPillarBillboardUvTopFade(entry, vertices, camera, x, y0, z, style.outerHalf, y0 + style.height, 0.024, bodyVOffset(scanAge, style), style.r, style.g, style.b, style.outerAlpha);
        }
    }

    private static void drawMarkerCores(MatrixStack.Entry entry, VertexConsumer vertices, Vec3d camera, float strength, int scanAge) {
        for (ClientTrace trace : visibleTraces) {
            if (trace.visibility <= 0.01f) continue;

            MarkerStyle style = markerStyle(trace, strength, scanAge);
            double x = trace.pos.getX() + 0.5;
            double y0 = trace.pos.getY() + 0.14;
            double z = trace.pos.getZ() + 0.5;
            drawPillarBillboardUvTopFade(entry, vertices, camera, x, y0, z, style.coreHalf, y0 + style.height, 0.048, coreVOffset(scanAge), Math.min(255, style.r + 26), Math.min(255, style.g + 26), Math.min(255, style.b + 26), style.coreAlpha);
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
                drawGroundWound(entry, vertices, trace.pos, x, y0, z, style);
            }
        }
    }

    private static void drawRitualThreads(MatrixStack.Entry entry, VertexConsumer vertices, Vec3d camera, float strength, int scanAge) {
        for (ClientTrace trace : visibleTraces) {
            if (trace.visibility <= 0.01f) continue;

            MarkerStyle style = markerStyle(trace, strength, scanAge);
            if (!style.distant && (trace.type == 1 || trace.type == 2)) {
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
        boolean rot = trace.type == 2;
        boolean deep = trace.type == 3;
        boolean distant = distance > DETAILED_MARKER_DISTANCE;
        float traceStrength = MathHelper.clamp((0.25f + mass * 0.75f) * strength * trace.visibility, 0f, 1.7f);

        int r = ritual ? 255 : rot ? 205 : deep ? 155 : Math.round(MathHelper.lerp(freshness, 126f, 245f));
        int g = ritual ? 38 : rot ? 76 : deep ? 34 : Math.round(MathHelper.lerp(freshness, 8f, 28f));
        int b = ritual ? 150 : rot ? 18 : deep ? 235 : Math.round(MathHelper.lerp(freshness, 20f, 50f));
        double height = (ritual ? 3.6 : deep ? 3.2 : rot ? 2.4 : 1.8) + mass * (ritual ? 5.4 : deep ? 5.8 : rot ? 4.2 : 4.8);
        height *= MathHelper.lerp(age, 1.0f, 0.62f);
        double bodyHalf = MathHelper.clamp((float)(height * PILLAR_BODY_TEXTURE_ASPECT * 0.26), distant ? 0.055f : 0.12f, distant ? 0.18f : 0.78f);
        double coreHalf = MathHelper.clamp((float)(height * PILLAR_CORE_TEXTURE_ASPECT * 0.16), distant ? 0.035f : 0.055f, distant ? 0.10f : 0.36f);

        boolean contained = trace.state == 3;
        boolean drained = trace.state == 4;
        boolean mutated = trace.state == 5;
        float stateAlpha = contained ? 1.18f : drained ? 0.42f : mutated ? 0.78f : 1f;
        int baseAlpha = Math.clamp((int)((ritual ? 150 : deep ? 145 : rot ? 118 : 130) * traceStrength * MathHelper.lerp(age, 1f, 0.68f) * stateAlpha), 34, distant ? 130 : 235);
        int coreAlpha = Math.clamp((int)((ritual ? 215 : deep ? 205 : rot ? 155 : 180) * traceStrength * MathHelper.lerp(age, 1f, 0.72f) * stateAlpha), distant ? 30 : 58, distant ? 145 : 255);
        float pulseSpeed = drained ? 0.018f : contained ? 0.13f : mutated ? 0.16f : ritual ? 0.105f : deep ? 0.052f : rot ? 0.032f : MathHelper.lerp(freshness, 0.026f, 0.078f);

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
                contained,
                distant
        );
    }

    private static void drawPulse(MatrixStack.Entry entry, VertexConsumer vertices, Vec3d camera, double x, double y0, double z, MarkerStyle style, int scanAge) {
        double travel = ((scanAge * style.pulseSpeed) % 1.0);
        double bandHeight = Math.max(0.34, style.height * 0.14);
        double bandBottom = y0 + travel * Math.max(0.1, style.height - bandHeight);
        double bandTop = bandBottom + bandHeight;
        int alpha = Math.clamp((int)(style.coreAlpha * (0.62 + style.freshness * 0.38)), 25, 245);
        double half = Math.max(0.055, style.coreHalf * (0.55 + style.mass * 0.3));

        int r = Math.min(255, style.r + 38);
        int g = Math.min(255, style.g + 18);
        int b = Math.min(255, style.b + 22);
        drawPillarBillboardUvTopFade(entry, vertices, camera, x, bandBottom, z, half, bandTop, 0.054, 0f, r, g, b, alpha);
    }

    private static void drawGroundWound(MatrixStack.Entry entry, VertexConsumer vertices, BlockPos pos, double x, double y, double z, MarkerStyle style) {
        double half = MathHelper.clamp((float)(style.outerHalf * (2.0 + style.mass * 0.9)), 0.38f, 1.55f);
        int alpha = Math.clamp((int)(style.outerAlpha * 1.35), 95, 245);
        if (style.contained) {
            alpha = Math.clamp((int)(alpha * 0.22), 18, 72);
        }
        double angle = woundRotation(pos);

        addRotatedGroundQuad(entry, vertices, x, y, z, half, angle, style.r, style.g, style.b, alpha);
        double glowHalf = half * 1.32;
        addRotatedGroundQuad(entry, vertices, x, y + 0.003, z, glowHalf, angle + 0.37, Math.min(255, style.r + 18), Math.min(255, style.g + 10), Math.min(255, style.b + 10), Math.clamp((int)(alpha * 0.42), 8, 120));
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
                float heightT = i / (float)segments;
                float topFade = topFadeAlpha(heightT);
                if (topFade <= 0.01f) continue;
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
                        Math.min(255, style.r + 42), Math.min(255, style.g + 34), Math.min(255, style.b + 34), Math.clamp((int)(alpha * topFade), 0, 190));
            }
        }
    }

    private static float bodyVOffset(int scanAge, MarkerStyle style) {
        return (scanAge * (0.018f + style.freshness * 0.018f)) % 1f;
    }

    private static float coreVOffset(int scanAge) {
        return -((scanAge * 0.006f) % 1f);
    }

    private static float topFadeAlpha(float heightT) {
        return 1f - smooth(MathHelper.clamp((heightT - 0.7f) / 0.3f, 0f, 1f));
    }

    private static double woundRotation(BlockPos pos) {
        long hash = pos.asLong() * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdL;
        hash ^= hash >>> 33;
        return ((hash & 0xffffL) / 65535.0) * Math.PI * 2.0;
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
        float durationFade = trace.state == 3 ? 1f : currentStrength(0f);

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
        drawPillarBillboardUvGradient(entry, vertices, camera, x, y0, z, half, y1, forwardOffset, vOffset, r, g, b, a, a);
    }

    private static void drawPillarBillboardUvTopFade(MatrixStack.Entry entry, VertexConsumer vertices, Vec3d camera, double x, double y0, double z, double half, double y1, double forwardOffset, float vOffset, int r, int g, int b, int alpha) {
        double splitY = y0 + (y1 - y0) * 0.7;
        float splitV = vOffset + 0.3f;
        drawPillarBillboardUvGradient(entry, vertices, camera, x, y0, z, half, splitY, forwardOffset, vOffset + 1f, vOffset + 1f, splitV, splitV, r, g, b, alpha, alpha);
        drawPillarBillboardUvGradient(entry, vertices, camera, x, splitY, z, half, y1, forwardOffset, splitV, splitV, vOffset, vOffset, r, g, b, alpha, 0);
    }

    private static void drawPillarBillboardUvGradient(MatrixStack.Entry entry, VertexConsumer vertices, Vec3d camera, double x, double y0, double z, double half, double y1, double forwardOffset, float vOffset, int r, int g, int b, int bottomAlpha, int topAlpha) {
        drawPillarBillboardUvGradient(entry, vertices, camera, x, y0, z, half, y1, forwardOffset, 1f + vOffset, 1f + vOffset, vOffset, vOffset, r, g, b, bottomAlpha, topAlpha);
    }

    private static void drawPillarBillboardUvGradient(MatrixStack.Entry entry, VertexConsumer vertices, Vec3d camera, double x, double y0, double z, double half, double y1, double forwardOffset, float vBottomLeft, float vBottomRight, float vTopRight, float vTopLeft, int r, int g, int b, int bottomAlpha, int topAlpha) {
        AxisLockedBillboard billboard = axisLockedBillboard(camera, x, z);
        double rightX = billboard.rightX;
        double rightZ = billboard.rightZ;
        double forwardX = billboard.forwardX;
        double forwardZ = billboard.forwardZ;
        double px = x + forwardX * forwardOffset;
        double pz = z + forwardZ * forwardOffset;

        addTexturedQuadUv(entry, vertices,
                px - rightX * half, y0, pz - rightZ * half,
                px + rightX * half, y0, pz + rightZ * half,
                px + rightX * half, y1, pz + rightZ * half,
                px - rightX * half, y1, pz - rightZ * half,
                0f, vBottomLeft,
                1f, vBottomRight,
                1f, vTopRight,
                0f, vTopLeft,
                r, g, b,
                bottomAlpha, bottomAlpha, topAlpha, topAlpha);
    }

    private static AxisLockedBillboard axisLockedBillboard(Vec3d camera, double x, double z) {
        Vector3f cameraRight = new Vector3f(1f, 0f, 0f)
                .rotate(MinecraftClient.getInstance().gameRenderer.getCamera().getRotation());
        double rightX = cameraRight.x();
        double rightZ = cameraRight.z();
        double rightLength = Math.sqrt(rightX * rightX + rightZ * rightZ);
        if (rightLength > 0.0001) {
            rightX /= rightLength;
            rightZ /= rightLength;
        } else {
            rightX = 1.0;
            rightZ = 0.0;
        }
        double dx = camera.x - x;
        double dz = camera.z - z;
        double length = Math.sqrt(dx * dx + dz * dz);
        double forwardX = length > 0.0001 ? dx / length : 0.0;
        double forwardZ = length > 0.0001 ? dz / length : 1.0;
        return new AxisLockedBillboard(rightX, rightZ, forwardX, forwardZ);
    }

    private static void drawTaperedPillarBillboard(MatrixStack.Entry entry, VertexConsumer vertices, Vec3d camera, double x, double y0, double z, double bottomHalf, double topHalf, double y1, double forwardOffset, float vOffset, int r, int g, int b, int a) {
        AxisLockedBillboard billboard = axisLockedBillboard(camera, x, z);
        double rightX = billboard.rightX;
        double rightZ = billboard.rightZ;
        double forwardX = billboard.forwardX;
        double forwardZ = billboard.forwardZ;
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

    private static void drawPaintedCell(MatrixStack.Entry entry, VertexConsumer vertices, PaintedCell cell) {
        Direction side = Direction.values()[Math.floorMod(cell.side, Direction.values().length)];
        if (!hasOpenPaintedSurface(cell.pos, side)) return;

        CellQuad quad = cellQuad(cell.pos, side, cell.pixelX, cell.pixelY);
        int shade = (cell.pixelX * 19 + cell.pixelY * 11 + cell.pos.getX() * 3 + cell.pos.getZ() * 5) & 15;
        int red = 132 + shade;
        int green = 4 + shade / 4;
        int blue = 18 + shade / 2;

        float u0 = cell.pixelX / 16f;
        float v0 = cell.pixelY / 16f;
        float u1 = u0 + 1f / 16f;
        float v1 = v0 + 1f / 16f;
        int alpha = Math.clamp((int)(208 * MathHelper.clamp(cell.life / 40f, 0f, 1f)), 0, 208);

        addTexturedQuadUv(entry, vertices,
                quad.a.x, quad.a.y, quad.a.z,
                quad.b.x, quad.b.y, quad.b.z,
                quad.c.x, quad.c.y, quad.c.z,
                quad.d.x, quad.d.y, quad.d.z,
                u0, v0,
                u1, v0,
                u1, v1,
                u0, v1,
                red, green, blue, alpha);
    }

    private static boolean hasOpenPaintedSurface(BlockPos pos, Direction side) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return false;

        return !client.world.getBlockState(pos).isAir()
                && client.world.getBlockState(pos.offset(side)).isAir();
    }

    private static CellQuad cellQuad(BlockPos pos, Direction side, int pixelX, int pixelY) {
        double min = 0.0;
        double max = 1.0 / 16.0;
        double u0 = pixelX / 16.0;
        double u1 = u0 + max;
        double v0 = pixelY / 16.0;
        double v1 = v0 + max;
        double lift = 0.013;
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();

        return switch (side) {
            case UP -> new CellQuad(
                    new Vec3d(x + u0, y + 1.0 + lift, z + v0),
                    new Vec3d(x + u1, y + 1.0 + lift, z + v0),
                    new Vec3d(x + u1, y + 1.0 + lift, z + v1),
                    new Vec3d(x + u0, y + 1.0 + lift, z + v1)
            );
            case DOWN -> new CellQuad(
                    new Vec3d(x + u0, y - lift, z + v1),
                    new Vec3d(x + u1, y - lift, z + v1),
                    new Vec3d(x + u1, y - lift, z + v0),
                    new Vec3d(x + u0, y - lift, z + v0)
            );
            case NORTH -> new CellQuad(
                    new Vec3d(x + u1, y + 1.0 - v0, z - lift),
                    new Vec3d(x + u0, y + 1.0 - v0, z - lift),
                    new Vec3d(x + u0, y + 1.0 - v1, z - lift),
                    new Vec3d(x + u1, y + 1.0 - v1, z - lift)
            );
            case SOUTH -> new CellQuad(
                    new Vec3d(x + u0, y + 1.0 - v0, z + 1.0 + lift),
                    new Vec3d(x + u1, y + 1.0 - v0, z + 1.0 + lift),
                    new Vec3d(x + u1, y + 1.0 - v1, z + 1.0 + lift),
                    new Vec3d(x + u0, y + 1.0 - v1, z + 1.0 + lift)
            );
            case EAST -> new CellQuad(
                    new Vec3d(x + 1.0 + lift, y + 1.0 - v0, z + u1),
                    new Vec3d(x + 1.0 + lift, y + 1.0 - v0, z + u0),
                    new Vec3d(x + 1.0 + lift, y + 1.0 - v1, z + u0),
                    new Vec3d(x + 1.0 + lift, y + 1.0 - v1, z + u1)
            );
            case WEST -> new CellQuad(
                    new Vec3d(x - lift, y + 1.0 - v0, z + u0),
                    new Vec3d(x - lift, y + 1.0 - v0, z + u1),
                    new Vec3d(x - lift, y + 1.0 - v1, z + u1),
                    new Vec3d(x - lift, y + 1.0 - v1, z + u0)
            );
        };
    }

    private static void addTexturedQuad(MatrixStack.Entry entry, VertexConsumer vertices, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, int r, int g, int b, int a) {
        addTexturedQuadUv(entry, vertices, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, 0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f, r, g, b, a);
    }

    private static void addRotatedGroundQuad(MatrixStack.Entry entry, VertexConsumer vertices, double x, double y, double z, double half, double angle, int r, int g, int b, int a) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        Vec3d p1 = rotatedGroundCorner(x, y, z, -half, -half, cos, sin);
        Vec3d p2 = rotatedGroundCorner(x, y, z, half, -half, cos, sin);
        Vec3d p3 = rotatedGroundCorner(x, y, z, half, half, cos, sin);
        Vec3d p4 = rotatedGroundCorner(x, y, z, -half, half, cos, sin);
        addTexturedQuad(entry, vertices, p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, p3.x, p3.y, p3.z, p4.x, p4.y, p4.z, r, g, b, a);
    }

    private static Vec3d rotatedGroundCorner(double x, double y, double z, double dx, double dz, double cos, double sin) {
        return new Vec3d(
                x + dx * cos - dz * sin,
                y,
                z + dx * sin + dz * cos
        );
    }

    private static void addTexturedQuadUv(MatrixStack.Entry entry, VertexConsumer vertices, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, float u1, float v1, float u2, float v2, float u3, float v3, float u4, float v4, int r, int g, int b, int a) {
        vertices.vertex(entry, (float)x1, (float)y1, (float)z1).color(r, g, b, a).texture(u1, v1).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0f, 1f, 0f);
        vertices.vertex(entry, (float)x2, (float)y2, (float)z2).color(r, g, b, a).texture(u2, v2).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0f, 1f, 0f);
        vertices.vertex(entry, (float)x3, (float)y3, (float)z3).color(r, g, b, a).texture(u3, v3).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0f, 1f, 0f);
        vertices.vertex(entry, (float)x4, (float)y4, (float)z4).color(r, g, b, a).texture(u4, v4).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0f, 1f, 0f);
    }

    private static void addTexturedQuadUv(MatrixStack.Entry entry, VertexConsumer vertices, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, float u1, float v1, float u2, float v2, float u3, float v3, float u4, float v4, int r, int g, int b, int a1, int a2, int a3, int a4) {
        vertices.vertex(entry, (float)x1, (float)y1, (float)z1).color(r, g, b, a1).texture(u1, v1).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0f, 1f, 0f);
        vertices.vertex(entry, (float)x2, (float)y2, (float)z2).color(r, g, b, a2).texture(u2, v2).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0f, 1f, 0f);
        vertices.vertex(entry, (float)x3, (float)y3, (float)z3).color(r, g, b, a3).texture(u3, v3).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0f, 1f, 0f);
        vertices.vertex(entry, (float)x4, (float)y4, (float)z4).color(r, g, b, a4).texture(u4, v4).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0f, 1f, 0f);
    }

    private static final class ClientTrace {
        private final int type;
        private final BlockPos pos;
        private final int strength;
        private final int ageTicks;
        private final int state;
        private float visibility;
        private float distance;

        private ClientTrace(int type, BlockPos pos, int strength, int ageTicks, int state) {
            this.type = type;
            this.pos = pos;
            this.strength = strength;
            this.ageTicks = ageTicks;
            this.state = state;
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

    private record AxisLockedBillboard(double rightX, double rightZ, double forwardX, double forwardZ) {
    }

    private static final class PaintedCell {
        private final BlockPos pos;
        private final int side;
        private final int pixelX;
        private final int pixelY;
        private int life;

        private PaintedCell(BlockPos pos, int side, int pixelX, int pixelY, int life) {
            this.pos = pos;
            this.side = side;
            this.pixelX = pixelX;
            this.pixelY = pixelY;
            this.life = life;
        }

        private boolean samePixel(PaintedCell other) {
            return side == other.side
                    && pixelX == other.pixelX
                    && pixelY == other.pixelY
                    && pos.equals(other.pos);
        }
    }

    private record CellQuad(Vec3d a, Vec3d b, Vec3d c, Vec3d d) {
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
            boolean contained,
            boolean distant
    ) {
    }
}
