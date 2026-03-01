package me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom;

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

    public static RitualDef get(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return BY_ITEM.get(stack.getItem());
    }

    public static int minRequiredStrings() {
        return Math.max(1, MIN_REQUIRED_STRINGS);
    }

    public static void registerRitualLoomRituals(){
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
                var el = com.google.gson.JsonParser.parseReader(reader);
                if (!el.isJsonObject()) continue;

                var root = el.getAsJsonObject();

                var inputObj = root.getAsJsonObject("input");
                var inputId = Identifier.of(inputObj.get("item").getAsString());
                var inputItem = Registries.ITEM.get(inputId);

                var resultObj = root.getAsJsonObject("result");
                var resultId = Identifier.of(resultObj.get("item").getAsString());
                int count = resultObj.has("count") ? resultObj.get("count").getAsInt() : 1;
                var resultItem = Registries.ITEM.get(resultId);
                var resultStack = new ItemStack(resultItem, Math.max(1, count));

                int bloodMl = Math.max(0, root.get("blood_ml").getAsInt());
                int pressTicks = Math.max(1, root.get("press_ticks").getAsInt());
                int reqStrings = Math.max(1, root.get("required_strings").getAsInt());

                map.put(inputItem, new RitualDef(inputItem, resultStack, bloodMl, pressTicks, reqStrings));

            } catch (Throwable t) {
                Suture_n_sorcery.LOGGER.error("[S&S][RitualLoom] Bad ritual json: {} -> {}", id, t);
            }
        }

        return Map.copyOf(map);
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