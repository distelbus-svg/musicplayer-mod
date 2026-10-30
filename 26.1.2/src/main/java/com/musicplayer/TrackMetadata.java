package com.musicplayer;

import net.minecraft.resources.Identifier;

public record TrackMetadata(
    String displayTitle,
    String artist,
    String album
) {}
