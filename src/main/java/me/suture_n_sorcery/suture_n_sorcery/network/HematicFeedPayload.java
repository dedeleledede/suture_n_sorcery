package me.suture_n_sorcery.suture_n_sorcery.network;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record HematicFeedPayload(int catalystHand, int hits, int total, boolean success) implements CustomPayload {
    public static final CustomPayload.Id<HematicFeedPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Suture_n_sorcery.MOD_ID, "hematic_feed"));

    public static final PacketCodec<RegistryByteBuf, HematicFeedPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, HematicFeedPayload::catalystHand,
            PacketCodecs.VAR_INT, HematicFeedPayload::hits,
            PacketCodecs.VAR_INT, HematicFeedPayload::total,
            PacketCodecs.BOOLEAN, HematicFeedPayload::success,
            HematicFeedPayload::new
    );

    static {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
