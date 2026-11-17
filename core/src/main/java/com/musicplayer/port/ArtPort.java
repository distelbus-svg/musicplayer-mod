package com.musicplayer.port;

import java.io.File;

/** Album art textures. Texture ids are opaque strings, e.g. "musicplayer:textures/gui/x.png". */
public interface ArtPort {
    String defaultArt();
    /** Id of the texture that holds the currently playing own-file cover. */
    String albumArtId();
    String builtInArt(String albumKey);
    String artForSong(String path, String albumKey);
    boolean hasArt();
    void clear();
    void loadForSong(File songFile);
    void loadForRemote(String path);
    void setCustomArt(String songPath, File image);
    /** Registers a PNG as texture musicplayer:<path> and returns its id string, or null if it cannot be read. */
    String registerTexture(String path, byte[] png);
}
