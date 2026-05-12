package me.suture_n_sorcery.suture_n_sorcery.blood_sense;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.suture_n_sorcery.suture_n_sorcery.Suture_n_sorcery;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.List;

public final class BloodSenseWorldState extends PersistentState {

    private static final Codec<StoredTrace> STORED_TRACE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("type").forGetter(StoredTrace::type),
            BlockPos.CODEC.fieldOf("pos").forGetter(StoredTrace::pos),
            Codec.LONG.fieldOf("created_time").forGetter(StoredTrace::createdTime),
            Codec.INT.fieldOf("strength").forGetter(StoredTrace::strength),
            Codec.INT.optionalFieldOf("state", BloodSenseTracker.STATE_HIDDEN).forGetter(StoredTrace::state)
    ).apply(instance, StoredTrace::new));

    private static final Codec<BloodSenseWorldState> CODEC = STORED_TRACE_CODEC.listOf()
            .fieldOf("traces")
            .xmap(BloodSenseWorldState::new, BloodSenseWorldState::traces)
            .codec();

    public static final PersistentStateType<BloodSenseWorldState> TYPE = new PersistentStateType<>(
            Suture_n_sorcery.MOD_ID + "_blood_sense",
            BloodSenseWorldState::new,
            CODEC,
            DataFixTypes.LEVEL
    );

    private final List<StoredTrace> traces;

    public BloodSenseWorldState() {
        this(new ArrayList<>());
    }

    private BloodSenseWorldState(List<StoredTrace> traces) {
        this.traces = new ArrayList<>(traces);
    }

    public List<StoredTrace> traces() {
        return traces;
    }

    public record StoredTrace(int type, BlockPos pos, long createdTime, int strength, int state) {
    }
}
