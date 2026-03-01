package me.suture_n_sorcery.suture_n_sorcery.client.screens;

import com.mojang.blaze3d.textures.GpuTextureView;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom.RitualLoomBlockEntity;
import me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom.RitualLoomScreenHandler;
import me.suture_n_sorcery.suture_n_sorcery.client.audio.PressControllerInstance;
import me.suture_n_sorcery.suture_n_sorcery.client.audio.PressurizeArm;
import me.suture_n_sorcery.suture_n_sorcery.items.HematicCatalyzer;
import me.suture_n_sorcery.suture_n_sorcery.mixin.client.DrawContextInvoker;
import me.suture_n_sorcery.suture_n_sorcery.render.ModShader;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.*;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom.RitualLoomScreenHandler.CORE_X;
import static me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom.RitualLoomScreenHandler.CORE_Y;
import static net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender;
import static net.minecraft.util.math.MathHelper.cos;
import static net.minecraft.util.math.MathHelper.sin;


public class RitualLoomScreen extends HandledScreen<RitualLoomScreenHandler>{

    // identifiers
    private static final Identifier GUI =
            Identifier.of(Suture_n_sorcery.MOD_ID, "textures/gui/ritual_loom.png");

    private static final Identifier BLOOD_SPRITESHEET =
            Identifier.of(Suture_n_sorcery.MOD_ID, "textures/block/concentrated_blood_still.png");

    private static final Identifier STRING_1 =
            Identifier.of(Suture_n_sorcery.MOD_ID, "textures/gui/string_fill.png");

    private static final Identifier STRING_1_FLIP =
            Identifier.of(Suture_n_sorcery.MOD_ID, "textures/gui/string_fill_flipped.png");

    private static final Identifier STRING_2 =
            Identifier.of(Suture_n_sorcery.MOD_ID, "textures/gui/string_fill_2.png");

    private static final Identifier STRING_2_FLIP =
            Identifier.of(Suture_n_sorcery.MOD_ID, "textures/gui/string_fill_2_flipped.png");

    private static final Identifier BUTTON_FRAME =
            Identifier.of(Suture_n_sorcery.MOD_ID, "textures/gui/button_frame.png");

    private static final Identifier HEMATIC_VIGNETTE =
            Identifier.of(Suture_n_sorcery.MOD_ID, "textures/gui/vignette.png");

    // dimentions

    private static final int BLOOD_FRAME_W = 16;
    private static final int BLOOD_FRAME_H = 16;
    private static final int BLOOD_FRAME_COUNT = 19;

    private static final int STRING_TILE_W = 5;
    private static final int STRING_TILE_H = 3;

    private static final int METER_W = 12;
    private static final int METER_H = 33;

    private static final int BLOOD_X = 8;
    private static final int BLOOD_Y = 37;

    private static final int STRING_X = 28;
    private static final int STRING_Y = 37;

    private static final int VIG_W = 300;
    private static final int VIG_H = 300;

    // vars

    private static final int SHOCKWAVE_DURATION_TICKS = 18;
    private static final float SHOCKWAVE_RADIUS_POWER = 2.0f;
    private static final boolean SHOCKWAVE_FLIP_V = true;

    private float shockwaveCenterX, shockwaveCenterY = 0f;
    private float shockwaveStrength = 0f;
    private float shockwaveRadius = 0.0f;
    private int shockwaveTicks = 0;

    private float vignetteFade = 0f;

    private float rippleAge = 9999f;

    private int openTicks = 0;

    private boolean noncesPrimed = false;
    private boolean ringSeeded = false;

    private int satStable = -1;
    private int satStableTicks = 0;

    private boolean postHookRegistered = false;

    private boolean rehydratedThisOpen = false;

    private float renderTime = 0f;

    private float pressBlend = 0f;
    private PressurizeHoldButton pressButton;

    private int lastCoreNonce = 0;
    private int lastStringNonce = 0;

    private float screenZoom = 0f;

    private int lastPhase = -1;
    private int ringInsertCursor = 0;
    private final float[] slotJitterAngle = new float[ORBIT_SLOTS];
    private final float[] slotJitterRadius = new float[ORBIT_SLOTS];
    private final float[] slotPhase = new float[ORBIT_SLOTS];

    private int visualOrbitCount = 0;
    private int orbitSeed = 0;
    private static final int ORBIT_SLOTS = 14;
    private final float[] orbitRedness = new float[ORBIT_SLOTS];

    private static final float TRAVEL_LEN_PX = 15f;
    private static final int MAX_TRAVELERS = 8;

    private static final Map<Integer, Integer> CACHED_ORBIT_BY_SYNC = new HashMap<>();
    private static final Map<Integer, float[]> CACHED_REDNESS_BY_SYNC = new HashMap<>();

    private final ArrayList<Traveler> travelers = new ArrayList<>();

    public RitualLoomScreen(RitualLoomScreenHandler handler, PlayerInventory inventory, Text title) {super(handler, inventory, title);}

