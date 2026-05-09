package me.suture_n_sorcery.suture_n_sorcery.blocks.RitualLoom;

public enum RitualLoomPhase {
    IDLE(0),
    SATURATING(1),
    SATURATED(2),
    CORE_INSERTED(3),
    PRESSURIZING(4),
    COMPLETE(5),
    FAILED(6);

    private final int id;

    RitualLoomPhase(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static RitualLoomPhase fromId(int id) {
        for (RitualLoomPhase phase : values()) {
            if (phase.id == id) return phase;
        }
        return IDLE;
    }
}
