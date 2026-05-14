package me.suture_n_sorcery.suture_n_sorcery.network;

import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public record VeinmakerTrailPayload(List<Cell> cells, boolean replace, int lifetimeTicks) implements CustomPayload {
    public static final CustomPayload.Id<VeinmakerTrailPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Suture_n_sorcery.MOD_ID, "veinmaker_trail"));

    public static final PacketCodec<RegistryByteBuf, VeinmakerTrailPayload> CODEC = PacketCodec.tuple(
            Cell.CODEC.collect(PacketCodecs.toList(1024)), VeinmakerTrailPayload::cells,
            PacketCodecs.BOOLEAN, VeinmakerTrailPayload::replace,
            PacketCodecs.VAR_INT, VeinmakerTrailPayload::lifetimeTicks,
            VeinmakerTrailPayload::new
    );

    public VeinmakerTrailPayload {
        cells = List.copyOf(cells);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    public record Cell(BlockPos pos, int side, int pixelX, int pixelY, int ageTicks, int groupId, boolean contained) {
        public static final PacketCodec<RegistryByteBuf, Cell> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, Cell::pos,
                PacketCodecs.VAR_INT, Cell::side,
                PacketCodecs.VAR_INT, Cell::pixelX,
                PacketCodecs.VAR_INT, Cell::pixelY,
                PacketCodecs.VAR_INT, Cell::ageTicks,
                PacketCodecs.VAR_INT, Cell::groupId,
                PacketCodecs.BOOLEAN, Cell::contained,
                Cell::new
        );
    }
}
