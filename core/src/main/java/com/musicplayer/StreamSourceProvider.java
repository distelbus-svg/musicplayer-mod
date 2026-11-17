package com.musicplayer;

import com.musicplayer.port.*;

import java.io.IOException;

/**
 * Supplies {@link StreamSource}s for song paths that are not local files (for example YouTube URLs).
 * Register an implementation with {@link MelodixApi#registerStreamSourceProvider}.
 */
public interface StreamSourceProvider {

    /** Whether this provider can play the given {@code SongInfo.getPath()}. Must be cheap. */
    boolean accepts(String path);

    /**
     * Opens the stream. Called on the playback thread, so it may block on network or processes.
     * Throw {@link IOException} with a user-readable message on failure; it is shown as a notification.
     *
     * @param seekToMs requested start position; implementations that cannot seek report 0 via
     *                 {@link StreamSource#startMs()}
     */
    StreamSource open(String path, long seekToMs) throws IOException;
}
