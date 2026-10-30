package com.musicplayer.player;

import com.musicplayer.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import javax.sound.sampled.AudioFormat;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Decodes a vanilla OGG soundtrack from a seeked time position to the end,
 * returning a ByteBuffer of 16-bit signed little-endian PCM data plus the AudioFormat.
 */
public class FullPcmDecoder {

    public static Result decodeSeeked(Identifier oggResId, float seconds) {
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(oggResId);
            if (res.isEmpty()) return null;
            byte[] raw;
            try (InputStream in = res.get().open()) {
                raw = in.readAllBytes();
            }
            return decodeBytesSeeked(raw, seconds);
        } catch (Exception e) {
            DebugLog.info("FullPcmDecoder: %s", e.getMessage());
            return null;
        }
    }

    private static Result decodeBytesSeeked(byte[] raw, float seconds) {
        try {
            Class<?> vfClass = Class.forName("com.jcraft.jorbis.VorbisFile");
            Object vf = vfClass.getConstructor(InputStream.class, byte[].class, int.class)
                .newInstance(new java.io.ByteArrayInputStream(raw), null, 0);

            Method getInfoM = vfClass.getMethod("getInfo", int.class);
            Object info = getInfoM.invoke(vf, -1);
            int rate = info.getClass().getField("rate").getInt(info);
            int channels = info.getClass().getField("channels").getInt(info);

            long seekPcmSample = (long)(seconds * rate + 0.5f);
            Method pcmSeekM = vfClass.getMethod("pcmSeek", long.class);
            pcmSeekM.invoke(vf, seekPcmSample);

            AudioFormat format = new AudioFormat(rate, 16, channels, true, false);

            Method readM = findReadMethod(vfClass);
            if (readM == null) { close(vf, vfClass); return null; }
            readM.setAccessible(true);

            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(8192);
            byte[] pcmBuf = new byte[8192];
            int[] bitstream = new int[1];

            while (true) {
                int ret = (int) readM.invoke(vf, pcmBuf, pcmBuf.length, 0, 2, 1, bitstream);
                if (ret <= 0) break;
                out.write(pcmBuf, 0, ret);
            }

            close(vf, vfClass);

            byte[] pcmBytes = out.toByteArray();
            ByteBuffer buf = ByteBuffer.allocateDirect(pcmBytes.length);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            buf.put(pcmBytes);
            buf.flip();

            return new Result(buf, format);
        } catch (Exception e) {
            DebugLog.info("FullPcmDecoder.decodeBytesSeeked: %s", e.getMessage());
            return null;
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

    public record Result(ByteBuffer pcmData, AudioFormat format) {}
}
