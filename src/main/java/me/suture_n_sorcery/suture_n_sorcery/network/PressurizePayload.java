package me.suture_n_sorcery.suture_n_sorcery.network;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record PressurizePayload(BlockPos pos, boolean pressed) implements CustomPayload {
    public static final Id<PressurizePayload> ID = new Id<>(Identifier.of(Suture_n_sorcery.MOD_ID, "ritual_loom_pressurize"));

    public static final PacketCodec<RegistryByteBuf, PressurizePayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, PressurizePayload::pos,
            PacketCodecs.BOOLEAN, PressurizePayload::pressed,
            PressurizePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
