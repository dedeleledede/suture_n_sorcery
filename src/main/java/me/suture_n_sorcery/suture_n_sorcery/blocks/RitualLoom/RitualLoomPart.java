package me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom;

import net.minecraft.util.StringIdentifiable;

public enum RitualLoomPart implements StringIdentifiable {
    MAIN("main"),
    EXTENSION("extension");

    private final String id;

    RitualLoomPart(String id) {
        this.id = id;
    }

    @Override
    public String asString() {
        return this.id;
    }
}