    // overrides
    @Override
    protected void init() {
        super.init();
        this.titleX = 8;
        this.titleY = 6;

        int guiX = (this.width - this.backgroundWidth) / 2;
        int guiY = (this.height - this.backgroundHeight) / 2;

        openTicks = 0;
        lastPhase = handler.getPhase();

        noncesPrimed = false;
        rehydratedThisOpen = false;

        this.pressButton = new PressurizeHoldButton(
                guiX + CORE_X - 27,
                guiY + CORE_Y - 34,
                69, 21,
                Text.literal("PRESSURIZE"),
                this
        );

        this.pressButton.visible = false;
        this.addDrawableChild(this.pressButton);

        // seed the client state from current server state
        travelers.clear();
        int key = handler.syncId;

        Integer cachedCount = CACHED_ORBIT_BY_SYNC.get(key);
        float[] cachedRed = CACHED_REDNESS_BY_SYNC.get(key);

        if (cachedCount != null && cachedRed != null) {
            visualOrbitCount = cachedCount;
            for (int i = 0; i < ORBIT_SLOTS; i++) orbitRedness[i] = cachedRed[i];
        } else {
            visualOrbitCount = 0;
            for (int i = 0; i < ORBIT_SLOTS; i++) orbitRedness[i] = 0f;
        }

        ringSeeded = false;

        lastCoreNonce = handler.getCoreNonce();
        lastStringNonce = handler.getStringNonce();

        shockwaveTicks = 0;
        shockwaveStrength = 0f;
        shockwaveRadius = 0f;

        rippleAge = 9999f;

        orbitSeed = 0x9e3779b9 ^ handler.syncId;

        if (handler.getPhase() == RitualLoomBlockEntity.PHASE_SATURATING) {
            int sat = handler.getSaturationTicks();
            if (sat > 0) {
                spawnTraveler();
                travelers.get(travelers.size() - 1).headT = clamp01(sat / 15f);
            }
        }

        for (int i = 0; i < ORBIT_SLOTS; i++) {
            slotJitterAngle[i] = randSigned(i, orbitSeed) * 0.18f;
            slotJitterRadius[i] = randSigned(i, orbitSeed + 1) * 1.2f;
            slotPhase[i] = randSigned(i, orbitSeed + 2) * 2.0f;
        }

        ringSeeded = false;
        satStable = -1;
        satStableTicks = 0;

        ringInsertCursor = visualOrbitCount % ORBIT_SLOTS;

        if (!postHookRegistered) {
            afterRender(this).register((screen, ctx, mouseX, mouseY, tickDelta) -> sns$postProcess(ctx, tickDelta));
            postHookRegistered = true;
        }
    }

    @Override
    public void handledScreenTick() {
        super.handledScreenTick();

        openTicks++;

        if (!ringSeeded) {
            int sat = handler.getSaturatedStrings();

            if (sat == satStable) satStableTicks++;
            else { satStable = sat; satStableTicks = 0; }

            boolean hadCache = CACHED_ORBIT_BY_SYNC.containsKey(handler.syncId);
            if (openTicks > 2 && satStableTicks >= 1 && (satStable > 0 || !hadCache)) {
                visualOrbitCount = Math.min(ORBIT_SLOTS, satStable);
                for (int i = 0; i < ORBIT_SLOTS; i++) {
                    orbitRedness[i] = (i < visualOrbitCount) ? 1f : 0f;
                }
                ringInsertCursor = visualOrbitCount % ORBIT_SLOTS;
                ringSeeded = true;
            }
        }

        if (!noncesPrimed) {
            lastCoreNonce = handler.getCoreNonce();
            lastStringNonce = handler.getStringNonce();
            noncesPrimed = true;
        }

        if (!rehydratedThisOpen && openTicks > 2) {
            if (handler.getPhase() == RitualLoomBlockEntity.PHASE_SATURATING) {
                int sat = handler.getSaturationTicks();
                if (sat > 0 && travelers.isEmpty()) {
                    spawnTraveler();
                    travelers.get(travelers.size() - 1).headT = clamp01(sat / 15f);
                }
            }
            rehydratedThisOpen = true;
        }

        ItemStack coreStack = handler.getSlot(RitualLoomBlockEntity.CORE_SLOT).getStack();
        boolean corePresentClient = !coreStack.isEmpty();

        boolean coreIsCatalyzer = isHematicCatalyzer(coreStack);
        boolean outputSitting = handler.getPhase() == RitualLoomBlockEntity.PHASE_COMPLETE;

        int req = handler.getRecipeRequiredStrings();
        boolean hasRitual = req > 0;

        boolean canPress = corePresentClient
                && hasRitual
                && !outputSitting
                && !coreIsCatalyzer
                && (handler.getPhase() == RitualLoomBlockEntity.PHASE_CORE_INSERTED
                || handler.getPhase() == RitualLoomBlockEntity.PHASE_PRESSURIZING);

        pressButton.visible = corePresentClient && hasRitual && !outputSitting && !coreIsCatalyzer;
        pressButton.active  = canPress;

        if (!canPress && pressButton.isHolding()) {
            pressButton.forceReleaseIfHolding();
        }

        boolean holding = handler.getPhase() == RitualLoomBlockEntity.PHASE_PRESSURIZING;

        float targetBlend = holding ? 1f : 0f;
        pressBlend += (targetBlend - pressBlend) * 0.18f;
        pressBlend = Math.max(0f, Math.min(1f, pressBlend));

        // zoom tied to blend
        float targetZoom = 0.06f * pressBlend;
        screenZoom += (targetZoom - screenZoom) * 0.18f;

        if (noncesPrimed) {
            int coreN = handler.getCoreNonce();
            if (coreN != lastCoreNonce) {
                lastCoreNonce = coreN;

                ItemStack coreStackNow = handler.getSlot(RitualLoomBlockEntity.CORE_SLOT).getStack();
                if (isHematicCatalyzer(coreStackNow)) {
                    rippleAge = 0f;

                    int guiX = (this.width - this.backgroundWidth) / 2;
                    int guiY = (this.height - this.backgroundHeight) / 2;
                    shockwaveCenterX = guiX + CORE_X + 8;
                    shockwaveCenterY = guiY + CORE_Y + 8;

                    shockwaveTicks = 1;
                    shockwaveStrength = 1.0f;

                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client != null && client.getSoundManager() != null) {
                        client.getSoundManager().play(
                                PositionedSoundInstance.master(
                                        SoundEvent.of(Identifier.of(Suture_n_sorcery.MOD_ID, "ritual_loom_success")),
                                        1
                                )
                        );
                    }
                }
            }

            int strN = handler.getStringNonce();
            if (strN != lastStringNonce) {
                // ignore churn right after opening
                if (openTicks > 6 && ringSeeded) {
                    spawnTraveler();
                }
                lastStringNonce = strN;
            }
        }

