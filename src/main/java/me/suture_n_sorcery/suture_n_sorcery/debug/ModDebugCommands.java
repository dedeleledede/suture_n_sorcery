package me.suture_n_sorcery.suture_n_sorcery.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import me.suture_n_sorcery.suture_n_sorcery.items.HematicCatalyst;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class ModDebugCommands {

    private ModDebugCommands() {
    }

    public static void registerDebugCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("sns")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("catalyst")
                        .then(literal("growth")
                                .then(literal("add")
                                        .then(argument("amount", IntegerArgumentType.integer(-100, 100))
                                                .executes(context -> addGrowth(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "amount")
                                                ))))
                                .then(literal("set")
                                        .then(argument("amount", IntegerArgumentType.integer(0, 100))
                                                .executes(context -> setGrowth(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "amount")
                                                )))))
                        .then(literal("unlink")
                                .executes(context -> unlinkCatalyst(context.getSource())))
                        .then(literal("bond")
                                .then(literal("clear")
                                        .executes(context -> clearPlayerBond(context.getSource()))))));
    }

    private static int addGrowth(ServerCommandSource source, int amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ItemStack catalyst = catalystInHand(player);
        if (catalyst.isEmpty()) {
            source.sendError(Text.literal("hold a hematic catalyst in either hand"));
            return 0;
        }

        int next = HematicCatalyst.getGrowth(catalyst) + amount;
        HematicCatalyst.setGrowthForDebug(catalyst, next);
        source.sendFeedback(() -> Text.literal("hematic catalyst growth: " + HematicCatalyst.getGrowth(catalyst) + "/100"), false);
        return 1;
    }

    private static int setGrowth(ServerCommandSource source, int amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ItemStack catalyst = catalystInHand(player);
        if (catalyst.isEmpty()) {
            source.sendError(Text.literal("hold a hematic catalyst in either hand"));
            return 0;
        }

        HematicCatalyst.setGrowthForDebug(catalyst, amount);
        source.sendFeedback(() -> Text.literal("hematic catalyst growth: " + HematicCatalyst.getGrowth(catalyst) + "/100"), false);
        return 1;
    }

    private static int unlinkCatalyst(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ItemStack catalyst = catalystInHand(player);
        if (catalyst.isEmpty()) {
            source.sendError(Text.literal("hold a hematic catalyst in either hand"));
            return 0;
        }

        HematicCatalyst.clearOwner(catalyst);
        source.sendFeedback(() -> Text.literal("hematic catalyst owner cleared"), false);
        return 1;
    }

    private static int clearPlayerBond(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        HematicCatalyst.setAbsorbedCatalyst(player, false);
        source.sendFeedback(() -> Text.literal("hematic catalyst absorption bond cleared"), false);
        return 1;
    }

    private static ItemStack catalystInHand(ServerPlayerEntity player) {
        ItemStack main = player.getStackInHand(Hand.MAIN_HAND);
        if (main.isOf(HematicCatalyst.HEMATIC_CATALYST)) return main;

        ItemStack off = player.getStackInHand(Hand.OFF_HAND);
        if (off.isOf(HematicCatalyst.HEMATIC_CATALYST)) return off;

        return ItemStack.EMPTY;
    }
}
