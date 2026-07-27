package com.musicplayer;

import com.musicplayer.config.MusicPlayerConfig;

import java.util.Arrays;

public class AudioDSP {

    private static boolean initialized = false;

    // EQ state
    private static boolean eqEnabled;
    private static float preamp;
    private static float[] bandGains; // 5 bands

    // Reverb state
    private static boolean reverbEnabled;
    private static float reverbWet;

    // Filter chain: [channel][band]
    private static BiQuad[][] eqFilters;
    private static int currentChannels;
    private static int currentSampleRate;

    // Reverb state per channel
    private static CombFilter[][] combFilters;
    private static AllPassFilter[][] allPassFilters;

    // Config frequencies and Q for 5-band EQ
    private static final float[] BAND_FREQS = {60f, 230f, 910f, 3600f, 14000f};
    private static final float BAND_Q = 1.0f;

    public static void reset(int channels, int sampleRate) {
        currentChannels = channels;
        currentSampleRate = sampleRate;
        eqFilters = new BiQuad[channels][5];
        for (int ch = 0; ch < channels; ch++) {
            for (int b = 0; b < 5; b++) {
                float gain = bandGains != null ? bandGains[b] : 0f;
                eqFilters[ch][b] = new BiQuad(BAND_FREQS[b], sampleRate, gain, BAND_Q);
            }
        }

        // Reverb: 2 comb + 1 all-pass per channel
        int[] combDelays = {(int)(0.031f * sampleRate), (int)(0.037f * sampleRate)};
        float[] combFeedback = {0.6f, 0.55f};
        int apDelay = (int)(0.005f * sampleRate);

        combFilters = new CombFilter[channels][2];
        allPassFilters = new AllPassFilter[channels][1];
        for (int ch = 0; ch < channels; ch++) {
            for (int i = 0; i < 2; i++) {
                combFilters[ch][i] = new CombFilter(combDelays[i], combFeedback[i]);
            }
            allPassFilters[ch][0] = new AllPassFilter(apDelay, 0.5f);
        }

        initialized = true;
    }

    public static void updateFromConfig() {
        MusicPlayerConfig cfg = MusicPlayerConfig.getInstance();
        eqEnabled = cfg.isEqEnabled();
        reverbEnabled = cfg.isReverbEnabled();
        reverbWet = cfg.getReverbWet();
        preamp = cfg.getPreamp();
        bandGains = cfg.getEqGains();
    }

    public static void processSamples(short[] buffer, int offset, int length, int channels, int sampleRate) {
        updateFromConfig();

        if (!eqEnabled && !reverbEnabled) return;

        if (!initialized || channels != currentChannels || sampleRate != currentSampleRate) {
            reset(channels, sampleRate);
        }

        int samplesPerChannel = length / channels;

        for (int ch = 0; ch < channels; ch++) {
            for (int i = 0; i < samplesPerChannel; i++) {
                int idx = offset + i * channels + ch;
                float sample = buffer[idx] / 32768f;

                // Preamp
                sample *= preamp;

                // EQ
                if (eqEnabled && eqFilters != null && eqFilters[ch] != null) {
                    for (int b = 0; b < 5; b++) {
                        eqFilters[ch][b].updateGain(bandGains != null ? bandGains[b] : 0f);
                        sample = eqFilters[ch][b].process(sample);
                    }
                }

                // Reverb
                if (reverbEnabled && combFilters != null) {
                    float wet = 0;
                    for (int c = 0; c < 2; c++) {
                        wet += combFilters[ch][c].process(sample);
                    }
                    wet = allPassFilters[ch][0].process(wet);
                    sample = sample * (1f - reverbWet) + wet * reverbWet;
                }

                // Clamp and write back
                sample = Math.max(-1f, Math.min(1f, sample));
                buffer[idx] = (short) (sample * 32768f);
            }
        }
    }

    public static void processBytes(byte[] buffer, int offset, int length, int channels, int sampleRate) {
        int sampleCount = length / 2;
        short[] shortBuf = new short[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            int idx = offset + i * 2;
            shortBuf[i] = (short) ((buffer[idx + 1] << 8) | (buffer[idx] & 0xFF));
        }
        processSamples(shortBuf, 0, sampleCount, channels, sampleRate);
        for (int i = 0; i < sampleCount; i++) {
            int idx = offset + i * 2;
            buffer[idx] = (byte) (shortBuf[i] & 0xFF);
            buffer[idx + 1] = (byte) ((shortBuf[i] >> 8) & 0xFF);
        }
    }

    // Bi-quad peaking filter
    static class BiQuad {
        private float b0, b1, b2, a1, a2;
        private float x1, x2, y1, y2;
        private float freq, sampleRate, q;
        private float lastGain;

        BiQuad(float freq, float sampleRate, float gain, float q) {
            this.freq = freq;
            this.sampleRate = sampleRate;
            this.q = q;
            this.lastGain = gain;
            computeCoeffs(gain);
        }

        void updateGain(float gain) {
            if (Math.abs(gain - lastGain) > 0.01f) {
                lastGain = gain;
                computeCoeffs(gain);
            }
        }

        private void computeCoeffs(float gain) {
            float V0 = (float) Math.pow(10, gain / 20.0);
            float K = (float) Math.tan(Math.PI * freq / sampleRate);
            float norm = 1f / (1f + K / q + K * K);

            b0 = (1f + V0 * K / q + K * K) * norm;
            b1 = 2f * (K * K - 1f) * norm;
            b2 = (1f - V0 * K / q + K * K) * norm;
            a1 = b1; // Same as b1 since it's 2*(K^2-1)*norm
            a2 = (1f - K / q + K * K) * norm;
        }

        float process(float input) {
            float out = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = input;
            y2 = y1;
            y1 = out;
            return out;
        }

        void reset() {
            x1 = x2 = y1 = y2 = 0;
        }
    }

    // Comb filter for reverb
    static class CombFilter {
        private final float[] buffer;
        private int index;
        private final float feedback;

        CombFilter(int delaySamples, float feedback) {
            this.buffer = new float[Math.max(1, delaySamples)];
            this.feedback = feedback;
            this.index = 0;
        }

        float process(float input) {
            float out = buffer[index];
            buffer[index] = input + out * feedback;
            index = (index + 1) % buffer.length;
            return out;
        }
    }

    // All-pass filter for reverb
    static class AllPassFilter {
        private final float[] buffer;
        private int index;
        private final float feedback;

        AllPassFilter(int delaySamples, float feedback) {
            this.buffer = new float[Math.max(1, delaySamples)];
            this.feedback = feedback;
            this.index = 0;
        }

        float process(float input) {
            float bufOut = buffer[index];
            float out = -input + bufOut;
            buffer[index] = input + bufOut * feedback;
            index = (index + 1) % buffer.length;
            return out;
        }
    }
}
