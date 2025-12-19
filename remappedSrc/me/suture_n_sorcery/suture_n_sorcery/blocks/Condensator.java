package me.suture_n_sorcery.suture_n_sorcery.blocks;

public static final Block CONDENSATOR = register(
        "condensator",
        Block::new,
        BlockBehaviour.Properties.of().sound(SoundType.ANVIL),
        true
);
