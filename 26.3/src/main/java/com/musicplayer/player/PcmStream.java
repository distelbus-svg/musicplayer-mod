package com.musicplayer.player;

import net.minecraft.client.sounds.AudioStream;
import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * An AudioStream that reads from a pre-decoded PCM ByteBuffer.
 * Used after seeking to play the remainder of a track from a seeked position.
 */
public class PcmStream implements AudioStream {
    private final ByteBuffer data;
    private final AudioFormat format;

    public PcmStream(ByteBuffer data, AudioFormat format) {
        this.data = data;
        this.format = format;
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        if (!data.hasRemaining()) return null;
        int toRead = Math.min(size, data.remaining());
        int pos = data.position();
        data.position(pos + toRead);
        return data.slice(pos, toRead).order(data.order());
    }

    @Override
    public void close() throws IOException {}
}
