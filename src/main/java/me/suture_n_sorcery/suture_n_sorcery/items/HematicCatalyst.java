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
    private static final int READY_HALF_UNITS = 200;
    private static final int FEED_COOLDOWN_TICKS = 80;
    private static final float MIN_FEED_HEALTH = 6.0f;
    private static final int SUCCESS_FEED_REWARD = 8;
    private static final int PARTIAL_FEED_REWARD = 2;
    private static final int PASSIVE_FEED_HALF_STEPS = 1;
    private static final int STAGE_ONE_MAX_GROWTH = 33;
    private static final int STAGE_TWO_MAX_GROWTH = 66;
    private static final int STAGE_ONE_NUBS = 6;
    private static final int STAGE_TWO_NUBS = 12;
    private static final int STAGE_THREE_NUBS = 18;

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

            if (source.isOf(ModDamageTypes.SUTURE_FEED)) return;

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

        if (!hasEnoughHealthToFeed(player)) return;

        int hits = Math.max(0, Math.min(payload.hits(), payload.total()));
        boolean anyProgress = hits > 0;

        int add = 0;
        if (payload.success()) add = SUCCESS_FEED_REWARD + hits;
        else if (anyProgress) add = PARTIAL_FEED_REWARD;

        if (add > 0) addGrowth(catalyst, add);

        // manual attempts always cost the needle once the payload is valid
        EquipmentSlot slot = (needleHand == Hand.MAIN_HAND) ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        needle.damage(1, player, slot);
    }

    // item id

    public static final Identifier HEMATIC_CATALYST_ID =
            Identifier.of(Suture_n_sorcery.MOD_ID, "hematic_catalyst");

    public static final RegistryKey<Item> HEMATIC_CATALYST_KEY =
            RegistryKey.of(RegistryKeys.ITEM, HEMATIC_CATALYST_ID);

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

    // catalyst stages mirror the feeding minigame target count
    public static int stageNubsFor(ItemStack stack) {
        int g = getGrowth(stack);
        if (g <= STAGE_ONE_MAX_GROWTH) return STAGE_ONE_NUBS;
        if (g <= STAGE_TWO_MAX_GROWTH) return STAGE_TWO_NUBS;
        return STAGE_THREE_NUBS;
    }

    private static boolean isSutureNeedle(ItemStack stack) {
        return stack.isOf(SutureNeedle.SUTURE_NEEDLE);
    }



    private static boolean tryPassiveFeed(PlayerEntity player, ItemStack stack, float damageTaken) {
        if (!stack.isOf(HEMATIC_CATALYST)) return false;
        if (isReady(stack)) return false;

        // ownership prevents a second player from filling a bound catalyst
        String owner = stack.getOrDefault(OWNER_UUID, "");
        String me = player.getUuidAsString();
        if (!owner.isEmpty() && !owner.equals(me)) return false;

        long now = player.getEntityWorld().getTime();
        long last = stack.getOrDefault(LAST_FEED_TICK, 0L);
        if (now - last < FEED_COOLDOWN_TICKS) return false;

        // first feed binds the catalyst, but each player only gets one active owner
        if (owner.isEmpty()) {
            if (hasOwnedCatalyst(player)) return false;
            stack.set(OWNER_UUID, me);
        }

        stack.set(LAST_FEED_TICK, now);
        addHalfGrowth(stack, PASSIVE_FEED_HALF_STEPS);

        return true;
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
