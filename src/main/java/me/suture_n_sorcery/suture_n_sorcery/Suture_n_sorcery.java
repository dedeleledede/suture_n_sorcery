package me.suture_n_sorcery.suture_n_sorcery;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Suture_n_sorcery implements ModInitializer {

    public static final String MOD_ID = "suture_n_sorcery";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {

        LOGGER.info("SUTURE & SORCERY HAS BEEN INITIALIZED.");

    }
}
