package me.suture_n_sorcery.suture_n_sorcery.network;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record HematicFeedPayload(int catalystHand, int hits, int total, boolean success) implements CustomPayload {
    public static final CustomPayload.Id<HematicFeedPayload> ID =
            CustomPayload.id(Suture_n_sorcery.MOD_ID + "hematic_feed");

    public static final PacketCodec<RegistryByteBuf, HematicFeedPayload> CODEC =
            PacketCodec.of(HematicFeedPayload::write, HematicFeedPayload::new);

    static {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);
    }

    public HematicFeedPayload(RegistryByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    public void write(RegistryByteBuf buf) {
        buf.writeVarInt(catalystHand);
        buf.writeVarInt(hits);
        buf.writeVarInt(total);
        buf.writeBoolean(success);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}