package me.suture_n_sorcery.suture_n_sorcery.items;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import me.suture_n_sorcery.suture_n_sorcery.items.needle.Needle;
import me.suture_n_sorcery.suture_n_sorcery.items.needle.NeedleStacks;
import me.suture_n_sorcery.suture_n_sorcery.items.needle.SutureNeedle;
import me.suture_n_sorcery.suture_n_sorcery.network.HematicFeedPayload;
import me.suture_n_sorcery.suture_n_sorcery.registries.ModDamageTypes;
import me.suture_n_sorcery.suture_n_sorcery.util.HematicBondHolder;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.component.ComponentType;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

import com.mojang.serialization.Codec;

import java.util.function.Consumer;
import java.util.function.Function;

public final class HematicCatalyst extends Item {
    public HematicCatalyst(Settings settings) {
        super(settings);
    }

    private static boolean FEED_NET_REGISTERED = false;
    private static final int MAX_GROWTH = 100;
    private static final int READY_HALF_UNITS = 200;
    private static final int FEED_COOLDOWN_TICKS = 80;
    private static final float MIN_FEED_HEALTH = 6.0f;
    private static final int PERFECT_MANUAL_FEED_HALF_STEPS = 14;
    private static final int MIN_MANUAL_FEED_HALF_STEPS = 1;
    private static final int PASSIVE_FEED_HALF_STEPS = 1;
    private static final float PASSIVE_DAMAGE_PER_HALF_STEP = 2.0f;
    private static final float MIN_MANUAL_FEED_DAMAGE = 0.25f;
    private static final float MAX_MANUAL_FEED_DAMAGE = 2.0f;
    private static final int MIN_FEEDING_NUBS = 6;
    private static final int MAX_FEEDING_NUBS = 16;

    // stack components

