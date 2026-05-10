package me.suture_n_sorcery.suture_n_sorcery;

import me.suture_n_sorcery.suture_n_sorcery.blood_sense.BloodSenseTracker;
import me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom.RitualLoomRitualHandler;
import me.suture_n_sorcery.suture_n_sorcery.registries.ItemGroups;
import me.suture_n_sorcery.suture_n_sorcery.registries.ModBlockEntities;
import me.suture_n_sorcery.suture_n_sorcery.registries.ModBlocks;
import me.suture_n_sorcery.suture_n_sorcery.registries.ModEffects;
import me.suture_n_sorcery.suture_n_sorcery.registries.ModFluids;
import me.suture_n_sorcery.suture_n_sorcery.registries.ModItems;
import me.suture_n_sorcery.suture_n_sorcery.registries.ModParticles;
import me.suture_n_sorcery.suture_n_sorcery.registries.ModScreenHandlers;
import me.suture_n_sorcery.suture_n_sorcery.registries.ModSounds;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Suture_n_sorcery implements ModInitializer {

    public static final String MOD_ID = "suture_n_sorcery";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[S&S] SUTURE & SORCERY HAS BEEN INITIALIZED.");

        try {
            registerContent();
        } catch (Exception e) {
            LOGGER.error("[S&S]{}", e.getMessage());
            throw new RuntimeException(e);
        }

        LOGGER.info("[S&S] EVERYTHING HAS BEEN REGISTERED SUCCESSFULLY.");
    }

    private static void registerContent() {
        ModFluids.registerFluids();
        ModBlocks.registerBlocks();
        ModItems.registerItems();
        ItemGroups.registerItemGroups();
        ModEffects.registerEffects();
        ModParticles.registerParticles();
        ModBlockEntities.registerBlockEntities();
        ModScreenHandlers.registerScreenHandlers();
        RitualLoomRitualHandler.registerRitualLoomRituals();
        ModSounds.registerSounds();
        BloodSenseTracker.registerBloodSenseEvents();
    }
}
