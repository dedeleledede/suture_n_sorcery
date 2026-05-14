package me.suture_n_sorcery.suture_n_sorcery.veinmaker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.List;

public final class VeinmakerTrailWorldState extends PersistentState {
    private static final Codec<PaintedCell> PAINTED_CELL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(PaintedCell::pos),
            Codec.INT.fieldOf("side").forGetter(PaintedCell::side),
            Codec.INT.fieldOf("pixel_x").forGetter(PaintedCell::pixelX),
            Codec.INT.fieldOf("pixel_y").forGetter(PaintedCell::pixelY),
            Codec.LONG.fieldOf("created_time").forGetter(PaintedCell::createdTime),
            Codec.INT.optionalFieldOf("group_id", 0).forGetter(PaintedCell::groupId)
    ).apply(instance, PaintedCell::new));

    private static final Codec<VeinmakerTrailWorldState> CODEC = PAINTED_CELL_CODEC.listOf()
            .fieldOf("painted_cells")
            .xmap(VeinmakerTrailWorldState::new, VeinmakerTrailWorldState::cells)
            .codec();

    public static final PersistentStateType<VeinmakerTrailWorldState> TYPE = new PersistentStateType<>(
            Suture_n_sorcery.MOD_ID + "_veinmaker_trails",
            VeinmakerTrailWorldState::new,
            CODEC,
            DataFixTypes.LEVEL
    );

    private final List<PaintedCell> cells;

    public VeinmakerTrailWorldState() {
        this(new ArrayList<>());
    }

    private VeinmakerTrailWorldState(List<PaintedCell> cells) {
        this.cells = new ArrayList<>(cells);
    }

    public List<PaintedCell> cells() {
        return cells;
    }

    public record PaintedCell(BlockPos pos, int side, int pixelX, int pixelY, long createdTime, int groupId) {
    }
}
