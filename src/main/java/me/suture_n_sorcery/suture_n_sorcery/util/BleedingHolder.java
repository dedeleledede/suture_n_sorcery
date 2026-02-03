package me.suture_n_sorcery.suture_n_sorcery.util;

public interface BleedingHolder {
    float suture_n_sorcery$getBleedStoredDamage();
    void suture_n_sorcery$setBleedStoredDamage(float value);

    default void suture_n_sorcery$addBleedStoredDamage(float add) {
        suture_n_sorcery$setBleedStoredDamage(suture_n_sorcery$getBleedStoredDamage() + add);
    }
}
