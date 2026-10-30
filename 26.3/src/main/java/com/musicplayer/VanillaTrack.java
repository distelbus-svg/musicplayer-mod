package com.musicplayer;

import net.minecraft.resources.Identifier;
import java.util.Optional;

public record VanillaTrack(
    String id,
    String title,
    String artist,
    String album,
    int durationSeconds,
    String coverKey,
    Identifier soundIdentifier,
    Identifier resolvedSoundId
) implements PlayableTrack {
    @Override public Identifier soundId() { return soundIdentifier; }
    @Override public TrackSource trackSource() {
        String check = soundIdentifier != null ? soundIdentifier.toString() : id;
        return (check.contains("music_disc") || check.contains("record/"))
            ? TrackSource.VANILLA_DISC : TrackSource.VANILLA_SOUNDTRACK;
    }
    @Override public Optional<Identifier> artwork() { return Optional.empty(); }

    public String durationText() {
        int min = durationSeconds / 60;
        int sec = durationSeconds % 60;
        return String.format("%d:%02d", min, sec);
    }
}
