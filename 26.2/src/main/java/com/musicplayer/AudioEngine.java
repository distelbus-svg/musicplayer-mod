package com.musicplayer;

import com.musicplayer.config.MusicPlayerConfig;
import javazoom.jl.decoder.*;
import javax.sound.sampled.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioEngine {

    private PlaybackSlot primary;
    private PlaybackSlot crossfading;

    private float volume = 1.0f;
    private float playbackSpeed = 1.0f;
    private long durationMs = 0;
    private long positionMs = 0;
    private File currentFile;
    private Runnable onFinished;
    private Runnable onTrackStart;
    private volatile Runnable pendingCallback;

    // Visualizer sample buffer (mono-mixed, updated on playback thread)
    private static final int VIZ_SAMPLES = 256;
    private final float[] vizBuffer = new float[VIZ_SAMPLES];
    private final float[] vizSmooth = new float[32];

    public void getVisualizerData(float[] out) {
        synchronized (vizBuffer) {
            int len = Math.min(out.length, VIZ_SAMPLES);
            System.arraycopy(vizBuffer, 0, out, 0, len);
        }
    }

    public void getVisualizerBars(float[] bars, int numBars) {
        synchronized (vizBuffer) {
            int groupSize = VIZ_SAMPLES / numBars;
            for (int b = 0; b < numBars; b++) {
                float sum = 0;
                for (int i = 0; i < groupSize; i++) {
                    int idx = b * groupSize + i;
                    if (idx < VIZ_SAMPLES) sum += Math.abs(vizBuffer[idx]);
                }
                float raw = sum / groupSize;
                // Exponential smoothing (faster response)
                vizSmooth[b] = vizSmooth[b] + (raw - vizSmooth[b]) * 0.4f;
                // Non-linear scaling: square root for more natural feel
                float scaled = vizSmooth[b] * 3.5f;
                bars[b] = Math.min(1f, (float) Math.sqrt(scaled));
            }
        }
    }

    // Duck state
    private volatile DuckStage duckStage = DuckStage.NONE;
    private volatile long duckStageStartMs;
    private volatile long duckHoldDurationMs;
    private volatile float duckStartMultiplier;
    private volatile float duckTargetMultiplier;
    private volatile float duckRestoreMultiplier;
    private volatile float duckMultiplier = 1.0f;
    private static final float DUCK_FADE_MS = 300f;

    private enum DuckStage { NONE, FADING_DOWN, HOLDING, FADING_UP }

    public void setOnFinished(Runnable r) { this.onFinished = r; }
    public void setOnTrackStart(Runnable r) { this.onTrackStart = r; }

    public void checkPendingCallback() {
        Runnable cb = pendingCallback;
        if (cb != null) {
            pendingCallback = null;
            cb.run();
        }
    }

    public void play(File file, long seekToMs) {
        MusicPlayerConfig cfg = MusicPlayerConfig.getInstance();
        this.playbackSpeed = cfg.getPlaybackSpeed();
        this.currentFile = file;

        if (primary != null && primary.playing && cfg.isCrossfadeEnabled() && cfg.getCrossfadeDurationMs() > 0) {
            startCrossfade(file, seekToMs, cfg.getCrossfadeDurationMs());
        } else {
            stop();
            primary = new PlaybackSlot();
            primary.fadeVolume = this.volume;
            primary.onFinished = this.onFinished;
            primary.onTrackStart = this.onTrackStart;
            primary.play(file, seekToMs, true);
        }
    }

    private void startCrossfade(File file, long seekToMs, int xfadeMs) {
        PlaybackSlot oldSlot = primary;

        // Start fade-out on old slot
        oldSlot.startFade(0, xfadeMs);

        primary = new PlaybackSlot();
        primary.onFinished = this.onFinished;
        primary.onTrackStart = this.onTrackStart;
        // Start new slot at volume 0, will fade in
        primary.fadeVolume = 0f;
        primary.play(file, seekToMs, true);
        primary.startFade(this.volume, xfadeMs);

        crossfading = oldSlot;
    }

    public void stop() {
        if (primary != null) {
            primary.stopReq = true;
            if (Thread.currentThread() != primary.thread) {
                primary.interrupt();
            }
            primary = null;
        }
        if (crossfading != null) {
            crossfading.stopReq = true;
            if (Thread.currentThread() != crossfading.thread) {
                crossfading.interrupt();
            }
            crossfading = null;
        }
    }

    public void stopPrimary() {
        if (primary != null) {
            primary.stopReq = true;
            if (Thread.currentThread() != primary.thread) {
                primary.interrupt();
            }
            primary = null;
        }
    }

    public void togglePause() {
        MusicPlayerConfig cfg = MusicPlayerConfig.getInstance();
        boolean fadeEnabled = cfg.isFadeOnPause() && cfg.getFadeDurationMs() > 0;

        if (primary == null || !primary.playing) return;

        if (fadeEnabled) {
            if (primary.pause) {
                primary.pause = false;
                primary.stopReq = false;
                primary.startFade(this.volume, cfg.getFadeDurationMs());
            } else {
                primary.startFade(0f, cfg.getFadeDurationMs());
            }
        } else {
            primary.pause = !primary.pause;
            primary.stopReq = false;
            if (primary.line != null) {
                if (primary.pause) primary.line.stop();
                else primary.line.start();
            }
        }
    }

    public void setVolume(float vol) {
        this.volume = vol;
        if (primary != null && !primary.fading) primary.applyVolume(vol);
    }

    public float getVolume() { return volume; }
    public float getPlaybackSpeed() { return playbackSpeed; }
    public boolean isActive() { return primary != null && primary.playing; }
    public boolean isPaused() { return primary != null && primary.pause; }
    public boolean isDucking() { return duckStage != DuckStage.NONE; }

    public long getDurationMs() { return primary != null ? primary.durationMs : durationMs; }
    public long getPositionMs() { return primary != null ? primary.positionMs : positionMs; }

    public void setPlaybackSpeed(float speed) {
        float clamped = Math.max(0.5f, Math.min(2.0f, speed));
        if (Math.abs(this.playbackSpeed - clamped) < 0.01f) return;
        this.playbackSpeed = clamped;
        if (isActive()) {
            long pos = primary.positionMs;
            File f = currentFile;
            stopPrimary();
            play(f, pos);
        }
    }

    public void updateMasterVolume() {
        float master = MusicPlayerConfig.getInstance().getMasterVolume();
        if (primary != null) primary.applyVolume(primary.fading ? primary.fadeVolume : this.volume * master);
        if (crossfading != null) crossfading.applyVolume(crossfading.fadeVolume * master);
    }

    public void duck(float targetVol, long holdMs, float restoreVol) {
        if (primary == null || !primary.playing) return;
        duckTargetMultiplier = targetVol / Math.max(0.01f, restoreVol);
        duckRestoreMultiplier = 1.0f;
        duckHoldDurationMs = holdMs;
        duckStartMultiplier = duckMultiplier;
        duckStage = DuckStage.FADING_DOWN;
        duckStageStartMs = System.currentTimeMillis();
    }

    void tickDuck() {
        if (duckStage == DuckStage.NONE) return;
        long now = System.currentTimeMillis();
        float elapsed = now - duckStageStartMs;

        switch (duckStage) {
            case FADING_DOWN -> {
                float t = Math.min(1f, elapsed / DUCK_FADE_MS);
                duckMultiplier = duckStartMultiplier + (duckTargetMultiplier - duckStartMultiplier) * t;
                if (primary != null && !primary.fading) primary.applyVolume(AudioEngine.this.volume);
                if (t >= 1f) {
                    duckMultiplier = duckTargetMultiplier;
                    duckStage = DuckStage.HOLDING;
                    duckStageStartMs = now;
                }
            }
            case HOLDING -> {
                if (primary != null && !primary.fading) primary.applyVolume(AudioEngine.this.volume);
                if (elapsed >= duckHoldDurationMs) {
                    duckStage = DuckStage.FADING_UP;
                    duckStageStartMs = now;
                    duckStartMultiplier = duckMultiplier;
                }
            }
            case FADING_UP -> {
                float t = Math.min(1f, elapsed / DUCK_FADE_MS);
                duckMultiplier = duckStartMultiplier + (duckRestoreMultiplier - duckStartMultiplier) * t;
                if (primary != null && !primary.fading) primary.applyVolume(AudioEngine.this.volume);
                if (t >= 1f) {
                    duckMultiplier = duckRestoreMultiplier;
                    duckStage = DuckStage.NONE;
                }
            }
        }
    }

    // ─── Playback Slot ───

    private class PlaybackSlot {
        SourceDataLine line;
        Thread thread;
        volatile boolean playing;
        volatile boolean pause;
        volatile boolean stopReq;
        float fadeVolume = 1.0f;
        boolean fading;
        float fadeTarget;
        long fadeStartMs, fadeDurationMs;
        float fadeStartVol;
        // Per-sample fade ramp (replaces the coarse MASTER_GAIN ramp for smoothness)
        float fadeGain = 1.0f;
        long fadeSamplesProcessed;
        long fadeTotalSamples;
        int fadeSampleRate = 44100;
        long durationMs, positionMs;
        Runnable onFinished;
        Runnable onTrackStart;

        void play(File file, long seekToMs, boolean startThread) {
            if (startThread) {
                thread = new Thread(() -> runPlayback(file, seekToMs), "MusicPlayback");
                thread.setDaemon(true);
                thread.start();
            }
        }

        void interrupt() {
            if (thread != null) {
                try { thread.join(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }

        void startFade(float target, long ms) {
            this.fadeStartVol = this.fadeVolume;
            this.fadeTarget = target;
            this.fadeDurationMs = Math.max(1, ms);
            this.fadeStartMs = System.currentTimeMillis();
            this.fading = true;
            this.fadeSamplesProcessed = 0;
            this.fadeTotalSamples = Math.max(1, (this.fadeSampleRate * this.fadeDurationMs) / 1000L);
            this.fadeGain = this.fadeStartVol;
        }

        void applyVolume(float vol) {
            // Volume is now applied per-sample in applyFadeToBuffer/applyFadeToBytes for
            // smooth ramping. Keep the line's MASTER_GAIN at unity (0 dB) so there's no
            // double gain staging and maximum headroom.
            if (line != null && line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                if (gain.getMinimum() <= 0f && 0f <= gain.getMaximum()) {
                    gain.setValue(0f);
                }
            }
        }

        void advanceFade(int sampleCount) {
            if (!fading) {
                fadeGain = fadeVolume;
                return;
            }
            fadeSamplesProcessed += sampleCount;
            float t = Math.min(1f, (float) fadeSamplesProcessed / (float) fadeTotalSamples);
            fadeGain = fadeStartVol + (fadeTarget - fadeStartVol) * t;
            if (t >= 1f) {
                fading = false;
                fadeGain = fadeTarget;
                fadeVolume = fadeTarget;
                if (fadeTarget == 0f) {
                    pause = true;
                }
                applyVolume(fadeVolume);
            }
        }

        private float currentOutputGain() {
            float master = MusicPlayerConfig.getInstance().getMasterVolume();
            float duck = AudioEngine.this.duckMultiplier;
            return fadeGain * AudioEngine.this.volume * master * duck;
        }

        private void applyFadeToBuffer(short[] buf, int offset, int len) {
            float base = currentOutputGain();
            if (base >= 0.999f && base <= 1.001f) return;
            for (int i = 0; i < len; i++) {
                buf[offset + i] = (short) (buf[offset + i] * base);
            }
        }

        private void applyFadeToBytes(byte[] buf, int offset, int len) {
            float base = currentOutputGain();
            if (base >= 0.999f && base <= 1.001f) return;
            int samples = len / 2;
            for (int i = 0; i < samples; i++) {
                int idx = offset + i * 2;
                short s = (short) ((buf[idx + 1] << 8) | (buf[idx] & 0xFF));
                s = (short) (s * base);
                buf[idx] = (byte) (s & 0xFF);
                buf[idx + 1] = (byte) ((s >> 8) & 0xFF);
            }
        }

        private void runPlayback(File file, long seekToMs) {
            playing = true;
            pause = false;
            stopReq = false;

            if (onTrackStart != null) onTrackStart.run();

            try {
                if (file.getName().toLowerCase().endsWith(".mp3")) playMp3(file, seekToMs);
                else if (file.getName().toLowerCase().endsWith(".wav")) playWav(file, seekToMs);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                playing = false;
                cleanupLine();
                if (!stopReq && onFinished != null) {
                    pendingCallback = onFinished;
                }
            }
        }

        private void cleanupLine() {
            if (line != null) {
                try {
                    if (line.isOpen()) {
                        line.drain();
                        line.close();
                    }
                } catch (Exception ignored) {}
                line = null;
            }
        }

        private void tickFade() {
            if (fading) {
                advanceFade(0);
            } else {
                fadeGain = fadeVolume;
            }
        }

        private void tickCrossfade() {
            if (crossfading == null || crossfading.playing) return;
            // Crossfaded slot finished: clean up
            crossfading = null;
        }

        private void playMp3(File file, long seekToMs) throws Exception {
            try (FileInputStream fis = new FileInputStream(file)) {
                Bitstream bitstream = new Bitstream(fis);
                Decoder decoder = new Decoder();

                durationMs = estimateMp3Duration(file);

                if (seekToMs > 0) {
                    long curMs = 0;
                    while (curMs < seekToMs) {
                        Header h = bitstream.readFrame();
                        if (h == null) break;
                        curMs += h.ms_per_frame();
                        bitstream.closeFrame();
                    }
                    positionMs = curMs;
                } else {
                    positionMs = 0;
                }

                Header header = bitstream.readFrame();
                if (header == null) return;

                AudioFormat fmt = new AudioFormat(header.frequency() * playbackSpeed, 16,
                    header.mode() == Header.SINGLE_CHANNEL ? 1 : 2, true, false);
                line = AudioSystem.getSourceDataLine(fmt);
                line.open(fmt);
                line.start();
                fadeSampleRate = (int)(header.frequency() * playbackSpeed);
                applyVolume(fadeVolume);
                fadeGain = fadeVolume;

                while (header != null && !stopReq) {
                    if (pause) { Thread.sleep(50); continue; }

                    AudioEngine.this.tickDuck();

                    SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                    short[] buf = output.getBuffer();
                    int ch = header.mode() == Header.SINGLE_CHANNEL ? 1 : 2;

                    int bufLen = output.getBufferLength();
                    synchronized (vizBuffer) {
                        float scale = ch > 1 ? 0.5f : 1f;
                        int step = Math.max(1, bufLen / VIZ_SAMPLES);
                        for (int i = 0, vi = 0; vi < VIZ_SAMPLES && i < bufLen; i += step, vi++) {
                            float sum = 0;
                            int count = 0;
                            for (int j = 0; j < step && i + j < bufLen; j++) {
                                sum += buf[i + j] * scale;
                                count++;
                            }
                            vizBuffer[vi] = (count > 0) ? sum / count / 32768f : 0;
                        }
                    }
                    AudioDSP.processSamples(buf, 0, output.getBufferLength(), ch, fadeSampleRate);
                    advanceFade(bufLen);
                    applyFadeToBuffer(buf, 0, bufLen);
                    byte[] bytes = new byte[output.getBufferLength() * 2];
                    for (int i = 0; i < output.getBufferLength(); i++) {
                        bytes[i * 2] = (byte) (buf[i] & 0xFF);
                        bytes[i * 2 + 1] = (byte) ((buf[i] >> 8) & 0xFF);
                    }
                    line.write(bytes, 0, bytes.length);
                    positionMs += (long) header.ms_per_frame();
                    bitstream.closeFrame();
                    header = bitstream.readFrame();
                }

                cleanupLine();
                bitstream.close();
            }
        }

        private void playWav(File file, long seekToMs) throws Exception {
            AudioInputStream ais = AudioSystem.getAudioInputStream(file);
            AudioFormat orig = ais.getFormat();
            AudioFormat fmt = new AudioFormat(
                orig.getSampleRate() * playbackSpeed, orig.getSampleSizeInBits(),
                orig.getChannels(), true, orig.isBigEndian());
            line = AudioSystem.getSourceDataLine(fmt);
            line.open(fmt);
            line.start();
            fadeSampleRate = (int)(orig.getSampleRate() * playbackSpeed);
            applyVolume(fadeVolume);
            fadeGain = fadeVolume;

            durationMs = ais.getFrameLength() * 1000 / (long) orig.getFrameRate();

            if (seekToMs > 0) {
                long skip = (seekToMs * orig.getFrameSize() * (long) orig.getFrameRate()) / 1000;
                ais.skip(skip);
                positionMs = seekToMs;
            } else {
                positionMs = 0;
            }

            byte[] buf = new byte[4096];
            int n;
            long total = 0;
            long frameSize = orig.getFrameSize();
            long frameRate = (long) orig.getFrameRate();

            while ((n = ais.read(buf)) != -1 && !stopReq) {
                if (pause) { Thread.sleep(50); continue; }
                AudioEngine.this.tickDuck();
                AudioDSP.processBytes(buf, 0, n, orig.getChannels(), fadeSampleRate);
                int sampleCount = n / 2;
                advanceFade(sampleCount);
                applyFadeToBytes(buf, 0, n);
                line.write(buf, 0, n);
                total += n;
                positionMs = total * 1000L / (frameSize * frameRate);
            }

            cleanupLine();
            ais.close();
        }

        private long estimateMp3Duration(File file) {
            try {
                org.jaudiotagger.audio.AudioFile af = org.jaudiotagger.audio.AudioFileIO.read(file);
                org.jaudiotagger.audio.AudioHeader h = af.getAudioHeader();
                if (h != null) {
                    int seconds = h.getTrackLength();
                    if (seconds > 0) return seconds * 1000L;
                }
            } catch (Exception ignored) {}
            return 0;
        }
    }

    // YouTube stub
    public void play(String url, long seekToMs) {}
}
