package com.musicplayer;

import com.musicplayer.port.*;

import java.io.Closeable;
import java.io.InputStream;

/**
 * An opened audio stream supplied by an addon (e.g. YouTube).
 *
 * The engine reads raw interleaved 16-bit signed little-endian PCM from {@link #pcm()} and runs it
 * through the normal pipeline (DSP/EQ, fade, duck, crossfade, visualizer), so addons never need to
 * reimplement playback. Closing the source must release any underlying process or connection and
 * unblock a pending {@code pcm().read()}.
 */
public interface StreamSource extends Closeable {

    /** Raw interleaved s16le PCM. */
    InputStream pcm();

    int sampleRate();

    int channels();

    /** Total duration in milliseconds, or 0 if unknown. */
    long durationMs();

    /**
     * Position in milliseconds that the stream actually starts at. Normally the seek position that
     * was requested in {@link StreamSourceProvider#open}, but 0 if the source could not seek.
     */
    long startMs();
}
