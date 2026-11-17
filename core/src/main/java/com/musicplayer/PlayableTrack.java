package com.musicplayer;

import com.musicplayer.port.*;

import java.util.Optional;

public interface PlayableTrack {
    String soundId();
    String title();
    String artist();
    String album();
    int durationSeconds();
    String coverKey();
    Optional<String> artwork();
    TrackSource trackSource();

    enum TrackSource {
        USER_LOCAL,
        VANILLA_SOUNDTRACK,
        VANILLA_DISC
    }
}
