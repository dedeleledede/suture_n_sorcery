package me.suture_n_sorcery.suture_n_sorcery.network;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record BloodSenseResponsePayload(List<Trace> traces) implements CustomPayload {
    public static final CustomPayload.Id<BloodSenseResponsePayload> ID =
            new CustomPayload.Id<>(Identifier.of(Suture_n_sorcery.MOD_ID, "blood_sense_response"));

    public static final PacketCodec<RegistryByteBuf, BloodSenseResponsePayload> CODEC =
            Trace.CODEC.collect(PacketCodecs.toList(96)).xmap(BloodSenseResponsePayload::new, BloodSenseResponsePayload::traces);

    public BloodSenseResponsePayload {
        traces = List.copyOf(traces);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    public record Trace(int type, int x, int y, int z, int strength, int ageTicks, int state) {
        public static final PacketCodec<RegistryByteBuf, Trace> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, Trace::type,
                PacketCodecs.VAR_INT, Trace::x,
                PacketCodecs.VAR_INT, Trace::y,
                PacketCodecs.VAR_INT, Trace::z,
                PacketCodecs.VAR_INT, Trace::strength,
                PacketCodecs.VAR_INT, Trace::ageTicks,
                PacketCodecs.VAR_INT, Trace::state,
                Trace::new
        );
    }
}
