package me.suture_n_sorcery.suture_n_sorcery.network;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record HematicBondPayload(boolean absorbed) implements CustomPayload {
    public static final CustomPayload.Id<HematicBondPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Suture_n_sorcery.MOD_ID, "hematic_bond"));

    public static final PacketCodec<RegistryByteBuf, HematicBondPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOLEAN, HematicBondPayload::absorbed,
            HematicBondPayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
