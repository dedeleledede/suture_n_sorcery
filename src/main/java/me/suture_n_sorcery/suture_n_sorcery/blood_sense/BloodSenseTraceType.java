package me.suture_n_sorcery.suture_n_sorcery.blood_sense;

public enum BloodSenseTraceType {
    DEATH,
    RITUAL;

    public static BloodSenseTraceType byId(int id) {
        return id == RITUAL.ordinal() ? RITUAL : DEATH;
    }
}
