package me.suture_n_sorcery.suture_n_sorcery.client.audio;

public final class PressurizeArm {
    private PressurizeArm() {}

    // set by the screen right before playing the press sound
    public static volatile int pressTicks = -1;

    public static int take() {
        int v = pressTicks;
        pressTicks = -1;
        return v;
    }
}