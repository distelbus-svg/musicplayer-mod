package com.musicplayer.port;

import com.musicplayer.VanillaTrack;
import java.util.List;

/** The list of Minecraft soundtrack tracks found in the loaded resources. */
public interface TrackRegistryPort {
    List<VanillaTrack> discoverTracks();
    void resetDiscovery();
    int trackCount();
    List<VanillaTrack> getAllTracks();
}
