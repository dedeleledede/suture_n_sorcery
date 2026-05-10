package me.suture_n_sorcery.suture_n_sorcery.network;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BloodSenseRequestPayload() implements CustomPayload {
    public static final CustomPayload.Id<BloodSenseRequestPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Suture_n_sorcery.MOD_ID, "blood_sense_request"));

    public static final PacketCodec<RegistryByteBuf, BloodSenseRequestPayload> CODEC =
            PacketCodec.unit(new BloodSenseRequestPayload());

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
