package com.musicplayer.port;

/**
 * Entry point for everything the shared code needs from the game. The version-specific
 * code provides the implementations in {@code com.musicplayer.McBootstrap#install()};
 * it is looked up on first use so no entrypoint ordering matters.
 */
public final class Ports {
    private static volatile boolean ready;
    private static volatile HostPort host;
    private static volatile VanillaPort vanilla;
    private static volatile SoundtrackPort soundtrack;
    private static volatile TrackRegistryPort registry;
    private static volatile ArtPort art;

    private Ports() {}

    public static synchronized void install(HostPort h, VanillaPort v, SoundtrackPort s, TrackRegistryPort r, ArtPort a) {
        host = h; vanilla = v; soundtrack = s; registry = r; art = a;
        ready = true;
    }

    private static void ensure() {
        if (ready) return;
        synchronized (Ports.class) {
            if (ready) return;
            try {
                Class.forName("com.musicplayer.McBootstrap", true, Ports.class.getClassLoader())
                        .getMethod("install").invoke(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Melodix: McBootstrap.install() missing or failed", e);
            }
            if (!ready) throw new IllegalStateException("Melodix: McBootstrap.install() did not call Ports.install");
        }
    }

    public static HostPort host() { ensure(); return host; }
    public static VanillaPort vanilla() { ensure(); return vanilla; }
    public static SoundtrackPort soundtrack() { ensure(); return soundtrack; }
    public static TrackRegistryPort registry() { ensure(); return registry; }
    public static ArtPort art() { ensure(); return art; }
}
