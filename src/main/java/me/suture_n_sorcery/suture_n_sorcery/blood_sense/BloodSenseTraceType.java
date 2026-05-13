package me.suture_n_sorcery.suture_n_sorcery.blood_sense;

public enum BloodSenseTraceType {
    DEATH,
    RITUAL,
    ROT,
    DEEP;

    public static BloodSenseTraceType byId(int id) {
        if (id < 0 || id >= values().length) return DEATH;
        return values()[id];
    }
}