        //shockwave update client-side
        if (shockwaveTicks > 0) {
            shockwaveTicks++;
            if (shockwaveTicks > SHOCKWAVE_DURATION_TICKS) {
                shockwaveTicks = 0;          // stop drawing postprocess
                shockwaveStrength = 0f;      // just to be safe
                shockwaveRadius = 1f;
            }
        }

        // vignette
        float target = pressBlend;
        float speed = (target > vignetteFade) ? 0.08f : 0.14f;
        vignetteFade += (target - vignetteFade) * speed;
        vignetteFade = Math.max(0f, Math.min(1f, vignetteFade));

        if (vignetteFade < 0.001f) vignetteFade = 0f;
        if (vignetteFade > 0.999f) vignetteFade = 1f;

        // boom timer
        rippleAge += 1f;

        // advance travelers
        for (int i = travelers.size() - 1; i >= 0; i--) {
            Traveler tr = travelers.get(i);
            tr.headT += tr.speed;
            if (tr.headT >= 1f) {
                tr.headT = 1f;
                tr.arrived = true;
            }
        }

        // ramp only completed orbit strings
        for (int i = 0; i < visualOrbitCount; i++) {
            orbitRedness[i] = Math.min(1f, orbitRedness[i] + 0.018f);
        }
    }

    @Override
    public void close() {
        if (pressButton != null) pressButton.forceReleaseIfHolding();
        int key = handler.syncId;

        CACHED_ORBIT_BY_SYNC.put(key, visualOrbitCount);

        float[] arr = new float[ORBIT_SLOTS];
        for (int i = 0; i < ORBIT_SLOTS; i++) arr[i] = orbitRedness[i];
        CACHED_REDNESS_BY_SYNC.put(key, arr);
        super.close();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (client != null && client.world != null) {
            renderTime = client.world.getTime() + delta;
        }

        super.render(ctx, mouseX, mouseY, delta);
        drawRitualOverlays(ctx, delta);

        for (int i = travelers.size() - 1; i >= 0; i--) {
            if (travelers.get(i).arrived) {
                int slot = travelers.get(i).slotIndex;

                orbitRedness[slot] = 0f;

                // only grow the ring count if it wasn't full yet
                if (visualOrbitCount < ORBIT_SLOTS) {
                    visualOrbitCount++;
                }

                travelers.remove(i);
            }
        }

        // cursor gravity
        if (handler.getPhase() == RitualLoomBlockEntity.PHASE_SATURATED
                && handler.getStrings() >= RitualLoomBlockEntity.REQUIRED_STRINGS) {

            // ignore carrying item if not core compatible
            assert this.client != null;
            boolean lmbDown = GLFW.glfwGetMouseButton(
                    this.client.getWindow().getHandle(),
                    GLFW.GLFW_MOUSE_BUTTON_LEFT
            ) == GLFW.GLFW_PRESS;

            ItemStack carried = handler.getCursorStack();
            boolean allowGravity = carried.isEmpty() || RitualLoomScreenHandler.isCoreAllowed(carried);

            if (!lmbDown && allowGravity) {
                applyCursorGravity();
            }
        }

        // Meter tooltips blood / strings
        if (isPointWithinBounds(BLOOD_X, BLOOD_Y, METER_W, METER_H, mouseX, mouseY)) {
            ctx.drawTooltip(
                    textRenderer,
                    List.of(Text.literal(handler.getBloodMl() + " / " + RitualLoomBlockEntity.MAX_BLOOD_ML + " mL")),
                    mouseX, mouseY
            );
        } else if (isPointWithinBounds(STRING_X, STRING_Y, METER_W, METER_H, mouseX, mouseY)) {
            ctx.drawTooltip(
                    textRenderer,
                    List.of(Text.literal(handler.getPoleStrings() + " / " + RitualLoomBlockEntity.MAX_STRINGS + " strings")),
                    mouseX, mouseY
            );
        } else {
            // Default slot/item tooltips
            this.drawMouseoverTooltip(ctx, mouseX, mouseY);
        }

        drawHematicOverlay(ctx);
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;

        // base GUI
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, GUI,
               x, y,
               0f, 0f,
                this.backgroundWidth, this.backgroundHeight,
                        256, 256
        );

        drawBloodMeter(ctx, x, y);
        drawStringMeter(ctx, x, y);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (pressButton != null && pressButton.visible && pressButton.active) {
            if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                    && pressButton.isMouseOver(click.x(), click.y())) {
                pressButton.startHolding();
                return true;
            }
        }

        /* debug: left click triggers boom
        if (click.button() == 0) {
            triggerBoomAtScreenPx(click.x(), click.y());
        }
        */

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (pressButton != null && pressButton.isHolding() && click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            pressButton.forceReleaseIfHolding();
            return true;
        }
        return super.mouseReleased(click);
    }

    // draws
    void drawHematicOverlay(DrawContext ctx) {
        if (vignetteFade <= 0.001f) return;

        // nicer opacity curve
        float a = smoothstep01(vignetteFade);

        // subtle red tint under the vignette
        int tintA = (int)(10 + 45 * a);
        ctx.fill(0, 0, this.width, this.height, (tintA << 24) | 0x660000);

        float baseScale = Math.max(this.width / (float)VIG_W, this.height / (float)VIG_H);
        float scale = baseScale * (1f + screenZoom);

        int dw = Math.round(VIG_W * scale);
        int dh = Math.round(VIG_H * scale);

        int x = (this.width - dw) / 2;
        int y = (this.height - dh) / 2;

        int col = ((int)(255 * a) << 24) | 0xFFFFFF;

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x, y);
        ctx.getMatrices().scale(scale, scale);

        ctx.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                HEMATIC_VIGNETTE,
                0, 0,
                0f, 0f,
                VIG_W, VIG_H,
                VIG_W, VIG_H,
                col
        );

        ctx.getMatrices().popMatrix();
    }
    private void drawBloodMeter(DrawContext ctx, int guiX, int guiY) {
        int filled = handler.getScaledBlood(METER_H);
        if (filled <= 0) return;

        int tankX = guiX + BLOOD_X;
        int tankY = guiY + BLOOD_Y;

        int clipTop = tankY + (METER_H - filled);
        ctx.enableScissor(tankX, clipTop, tankX + METER_W, tankY + METER_H);

        int tick = (int) (System.currentTimeMillis() / 100L);
        int frame = Math.floorMod(tick, BLOOD_FRAME_COUNT);
        float vFrame = frame * (float) BLOOD_FRAME_H;

        // tile the 16x16 frame to fill a 16x44 meter (cropping last row)
        for (int yOff = 0; yOff < METER_H; yOff += BLOOD_FRAME_H) {
            for (int xOff = 0; xOff < METER_W; xOff += BLOOD_FRAME_W) {
                int drawW = Math.min(BLOOD_FRAME_W, METER_W - xOff);
                int drawH = Math.min(BLOOD_FRAME_H, METER_H - yOff);

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
    private void drawLineQuad(DrawContext ctx, float x0, float y0, float x1, float y1, float thickness, int argb) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.01f) return;

        float ang = (float) Math.atan2(dy, dx);

        int half = Math.max(0, Math.round(thickness * 0.5f)); // allow 1px line when thickness < 2

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x0, y0);
        ctx.getMatrices().rotate(ang);

        // draw a thin rectangle in local space rotated by the matrix
        ctx.fill(0, -half, (int) len + 1, half + 1, argb);

        ctx.getMatrices().popMatrix();
    }
    private void drawStringMeter(DrawContext ctx, int guiX, int guiY) {
        int strings = handler.getPoleStrings();
        if (strings <= 0) return;

        int meterX = guiX + STRING_X;
        int meterY = guiY + STRING_Y;

        // how many 5x3 sprites to draw up to 11
        int maxSprites = 11;
        int sprites = (strings * maxSprites + RitualLoomBlockEntity.MAX_STRINGS - 1) / RitualLoomBlockEntity.MAX_STRINGS;
        sprites = Math.min(sprites, maxSprites);

        int baseX = meterX + (METER_W - STRING_TILE_W) / 2;
        int baseY = meterY + METER_H - STRING_TILE_H;

        for (int i = 0; i < sprites; i++) {
            Identifier tex;
            int xShift = 0;

            // alternate between 2 textures + flipped variants
            if ((i & 1) == 0) tex = (i % 4 == 0) ? STRING_1 : STRING_1_FLIP;
            else tex = (i % 4 == 1) ? STRING_2 : STRING_2_FLIP;

            // string2 flipped should be 1px to the right
            if (tex == STRING_2_FLIP) xShift = 1;

            int drawY = baseY - i * STRING_TILE_H;

            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex,
                    baseX + xShift, drawY,
                    0f, 0f,
                    STRING_TILE_W, STRING_TILE_H,
                    STRING_TILE_W, STRING_TILE_H
            );
        }
    }
    private void drawDistortedRing(DrawContext ctx, float cx, float cy, float ageTicks, int rgb) {
        float t = Math.min(1f, ageTicks / 16f);
        float radius = 2f + t * 40f;
        float amp = (1f - t) * 3.5f;        // distortion shrinks as it expands
        float thickness = 1.5f + (1f - t);

        int segments = 72;
        float time = ageTicks * 0.35f;

        for (int i = 0; i < segments; i++) {
            float a = (float)(i * (Math.PI * 2.0 / segments));
            float wobble = (float)Math.sin(a * 7f + time) * amp
                    + (float)Math.sin(a * 13f - time * 0.7f) * (amp * 0.5f);

            float r = radius + wobble;

            float px = cx + (float)Math.cos(a) * r;
            float py = cy + (float)Math.sin(a) * r;

            int alpha = (int)(180 * (1f - t));
            int c = (alpha << 24) | (rgb & 0x00FFFFFF);

            ctx.fill((int)(px - thickness), (int)(py), (int)(px + thickness), (int)(py + 1), c);
            ctx.fill((int)(px), (int)(py - thickness), (int)(px + 1), (int)(py + thickness), c);
        }
    }
    private void drawRitualOverlays(DrawContext ctx, float delta) {
        int guiX = (this.width - this.backgroundWidth) / 2;
        int guiY = (this.height - this.backgroundHeight) / 2;

        final float coreX = guiX + CORE_X + 8f;
        final float coreY = guiY + CORE_Y + 8f;

        int phase = handler.getPhase();
        float pressure01 = Math.min(1f, handler.getPressure() / 1000f);

        int phaseNow = handler.getPhase();
        if (phaseNow != lastPhase) {
            // ENTER pressurizing: server confirmed it started (blood was sufficient)
            if (phaseNow == RitualLoomBlockEntity.PHASE_PRESSURIZING) {
                PressurizeArm.pressTicks = handler.getRecipePressTicks();

                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null) {
                    mc.getSoundManager().play(
                            PositionedSoundInstance.master(
                                    SoundEvent.of(Identifier.of(Suture_n_sorcery.MOD_ID, "ritual_loom_pressurize")),
                                    1.0f
                            )
                    );
                }
            }

            // EXIT pressurizing: server confirmed it stopped/finished
            if (lastPhase == RitualLoomBlockEntity.PHASE_PRESSURIZING
                    && phaseNow != RitualLoomBlockEntity.PHASE_PRESSURIZING) {
                PressControllerInstance.requestFadeCurrent();
            }

            lastPhase = phaseNow;
        }

        // pressBlend crossfades orbit -> pressurize visuals
        boolean holding = (phase == RitualLoomBlockEntity.PHASE_PRESSURIZING);

        // circle with wobble
        if (rippleAge < 18f) {
            drawDistortedRing(ctx, coreX, coreY, rippleAge, 0xFF2A2A);
        }

        int pole = handler.getPoleStrings();

        if (pole <= 0 && visualOrbitCount <= 0 && travelers.isEmpty() && !holding) return;

        ctx.getMatrices().pushMatrix();

        // zoom only affects ritual visuals
        ctx.getMatrices().translate(coreX, coreY);
        float z = 1f + screenZoom;
        ctx.getMatrices().scale(z, z);
        ctx.getMatrices().translate(-coreX, -coreY);

        assert this.client != null;
        assert this.client.world != null;

        // stable time
        float time = this.client.world.getTime() + delta;

        // emission point = center of the TOPMOST pole string sprite
        float[] tip = getPoleTip(guiX, guiY);

        // Phase 1 newly converted strings leave the pole and travel to the core

        float orbitSpeed = 0.00175f + 0.0045f * pressure01;
        if (!travelers.isEmpty()) {
            float orbitR = 18f; // bigger circle around the slot
            float approxLen = 120f; // used to convert 15px to t-length (good enough)

            for (Traveler tr : travelers) {
                // End point is ON THE ORBIT CIRCLE (not center)
                float baseA = (float)(tr.slotIndex * (Math.PI * 2.0 / ORBIT_SLOTS));
                float jA = randSigned(tr.slotIndex, orbitSeed) * 0.12f; // tiny stable jitter per slot
                float endA = baseA + time * orbitSpeed + jA;
                float ex = coreX + (float) Math.cos(endA) * orbitR;
                float ey = coreY + (float) Math.sin(endA) * orbitR;
                float endR = orbitR + slotJitterRadius[tr.slotIndex];

                float ctrlR = orbitR + 22f;
                float cpx = coreX + (float) Math.cos(endA + 1.57f) * ctrlR;
                float cpy = coreY + (float) Math.sin(endA + 1.57f) * ctrlR;

                float segT = TRAVEL_LEN_PX / approxLen;
                float head = Math.min(1f, tr.headT);
                float tail = Math.max(0f, head - segT);

                int travelerRgb = 0xFFFFFF;
                drawWavyBezierSegment(ctx,
                        tip[0], tip[1],
                        cpx, cpy,
                        ex, ey,
                        tail, head,
                        time, tr.phase,
                        .55f, travelerRgb);

                // fade-in the ring arc right at the landing spot during the last 25% of travel
                if (visualOrbitCount < ORBIT_SLOTS && tr.slotIndex == visualOrbitCount) {
                    float fade = smoothstep01((head - 0.75f) / 0.25f);
                    if (fade > 0f) {
                        int alpha = (int)(160 * fade);
                        drawWavyArcSegment(ctx, coreX, coreY, endR, endA, TRAVEL_LEN_PX,
                                0.55f, 0xFFFFFF, slotPhase[tr.slotIndex], alpha);
                    }
                }
            }
        }

        int showCount = Math.min(ORBIT_SLOTS, visualOrbitCount);

        int required = handler.getRecipeRequiredStrings();
        if (required <= 0) required = 0;

        int pullCount = holding ? Math.min(showCount, required) : 0;



        // base orbit radius; expand while holding (pressure + pressBlend)
        float orbitBaseR = 18f;
        float orbitExpandR = orbitBaseR + 10f * pressBlend + 12f * pressure01;
        int pulledSoFar = 0;

        for (int i = 0; i < showCount; i++) {
            boolean pullingThis = holding && shouldPullIndex(i, showCount, pullCount);
            float base = (float)(i * (Math.PI * 2.0 / ORBIT_SLOTS));

            int seed = orbitSeed;
            float jitterScale = Math.min(1f, showCount / 10f);

            float jA = randSigned(i, seed) * 0.22f * jitterScale;
            float jP = randSigned(i, seed + 2) * 2.0f;

            float ang = base + time * orbitSpeed + jA;
            float r = (pullingThis ? orbitBaseR : orbitExpandR) + slotJitterRadius[i];

            float sx = coreX + cos(ang) * r;
            float sy = coreY + sin(ang) * r;

            if (pullingThis) {
                int k = pulledSoFar++;

                float attachA = (float)(k * (Math.PI * 2.0 / pullCount));

                float centerGapR = 1.35f;

                float ax = coreX + (float)Math.cos(attachA) * centerGapR;
                float ay = coreY + (float)Math.sin(attachA) * centerGapR;

                float tx = -(float)Math.sin(ang);
                float ty =  (float)Math.cos(ang);

                float cpx = sx + tx * 22f;
                float cpy = sy + ty * 22f;

                float pull01 = smoothstep01((pressure01 - 0.30f) / 0.50f);

                // never white while pulling to the core
                int pullRgb = lerpRgb(0xB00000, 0xFF2A2A, pressure01);

                drawWavyBezierSegmentSpikeEnd(ctx,
                        sx, sy,
                        cpx, cpy,
                        ax, ay,
                        0f, pull01,
                        time, i * 0.55f + jP,
                        0.60f,
                        0.72f,
                        0.10f,
                        pullRgb);
            } else {
                int alphaBase = 160;
                float r01 = orbitRedness[i];
                int orbitRgb = lerpRgb(0xFFFFFF, 0xB00000, r01);

                drawWavyArcSegment(ctx, coreX, coreY, r, ang, TRAVEL_LEN_PX,
                        0.55f, orbitRgb, i * 0.9f + jP, alphaBase);
            }
        }

        ctx.getMatrices().popMatrix();

    }
    private void drawWavyArcSegment(DrawContext ctx, float cx, float cy, float r,
                                    float centerAngle, float lengthPx,
                                    float thickness, int rgb, float phase, int alphaBase) {
        float halfArc = (lengthPx / r) * 0.5f;
        int steps = 18;
        float prevX = 0, prevY = 0;
        boolean hasPrev = false;

        for (int i = 0; i <= steps; i++) {
            float u = i / (float)steps;
            float a = centerAngle - halfArc + (2f * halfArc * u);

            float bx = cx + (float)Math.cos(a) * r;
            float by = cy + (float)Math.sin(a) * r;

            float nx = bx - cx;
            float ny = by - cy;
            float inv = 1f / (float)Math.max(0.0001, Math.hypot(nx, ny));
            nx *= inv; ny *= inv;

            float amp = 2.0f;
            float wave = (float)Math.sin(u * 10f + renderTime * 0.55f + phase) * amp;

            float x = bx + nx * wave;
            float y = by + ny * wave;

            if (hasPrev) {
                float localThick = thickness * (0.18f + 0.82f * (float)Math.sin(Math.PI * u)); // thin ends, thick middle
                int col = (Math.max(0, alphaBase) << 24) | (rgb & 0x00FFFFFF);
                drawLineQuad(ctx, prevX, prevY, x, y, localThick, col);
            }
            prevX = x; prevY = y; hasPrev = true;
        }
    }
    private void drawWavyBezierSegment(DrawContext ctx,
                                       float sx, float sy,
                                       float cx, float cy,
                                       float ex, float ey,
                                       float t0, float t1,
                                       float time, float phase,
                                       float thickness, int rgb) {
        int steps = 18;
        float prevX = 0, prevY = 0;
        boolean hasPrev = false;

        for (int i = 0; i <= steps; i++) {
            float u = i / (float)steps;
            float t = t0 + (t1 - t0) * u;

            float bx = bez2(sx, cx, ex, t);
            float by = bez2(sy, cy, ey, t);

            float tx = 2f * (1f - t) * (cx - sx) + 2f * t * (ex - cx);
            float ty = 2f * (1f - t) * (cy - sy) + 2f * t * (ey - cy);
            float inv = 1f / (float)Math.max(0.0001, Math.hypot(tx, ty));
            tx *= inv; ty *= inv;

            float nx = -ty, ny = tx;

            float amp = (1f - t) * 2.6f;
            float wave = (float)Math.sin(u * 10f + time * 0.55f + phase) * amp;

            float x = bx + nx * wave;
            float y = by + ny * wave;

            if (hasPrev) {
                int col = (0x90 << 24) | (rgb & 0x00FFFFFF);
                drawLineQuad(ctx, prevX, prevY, x, y, thickness, col);
            }
            prevX = x; prevY = y; hasPrev = true;
        }
    }
    private void drawWavyBezierSegmentSpikeEnd(DrawContext ctx,
                                                float sx, float sy,
                                                float cx, float cy,
                                                float ex, float ey,
                                                float t0, float t1,
                                                float time, float phase,
                                                float baseThickness,
                                                float spikeStartU,
                                                float tipThickness,
                                                int rgb
    ) {
        int steps = 18;
        float prevX = 0, prevY = 0;
        boolean hasPrev = false;

        for (int i = 0; i <= steps; i++) {
            float u = i / (float)steps;
            float t = t0 + (t1 - t0) * u;

            float bx = bez2(sx, cx, ex, t);
            float by = bez2(sy, cy, ey, t);

            float tx = 2f * (1f - t) * (cx - sx) + 2f * t * (ex - cx);
            float ty = 2f * (1f - t) * (cy - sy) + 2f * t * (ey - cy);
            float inv = 1f / (float)Math.max(0.0001, Math.hypot(tx, ty));
            tx *= inv; ty *= inv;

            float nx = -ty, ny = tx;

            float amp = (1f - t) * 2.6f;
            float wave = (float)Math.sin(u * 10f + time * 0.55f + phase) * amp;

            float x = bx + nx * wave;
            float y = by + ny * wave;

            float localThick = baseThickness;
            if (u > spikeStartU) {
                float uu = (u - spikeStartU) / (1f - spikeStartU);
                float f = 1f - uu;
                f = f * f; // quadratic falloff
                localThick = tipThickness + (baseThickness - tipThickness) * f;
            }

            if (hasPrev) {
                int col = (0x90 << 24) | (rgb & 0x00FFFFFF);
                drawLineQuad(ctx, prevX, prevY, x, y, localThick, col);
            }

            prevX = x; prevY = y; hasPrev = true;
        }
    }

    // gpu
    private static GpuTextureView sns$getOutputView(MinecraftClient client) {
        var ov = com.mojang.blaze3d.systems.RenderSystem.outputColorTextureOverride; // :contentReference[oaicite:3]{index=3}
        return ov != null ? ov : client.getFramebuffer().getColorAttachmentView();
    }
    private void sns$postProcess(DrawContext ctx, float tickDelta   ) {
        if (client == null) return;
        if (shockwaveTicks <= 0) return;

        float t = clamp01((shockwaveTicks + tickDelta) / (float) SHOCKWAVE_DURATION_TICKS);

        float radius = (float)Math.pow(t, SHOCKWAVE_RADIUS_POWER);

        float strength = 1f - t;
        strength = strength * strength * strength;

        GpuTextureView srcView = sns$getOutputView(client);
        if (srcView == null) return;

        var srcTex = srcView.texture();
        int srcW = srcTex.getWidth(0);
        int srcH = srcTex.getHeight(0);

        int x1 = this.x;
        int x2 = this.x + this.backgroundWidth;

        int y1 = this.y;
        int y2 = this.y + this.backgroundHeight;

        float sx = srcW / (float) this.width;
        float sy = srcH / (float) this.height;

        float px1 = (x1 + 0.0f) * sx;
        float py1 = (y1 + 0.0f) * sy;
        float px2 = (x2 + 0.0f) * sx;
        float py2 = (y2 + 0.0f) * sy;

        float u1 = clamp01(px1 / srcW);
        float u2 = clamp01(px2 / srcW);
        float v1 = clamp01(py1 / srcH);
        float v2 = clamp01(py2 / srcH);

        // Pack params for shader
        // boom center -> source texture UV space
        float cx = clamp01((shockwaveCenterX * sx) / (float) srcW);
        float cy = clamp01((shockwaveCenterY * sy) / (float) srcH);

        // debug flip stuff
        if (SHOCKWAVE_FLIP_V) {
            float tv1 = 1f - v1;
            float tv2 = 1f - v2;
            v1 = tv1;
            v2 = tv2;
            cy = 1f - cy;
        }

        int pr = Math.clamp((int)(clamp01(cx) * 255f), 0, 255);
        int pg = Math.clamp((int)(clamp01(cy) * 255f), 0, 255);

        shockwaveRadius = radius;
        shockwaveStrength = strength;

        int pb = Math.clamp((int)(clamp01(shockwaveRadius) * 255f), 0, 255);
        int pa = Math.clamp((int)(clamp01(shockwaveStrength) * 255f), 0, 255);

        int packed = (pa << 24) | (pr << 16) | (pg << 8) | pb;

        ((DrawContextInvoker) ctx).sns$drawTexturedQuad(
                ModShader.SHOCKWAVE,
                srcView,
                x1, y1, x2, y2,
                u1, u2, v1, v2,
                packed
        );
    }

    // press and hold button class
    private static final class PressurizeHoldButton extends ClickableWidget {
        private final RitualLoomScreen screen;
        private boolean holding = false;

        public boolean isHolding() {
            return holding;
        }

        void startHolding() {
            holding = true;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.interactionManager != null) {
                client.interactionManager.clickButton(screen.handler.syncId, RitualLoomScreenHandler.BTN_PRESSURIZE_START);
            }
        }

        void stopHolding() {
            holding = false;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.interactionManager != null) {
                client.interactionManager.clickButton(screen.handler.syncId, RitualLoomScreenHandler.BTN_PRESSURIZE_STOP);
            }
        }

        private PressurizeHoldButton(int x, int y, int w, int h, Text msg, RitualLoomScreen screen) {
            super(x, y, w, h, msg);
            this.screen = screen;
        }

        public void forceReleaseIfHolding() {
            if (holding) stopHolding();
        }

        // load pressurize button and make the button
        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            final int TEX_W = 69;
            final int TEX_H = 21;

            int x = getX();
            int y = getY();

            int frameCol = this.active ? 0xFFFFFFFF : 0xAAFFFFFF;

            ctx.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    BUTTON_FRAME,
                    x, y,
                    0f, 0f,
                    this.width, this.height,
                    TEX_W, TEX_H,
                    frameCol
            );

            String label = getMessage().getString();

            int topCol = 0xFFFCDECB; // fcdecb
            int botCol = 0xFFEBB099; // ebb099

            int textW = screen.textRenderer.getWidth(label);

            int tx = x + (this.width - textW) / 2 + 1; // +1 for adjusting text pos
            int ty = y + (this.height - 8) / 2;

            ctx.drawText(screen.textRenderer, Text.literal(label), tx, ty + 1, botCol, false);

            if (!holding) {
                ctx.drawText(screen.textRenderer, Text.literal(label), tx, ty, topCol, false);
            }
        }

        @Override
        protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
            builder.put(net.minecraft.client.gui.screen.narration.NarrationPart.TITLE, getMessage());
        }
    }

    //string travellers class
    private static final class Traveler {
        public int slotIndex;
        float headT;         // 0..1 along curve
        float speed;         // how fast headT advances
        float phase;         // wave phase offset
        boolean arrived;
    }
    private void spawnTraveler() {
        if (this.client == null) return;
        if (travelers.size() >= MAX_TRAVELERS) travelers.remove(0);

        int slot;
        if (visualOrbitCount < ORBIT_SLOTS) {
            slot = visualOrbitCount; // next slot that will appear
        } else {
            slot = ringInsertCursor;
            ringInsertCursor = (ringInsertCursor + 1) % ORBIT_SLOTS;
        }

        Traveler tr = new Traveler();
        tr.headT = 0f;
        tr.speed = 0.018f;
        tr.slotIndex = slot;
        tr.phase = (float)(Math.random() * 10.0);
        tr.arrived = false;
        travelers.add(tr);
    }

    //helpers
    private static float clamp01(float v) {
        return v < 0f ? 0f : (Math.min(v, 1f));

    }
    private int lerpRgb(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        int rr = (int)(ar + (br - ar) * t);
        int rg = (int)(ag + (bg - ag) * t);
        int rb = (int)(ab + (bb - ab) * t);
        return (rr << 16) | (rg << 8) | rb;
    }
    private static float bez2(float a, float b, float c, float t) {
        float it = 1f - t;
        return it * it * a + 2f * it * t * b + t * t * c;
    }
    private static float randSigned(int i, int seed) {
        int x = i * 0x1f1f1f1f ^ seed * 0x9e3779b9;
        x ^= (x >>> 16);
        x *= 0x7feb352d;
        x ^= (x >>> 15);
        x *= 0x846ca68b;
        x ^= (x >>> 16);
        return ((x & 0x7fffffff) / (float)0x7fffffff) * 2f - 1f;
    }
    private float[] getPoleTip(int guiX, int guiY) {
        int strings = handler.getPoleStrings();

        int meterX = guiX + STRING_X;
        int meterY = guiY + STRING_Y;

        int maxSprites = 11;
        int sprites = 0;
        if (strings > 0) {
            sprites = (strings * maxSprites + RitualLoomBlockEntity.MAX_STRINGS - 1) / RitualLoomBlockEntity.MAX_STRINGS;
            sprites = Math.min(sprites, maxSprites);
        }

        int baseX = meterX + (METER_W - STRING_TILE_W) / 2;
        int baseY = meterY + METER_H - STRING_TILE_H;

        int i = Math.max(0, sprites - 1);
        float x = baseX + STRING_TILE_W * 0.5f;
        float y = baseY - i * STRING_TILE_H + STRING_TILE_H * 0.5f;
        return new float[]{x, y};
    }
    private boolean isHematicCatalyzer(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return Registries.ITEM.getId(stack.getItem()).equals(HematicCatalyzer.HEMATIC_CATALYZER_ID);
    }
    private static float smoothstep01(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }
    private boolean shouldPullIndex(int i, int showCount, int pullCount) {
        if (pullCount <= 0) return false;
        if (pullCount >= showCount) return true;

        // Evenly spaced selection across [0, showCount)
        double step = (double) showCount / (double) pullCount;
        for (int k = 0; k < pullCount; k++) {
            int idx = (int) Math.floor(k * step);
            if (idx == i) return true;
        }
        return false;
    }
    private void applyCursorGravity() {
        if (this.client == null) return;
        if (pressButton != null && pressButton.isHovered()) return;

        int guiX = (this.width - this.backgroundWidth) / 2;
        int guiY = (this.height - this.backgroundHeight) / 2;

        double coreGuiX = guiX + CORE_X + 8.0;
        double coreGuiY = guiY + CORE_Y + 8.0;

        // convert GUI coords -> window pixel coords
        var window = this.client.getWindow();
        double scale = window.getScaleFactor();

        double targetX = coreGuiX * scale;
        double targetY = coreGuiY * scale;

        // read current GLFW cursor position (already in window pixel coords)
        long handle = window.getHandle();
        double[] cx = new double[1];
        double[] cy = new double[1];
        org.lwjgl.glfw.GLFW.glfwGetCursorPos(handle, cx, cy);

        double dx = targetX - cx[0];
        double dy = targetY - cy[0];

        double dist2 = dx * dx + dy * dy;

        // Only pull when within a radiu
        double maxR = 40.0 * scale;
        if (dist2 > maxR * maxR) return;

        // Smooth pull strength
        double dist = Math.sqrt(dist2);
        double strength = 1.0 - (dist / maxR);
        if (strength <= 0) return;
        strength = strength * strength;

        double step = (0.35 + 1.35 * strength) * scale; // pixels per frame

        double nx = (dist > 0.0001) ? (dx / dist) : 0.0;
        double ny = (dist > 0.0001) ? (dy / dist) : 0.0;

        double newX = cx[0] + nx * step;
        double newY = cy[0] + ny * step;

        org.lwjgl.glfw.GLFW.glfwSetCursorPos(handle, newX, newY);
    }

    /* DEBUG CLICK FX
    private void triggerBoomAtScreenPx(double mouseX, double mouseY) {
        // If mouseX/mouseY are GUI-space, convert to screen-space:
        // normalize using current screen size (this.width/this.height) is safest

        boomCenterX = (float) mouseX;   // pixels, not 0..1
        boomCenterY = (float) (this.height - mouseY); // <-- invert Y

        boomRadius = 0.0f;
        boomStrength = 1.0f;
        boomTicks = 1;
    }
    */
}