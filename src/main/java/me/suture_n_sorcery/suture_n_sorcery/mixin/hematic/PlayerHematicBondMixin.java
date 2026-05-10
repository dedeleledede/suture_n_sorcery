package me.suture_n_sorcery.suture_n_sorcery.mixin.hematic;

import me.suture_n_sorcery.suture_n_sorcery.util.HematicBondHolder;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntity.class)
public abstract class PlayerHematicBondMixin implements HematicBondHolder {
    @Unique
    private boolean suture_n_sorcery$absorbedHematicCatalyst;

    @Override
    public boolean suture_n_sorcery$hasAbsorbedHematicCatalyst() {
        return this.suture_n_sorcery$absorbedHematicCatalyst;
    }

    @Override
    public void suture_n_sorcery$setAbsorbedHematicCatalyst(boolean absorbed) {
        this.suture_n_sorcery$absorbedHematicCatalyst = absorbed;
    }
}
