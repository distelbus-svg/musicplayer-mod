package com.musicplayer;

import com.musicplayer.config.MusicPlayerConfig;

/**
 * Per-playback-slot DSP chain (5-band peaking EQ + simple reverb).
 * One instance per playback thread, so crossfading slots never share filter state.
 * Settings are re-read only when the config revision changes.
 */
public class AudioDSP {

    private static final int BANDS = 5;
    private static final float[] BAND_FREQS = {60f, 230f, 910f, 3600f, 14000f};
    private static final float BAND_Q = 1.0f;

    private boolean eqEnabled;
    private boolean reverbEnabled;
    private float reverbWet;
    private float preamp = 1f;
    private final float[] bandGains = new float[BANDS];
    private int configRevision = -1;

    private int channels = -1;
    private int sampleRate = -1;
    private BiQuad[][] eqFilters;
    private CombFilter[][] combFilters;
    private AllPassFilter[][] allPassFilters;

    private short[] scratch = new short[0];

    /** Kept for callers; instances poll the config revision themselves. */
    public static void updateFromConfig() {
    }

    private void refreshConfig() {
        MusicPlayerConfig cfg = MusicPlayerConfig.getInstance();
        int rev = cfg.getRevision();
        if (rev == configRevision) return;
        configRevision = rev;
        eqEnabled = cfg.isEqEnabled();
        reverbEnabled = cfg.isReverbEnabled();
        reverbWet = cfg.getReverbWet();
        preamp = cfg.getPreamp();
        float[] g = cfg.getEqGains();
        System.arraycopy(g, 0, bandGains, 0, BANDS);
        if (eqFilters != null) {
            for (BiQuad[] perChannel : eqFilters) {
                for (int b = 0; b < BANDS; b++) perChannel[b].setGain(bandGains[b]);
            }
        }
    }

    private void init(int channels, int sampleRate) {
        this.channels = channels;
        this.sampleRate = sampleRate;

        eqFilters = new BiQuad[channels][BANDS];
        for (int ch = 0; ch < channels; ch++) {
            for (int b = 0; b < BANDS; b++) {
                eqFilters[ch][b] = new BiQuad(BAND_FREQS[b], sampleRate, bandGains[b], BAND_Q);
            }
        }

        int[] combDelays = {(int) (0.031f * sampleRate), (int) (0.037f * sampleRate)};
        float[] combFeedback = {0.6f, 0.55f};
        int apDelay = (int) (0.005f * sampleRate);

        combFilters = new CombFilter[channels][2];
        allPassFilters = new AllPassFilter[channels][1];
        for (int ch = 0; ch < channels; ch++) {
            for (int i = 0; i < 2; i++) {
                combFilters[ch][i] = new CombFilter(combDelays[i], combFeedback[i]);
            }
            allPassFilters[ch][0] = new AllPassFilter(apDelay, 0.5f);
        }
    }

    public void processSamples(short[] buffer, int offset, int length, int channels, int sampleRate) {
        refreshConfig();
        if (!eqEnabled && !reverbEnabled) return;
        if (channels <= 0) return;

        if (eqFilters == null || channels != this.channels || sampleRate != this.sampleRate) {
            init(channels, sampleRate);
        }

        final boolean eq = eqEnabled;
        final boolean reverb = reverbEnabled;
        final float wetMix = reverbWet;
        final float dryMix = 1f - reverbWet;
        final int frames = length / channels;

        for (int ch = 0; ch < channels; ch++) {
            BiQuad[] eqCh = eqFilters[ch];
            CombFilter c0 = combFilters[ch][0];
            CombFilter c1 = combFilters[ch][1];
            AllPassFilter ap = allPassFilters[ch][0];

            int idx = offset + ch;
            for (int i = 0; i < frames; i++, idx += channels) {
                float sample = buffer[idx] * (1f / 32768f) * preamp;

                if (eq) {
                    for (int b = 0; b < BANDS; b++) {
                        sample = eqCh[b].process(sample);
                    }
                }

                if (reverb) {
                    float wet = c0.process(sample) + c1.process(sample);
                    wet = ap.process(wet);
                    sample = sample * dryMix + wet * wetMix;
                }

                if (sample > 1f) sample = 1f;
                else if (sample < -1f) sample = -1f;
                buffer[idx] = (short) (sample * 32767f);
            }
        }
    }

    /** 16-bit little-endian PCM variant. */
    public void processBytes(byte[] buffer, int offset, int length, int channels, int sampleRate) {
        int sampleCount = length / 2;
        if (scratch.length < sampleCount) scratch = new short[sampleCount];
        for (int i = 0, idx = offset; i < sampleCount; i++, idx += 2) {
            scratch[i] = (short) ((buffer[idx + 1] << 8) | (buffer[idx] & 0xFF));
        }
        processSamples(scratch, 0, sampleCount, channels, sampleRate);
        for (int i = 0, idx = offset; i < sampleCount; i++, idx += 2) {
            buffer[idx] = (byte) (scratch[i] & 0xFF);
            buffer[idx + 1] = (byte) ((scratch[i] >> 8) & 0xFF);
        }
    }

    // Bi-quad peaking filter
    static final class BiQuad {
        private float b0, b1, b2, a1, a2;
        private float x1, x2, y1, y2;
        private final float freq, sampleRate, q;
        private float lastGain;

        BiQuad(float freq, float sampleRate, float gain, float q) {
            this.freq = freq;
            this.sampleRate = sampleRate;
            this.q = q;
            this.lastGain = gain;
            computeCoeffs(gain);
        }

        void setGain(float gain) {
            if (Math.abs(gain - lastGain) > 0.01f) {
                lastGain = gain;
                computeCoeffs(gain);
            }
        }

        private void computeCoeffs(float gain) {
            float v0 = (float) Math.pow(10, gain / 20.0);
            float k = (float) Math.tan(Math.PI * freq / sampleRate);
            float norm = 1f / (1f + k / q + k * k);

            b0 = (1f + v0 * k / q + k * k) * norm;
            b1 = 2f * (k * k - 1f) * norm;
            b2 = (1f - v0 * k / q + k * k) * norm;
            a1 = b1;
            a2 = (1f - k / q + k * k) * norm;
        }

        float process(float input) {
            float out = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = input;
            y2 = y1;
            y1 = out;
            return out;
        }
    }

    // Comb filter for reverb
    static final class CombFilter {
        private final float[] buffer;
        private int index;
        private final float feedback;

        CombFilter(int delaySamples, float feedback) {
            this.buffer = new float[Math.max(1, delaySamples)];
            this.feedback = feedback;
        }

        float process(float input) {
            float out = buffer[index];
            buffer[index] = input + out * feedback;
            if (++index == buffer.length) index = 0;
            return out;
        }
    }

    // All-pass filter for reverb
    static final class AllPassFilter {
        private final float[] buffer;
        private int index;
        private final float feedback;

        AllPassFilter(int delaySamples, float feedback) {
            this.buffer = new float[Math.max(1, delaySamples)];
            this.feedback = feedback;
        }

        float process(float input) {
            float bufOut = buffer[index];
            float out = -input + bufOut;
            buffer[index] = input + bufOut * feedback;
            if (++index == buffer.length) index = 0;
            return out;
        }
    }
}
