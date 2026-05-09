package me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceFinder;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SinglePreparationResourceReloader;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;

import java.util.HashMap;
import java.util.Map;

public final class RitualLoomRitualHandler extends SinglePreparationResourceReloader<Map<Item, RitualLoomRitualHandler.RitualDef>> {

    public record RitualDef(
            Item input,
            ItemStack result,
            int bloodMlCost,
            int pressTicks,
            int requiredStrings
    ) {}

    private static volatile Map<Item, RitualDef> BY_ITEM = Map.of();
    private static volatile int MIN_REQUIRED_STRINGS = 1;

    private static final ResourceFinder FINDER =
            ResourceFinder.json("ritual_loom");

    private static final String KEY_INPUT = "input";
    private static final String KEY_RESULT = "result";
    private static final String KEY_ITEM = "item";
    private static final String KEY_COUNT = "count";
    private static final String KEY_BLOOD_ML = "blood_ml";
    private static final String KEY_PRESS_TICKS = "press_ticks";
    private static final String KEY_REQUIRED_STRINGS = "required_strings";

    public static RitualDef get(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return BY_ITEM.get(stack.getItem());
    }

    public static int minRequiredStrings() {
        return Math.max(1, MIN_REQUIRED_STRINGS);
    }

    public static void registerRitualLoomRituals() {
        ResourceLoader
                .get(ResourceType.SERVER_DATA)
                .registerReloader(
                        Identifier.of(Suture_n_sorcery.MOD_ID, "ritual_loom_rituals"),
                        new RitualLoomRitualHandler()
                );
    }

    @Override
    protected Map<Item, RitualDef> prepare(ResourceManager manager, Profiler profiler) {
        HashMap<Item, RitualDef> map = new HashMap<>();

        var resources = FINDER.findResources(manager);

        for (var entry : resources.entrySet()) {
            var id = entry.getKey();
            var resource = entry.getValue();

            try (var reader = resource.getReader()) {
                var el = JsonParser.parseReader(reader);
                if (!el.isJsonObject()) continue;

                var def = parseRitualDef(el.getAsJsonObject());
                map.put(def.input(), def);

            } catch (Throwable t) {
                Suture_n_sorcery.LOGGER.error("[S&S][RitualLoom] Bad ritual json: {} -> {}", id, t);
            }
        }

        return Map.copyOf(map);
    }

    private static RitualDef parseRitualDef(JsonObject root) {
        var inputItem = parseItem(root.getAsJsonObject(KEY_INPUT));
        var resultStack = parseResultStack(root.getAsJsonObject(KEY_RESULT));

        int bloodMl = Math.max(0, root.get(KEY_BLOOD_ML).getAsInt());
        int pressTicks = Math.max(1, root.get(KEY_PRESS_TICKS).getAsInt());
        int reqStrings = Math.max(1, root.get(KEY_REQUIRED_STRINGS).getAsInt());

        return new RitualDef(inputItem, resultStack, bloodMl, pressTicks, reqStrings);
    }

    private static Item parseItem(JsonObject object) {
        var id = Identifier.of(object.get(KEY_ITEM).getAsString());
        return Registries.ITEM.get(id);
    }

    private static ItemStack parseResultStack(JsonObject object) {
        var item = parseItem(object);
        int count = object.has(KEY_COUNT) ? object.get(KEY_COUNT).getAsInt() : 1;
        return new ItemStack(item, Math.max(1, count));
    }

    @Override
    protected void apply(Map<Item, RitualDef> prepared,
                         ResourceManager manager,
                         Profiler profiler) {

        BY_ITEM = prepared;

        int minReq = Integer.MAX_VALUE;
        for (var def : prepared.values()) {
            minReq = Math.min(minReq, def.requiredStrings());
        }
        MIN_REQUIRED_STRINGS = (minReq == Integer.MAX_VALUE) ? 1 : Math.max(1, minReq);
    }
}