    public static final ComponentType<Integer> GROWTH = registerComponent(
            "hematic_growth",
            b -> b.codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT)
    );

    public static final ComponentType<Boolean> MATURATION_LOCK = registerComponent(
            "hematic_maturation_lock",
            b -> b.codec(Codec.BOOL).packetCodec(PacketCodecs.BOOLEAN)
    );

    public static final ComponentType<Long> LAST_FEED_TICK = registerComponent(
            "hematic_last_feed_tick",
            b -> b.codec(Codec.LONG).packetCodec(PacketCodecs.VAR_LONG)
    );

    public static final ComponentType<String> OWNER_UUID = registerComponent(
            "hematic_owner_uuid",
            b -> b.codec(Codec.STRING).packetCodec(PacketCodecs.STRING)
    );

    public static final ComponentType<Integer> GROWTH_HALF = registerComponent(
            "hematic_growth_half",
            b -> b.codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT)
    );

    // server feed hooks

    static {
        registerFeedNetOnce();
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (!(entity instanceof PlayerEntity player)) return;
            if (player.isCreative() || player.isSpectator()) return;
            if (damageTaken <= 0.0f) return;
            if (hasAbsorbedCatalyst(player)) return;

            if (source.isOf(ModDamageTypes.SUTURE_FEED)) return;

            var world = player.getEntityWorld();
            if (!(world instanceof ServerWorld serverWorld)) return;

            ItemStack stack = findCatalystInInventory(player);
            if (stack != null) tryPassiveFeed(player, stack, damageTaken);
        });
    }

    private static void registerFeedNetOnce() {
        if (FEED_NET_REGISTERED) return;
        FEED_NET_REGISTERED = true;

        ServerPlayNetworking.registerGlobalReceiver(HematicFeedPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();

            context.server().execute(() -> handleManualFeedResult(player, payload));
        });
    }

    private static void handleManualFeedResult(ServerPlayerEntity player, HematicFeedPayload payload) {
        Hand catalystHand = (payload.catalystHand() == 0) ? Hand.MAIN_HAND : Hand.OFF_HAND;
        Hand needleHand = (catalystHand == Hand.MAIN_HAND) ? Hand.OFF_HAND : Hand.MAIN_HAND;

        ItemStack catalyst = player.getStackInHand(catalystHand);
        ItemStack needle = player.getStackInHand(needleHand);

        if (!catalyst.isOf(HematicCatalyst.HEMATIC_CATALYST)) return;
        if (!isSutureNeedle(needle)) return;

        if (!canPlayerUseCatalyst(player, catalyst)) return;
        if (!hasEnoughHealthToFeed(player)) return;

        int hits = Math.max(0, Math.min(payload.hits(), payload.total()));
        int add = manualFeedHalfSteps(hits, payload.total(), payload.success());
        if (add <= 0) return;

        if (!bindIfNeeded(player, catalyst)) return;
        addHalfGrowth(catalyst, add);
        damageForManualFeed(player, add);

        EquipmentSlot slot = NeedleStacks.slotForHand(needleHand);
        needle.damage(1, player, slot);
        NeedleStacks.setInHand(player, needleHand, NeedleStacks.convertKeepingDamage(needle, Needle.NEEDLE));
    }

    private static int manualFeedHalfSteps(int hits, int total, boolean success) {
        if (hits <= 0 || total <= 0) return 0;

        float ratio = Math.min(1.0f, hits / (float) total);
        int reward = Math.round(PERFECT_MANUAL_FEED_HALF_STEPS * ratio);
        if (success) reward = PERFECT_MANUAL_FEED_HALF_STEPS;
        return Math.max(MIN_MANUAL_FEED_HALF_STEPS, reward);
    }

    private static void damageForManualFeed(ServerPlayerEntity player, int halfSteps) {
        float ratio = Math.min(1.0f, halfSteps / (float) PERFECT_MANUAL_FEED_HALF_STEPS);
        float damage = MIN_MANUAL_FEED_DAMAGE + ((MAX_MANUAL_FEED_DAMAGE - MIN_MANUAL_FEED_DAMAGE) * ratio);
        var src = player.getEntityWorld().getDamageSources().create(ModDamageTypes.SUTURE_FEED);
        player.damage(player.getEntityWorld(), src, damage);
    }

    // item id

    public static final Identifier HEMATIC_CATALYST_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "hematic_catalyst");

    public static final RegistryKey<Item> HEMATIC_CATALYST_KEY =
            RegistryKey.of(RegistryKeys.ITEM, HEMATIC_CATALYST_ID);

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        Hand other = oppositeHand(hand);
        if (!isSutureNeedle(user.getStackInHand(other))) return ActionResult.PASS;
        return useNeedleOnCatalyst(world, user, other, hand);
    }

    public static ActionResult useNeedleOnCatalyst(World world, PlayerEntity user, Hand needleHand, Hand catalystHand) {
        ItemStack needle = user.getStackInHand(needleHand);
        ItemStack catalyst = user.getStackInHand(catalystHand);

        if (!isSutureNeedle(needle)) return ActionResult.PASS;
        if (!catalyst.isOf(HEMATIC_CATALYST)) return ActionResult.PASS;
        if (!canPlayerUseCatalyst(user, catalyst)) return ActionResult.FAIL;

        if (isReady(catalyst)) {
            if (world.isClient()) return ActionResult.SUCCESS;
            return absorbCatalyst(user, catalyst) ? ActionResult.SUCCESS : ActionResult.FAIL;
        }

        if (!hasEnoughHealthToFeed(user)) return ActionResult.FAIL;
        if (!world.isClient()) return ActionResult.SUCCESS;

        openFeedingMiniGame(catalyst, catalystHand);
        return ActionResult.SUCCESS;
    }

    private static void openFeedingMiniGame(ItemStack catalyst, Hand catalystHand) {
        int pct = feedingGrowthPercent(catalyst);
        int catalystHandOrdinal = (catalystHand == Hand.MAIN_HAND) ? 0 : 1;

        try {
            Class<?> bridge = Class.forName("me.suture_n_sorcery.suture_n_sorcery.client.items.SutureNeedleClientBridge");
            bridge.getMethod("openFeedingMiniGame", int.class, int.class)
                    .invoke(null, pct, catalystHandOrdinal);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to open feeding minigame", e);
        }
    }

    private static <T> ComponentType<T> registerComponent(
            String path,
            Function<ComponentType.Builder<T>, ComponentType.Builder<T>> build
    ) {
        return Registry.register(
                Registries.DATA_COMPONENT_TYPE,
                Identifier.of(Suture_n_sorcery.MOD_ID, path),
                build.apply(ComponentType.builder()).build()
        );
    }

    private static boolean isOwnedBy(ItemStack st, PlayerEntity player) {
        String owner = st.getOrDefault(OWNER_UUID, "");
        return !owner.isEmpty() && owner.equals(player.getUuidAsString());
    }

    public static boolean hasAbsorbedCatalyst(PlayerEntity player) {
        return player instanceof HematicBondHolder holder
                && holder.suture_n_sorcery$hasAbsorbedHematicCatalyst();
    }

    private static boolean hasOwnedCatalyst(PlayerEntity player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isOf(HEMATIC_CATALYST) && isOwnedBy(st, player)) return true;
        }
        return false;
    }

    private static ItemStack findCatalystInInventory(PlayerEntity player) {
        if (hasAbsorbedCatalyst(player)) return null;

        var inv = player.getInventory();

        // an owned catalyst should feed before a fresh unbound one
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isOf(HEMATIC_CATALYST) && isOwnedBy(st, player) && !isReady(st)) return st;
        }

        // unowned catalysts bind on their first successful passive feed
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isOf(HEMATIC_CATALYST) && st.getOrDefault(OWNER_UUID, "").isEmpty() && !isReady(st)) return st;
        }

        return null;
    }

    // growth api
    public static int getGrowth(ItemStack stack) {
        int v = stack.getOrDefault(GROWTH, 0);
        if (v < 0) return 0;
        return Math.min(v, MAX_GROWTH);
    }

    public static void setGrowth(ItemStack stack, int growth) {
        if (growth < 0) growth = 0;
        if (growth > MAX_GROWTH) growth = MAX_GROWTH;

        stack.set(GROWTH, growth);

        if (growth == MAX_GROWTH) {
            stack.set(MATURATION_LOCK, true);
        }
    }

    public static void addGrowth(ItemStack stack, int delta) {
        setGrowth(stack, getGrowth(stack) + delta);
    }

    public static void setGrowthForDebug(ItemStack stack, int growth) {
        if (growth < 0) growth = 0;
        if (growth > MAX_GROWTH) growth = MAX_GROWTH;

        setHalfUnits(stack, growth * 2);
        stack.set(MATURATION_LOCK, growth >= MAX_GROWTH);
    }

    public static void clearOwner(ItemStack stack) {
        stack.set(OWNER_UUID, "");
    }

    public static void setAbsorbedCatalyst(PlayerEntity player, boolean absorbed) {
        if (player instanceof HematicBondHolder holder) {
            holder.suture_n_sorcery$setAbsorbedHematicCatalyst(absorbed);
        }
    }

    public static boolean isReady(ItemStack stack) {
        return stack.getOrDefault(MATURATION_LOCK, false) || halfUnits(stack) >= READY_HALF_UNITS;
    }

    public static boolean hasEnoughHealthToFeed(PlayerEntity player) {
        return player.getHealth() > MIN_FEED_HEALTH;
    }

    public static int feedingGrowthPercent(ItemStack stack) {
        int pct = (halfUnits(stack) * 100) / READY_HALF_UNITS;
        return Math.min(pct, 99);
    }

    public static boolean canPlayerUseCatalyst(PlayerEntity player, ItemStack stack) {
        if (!stack.isOf(HEMATIC_CATALYST)) return false;
        if (hasAbsorbedCatalyst(player)) return false;

        String owner = stack.getOrDefault(OWNER_UUID, "");
        if (owner.isEmpty()) return !hasOwnedCatalyst(player);
        return owner.equals(player.getUuidAsString());
    }

    public static boolean canAbsorbCatalyst(PlayerEntity player, ItemStack stack) {
        return stack.isOf(HEMATIC_CATALYST)
                && isReady(stack)
                && isOwnedBy(stack, player)
                && !hasAbsorbedCatalyst(player);
    }

    public static boolean absorbCatalyst(PlayerEntity player, ItemStack stack) {
        if (!canAbsorbCatalyst(player, stack)) return false;
        if (player instanceof HematicBondHolder holder) {
            holder.suture_n_sorcery$setAbsorbedHematicCatalyst(true);
        }
        stack.decrement(1);
        return true;
    }

    private static int halfUnits(ItemStack stack) {
        int g = getGrowth(stack);
        int h = stack.getOrDefault(GROWTH_HALF, 0);
        if (h != 0) h = 1;
        return g * 2 + h;
    }

    private static void setHalfUnits(ItemStack stack, int hu) {
        if (hu < 0) hu = 0;
        if (hu > READY_HALF_UNITS) hu = READY_HALF_UNITS;

        int g = hu / 2;
        int h = hu & 1;

        stack.set(GROWTH, g);
        stack.set(GROWTH_HALF, h);

        if (hu >= READY_HALF_UNITS) stack.set(MATURATION_LOCK, true);
    }

    public static void addHalfGrowth(ItemStack stack, int halfSteps) {
        setHalfUnits(stack, halfUnits(stack) + halfSteps);
    }

    private static String growthText(ItemStack stack) {
        int hu = halfUnits(stack);
        int g = hu / 2;
        return ((hu & 1) == 1) ? (g + ".5") : Integer.toString(g);
    }

    public static int stageNubsFor(ItemStack stack) {
        int pct = feedingGrowthPercent(stack);
        int nubs = MIN_FEEDING_NUBS + Math.round((MAX_FEEDING_NUBS - MIN_FEEDING_NUBS) * (pct / 99.0f));
        if ((nubs & 1) == 1) nubs++;
        return Math.min(MAX_FEEDING_NUBS, nubs);
    }

    private static boolean isSutureNeedle(ItemStack stack) {
        return stack.isOf(SutureNeedle.SUTURE_NEEDLE);
    }



    private static boolean tryPassiveFeed(PlayerEntity player, ItemStack stack, float damageTaken) {
        if (!stack.isOf(HEMATIC_CATALYST)) return false;
        if (isReady(stack)) return false;
        if (hasAbsorbedCatalyst(player)) return false;
        if (!canPlayerUseCatalyst(player, stack)) return false;

        long now = player.getEntityWorld().getTime();
        long last = stack.getOrDefault(LAST_FEED_TICK, 0L);
        if (now - last < FEED_COOLDOWN_TICKS) return false;

        if (!bindIfNeeded(player, stack)) return false;

        int halfSteps = passiveFeedHalfSteps(damageTaken);
        if (halfSteps <= 0) return false;

        stack.set(LAST_FEED_TICK, now);
        addHalfGrowth(stack, halfSteps);
        return true;
    }

    private static int passiveFeedHalfSteps(float damageTaken) {
        return Math.max(PASSIVE_FEED_HALF_STEPS, Math.round(damageTaken / PASSIVE_DAMAGE_PER_HALF_STEP));
    }

    private static boolean bindIfNeeded(PlayerEntity player, ItemStack stack) {
        String owner = stack.getOrDefault(OWNER_UUID, "");
        if (!owner.isEmpty()) return owner.equals(player.getUuidAsString());
        if (hasOwnedCatalyst(player)) return false;
        stack.set(OWNER_UUID, player.getUuidAsString());
        return true;
    }

    private static Hand oppositeHand(Hand hand) {
        return (hand == Hand.MAIN_HAND) ? Hand.OFF_HAND : Hand.MAIN_HAND;
    }

    public static final Item HEMATIC_CATALYST = new HematicCatalyst(new Settings()
            .registryKey(HEMATIC_CATALYST_KEY)
            .maxCount(1)
            .component(GROWTH, 0)
            .component(GROWTH_HALF, 0)
            .component(MATURATION_LOCK, false)
            .component(LAST_FEED_TICK, 0L)
            .component(OWNER_UUID, "")
    );

    @Override
    public void appendTooltip(
            ItemStack stack,
            Item.TooltipContext context,
            TooltipDisplayComponent displayComponent,
            Consumer<Text> textConsumer,
            TooltipType type
    ) {
        textConsumer.accept(Text.translatable("tooltip.suture_n_sorcery.hematic_growth", growthText(stack), MAX_GROWTH));
        textConsumer.accept(Text.translatable("tooltip.suture_n_sorcery.hematic_warm_close"));
    }
}
