package com.musicplayer;

import net.minecraft.resources.Identifier;
import java.util.Optional;

public interface PlayableTrack {
    Identifier soundId();
    String title();
    String artist();
    String album();
    int durationSeconds();
    String coverKey();
    Optional<Identifier> artwork();
    TrackSource trackSource();

    enum TrackSource {
        USER_LOCAL,
        VANILLA_SOUNDTRACK,
        VANILLA_DISC
    }
}
