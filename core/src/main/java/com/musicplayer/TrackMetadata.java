package com.musicplayer;

import com.musicplayer.port.*;

public record TrackMetadata(
    String displayTitle,
    String artist,
    String album
) {}
