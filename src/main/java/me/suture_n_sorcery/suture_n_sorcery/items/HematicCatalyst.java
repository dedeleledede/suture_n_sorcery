package me.suture_n_sorcery.suture_n_sorcery.items;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import me.suture_n_sorcery.suture_n_sorcery.items.needle.SutureNeedle;
import me.suture_n_sorcery.suture_n_sorcery.network.HematicFeedPayload;
import me.suture_n_sorcery.suture_n_sorcery.registries.ModDamageTypes;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.component.ComponentType;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.ItemStack;
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
    private static final int MAX_GROWTH = 150;
    private static final int FEED_COOLDOWN_TICKS = 80;

    // components

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

    // server stuff

    static {
        registerFeedNetOnce();
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (!(entity instanceof PlayerEntity player)) return;
            if (player.isCreative() || player.isSpectator()) return;
            if (damageTaken <= 0.0f) return;

            if (source.isOf(ModDamageTypes.SUTURE_FEED)) return; // prevent recursion

            var world = player.getEntityWorld();
            if (!(world instanceof ServerWorld serverWorld)) return;

            ItemStack stack = findCatalystInInventory(player);
            boolean fed = (stack != null) && tryPassiveFeed(player, stack, damageTaken);

            if (fed) {
                var src = world.getDamageSources().create(ModDamageTypes.SUTURE_FEED);
                player.damage(serverWorld, src, 0.5f);
            }
        });
    }

    private static void registerFeedNetOnce() {
        if (FEED_NET_REGISTERED) return;
        FEED_NET_REGISTERED = true;

        ServerPlayNetworking.registerGlobalReceiver(HematicFeedPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();

            // already runs on server thread in the payload API, but keeping it explicit is fine
            context.server().execute(() -> handleManualFeedResult(player, payload));
        });
    }

    private static void handleManualFeedResult(ServerPlayerEntity player, HematicFeedPayload payload) {
        Hand catalystHand = (payload.catalystHand() == 0) ? Hand.MAIN_HAND : Hand.OFF_HAND;
        Hand needleHand = (catalystHand == Hand.MAIN_HAND) ? Hand.OFF_HAND : Hand.MAIN_HAND;

        ItemStack catalyst = player.getStackInHand(catalystHand);
        ItemStack needle = player.getStackInHand(needleHand);

        if (!catalyst.isOf(HematicCatalyst.HEMATIC_CATALYST)) return;
        if (!isSutureNeedle(needle)) return; // you define this check

        // safety gate from your plan (> 3 hearts)
        if (player.getHealth() <= 6.0f) return;

        int hits = Math.max(0, Math.min(payload.hits(), payload.total()));
        boolean anyProgress = hits > 0;

        // rewards (manual > passive)
        int add = 0;
        if (payload.success()) add = 8 + hits;         // big reward
        else if (anyProgress) add = 2;                 // partial reward

        if (add > 0) addGrowth(catalyst, add);

        // needle durability always -1 on manual attempt (success or fail)
        EquipmentSlot slot = (needleHand == Hand.MAIN_HAND) ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        needle.damage(1, player, slot);
    }

    // ids keys

    public static final Identifier HEMATIC_CATALYST_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "hematic_catalyst");

    public static final RegistryKey<Item> HEMATIC_CATALYST_KEY =
            RegistryKey.of(RegistryKeys.ITEM, HEMATIC_CATALYST_ID);

    // ---- Data components (per-stack state) ----
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

    // helpers

    private static boolean isOwnedBy(ItemStack st, PlayerEntity player) {
        String owner = st.getOrDefault(OWNER_UUID, "");
        return !owner.isEmpty() && owner.equals(player.getUuidAsString());
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
        var inv = player.getInventory();

        // 1) already-owned first
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isOf(HEMATIC_CATALYST) && isOwnedBy(st, player) && !isReady(st)) return st;
        }

        // 2) otherwise first unowned (will bind on successful feed)
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isOf(HEMATIC_CATALYST) && st.getOrDefault(OWNER_UUID, "").isEmpty() && !isReady(st)) return st;
        }

        return null;
    }

    // ---- Growth API ----
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

    public static boolean isReady(ItemStack stack) {
        return stack.getOrDefault(MATURATION_LOCK, false) || halfUnits(stack) >= 200;
    }

    private static int halfUnits(ItemStack stack) {
        int g = getGrowth(stack);                 // 0..100
        int h = stack.getOrDefault(GROWTH_HALF, 0); // 0 or 1
        if (h != 0) h = 1;
        return g * 2 + h; // 0..200
    }

    private static void setHalfUnits(ItemStack stack, int hu) {
        if (hu < 0) hu = 0;
        if (hu > 200) hu = 200;

        int g = hu / 2;
        int h = hu & 1;

        stack.set(GROWTH, g);
        stack.set(GROWTH_HALF, h);

        if (hu >= 200) stack.set(MATURATION_LOCK, true);
    }

    public static void addHalfGrowth(ItemStack stack, int halfSteps) {
        setHalfUnits(stack, halfUnits(stack) + halfSteps);
    }

    private static String growthText(ItemStack stack) {
        int hu = halfUnits(stack);
        int g = hu / 2;
        return ((hu & 1) == 1) ? (g + ".5") : Integer.toString(g);
    }

    // Stage mapping -> nub counts
    // Stage 1 (0–33%): 6 nubs
    // Stage 2 (34–66%): 12 nubs
    // Stage 3 (67–99%): 18 nubs
    public static int stageNubsFor(ItemStack stack) {
        int g = getGrowth(stack);
        if (g >= 100) return 18;
        if (g <= 33) return 6;
        if (g <= 66) return 12;
        return 18;
    }

    private static boolean isSutureNeedle(ItemStack stack) {
        // rename this constant to whatever your actual needle item is called
        return stack.isOf(SutureNeedle.SUTURE_NEEDLE);
    }



    private static boolean tryPassiveFeed(PlayerEntity player, ItemStack stack, float damageTaken) {
        if (!stack.isOf(HEMATIC_CATALYST)) return false;
        if (isReady(stack)) return false;

        // must be in inventory; reject if owned by someone else
        String owner = stack.getOrDefault(OWNER_UUID, "");
        String me = player.getUuidAsString();
        if (!owner.isEmpty() && !owner.equals(me)) return false;

        long now = player.getEntityWorld().getTime();
        long last = stack.getOrDefault(LAST_FEED_TICK, 0L);
        if (now - last < FEED_COOLDOWN_TICKS) return false;

        // bind on first successful feed, but only if player doesn't already own another
        if (owner.isEmpty()) {
            if (hasOwnedCatalyst(player)) return false; // ✅ only bind to ONE catalyst ever
            stack.set(OWNER_UUID, me);
        }

        // very slow passive growth
        stack.set(LAST_FEED_TICK, now);
        addHalfGrowth(stack, 1); // +0.5 growth

        return true;
    }

    // If you register items elsewhere, remove this and set these defaults in your registry instead.
    public static final Item HEMATIC_CATALYST = new HematicCatalyst(new Settings()
            .registryKey(HEMATIC_CATALYST_KEY)
            .maxCount(1)
            .component(GROWTH, 0)
            .component(GROWTH_HALF, 0)
            .component(MATURATION_LOCK, false)
            .component(LAST_FEED_TICK, 0L)
            .component(OWNER_UUID, "")
    );

    // ---- Tooltips ----
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