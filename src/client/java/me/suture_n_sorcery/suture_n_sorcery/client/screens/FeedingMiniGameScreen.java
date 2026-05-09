package me.suture_n_sorcery.suture_n_sorcery.client.screens;

import me.suture_n_sorcery.suture_n_sorcery.network.HematicFeedPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public final class FeedingMiniGameScreen extends Screen {
    // Rope shape / feel
    private static final int NODES = 65;          // more = smoother curves
    private static final float SEG_LEN = 4.0f;    // px per segment => total length ~ (NODES-1)*SEG_LEN
    private static final int SOLVER_ITERS = 6;    // constraint iterations per frame
    private static final float DAMPING = 0.92f;   // 0..1 (lower = more “sticky”)
    // 0..1 (higher = less lag)
    private static final float BOB_R = 4.0f; // bob collision radius vs obstacles

    private final boolean[] segLeftHit  = new boolean[CUT_SEGMENTS];
    private final boolean[] segRightHit = new boolean[CUT_SEGMENTS];

    private static final float PULL_RESERVE = 40.0f; // px reserved just to pull tight at the end

    // ---- Wound (Option C) ----
    private static final float CUT_ZONE_R = 100.0f;      // stay within 100px of center
    private static final int   CUT_SEGMENTS = 9;         // number of stitchable sections
    private static final float CUT_STEP = 18.0f;         // spacing between sections
    private static final float CUT_BASE_GAP = 26.0f;     // initial gap between edges
    private static final float EDGE_JITTER = 6.0f;       // jaggedness
    private static final float HOOK_R = 9.0f;            // how close bob must be to hook

    private static final float MISS_PENALTY = 10.0f;

    // seam center points
    private final float[] seamX = new float[CUT_SEGMENTS];
    private final float[] seamY = new float[CUT_SEGMENTS];

    private final ArrayList<StitchPoint> stitchPath = new ArrayList<>();

    // base edge offsets (jagged)
    private final float[] leftOffX = new float[CUT_SEGMENTS];
    private final float[] leftOffY = new float[CUT_SEGMENTS];
    private final float[] rightOffX = new float[CUT_SEGMENTS];
    private final float[] rightOffY = new float[CUT_SEGMENTS];

    // 0..1 closure per segment
    private final float[] closeT = new float[CUT_SEGMENTS];

    private float endLockSX, endLockSY; // lerp start
    // Render
    private static final float ROPE_THICK = 0.3f;
    private static final int ROPE_COLOR = 0xFFB12900;
    private static final int BOB_COLOR  = 0xFF66CCFF;
    private static final int ANC_COLOR  = 0xFFCC3333;
    private static final int HIT_BALL_COLOR = 0xFF22FF66;
    private static final int HIT_BALL_R = 4;

    private int freeSegCurrent = NODES - 1; // how many segments are "free" (usable)
    private int freeSegTarget  = NODES - 1;

    private int computeFreeSegTarget() {
        return Math.max(0, Math.min(NODES - 1, (int) (remainingThread() / SEG_LEN)));
    }

    private static final float ROPE_MAX = (NODES - 1) * SEG_LEN; // physical rope capacity
    private static final float THREAD_MARGIN = 1.35f;            // allow some waste/misses

    private int ropeEndSeg = -1;
    private int ropeEndSide = -1;

    private float threadMax = ROPE_MAX; // computed per generated cut
    private float usedThread = 0.0f;
    private float endLockX, endLockY;     // current constraint point for rope end
    private float endLockTX, endLockTY;   // target constraint (last anchor)
    private float endLockAlpha = 1.0f;    // 0..1 transition progress
    private static final float END_LOCK_SPEED = 0.25f; // how fast it threads to the new anchor

    private float lastStitchDrawT = 1.0f; // 0..1 progress for newest segment

    private final ArrayList<Anchor> anchorMarks = new ArrayList<>();

    private float remainingThread() {
        return Math.max(0.0f, threadMax - usedThread);
    }

    // “Threading” behavior
    // how much stretch imbalance before shifting pin

    private final Screen parent;

    // Rope node positions + previous positions (verlet)
    private final float[] x = new float[NODES];
    private final float[] y = new float[NODES];
    private final float[] px = new float[NODES];
    private final float[] py = new float[NODES];

    // Bob (node 0 is forced to bob position)
    private float bobX, bobY;
    private float targetX, targetY;

    private boolean timingActive = false;
    private float timingX, timingY;

    private static final int TIMING_TOTAL = 42;
    private static final float TIMING_R0 = 34.0f;
    private static final float TIMING_R1 = 2.0f;
    private static final float TIMING_TARGET_R = HOOK_R;
    private static final float TIMING_WINDOW = 2.0f;
    private boolean chainMode = false;

    // --- Hematic stage -> nub budget ---
    private int hematicPct = 0;     // 0..99 (or 0..100)
    private int stageIndex = 0;     // 0,1,2
    private int stageNubs = 6;      // 6 / 12 / 18

    private int hitFlashTicks = 0;
    private int missFlashTicks = 0;
    private float flashX = 0, flashY = 0;

    private static final int FLASH_TICKS = 10;
    private static final float ARM_DIST = HOOK_R * 1.9f;   // must get this close to start timer
    private static final float HOLD_DIST = HOOK_R * 2.4f;  // if you leave beyond this, pause timer

    // current required target after the run starts
    private int zigSeg = CUT_SEGMENTS - 1; // starts at bottom
    private int zigSide = 0;               // will be set by first click (0 left / 1 right)

    // when true, timing circle is allowed to be shown
    private boolean timingEnabled = false;

    // --- Nub queue (deterministic zigzag) ---
    private record Nub(int seg, int side) {} // side: 0=left, 1=right
    private final ArrayList<Nub> nubSeq = new ArrayList<>();
    private int nubIndex = 0;

    // States
    private enum SutState { STITCHING, PULL_TO_CLOSE, CLOSING, DONE }
    private SutState sutState = SutState.STITCHING;

    private boolean timingArmed = true;
    private int timingTicks = TIMING_TOTAL;
    // Close animation
    private float closeAnimT = 0.0f; // 0..1
    private static final float PULL_START_TENSION = 0.92f;  // how taut to start closing
    private static final float CLOSE_SPEED = 0.06f;         // per tick at full tension

    private boolean sentResult = false;
    private int catalystHandOrdinal = 0; // 0 main, 1 off (set this when you open the screen)

    // Anchors (each anchor is a fixed point + a pinned node index that can “thread”)
    private final ArrayList<Anchor> anchors = new ArrayList<>();

    private record Anchor(float ax, float ay, int pinIndex) {} // pinIndex slides along the rope
    private record Obstacle(float x, float y, float r) {}
    private record StitchPoint(float x, float y, int side, int seg, boolean hit) {}

    private final ArrayList<Obstacle> obstacles = new ArrayList<>();


    public FeedingMiniGameScreen(Screen parent, int pct, int catalystHandOrdinal) {
        super(Text.literal("Feeding Minigame"));
        this.parent = parent;
        this.hematicPct = Math.max(0, Math.min(99, pct));
        this.catalystHandOrdinal = catalystHandOrdinal;
    }

    public FeedingMiniGameScreen setHematicPct(int pct) {
        hematicPct = Math.max(0, Math.min(99, pct));
        int newStage = stageFromPct(hematicPct);
        if (newStage != stageIndex) {
            stageIndex = newStage;
            stageNubs = (stageIndex == 0) ? 6 : (stageIndex == 1) ? 12 : 18;

            buildNubSequence();
            recomputeThreadBudgetForCut();
            syncTargetFromNubIndex();
        }
        return this;
    }

    private int stageFromPct(int pct) {
        if (pct <= 33) return 0;     // Stage 1
        if (pct <= 66) return 1;     // Stage 2
        return 2;                    // Stage 3 (67..99)
    }

    @Override
    protected void init() {
        anchors.clear();
        anchorMarks.clear();
        stitchPath.clear();
        obstacles.clear();

        Arrays.fill(segLeftHit, false);
        Arrays.fill(segRightHit, false);

        timingEnabled = false;
        timingActive = false;
        timingArmed = false;
        chainMode = false;
        timingTicks = 0;

        hitFlashTicks = 0;
        missFlashTicks = 0;
        flashX = flashY = 0;

        zigSeg = CUT_SEGMENTS - 1;
        zigSide = 0;

        bobX = this.width * 0.5f;
        bobY = this.height * 0.5f;
        targetX = bobX;
        targetY = bobY;

        for (int i = 0; i < NODES; i++) {
            float ix = bobX - i * SEG_LEN;
            float iy = bobY;
            x[i] = px[i] = ix;
            y[i] = py[i] = iy;
        }

        endLockX = x[NODES - 1];
        endLockY = y[NODES - 1];
        endLockTX = endLockX;
        endLockTY = endLockY;
        endLockSX = endLockX;
        endLockSY = endLockY;
        endLockAlpha = 1.0f;

        usedThread = 0.0f;
        freeSegCurrent = freeSegTarget = NODES - 1;
        lastStitchDrawT = 1.0f;

        generateCut();
        setHematicPct(hematicPct); // if hematicPct is already set externally
        buildNubSequence();
        recomputeThreadBudgetForCut();
        syncTargetFromNubIndex();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_H) {
            obstacles.add(new Obstacle(targetX, targetY, 18.0f));
            return true; // consumed
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        assert this.client != null;
        sendResult(false);
        this.client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);

        if (sutState != SutState.STITCHING) return true;
        if (nubIndex < 0 || nubIndex >= nubSeq.size()) return true;

        Nub n = nubSeq.get(nubIndex);
        float tx = edgeX(n.seg, n.side);
        float ty = edgeY(n.seg, n.side);

        boolean inRange = dist(bobX, bobY, tx, ty) <= HOOK_R;

        // If you clicked OUTSIDE the nub circle: do NOT skip.
        if (!inRange) {
            // If you still want early clicks to waste thread, keep this:
            if (!spendThreadOnMiss()) {
                enterPullToClose();
            }

            // Visual feedback (optional)
            missFlashTicks = FLASH_TICKS;
            flashX = tx;
            flashY = ty;

            return true;
        }

        // From here: click is INSIDE the nub circle
        boolean ok = timingActive && timingSuccess();

        if (!ok) {
            // Wrong timing (or early) INSIDE the circle => waste thread AND advance
            if (!spendThreadOnMiss()) {
                enterPullToClose();
                return true;
            }

            missFlashTicks = FLASH_TICKS;
            flashX = tx;
            flashY = ty;

            nubIndex++;
            syncTargetFromNubIndex();

            if (nubIndex >= nubSeq.size()) {
                sutState = SutState.PULL_TO_CLOSE;
                timingEnabled = false;
                timingActive = false;
                timingArmed = false;
                return true;
            }

            if (!chainMode) chainMode = true;

            timingActive = true;
            timingArmed = false;
            timingTicks = TIMING_TOTAL;

            return true;
        }

        // Success => place stitch point
        boolean placed = registerAttemptPoint(n.seg, n.side, true);
        if (!placed) {
            enterPullToClose();
            return true;
        }

        hitFlashTicks = FLASH_TICKS;
        flashX = tx;
        flashY = ty;

        if (n.side == 0) segLeftHit[n.seg] = true;
        else segRightHit[n.seg] = true;

        nubIndex++;
        syncTargetFromNubIndex();

        if (!chainMode) chainMode = true;

        if (nubIndex >= nubSeq.size()) {
            sutState = SutState.PULL_TO_CLOSE;
            timingEnabled = false;
            timingActive = false;
            timingArmed = false;
            return true;
        }

        timingActive = true;
        timingArmed = false;
        timingTicks = TIMING_TOTAL;

        return true;
    }

    private void drawTargetAndFeedback(DrawContext ctx) {
        // Target marker (always visible after start)
        if (timingEnabled && zigSeg >= 0) {
            // fixed "hit ring" (what you want the shrinking ring to match)
            drawCircle(ctx, timingX, timingY, TIMING_TARGET_R, 0x6600FF00);

            // slack band (visualize window)
            drawCircle(ctx, timingX, timingY, TIMING_TARGET_R + TIMING_WINDOW, 0x2200FF00);
            drawCircle(ctx, timingX, timingY, Math.max(1.0f, TIMING_TARGET_R - TIMING_WINDOW), 0x2200FF00);

            if (timingActive) drawTiming(ctx);
        }

        // Hit flash (green)
        if (hitFlashTicks > 0) {
            float a = hitFlashTicks / (float) FLASH_TICKS;
            int col = ( ((int)(a * 200) & 255) << 24) | 0x0000FF00;
            drawCircle(ctx, flashX, flashY, 14.0f, col);
        }

        // Miss flash (red)
        if (missFlashTicks > 0) {
            float a = missFlashTicks / (float) FLASH_TICKS;
            int col = ( ((int)(a * 180) & 255) << 24) | 0x00FF2222;
            drawCircle(ctx, flashX, flashY, 14.0f, col);
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (lastStitchDrawT < 1.0f) {
            lastStitchDrawT = Math.min(1.0f, lastStitchDrawT + 0.25f);
        }

        if (hitFlashTicks > 0) hitFlashTicks--;
        if (missFlashTicks > 0) missFlashTicks--;

        // Closing anim update
        if (sutState == SutState.CLOSING) {
            float tension = computePullTension();
            closeAnimT = Math.min(1.0f, closeAnimT + CLOSE_SPEED * Math.max(0.35f, tension));

            float t = closeAnimT;
            t = t * t * (3.0f - 2.0f * t); // smoothstep

            for (int i = 0; i < CUT_SEGMENTS; i++) {
                // ✅ only segments stitched on BOTH sides close
                closeT[i] = (segLeftHit[i] && segRightHit[i]) ? t : 0.0f;
                syncRopeEndToClosingEdge();
            }

            if (closeAnimT >= 1.0f) {
                sutState = SutState.DONE;

                // success = you stitched every required nub
                sendResult(stitchPath.size() >= nubSeq.size());
            }
            return;
        }
        // Stitching timing
        if (sutState != SutState.STITCHING) return;
        if (nubIndex < 0 || nubIndex >= nubSeq.size()) return;

        Nub n = nubSeq.get(nubIndex);
        timingX = edgeX(n.seg, n.side);
        timingY = edgeY(n.seg, n.side);

        if (!chainMode) {
            // Pre-chain: old behavior (only starts when you approach)
            float dToTarget = dist(bobX, bobY, timingX, timingY);

            if (timingArmed && !timingActive) {
                if (dToTarget <= ARM_DIST) {
                    timingActive = true;
                    timingTicks = TIMING_TOTAL;
                }
                return;
            }

            if (timingActive && dToTarget > HOLD_DIST) {
                timingActive = false;
                timingArmed = true;
                timingTicks = TIMING_TOTAL;
                return;
            }

            if (timingActive) {
                timingTicks--;
                if (timingTicks <= 0) {
                    // miss (no anchor), just reset
                    spendThreadOnMiss();

                    missFlashTicks = FLASH_TICKS;
                    flashX = timingX;
                    flashY = timingY;

                    timingActive = false;
                    timingArmed = true;
                    timingTicks = TIMING_TOTAL;
                }
            }
            return;
        }

        // Chain mode: timer ALWAYS runs, regardless of mouse position.
        // If it expires => advance to next nub, no anchor added.
        if (!timingActive) {
            timingActive = true;
            timingTicks = TIMING_TOTAL;
        }

        timingTicks--;
        if (timingTicks <= 0) {
            spendThreadOnMiss();

            missFlashTicks = FLASH_TICKS;
            flashX = timingX;
            flashY = timingY;

            nubIndex++;
            syncTargetFromNubIndex();

            if (nubIndex >= nubSeq.size()) {
                sutState = SutState.PULL_TO_CLOSE;
                timingEnabled = false;
                timingActive = false;
                timingArmed = false;
                return;
            }

            timingActive = true;
            timingTicks = TIMING_TOTAL;
        }
    }

    private float estimateNubSeqRequired() {
        if (nubSeq.isEmpty()) return 0.0f;

        Nub first = nubSeq.get(0);
        float px0 = edgeX(first.seg, first.side);
        float py0 = edgeY(first.seg, first.side);

        float sum = 0.0f;

        for (int i = 1; i < nubSeq.size(); i++) {
            Nub n = nubSeq.get(i);
            float nx = edgeX(n.seg, n.side);
            float ny = edgeY(n.seg, n.side);

            // match spendThreadForNewPoint(): dist + 2.0f :contentReference[oaicite:6]{index=6}
            sum += dist(px0, py0, nx, ny) + 2.0f;

            px0 = nx;
            py0 = ny;
        }

        return sum;
    }

    private void buildNubSequence() {
        nubSeq.clear();
        nubIndex = 0;
        sutState = SutState.STITCHING;

        // stageNubs is 6/12/18 -> segmentsToUse is 3/6/9
        int segmentsToUse = Math.max(1, Math.min(CUT_SEGMENTS, stageNubs / 2));

        // choose a consistent order to avoid “square” vertical hops:
        // L(seg) -> R(seg) -> L(seg-1) -> R(seg-1) ...
        int firstSide = 0; // 0 = left first. set 1 if you want right first

        int startSeg = CUT_SEGMENTS - 1;
        int endSeg = CUT_SEGMENTS - segmentsToUse;

        for (int seg = startSeg; seg >= endSeg; seg--) {
            nubSeq.add(new Nub(seg, firstSide));
            nubSeq.add(new Nub(seg, 1 - firstSide));
        }

        timingActive = false;
        timingArmed = true;
        timingTicks = TIMING_TOTAL;

        closeAnimT = 0.0f;
        chainMode = false;

        syncTargetFromNubIndex();
    }

    private void clampBobIfTaut() {
        if (anchors.isEmpty()) return;

        Anchor last = anchors.get(anchors.size() - 1);
        float maxDist = remainingThread();

        float dx = bobX - last.ax;
        float dy = bobY - last.ay;
        float d2 = dx * dx + dy * dy;
        if (d2 < 0.000001f) return;

        float d = (float) Math.sqrt(d2);
        if (d > maxDist && maxDist > 0.0f) {
            float s = maxDist / d;
            bobX = last.ax + dx * s;
            bobY = last.ay + dy * s;
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        targetX = mouseX + 0.5f;
        targetY = mouseY + 0.5f;

        float[] t = pushOutOfObstacles(targetX, targetY, BOB_R);
        targetX = t[0];
        targetY = t[1];

        ctx.fill(0, 0, this.width, this.height, 0xAA000000);

        updateBob();

        float[] p = pushOutOfObstacles(bobX, bobY, BOB_R);
        bobX = p[0];
        bobY = p[1];

        // ✅ clamp BEFORE rope sim so rope uses the same bob position you draw
        clampBobIfTaut();
        p = pushOutOfObstacles(bobX, bobY, BOB_R);
        bobX = p[0];
        bobY = p[1];

        // keep marker glued to current nub
        syncTargetFromNubIndex();
        stepRopePhysics();

        if (sutState == SutState.PULL_TO_CLOSE) {
            if (hasAnyStitchedSegment()) {
                float tension = computePullTension();
                if (tension >= PULL_START_TENSION) {
                    sutState = SutState.CLOSING;
                    closeAnimT = 0.0f;
                }
            }
        }

        drawCut(ctx);
        drawTargetAndFeedback(ctx); // move this ABOVE
        drawStitchPath(ctx);        // so stitches render on top

        drawTargetAndFeedback(ctx);
        drawClosureMeter(ctx);

        drawRope(ctx);
        drawMarkers(ctx);
        drawStringLeftMeter(ctx);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private boolean timingSuccess() {
        float t = 1.0f - (timingTicks / (float) TIMING_TOTAL);
        float r = lerp(TIMING_R0, TIMING_R1, t);
        return Math.abs(r - TIMING_TARGET_R) <= TIMING_WINDOW;
    }

    private void drawTiming(DrawContext ctx) {
        if (!timingActive) return;

        float t = 1.0f - (timingTicks / (float) TIMING_TOTAL);
        float r = lerp(TIMING_R0, TIMING_R1, t);

        drawCircle(ctx, timingX, timingY, r, 0x66FFFFFF);
    }


    private boolean hasAnyStitchedSegment() {
        for (int i = 0; i < CUT_SEGMENTS; i++) {
            if (segLeftHit[i] && segRightHit[i]) return true;
        }
        return false;
    }

    private void sendResult(boolean success) {
        if (sentResult) return;
        sentResult = true;

        int hits = stitchPath.size();   // successful stitches you actually placed
        int total = nubSeq.size();      // required nubs for this stage

        ClientPlayNetworking.send(new HematicFeedPayload(
                catalystHandOrdinal,
                hits,
                total,
                success
        ));
    }

    private void enterPullToClose() {
        if (sutState != SutState.STITCHING) return;
        if (hasAnyStitchedSegment()) return; // nothing to tighten yet

        sutState = SutState.PULL_TO_CLOSE;

        // stop timing/targets
        timingEnabled = false;
        timingActive = false;
        timingArmed = false;
        zigSeg = -1;

        // guarantee some slack for the "pull" gesture
        float rem = threadMax - usedThread;
        if (rem < PULL_RESERVE) {
            threadMax = usedThread + PULL_RESERVE;
            freeSegTarget = computeFreeSegTarget();
        }
    }

    private float computePullTension() {
        if (anchors.isEmpty()) return 0.0f;

        Anchor last = anchors.get(anchors.size() - 1);
        float max = Math.max(remainingThread(), 0.0001f);
        float d = dist(bobX, bobY, last.ax, last.ay);

        return clamp01(d / max); // 1.0 when fully taut
    }

    private float bobLagFactor() {
        float t = hematicPct / 99.0f;
        return lerp(0.24f, 0.12f, t);
    }

    private void updateBob() {
        float lag = bobLagFactor();

        bobX += (targetX - bobX) * lag;
        bobY += (targetY - bobY) * lag;

        float[] p = pushOutOfObstacles(bobX, bobY, BOB_R);
        bobX = p[0];
        bobY = p[1];
    }

    private boolean spendThreadForNewPoint(float nx, float ny) {
        if (stitchPath.isEmpty()) return true;

        StitchPoint last = stitchPath.get(stitchPath.size() - 1);
        float cost = dist(last.x, last.y, nx, ny);

        // optional: add a small constant per stitch (knot/handling cost)
        cost += 2.0f;

        if (usedThread + cost > threadMax) return false;
        usedThread += cost;
        return true;
    }

    private boolean spendThreadOnMiss() {
        if (usedThread + MISS_PENALTY > threadMax) return false;
        usedThread += MISS_PENALTY;

        // keep collapse target consistent
        freeSegTarget = computeFreeSegTarget();
        return true;
    }

    private void addAnchorAt(float ax, float ay) {
        anchorMarks.add(new Anchor(ax, ay, -1));
    }

    private void retargetRopeEndTo(float ax, float ay) {
        anchors.clear();
        anchors.add(new Anchor(ax, ay, -1));

        freeSegTarget = computeFreeSegTarget();

        // ✅ lock tail instantly to the new stitch point
        endLockX = endLockTX = ax;
        endLockY = endLockTY = ay;
        endLockSX = ax;
        endLockSY = ay;
        endLockAlpha = 1.0f;

        // ✅ hard-pin the simulated tail node too (prevents drift / “not fixed” look)
        x[NODES - 1] = px[NODES - 1] = ax;
        y[NODES - 1] = py[NODES - 1] = ay;
    }

    private void drawCut(DrawContext ctx) {
        // draw edges as little “nodes” + connecting strips
        int edgeCol = 0xFFAA3333;
        int seamCol = 0x66330000;

        // seam fill (simple)
        for (int i = 0; i < CUT_SEGMENTS - 1; i++) {
            int x0 = (int) seamX[i];
            int y0 = (int) seamY[i];
            int x1 = (int) seamX[i + 1];
            int y1 = (int) seamY[i + 1];
            drawThickLine(ctx, x0, y0, x1, y1, 1.2f, seamCol);
        }

        // edges
        for (int i = 0; i < CUT_SEGMENTS; i++) {
            float lx = edgeX(i, 0), ly = edgeY(i, 0);
            float rx = edgeX(i, 1), ry = edgeY(i, 1);

            drawNodule(ctx, lx, ly, 3, edgeCol);
            drawNodule(ctx, rx, ry, 3, edgeCol);

            // show green hit balls on nubs that have been hit
            if (segLeftHit[i]) {
                drawNodule(ctx, lx, ly, HIT_BALL_R, HIT_BALL_COLOR);
            }
            if (segRightHit[i]) {
                drawNodule(ctx, rx, ry, HIT_BALL_R, HIT_BALL_COLOR);
            }

            // if stitched, draw a bridge line (looks like a tightened stitch)
            if (segLeftHit[i] && segRightHit[i]) {
                drawThickLine(ctx, lx, ly, rx, ry, 1.0f, 0xFFDD8888);
            }
        }

        // pending hook highlight
        if (timingEnabled && zigSeg >= 0) {
            float hx = edgeX(zigSeg, zigSide);
            float hy = edgeY(zigSeg, zigSide);
            drawNodule(ctx, hx, hy, 5, 0xFFFFFFAA);
        }
    }

    private void drawNodule(DrawContext ctx, float x, float y, int r, int col) {
        int ix = (int) x;
        int iy = (int) y;
        ctx.fill(ix - r, iy - r, ix + r + 1, iy + r + 1, col);
    }

    private void syncTargetFromNubIndex() {
        if (sutState != SutState.STITCHING || nubIndex < 0 || nubIndex >= nubSeq.size()) {
            timingEnabled = false;
            zigSeg = -1;
            return;
        }

        timingEnabled = true;

        Nub n = nubSeq.get(nubIndex);
        zigSeg = n.seg;
        zigSide = n.side;

        timingX = edgeX(zigSeg, zigSide);
        timingY = edgeY(zigSeg, zigSide);
    }

    private boolean registerAttemptPoint(int seg, int side, boolean hit) {
        if (!hit) return spendThreadOnMiss();

        float ex = edgeX(seg, side);
        float ey = edgeY(seg, side);

        ropeEndSeg = seg;
        ropeEndSide = side;

        if (!spendThreadForNewPoint(ex, ey)) {  // :contentReference[oaicite:4]{index=4}
            enterPullToClose();                 // ✅ instead of “nothing happens”
            return false;
        }

        stitchPath.add(new StitchPoint(ex, ey, side, seg, true));
        if (side == 0) segLeftHit[seg] = true;
        else segRightHit[seg] = true;
        lastStitchDrawT = 0.0f;

        addAnchorAt(ex, ey);
        retargetRopeEndTo(ex, ey);
        return true;
    }

    private void syncRopeEndToClosingEdge() {
        if (ropeEndSeg < 0) return;

        float ax = edgeX(ropeEndSeg, ropeEndSide);
        float ay = edgeY(ropeEndSeg, ropeEndSide);

        // keep rope end hard-attached to the closing edge
        endLockX = endLockTX = ax;
        endLockY = endLockTY = ay;
        endLockAlpha = 1.0f;

        x[NODES - 1] = px[NODES - 1] = ax;
        y[NODES - 1] = py[NODES - 1] = ay;

        if (!anchors.isEmpty()) {
            anchors.set(0, new Anchor(ax, ay, -1));
        }
    }

    private void drawClosureMeter(DrawContext ctx) {
        float sum = 0.0f;
        for (int i = 0; i < CUT_SEGMENTS; i++) sum += closeT[i];
        float avg = sum / (float) CUT_SEGMENTS;

        int mx = 10, my = this.height - 20;
        int w = 120, h = 8;

        ctx.fill(mx - 1, my - 1, mx + w + 1, my + h + 1, 0xFF111111);
        ctx.fill(mx, my, mx + w, my + h, 0xFF2A2A2A);
        ctx.fill(mx, my, mx + (int)(w * clamp01(avg)), my + h, 0xB1FF0000);

        ctx.drawText(this.textRenderer, Text.literal("Closure " + (int)(avg * 100f) + "%"), mx, my - 10, 0xFFFFFFFF, true);
    }

    private void generateCut() {
        Random rng = new Random(System.nanoTime());

        float cx = this.width * 0.5f;
        float cy = this.height * 0.5f;

        // Place the cut vertically-ish and keep it within 100px of center
        float totalLen = (CUT_SEGMENTS - 1) * CUT_STEP;
        float y0 = cy - totalLen * 0.5f;

        for (int i = 0; i < CUT_SEGMENTS; i++) {
            float y = y0 + i * CUT_STEP;

            // seam center, slight horizontal wobble
            float x = cx + (rng.nextFloat() * 2f - 1f) * (CUT_ZONE_R * 0.25f);

            seamX[i] = x;
            seamY[i] = y;

            // jagged offsets for each edge
            leftOffX[i]  = -CUT_BASE_GAP * 0.5f + (rng.nextFloat() * 2f - 1f) * EDGE_JITTER;
            rightOffX[i] =  CUT_BASE_GAP * 0.5f + (rng.nextFloat() * 2f - 1f) * EDGE_JITTER;

            leftOffY[i]  = (rng.nextFloat() * 2f - 1f) * EDGE_JITTER;
            rightOffY[i] = (rng.nextFloat() * 2f - 1f) * EDGE_JITTER;

            closeT[i] = 0.0f;
        }
    }

    private float edgeX(int seg, int side) {
        float t = clamp01(closeT[seg]);
        float x = seamX[seg];

        float base;
        if (side == 0) { // left
            base = x + leftOffX[seg];
        } else {         // right
            base = x + rightOffX[seg];
        }
        return lerp(base, x, t);
    }

    private float edgeY(int seg, int side) {
        float t = clamp01(closeT[seg]);
        float y = seamY[seg];

        float base;
        if (side == 0) {
            base = y + leftOffY[seg];
        } else {
            base = y + rightOffY[seg];
        }
        return lerp(base, y, t * 0.35f);  // y closes less than x (looks more natural)
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private void stepRopePhysics() {
        // Decide whether we are currently "threading" into a new stitch
        boolean stitched = !anchors.isEmpty();
        boolean threading = stitched && (endLockAlpha < 1.0f);

        // During threading: keep full rope length so it slides smoothly (no snapping)
        int activeSeg = stitched
                ? (threading ? (NODES - 1) : freeSegCurrent)
                : (NODES - 1);

        // --- Smooth end lock (only when stitched) ---
        if (stitched) {
            if (endLockAlpha < 1.0f) {
                endLockAlpha = Math.min(1.0f, endLockAlpha + END_LOCK_SPEED);
                float t = endLockAlpha;
                t = t * t * (3.0f - 2.0f * t); // smoothstep
                endLockX = endLockSX + (endLockTX - endLockSX) * t;
                endLockY = endLockSY + (endLockTY - endLockSY) * t;
            } else {
                endLockX = endLockTX;
                endLockY = endLockTY;
            }
        }

        // --- Verlet integration ---
        x[0] = bobX; y[0] = bobY;
        px[0] = bobX; py[0] = bobY;

        for (int i = 1; i < NODES; i++) {
            float vx = (x[i] - px[i]) * DAMPING;
            float vy = (y[i] - py[i]) * DAMPING;
            px[i] = x[i];
            py[i] = y[i];
            x[i] += vx;
            y[i] += vy;
        }

        // Only start consuming/collapsing segments AFTER threading finishes
        if (stitched && !threading) {
            if (freeSegCurrent > freeSegTarget) freeSegCurrent--;
        }

        int iters = obstacles.isEmpty() ? SOLVER_ITERS : (SOLVER_ITERS + 6);
        int endIndex = NODES - 1;

        for (int iter = 0; iter < iters; iter++) {
            // hard pin bob
            x[0] = bobX; y[0] = bobY;
            px[0] = bobX; py[0] = bobY;

            // hard pin stitched end (always same index)
            if (stitched) {
                x[endIndex] = endLockX;
                y[endIndex] = endLockY;
                px[endIndex] = endLockX;
                py[endIndex] = endLockY;
            }

            if (!anchors.isEmpty()) {
                x[NODES - 1] = endLockX;
                y[NODES - 1] = endLockY;
                px[NODES - 1] = endLockX;
                py[NODES - 1] = endLockY;
            }

            // segment constraints:
            // - during threading: all segments SEG_LEN
            // - after threading: segments after activeSeg collapse to 0
            for (int i = 0; i < NODES - 1; i++) {
                float len = (i < activeSeg) ? SEG_LEN : 0.0f;

                boolean aPinned = (i == 0);
                boolean bPinned = stitched && (i + 1 == endIndex);

                satisfyDistance(i, i + 1, len, aPinned, bPinned);
            }

            solveObstaclesWithLimit(activeSeg);

            // re-tighten
            for (int i = 0; i < NODES - 1; i++) {
                float len = (i < activeSeg) ? SEG_LEN : 0.0f;

                boolean aPinned = (i == 0);
                boolean bPinned = stitched && (i + 1 == endIndex);

                satisfyDistance(i, i + 1, len, aPinned, bPinned);
            }
        }
    }

    private void drawStitchPath(DrawContext ctx) {
        int n = stitchPath.size();
        if (n == 0) return;

        int lastSegIndex = n - 2;

        // committed segments
        for (int i = 0; i < n - 1; i++) {
            StitchPoint a = stitchPath.get(i);
            StitchPoint b = stitchPath.get(i + 1);

            float ax = stitchPX(a);
            float ay = stitchPY(a);

            float bx = stitchPX(b);
            float by = stitchPY(b);

            int col = (a.hit && b.hit) ? 0xFFFF2A2A : 0x88330000;

            // animate only the newest committed segment
            if (i == lastSegIndex && lastStitchDrawT < 1.0f) {
                bx = ax + (bx - ax) * lastStitchDrawT;
                by = ay + (by - ay) * lastStitchDrawT;
            }

            drawThickLine(ctx, ax, ay, bx, by, 1.4f, col);
        }
    }

    private void recomputeThreadBudgetForCut() {
        float req = estimateNubSeqRequired();

        // Set max thread to exactly what the stitches need (plus margin)
        threadMax = req * THREAD_MARGIN;
        usedThread = 0.0f;

        freeSegCurrent = freeSegTarget = computeFreeSegTarget();
    }

    private void solveObstaclesWithLimit(int freeSeg) {
        if (obstacles.isEmpty()) return;
        freeSeg = Math.max(0, Math.min(NODES - 1, freeSeg));

        int maxNode = Math.min(NODES - 1, freeSeg + 1); // nodes 0..freeSeg+1 can influence the visible rope
        int endPinned = (!anchors.isEmpty()) ? (NODES - 1) : -999;

        // push nodes out (skip bob and pinned end; they’re handled elsewhere)
        for (int i = 1; i <= maxNode; i++) {
            if (i == endPinned) continue;
            float[] p = pushOutOfObstacles(x[i], y[i], ROPE_THICK * 0.5f); // include rope thickness
            x[i] = p[0];
            y[i] = p[1];
        }

        // segment collision only for visible/free part
        for (Obstacle ob : obstacles) {
            float cx = ob.x, cy = ob.y;
            float r = ob.r + ROPE_THICK * 0.5f;

            for (int i = 0; i < freeSeg; i++) {
                int b = i + 1;

                boolean aPinned = (i == 0);
                boolean bPinned = (b == endPinned);

                float ax = x[i], ay = y[i];
                float bx = x[b], by = y[b];

                float vx = bx - ax;
                float vy = by - ay;
                float vv = vx * vx + vy * vy;
                if (vv < 0.000001f) continue;

                float t = ((cx - ax) * vx + (cy - ay) * vy) / vv;
                if (t < 0f) t = 0f; else if (t > 1f) t = 1f;

                float px0 = ax + vx * t;
                float py0 = ay + vy * t;

                float dx = px0 - cx;
                float dy = py0 - cy;
                float d2 = dx * dx + dy * dy;

                if (d2 < r * r) {
                    float d = (float) Math.sqrt(Math.max(d2, 0.000001f));
                    float pen = (r - d);
                    float nx = dx / d;
                    float ny = dy / d;

                    float wa = 1.0f - t;
                    float wb = t;

                    if (aPinned) { wa = 0.0f; wb = 1.0f; }
                    if (bPinned) { wa = 1.0f; wb = 0.0f; }

                    if (!aPinned) { x[i] += nx * pen * wa; y[i] += ny * pen * wa; }
                    if (!bPinned) { x[b] += nx * pen * wb; y[b] += ny * pen * wb; }
                }
            }
        }
    }

    private void satisfyDistance(int a, int b, float len, boolean aPinned, boolean bPinned) {
        float dx = x[b] - x[a];
        float dy = y[b] - y[a];
        float d2 = dx * dx + dy * dy;
        if (d2 < 0.000001f) return;

        float d = (float) Math.sqrt(d2);
        float diff = (d - len) / d;

        // both pinned => nothing to do
        if (aPinned && bPinned) return;

        // If one side is pinned, push all correction into the other side.
        float wa = 0.5f;
        float wb = 0.5f;
        if (aPinned) { wa = 0.0f; wb = 1.0f; }
        if (bPinned) { wa = 1.0f; wb = 0.0f; }

        x[a] += dx * diff * wa;
        y[a] += dy * diff * wa;
        x[b] -= dx * diff * wb;
        y[b] -= dy * diff * wb;
    }

    private float[] pushOutOfObstacles(float px, float py, float r) {
        for (Obstacle ob : obstacles) {
            float dx = px - ob.x;
            float dy = py - ob.y;
            float rr = (ob.r + r);
            float d2 = dx * dx + dy * dy;

            if (d2 < rr * rr) {
                if (d2 < 0.000001f) {
                    // degenerate: shove upward
                    py = ob.y - rr;
                    continue;
                }
                float d = (float) Math.sqrt(d2);
                float s = rr / d;
                px = ob.x + dx * s;
                py = ob.y + dy * s;
            }
        }
        return new float[]{px, py};
    }

    private void drawCircle(DrawContext ctx, float cx, float cy, float r, int argb) {
        int minY = (int) Math.floor(cy - r);
        int maxY = (int) Math.ceil (cy + r);

        float rr = r * r;
        for (int y = minY; y <= maxY; y++) {
            float dy = (y + 0.5f) - cy;
            float dy2 = dy * dy;
            if (dy2 > rr) continue;

            float span = (float) Math.sqrt(rr - dy2);
            int x1 = (int) Math.floor(cx - span);
            int x2 = (int) Math.ceil (cx + span);
            ctx.fill(x1, y, x2, y + 1, argb);
        }
    }

    // -------- Rendering --------

    private void drawRope(DrawContext ctx) {
        boolean stitched = !anchors.isEmpty();
        boolean threading = stitched && (endLockAlpha < 1.0f);

        int segs = stitched ? (threading ? (NODES - 1) : freeSegCurrent) : (NODES - 1);
        segs = Math.max(0, Math.min(NODES - 1, segs));

        for (int i = 0; i < segs; i++) {
            drawThickLine(ctx, x[i], y[i], x[i + 1], y[i + 1], ROPE_THICK, ROPE_COLOR);
        }
    }

    private void drawMarkers(DrawContext ctx) {
        // Anchors = stitch points (these can move with closeT)
        for (StitchPoint sp : stitchPath) {
            float ax = stitchPX(sp);
            float ay = stitchPY(sp);
            int ix = (int) ax;
            int iy = (int) ay;
            ctx.fill(ix - 2, iy - 2, ix + 3, iy + 3, ANC_COLOR);
        }

        // Bob
        int bx = (int) bobX;
        int by = (int) bobY;
        ctx.fill(bx - 3, by - 3, bx + 4, by + 4, BOB_COLOR);
    }

    private float stitchPX(StitchPoint sp) {
        if (sutState == SutState.CLOSING || sutState == SutState.DONE) {
            syncRopeEndToClosingEdge();
            return edgeX(sp.seg, sp.side);
        }
        return sp.x;
    }

    private float stitchPY(StitchPoint sp) {
        if (sutState == SutState.CLOSING || sutState == SutState.DONE) {
            return edgeY(sp.seg, sp.side);
        }
        return sp.y;
    }

    private void drawStringLeftMeter(DrawContext ctx) {
        float total = threadMax;
        float left = remainingThread();

        // tension: how close we are to taut (0 = slack, 1 = fully taut)
        float tension = 0.0f;
        if (!anchors.isEmpty()) {
            Anchor last = anchors.get(anchors.size() - 1);
            float d = dist(bobX, bobY, last.ax, last.ay);
            float denom = Math.max(left, 0.0001f);
            tension = clamp01(d / denom);
        }

        int x0 = 10, y0 = 10;
        int w = 110, h = 8;
        int gap = 10;

        // Bar 1: thread left (ratio)
        float leftRatio = clamp01(left / total);

        ctx.fill(x0 - 1, y0 - 1, x0 + w + 1, y0 + h + 1, 0xFF111111);
        ctx.fill(x0, y0, x0 + w, y0 + h, 0xFF2A2A2A);
        ctx.fill(x0, y0, x0 + (int)(w * leftRatio), y0 + h, 0xFF88DD88);

        ctx.drawText(this.textRenderer,
                Text.literal("Thread " + (int)left + "/" + (int)total),
                x0, y0 + h + 2, 0xFFFFFFFF, true
        );

        // Bar 2: tension
        int y1 = y0 + h + gap + 10;
        ctx.fill(x0 - 1, y1 - 1, x0 + w + 1, y1 + h + 1, 0xFF111111);
        ctx.fill(x0, y1, x0 + w, y1 + h, 0xFF2A2A2A);
        ctx.fill(x0, y1, x0 + (int)(w * tension), y1 + h, 0xFFFF8888);

        ctx.drawText(this.textRenderer,
                Text.literal("Tension " + (int)(tension * 100.0f) + "%"),
                x0, y1 + h + 2, 0xFFFFFFFF, true
        );
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        return Math.min(v, 1f);
    }

    // Thick line as a quad (two triangles) using GUI render layer.
    private void drawThickLine(DrawContext ctx,
                               float x1, float y1, float x2, float y2,
                               float thickness, int argb) {



        float dx = x2 - x1;
        float dy = y2 - y1;
        float len2 = dx * dx + dy * dy;
        if (len2 < 0.000001f) return;

        float len = (float) Math.sqrt(len2);

        // unit tangent
        float ux = dx / len;
        float uy = dy / len;

        // unit normal (perpendicular)
        float nx = -uy;
        float ny = ux;

        float half = thickness * 0.5f;

        // stamp spacing (smaller => less "boxy", harder to slip through visually)
        float step = 0.5f;
        int steps = Math.max(1, (int) (len / step));

        float along = step * 1.5f;

        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float cx = x1 + dx * t;
            float cy = y1 + dy * t;

            float rx1 = cx - nx * half - ux * along;
            float ry1 = cy - ny * half - uy * along;
            float rx2 = cx + nx * half + ux * along;
            float ry2 = cy + ny * half + uy * along;

            int ix1 = (int) Math.floor(Math.min(rx1, rx2));
            int iy1 = (int) Math.floor(Math.min(ry1, ry2));
            int ix2 = (int) Math.ceil (Math.max(rx1, rx2));
            int iy2 = (int) Math.ceil (Math.max(ry1, ry2));

            if (ix2 <= ix1) ix2 = ix1 + 1;
            if (iy2 <= iy1) iy2 = iy1 + 1;

            ctx.fill(ix1, iy1, ix2, iy2, argb);
        }
    }

    private static float dist(float ax, float ay, float bx, float by) {
        float dx = bx - ax;
        float dy = by - ay;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}