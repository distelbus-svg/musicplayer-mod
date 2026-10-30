package com.musicplayer.player;

import com.musicplayer.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;

public class VanillaPCMCache {
    private static final int TARGET_RATE = 4000;
    private static final int PEAKS_PER_WINDOW = 16;

    private float[][] amplitudeEnvelope; // [windowIdx][peakIdx] — 16 sub-peaks per 100ms window
    private int totalWindows;
    private int sampleRate;

    private VanillaPCMCache() {}

    public static VanillaPCMCache decode(Identifier oggResId) {
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(oggResId);
            if (res.isEmpty()) return null;
            byte[] raw;
            try (InputStream in = res.get().open()) {
                raw = in.readAllBytes();
            }
            return decodeBytes(raw);
        } catch (Exception e) {
            DebugLog.info("VanillaPCMCache.decode: %s", e.getMessage());
            return null;
        }
    }

    private static VanillaPCMCache decodeBytes(byte[] raw) {
        try {
            Class<?> vfClass = Class.forName("com.jcraft.jorbis.VorbisFile");
            Object vf = vfClass.getConstructor(java.io.InputStream.class, byte[].class, int.class)
                .newInstance(new java.io.ByteArrayInputStream(raw), null, 0);

            Method getInfoM = vfClass.getMethod("getInfo", int.class);
            Object info = getInfoM.invoke(vf, -1);
            int rate = info.getClass().getField("rate").getInt(info);
            int channels = info.getClass().getField("channels").getInt(info);

            Method readM = findReadMethod(vfClass);
            if (readM == null) { close(vf, vfClass); return null; }
            readM.setAccessible(true);

            int decimation = Math.max(1, rate / TARGET_RATE);
            int bufSize = 8192;
            byte[] pcmBuf = new byte[bufSize];
            int[] bitstream = new int[1];

            int framesPerWindow = rate / 10;
            int framesPerPeak = framesPerWindow / PEAKS_PER_WINDOW;
            java.util.ArrayList<float[]> amps = new java.util.ArrayList<>(2048);
            int windowIdx = 0;
            int peakIdx = 0;
            int framesInPeak = 0;
            float[] currentPeaks = new float[PEAKS_PER_WINDOW];
            float peakMax = 0;

            while (true) {
                int ret = (int) readM.invoke(vf, pcmBuf, bufSize, 0, 2, 1, bitstream);
                if (ret <= 0) break;

                int totalShorts = ret / 2;
                int maxByte = Math.min(ret, bufSize);
                for (int offset = 0; offset + channels <= totalShorts; offset += channels * decimation) {
                    float groupPeak = 0;
                    for (int f = 0; f < decimation; f++) {
                        int frameBase = offset + f * channels;
                        if (frameBase + channels > totalShorts) break;
                        for (int ch = 0; ch < channels; ch++) {
                            int byteIdx = (frameBase + ch) * 2;
                            if (byteIdx + 1 >= maxByte) break;
                            short s = (short) ((pcmBuf[byteIdx] & 0xFF) | (pcmBuf[byteIdx + 1] << 8));
                            float amp = Math.abs(s / 32768f);
                            if (amp > groupPeak) groupPeak = amp;
                        }
                    }
                    if (groupPeak > peakMax) peakMax = groupPeak;
                    framesInPeak += decimation;

                    if (framesInPeak >= framesPerPeak) {
                        currentPeaks[peakIdx] = peakMax;
                        peakIdx++;
                        peakMax = 0;
                        framesInPeak = 0;

                        if (peakIdx >= PEAKS_PER_WINDOW) {
                            amps.add(currentPeaks.clone());
                            windowIdx++;
                            peakIdx = 0;
                            peakMax = 0;
                            Arrays.fill(currentPeaks, 0);
                        }
                    }
                }
            }

            if (peakIdx > 0) {
                currentPeaks[peakIdx] = peakMax;
                amps.add(currentPeaks.clone());
                windowIdx++;
            }

            close(vf, vfClass);

            VanillaPCMCache cache = new VanillaPCMCache();
            cache.sampleRate = rate;
            cache.totalWindows = windowIdx;
            cache.amplitudeEnvelope = amps.toArray(new float[0][]);
            DebugLog.info("VanillaPCMCache: %d windows, %d rate, %d channels, %d decimation, %d peaks/win",
                windowIdx, rate, channels, decimation, PEAKS_PER_WINDOW);
            return cache;
        } catch (Exception e) {
            DebugLog.info("VanillaPCMCache.decode: %s", e.getMessage());
            return null;
        }
    }

    public void getBars(float[] bars, int numBars, long elapsedMs) {
        if (amplitudeEnvelope == null || totalWindows == 0) return;
        int currentWindow = (int) (elapsedMs / 100);
        if (currentWindow < 0) currentWindow = 0;
        if (currentWindow >= totalWindows) currentWindow = totalWindows - 1;

        float[] peaks = amplitudeEnvelope[currentWindow];
        for (int i = 0; i < numBars; i++) {
            int p = (i * PEAKS_PER_WINDOW) / numBars;
            if (p >= PEAKS_PER_WINDOW) p = PEAKS_PER_WINDOW - 1;
            float amp = peaks[p];
            bars[i] = Math.min(1f, (float) Math.sqrt(amp * 3.5f));
        }
    }

    private static Method findReadMethod(Class<?> cls) {
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals("read") && m.getParameterCount() == 6) {
                Class<?>[] params = m.getParameterTypes();
                if (params[0] == byte[].class && params[1] == int.class
                    && params[2] == int.class && params[3] == int.class
                    && params[4] == int.class && params[5] == int[].class) {
                    return m;
                }
            }
        }
        return null;
    }

    private static void close(Object vf, Class<?> cls) {
        try {
            cls.getMethod("close").invoke(vf);
        } catch (Exception ignored) {}
    }
}
