package com.musicplayer;

import com.musicplayer.port.*;

import com.musicplayer.config.MusicPlayerConfig;
import com.musicplayer.config.MusicPlayerConfig.PlaybackMode;

import java.io.File;
import java.util.*;

/**
 * High-level music playback manager (singleton).
 * Orchestrates the playlist, playback modes, and delegates to AudioEngine or VanillaMusicPlayer.
 */
public class MusicPlayerManager {

    public enum SongSource { USER, MINECRAFT }

    private static final MusicPlayerManager INSTANCE = new MusicPlayerManager();

    private final AudioEngine engine = new AudioEngine();
    private final MusicPlayerConfig config = MusicPlayerConfig.getInstance();

    private final List<SongInfo> playlist = new ArrayList<>();
    private final List<Integer> shuffleOrder = new ArrayList<>();
    private int currentIndex = -1;
    private int shuffleIndex = -1;
    private boolean loaded = false;
    private Playlist currentCustomPlaylist = null;

    // Vanilla music support
    private SongSource currentSource = SongSource.USER;
    private final List<VanillaTrack> vanillaTracks = new ArrayList<>();
    private int vanillaIndex = -1;

    // The Minecraft songs next/previous/shuffle move through: the list shown on the Minecraft tab
    // (for example one category), or every soundtrack track while this is empty.
    private final List<VanillaTrack> vanillaQueue = new ArrayList<>();
    private final List<Integer> vanillaShuffle = new ArrayList<>();
    private int vanillaShufflePos = -1;

    // Queue persistence: pending restore after reloadLibraryFromDisk
    private boolean pendingQueueRestore = false;

    // Reentrancy guard: nextTrack() must not recurse through the
    // onTrackFinished → nextTrack → playVanillaTrack → playTrack
    // → createMusicInstance()=null → pendingFailedAdvance → onTrackFinished chain.
    private boolean nextTrackInProgress;

    // Tracks whether the current song from a custom playlist is a vanilla track,
    // so engine-routing methods know which player to use.
    private boolean currentSongIsVanilla;

    // Cache for "all available songs" used by the edit-playlist panel.
    // Invalidated when the music folder is rescanned.
    private List<SongInfo> cachedEditAllSongs = null;
    private boolean editAllSongsCacheValid = false;

    private MusicPlayerManager() {
        engine.setVolume(config.getVolume());
        Ports.soundtrack().setVolume(config.getVolume());
        engine.setOnFinished(this::onTrackFinished);
        Ports.soundtrack().setOnTrackFinished(this::nextTrack);
        engine.setOnTrackStart(() -> {
            SongInfo current = getCurrentSong();
            if (current != null) {
                com.musicplayer.stats.ListeningStatisticsManager.getInstance().startTrack(current.getPath());
                var style = config.getNowPlayingStyle();
                if (style == com.musicplayer.config.MusicPlayerConfig.NowPlayingStyle.WIDGET) {
                    Ports.host().miniPlayerSlideIn();
                } else if (style == com.musicplayer.config.MusicPlayerConfig.NowPlayingStyle.ABOVE_HOTBAR) {
                    Ports.host().showNowPlaying(current);
                }
            }
        });
    }

    public static MusicPlayerManager getInstance() {
        return INSTANCE;
    }

    /**
     * Scans the configured music folder and populates the library.
     */
    public void reloadLibraryFromDisk() {
        playlist.clear();
        shuffleOrder.clear();
        currentIndex = -1;
        shuffleIndex = -1;
        editAllSongsCacheValid = false;

        String folderPath = config.getMusicFolderPath();
        if (folderPath == null || folderPath.isEmpty()) {
            loaded = true;
            return;
        }

        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            loaded = true;
            return;
        }

        scanFolderRecursive(folder, playlist);

        regenerateShuffleOrder();

        // Try to restore last played song
        String lastSong = config.getLastPlayedSong();
        if (lastSong != null && !lastSong.isEmpty()) {
            for (int i = 0; i < playlist.size(); i++) {
                if (playlist.get(i).getTitle().equals(lastSong)) {
                    currentIndex = i;
                    break;
                }
            }
        }

        // If queue persistence is on and we have a saved song path, try to restore via path match
        if (config.isQueuePersistence()) {
            String savedPath = config.getSavedQueuePath();
            if (savedPath != null && !savedPath.isEmpty()) {
                for (int i = 0; i < playlist.size(); i++) {
                    if (playlist.get(i).getPath().equals(savedPath)) {
                        currentIndex = i;
                        break;
                    }
                }
            }
            if (currentIndex >= 0 && !config.isSavedQueuePaused()) {
                pendingQueueRestore = true;
            }
        }

        loaded = true;
    }

    private void scanFolderRecursive(File dir, List<SongInfo> out) {
        File[] entries = dir.listFiles();
        if (entries == null) return;
        // listFiles() order is filesystem-dependent; sort so the library order is stable everywhere.
        Arrays.sort(entries, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File f : entries) {
            if (f.isDirectory()) {
                if (java.nio.file.Files.isSymbolicLink(f.toPath())) continue; // avoid symlink cycles
                scanFolderRecursive(f, out);
            } else {
                String lower = f.getName().toLowerCase();
                if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".flac")) {
                    out.add(SongInfo.fromFile(f));
                }
            }
        }
    }

    /**
     * Loads a specific custom playlist.
     */
    public void loadCustomPlaylist(Playlist playlistObj) {
        playlist.clear();
        shuffleOrder.clear();
        currentIndex = -1;
        shuffleIndex = -1;
        currentCustomPlaylist = playlistObj;
        currentSongIsVanilla = false;

        playlist.addAll(playlistObj.getSongs());

        regenerateShuffleOrder();
        loaded = true;
    }

    /**
     * Switches back to the main music folder library.
     */
    public void switchToMainLibrary() {
        currentCustomPlaylist = null;
        currentSongIsVanilla = false;
        reloadLibraryFromDisk();
    }

    public Playlist getCurrentCustomPlaylist() {
        return currentCustomPlaylist;
    }

    public boolean isUsingCustomPlaylist() {
        return currentCustomPlaylist != null;
    }

    public void setMusicFolder(String path) {
        config.setMusicFolderPath(path);
        reloadLibraryFromDisk();
    }

    // ---- Playback controls ----

    // ─── Source management ───

    public SongSource getCurrentSource() { return currentSource; }

    public void setCurrentSource(SongSource source) {
        if (source == currentSource) return;
        if (source == SongSource.MINECRAFT) {
            stop();
            Ports.vanilla().setActive(true);
            TrackRegistryPort reg = Ports.registry();
            reg.resetDiscovery();
            reg.discoverTracks();
            vanillaTracks.clear();
            vanillaTracks.addAll(reg.getAllTracks());
            vanillaIndex = -1;
            vanillaQueue.clear();
            vanillaShuffle.clear();
            vanillaShufflePos = -1;
            currentSongIsVanilla = false;
            if (!vanillaTracks.isEmpty()) {
                playVanillaTrack(0);
            }
        } else {
            Ports.host().stopGameMusic();
            Ports.vanilla().setActive(false);
            Ports.vanilla().stop();
            vanillaIndex = -1;
        }
        currentSource = source;
        currentSongIsVanilla = false;
    }

    public void setVanillaTracks(List<VanillaTrack> tracks) {
        vanillaTracks.clear();
        vanillaTracks.addAll(tracks);
    }

    public List<VanillaTrack> getVanillaTracks() { return Collections.unmodifiableList(vanillaList()); }

    public int getVanillaIndex() { return vanillaIndex; }

    // ─── Playback controls ───

    public void playSong(int index) {
        if (index < 0 || index >= playlist.size()) return;
        if (config.isQueuePersistence()) {
            saveQueueState();
        }
        currentIndex = index;
        SongInfo song = playlist.get(index);
        config.setLastPlayedSong(song.getTitle());
        if (config.isQueuePersistence() && song.getSource() == SongInfo.SourceType.LOCAL) {
            config.setSavedQueuePath(song.getPath());
            config.setSavedQueuePositionMs(0);
            config.setSavedQueuePaused(false);
        }

        if (song.getSource() == SongInfo.SourceType.VANILLA) {
            currentSongIsVanilla = true;
            currentSource = SongSource.MINECRAFT;
            engine.stop();
            Ports.host().stopGameMusic();
            ensureVanillaTracksLoaded();
            playVanillaTrackById(song.getPath());
        } else {
            currentSongIsVanilla = false;
            currentSource = SongSource.USER;
            Ports.vanilla().stop();
            Ports.host().stopGameMusic();
            if (song.getSource() == SongInfo.SourceType.LOCAL) {
                engine.play(new File(song.getPath()), 0);
                Ports.art().loadForSong(new File(song.getPath()));
            } else if (song.getSource() == SongInfo.SourceType.YOUTUBE) {
                // Played by an addon through MelodixApi; without one installed the engine shows a notice.
                engine.play(song.getPath(), 0);
                Ports.art().loadForRemote(song.getPath());
            } else {
                Ports.art().clear();
            }
        }
    }

    /** The songs next/previous/shuffle move through while playing from the Minecraft tab. */
    private List<VanillaTrack> vanillaList() {
        return vanillaQueue.isEmpty() ? vanillaTracks : vanillaQueue;
    }

    /** True while the current song was started from the Minecraft soundtrack list (not from a playlist). */
    public boolean isInVanillaList() {
        return currentSource == SongSource.MINECRAFT && !currentSongIsVanilla;
    }

    /**
     * The game started one of its own tracks while Melodix was idle: show it in the player so it can be paused,
     * skipped and have its volume changed like any other Minecraft song.
     */
    public void adoptAutoMusic() {
        VanillaTrack t = Ports.soundtrack().getCurrentTrack();
        if (t == null) return;
        ensureVanillaTracksLoaded();
        vanillaQueue.clear();
        vanillaShuffle.clear();
        vanillaShufflePos = -1;
        vanillaIndex = -1;
        for (int i = 0; i < vanillaTracks.size(); i++) {
            if (vanillaTracks.get(i).id().equals(t.id())) {
                vanillaIndex = i;
                break;
            }
        }
        currentSongIsVanilla = false;
        currentSource = SongSource.MINECRAFT;
        Ports.vanilla().setActive(true);
    }

    /**
     * Plays a Minecraft track chosen on the Minecraft tab. {@code context} is the list the person was looking at
     * (for example one category); next, previous and shuffle stay inside it. Unlike setCurrentSource this never
     * starts a different track first, which used to overlap with the chosen one.
     */
    public void playMinecraftTrack(List<VanillaTrack> context, String soundId) {
        if (currentSource != SongSource.MINECRAFT) {
            stop();
            Ports.vanilla().setActive(true);
            currentSource = SongSource.MINECRAFT;
        }
        currentSongIsVanilla = false;
        ensureVanillaTracksLoaded();
        vanillaQueue.clear();
        if (context != null && context.size() != vanillaTracks.size()) vanillaQueue.addAll(context);
        vanillaShuffle.clear();
        vanillaShufflePos = -1;
        playVanillaTrackById(soundId);
    }

    public void playVanillaTrack(int index) {
        List<VanillaTrack> list = vanillaList();
        if (index < 0 || index >= list.size()) return;
        vanillaIndex = index;
        if (config.getPlaybackMode() == PlaybackMode.SHUFFLE) syncVanillaShuffle(index);
        VanillaTrack track = list.get(index);
        VanillaPort vmp = Ports.vanilla();
        vmp.playTrack(track);
        Ports.art().clear();
    }

    /** Keeps the shuffle order in step with the track that is playing (a click, or a fresh shuffle). */
    private void syncVanillaShuffle(int index) {
        int n = vanillaList().size();
        boolean valid = vanillaShuffle.size() == n && vanillaShufflePos >= 0 && vanillaShufflePos < n
                && vanillaShuffle.get(vanillaShufflePos) == index;
        if (valid) return;
        vanillaShuffle.clear();
        for (int i = 0; i < n; i++) if (i != index) vanillaShuffle.add(i);
        Collections.shuffle(vanillaShuffle);
        vanillaShuffle.add(0, index);
        vanillaShufflePos = 0;
    }

    public void playVanillaTrackById(String soundId) {
        List<VanillaTrack> list = vanillaList();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(soundId)) {
                playVanillaTrack(i);
                return;
            }
        }
        if (!vanillaQueue.isEmpty()) {
            // not in the filtered list: go back to all tracks
            vanillaQueue.clear();
            vanillaShuffle.clear();
            vanillaShufflePos = -1;
            for (int i = 0; i < vanillaTracks.size(); i++) {
                if (vanillaTracks.get(i).id().equals(soundId)) {
                    playVanillaTrack(i);
                    return;
                }
            }
        }
    }

    public void togglePlayPause() {
        if (currentSongIsVanilla) {
            VanillaPort vmp = Ports.vanilla();
            if (vmp.isPlaying() || vmp.isPaused()) {
                vmp.togglePause();
            } else if (!vanillaTracks.isEmpty()) {
                SongInfo current = getCurrentSong();
                if (current != null) playVanillaTrackById(current.getPath());
            }
            return;
        }
        if (currentSource == SongSource.MINECRAFT) {
            VanillaPort vmp = Ports.vanilla();
            SoundtrackPort ssm = Ports.soundtrack();
            if (vmp.isPlaying() || vmp.isPaused()) {
                vmp.togglePause();
            } else if (ssm.isAutoPlaying() || ssm.isAutoPaused()) {
                ssm.toggleAutoPause();
            } else if (!vanillaTracks.isEmpty()) {
                if (vanillaIndex < 0) vanillaIndex = 0;
                playVanillaTrack(vanillaIndex);
            }
            return;
        }
        if (engine.isActive()) {
            if (engine.isPaused()) {
                SongInfo current = getCurrentSong();
                if (current != null) {
                    com.musicplayer.stats.ListeningStatisticsManager.getInstance().resumeTrack(current.getPath());
                }
                if (config.isQueuePersistence()) {
                    config.setSavedQueuePaused(false);
                }
            } else {
                com.musicplayer.stats.ListeningStatisticsManager.getInstance().pauseTrack();
                if (config.isQueuePersistence()) {
                    config.setSavedQueuePaused(true);
                    config.setSavedQueuePositionMs(engine.getPositionMs());
                }
            }
            engine.togglePause();
        } else if (!playlist.isEmpty()) {
            if (currentIndex < 0) currentIndex = 0;
            playSong(currentIndex);
        }
    }

    public void stop() {
        if (config.isQueuePersistence()) {
            saveQueueState();
        }
        com.musicplayer.stats.ListeningStatisticsManager.getInstance().stopTrack();
        engine.stop();
        Ports.vanilla().stop();
        SoundtrackPort ssm = Ports.soundtrack();
        if (ssm.isAutoPlaying()) {
            Ports.host().stopGameMusic();
        }
    }

    public boolean processQueueRestore() {
        if (!pendingQueueRestore) return false;
        pendingQueueRestore = false;
        if (!config.isQueuePersistence() || currentIndex < 0) return false;
        SongInfo song = playlist.get(currentIndex);
        if (song.getSource() == SongInfo.SourceType.LOCAL) {
            long pos = config.getSavedQueuePositionMs();
            currentSource = SongSource.USER;
            engine.play(new File(song.getPath()), pos);
        }
        return true;
    }

    private void saveQueueState() {
        if (!config.isQueuePersistence()) return;
        SongInfo current = getCurrentSong();
        if (current != null && current.getSource() == SongInfo.SourceType.LOCAL) {
            config.setSavedQueuePath(current.getPath());
            config.setSavedQueuePositionMs(engine.getPositionMs());
            config.setSavedQueuePaused(engine.isPaused());
        }
    }

    public void nextTrack() {
        if (nextTrackInProgress) return;
        nextTrackInProgress = true;
        try {
            if (isInVanillaList()) {
                advanceVanilla();
            } else {
                advanceLibrary();
            }
        } finally {
            nextTrackInProgress = false;
        }
    }

    /** Advance within the loaded library or custom playlist according to the playback mode. */
    private void advanceLibrary() {
        if (playlist.isEmpty()) return;
        switch (config.getPlaybackMode()) {
            case SHUFFLE -> {
                shuffleIndex++;
                if (shuffleIndex >= shuffleOrder.size()) {
                    regenerateShuffleOrder();
                    shuffleIndex = 0;
                }
                playSong(shuffleOrder.get(shuffleIndex));
            }
            case REPEAT_ONE -> playSong(currentIndex >= 0 ? currentIndex : 0);
            case REPEAT_ALL -> {
                currentIndex = (currentIndex + 1) % playlist.size();
                playSong(currentIndex);
            }
            default -> {
                if (currentIndex + 1 < playlist.size()) {
                    playSong(currentIndex + 1);
                } else {
                    stop();
                }
            }
        }
    }

    /** Advance within the Minecraft soundtrack list according to the playback mode. */
    private void advanceVanilla() {
        List<VanillaTrack> list = vanillaList();
        if (list.isEmpty()) return;
        SoundtrackPort ssm = Ports.soundtrack();
        if (ssm.isAutoPlaying()) {
            Ports.host().stopGameMusic();
            ssm.clearAutoMusic();
        }
        int n = list.size();
        switch (config.getPlaybackMode()) {
            case SHUFFLE -> {
                int last = vanillaIndex >= 0 ? vanillaIndex : 0;
                syncVanillaShuffle(last);
                vanillaShufflePos++;
                if (vanillaShufflePos >= vanillaShuffle.size()) {
                    // new round: reshuffle without repeating the track that just played
                    vanillaShuffle.clear();
                    for (int i = 0; i < n; i++) vanillaShuffle.add(i);
                    Collections.shuffle(vanillaShuffle);
                    if (n > 1 && vanillaShuffle.get(0) == last) Collections.swap(vanillaShuffle, 0, 1);
                    vanillaShufflePos = 0;
                }
                playVanillaTrack(vanillaShuffle.get(vanillaShufflePos));
            }
            case REPEAT_ONE -> playVanillaTrack(vanillaIndex >= 0 ? vanillaIndex : 0);
            case REPEAT_ALL -> playVanillaTrack((vanillaIndex + 1) % n);
            default -> {
                if (vanillaIndex + 1 < n) {
                    playVanillaTrack(vanillaIndex + 1);
                } else {
                    stop();
                }
            }
        }
    }

    public void previousTrack() {
        if (isUsingCustomPlaylist() && !isInVanillaList()) {
            if (playlist.isEmpty()) return;
            if (currentSongIsVanilla) {
                VanillaPort vmp = Ports.vanilla();
                if (vmp.getElapsedMs() > 3000 && currentIndex >= 0) {
                    playSong(currentIndex);
                    return;
                }
            } else {
                if (engine.getPositionMs() > 3000 && currentIndex >= 0) {
                    playSong(currentIndex);
                    return;
                }
            }
            PlaybackMode mode = config.getPlaybackMode();
            if (mode == PlaybackMode.SHUFFLE) {
                shuffleIndex--;
                if (shuffleIndex < 0) {
                    regenerateShuffleOrder();
                    shuffleIndex = shuffleOrder.size() - 1;
                }
                playSong(shuffleOrder.get(shuffleIndex));
            } else {
                if (currentIndex > 0) {
                    playSong(currentIndex - 1);
                } else if (mode == PlaybackMode.REPEAT_ALL) {
                    playSong(playlist.size() - 1);
                } else {
                    playSong(0);
                }
            }
            return;
        }
        if (isInVanillaList()) {
            List<VanillaTrack> list = vanillaList();
            if (list.isEmpty()) return;
            SoundtrackPort ssm = Ports.soundtrack();
            if (ssm.isAutoPlaying()) {
                Ports.host().stopGameMusic();
                ssm.clearAutoMusic();
            }
            VanillaPort vmp = Ports.vanilla();
            if (vmp.getElapsedMs() > 3000 && vanillaIndex >= 0) {
                playVanillaTrack(vanillaIndex);
                return;
            }
            PlaybackMode mode = config.getPlaybackMode();
            if (mode == PlaybackMode.SHUFFLE) {
                syncVanillaShuffle(vanillaIndex >= 0 ? vanillaIndex : 0);
                if (vanillaShufflePos > 0) {
                    vanillaShufflePos--;
                    playVanillaTrack(vanillaShuffle.get(vanillaShufflePos));
                } else {
                    playVanillaTrack(vanillaIndex >= 0 ? vanillaIndex : 0);
                }
            } else {
                if (vanillaIndex > 0) {
                    playVanillaTrack(vanillaIndex - 1);
                } else if (mode == PlaybackMode.REPEAT_ALL) {
                    playVanillaTrack(list.size() - 1);
                } else {
                    playVanillaTrack(0);
                }
            }
            return;
        }
        if (playlist.isEmpty()) return;

        if (engine.getPositionMs() > 3000 && currentIndex >= 0) {
            playSong(currentIndex);
            return;
        }

        PlaybackMode mode = config.getPlaybackMode();
        if (mode == PlaybackMode.SHUFFLE) {
            shuffleIndex--;
            if (shuffleIndex < 0) {
                regenerateShuffleOrder();
                shuffleIndex = shuffleOrder.size() - 1;
            }
            playSong(shuffleOrder.get(shuffleIndex));
        } else {
            if (currentIndex > 0) {
                playSong(currentIndex - 1);
            } else if (mode == PlaybackMode.REPEAT_ALL) {
                playSong(playlist.size() - 1);
            } else {
                playSong(0);
            }
        }
    }

    public void seek(float percent) {
        if (currentSource == SongSource.MINECRAFT) {
            VanillaTrack vt = Ports.vanilla().getCurrentTrack();
            if (vt == null) return;
            float targetSec = vt.durationSeconds() * Math.max(0, Math.min(1, percent));
            Ports.vanilla().seek(targetSec);
            return;
        }
        if (currentIndex < 0 || currentIndex >= playlist.size()) return;
        Ports.host().stopGameMusic();
        long targetMs = (long) (engine.getDurationMs() * Math.max(0, Math.min(1, percent)));
        SongInfo song = playlist.get(currentIndex);
        if (song.getSource() == SongInfo.SourceType.LOCAL) {
            File f = new File(song.getPath());
            if (engine.isActive() && f.equals(engine.getCurrentFile())) {
                engine.seek(targetMs); // keeps pause state, no crossfade, not counted as a new play
            } else {
                engine.play(f, targetMs);
            }
        } else if (song.getSource() == SongInfo.SourceType.YOUTUBE) {
            if (engine.isActive() && song.getPath().equals(engine.getCurrentUrl())) {
                engine.seek(targetMs);
            } else {
                engine.play(song.getPath(), targetMs);
            }
        }
    }

    public void adjustVolume(float delta) {
        if (currentSource == SongSource.MINECRAFT) {
            VanillaPort vmp = Ports.vanilla();
            float newVol = Math.max(0f, Math.min(1f, vmp.getVolume() + delta));
            vmp.setVolume(newVol);
            config.setVolume(newVol);
            return;
        }
        float newVol = Math.max(0f, Math.min(1f, engine.getVolume() + delta));
        engine.setVolume(newVol);
        config.setVolume(newVol);
    }

    public void setVolume(float vol) {
        if (currentSource == SongSource.MINECRAFT) {
            Ports.vanilla().setVolume(vol);
            config.setVolume(vol);
            return;
        }
        engine.setVolume(vol);
        config.setVolume(vol);
    }

    public void cyclePlaybackMode() {
        PlaybackMode current = config.getPlaybackMode();
        PlaybackMode next = switch (current) {
            case SEQUENTIAL -> PlaybackMode.SHUFFLE;
            case SHUFFLE -> PlaybackMode.REPEAT_ALL;
            case REPEAT_ALL -> PlaybackMode.REPEAT_ONE;
            case REPEAT_ONE -> PlaybackMode.SEQUENTIAL;
        };
        config.setPlaybackMode(next);
        if (next == PlaybackMode.SHUFFLE) {
            regenerateShuffleOrder();
            shuffleIndex = -1;
        }
    }

    public void setPlaybackMode(PlaybackMode mode) {
        config.setPlaybackMode(mode);
        if (mode == PlaybackMode.SHUFFLE) {
            regenerateShuffleOrder();
            shuffleIndex = -1;
        }
    }

    public void toggleShuffle() {
        if (config.getPlaybackMode() == PlaybackMode.SHUFFLE) {
            config.setPlaybackMode(PlaybackMode.SEQUENTIAL);
        } else {
            config.setPlaybackMode(PlaybackMode.SHUFFLE);
            regenerateShuffleOrder();
            shuffleIndex = -1;
        }
    }

    public void toggleRepeat() {
        PlaybackMode mode = config.getPlaybackMode();
        switch (mode) {
            case REPEAT_ALL -> config.setPlaybackMode(PlaybackMode.REPEAT_ONE);
            case REPEAT_ONE -> config.setPlaybackMode(PlaybackMode.SEQUENTIAL);
            default -> config.setPlaybackMode(PlaybackMode.REPEAT_ALL);
        }
    }

    // ---- Getters ----

    public List<SongInfo> getPlaylist() {
        return Collections.unmodifiableList(playlist);
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public SongInfo getCurrentSong() {
        if (currentSource == SongSource.MINECRAFT) {
            VanillaTrack vt = Ports.vanilla().getCurrentTrack();
            if (vt == null) return null;
            return new SongInfo(vt.title(), vt.artist(), vt.id(), currentSongIsVanilla ? SongInfo.SourceType.VANILLA : SongInfo.SourceType.LOCAL);
        }
        if (currentIndex >= 0 && currentIndex < playlist.size()) {
            return playlist.get(currentIndex);
        }
        return null;
    }

    public AudioEngine getEngine() {
        return engine;
    }

    public MusicPlayerConfig getConfig() {
        return config;
    }

    /**
     * Returns the cached list of all songs (folder scan + vanilla tracks) used by
     * the edit-playlist panel. Invalidated when the music folder is rescanned.
     */
    public List<SongInfo> getCachedEditAllSongs() {
        if (editAllSongsCacheValid && cachedEditAllSongs != null) {
            return cachedEditAllSongs;
        }
        List<SongInfo> result = new ArrayList<>();
        String folderPath = config.getMusicFolderPath();
        if (folderPath != null && !folderPath.isEmpty()) {
            File folder = new File(folderPath);
            if (folder.isDirectory()) {
                scanFolderRecursive(folder, result);
            }
        }
        ensureVanillaTracksLoaded();
        Set<String> seenPaths = new HashSet<>();
        for (SongInfo s : result) seenPaths.add(s.getPath());
        for (VanillaTrack vt : vanillaTracks) {
            if (seenPaths.add(vt.id())) {
                result.add(new SongInfo(vt.title(), vt.artist(), vt.id(), SongInfo.SourceType.VANILLA));
            }
        }
        cachedEditAllSongs = result;
        editAllSongsCacheValid = true;
        return result;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public List<SongInfo> getUpNextQueue(int count) {
        if (count <= 0) return List.of();
        if (isInVanillaList()) {
            return upNextVanilla(count);
        }
        return upNextLibrary(count);
    }

    private List<SongInfo> upNextLibrary(int count) {
        if (playlist.isEmpty() || currentIndex < 0) return List.of();
        List<SongInfo> result = new ArrayList<>(count);
        switch (config.getPlaybackMode()) {
            case SEQUENTIAL -> {
                for (int i = 1; i <= count && currentIndex + i < playlist.size(); i++) {
                    result.add(playlist.get(currentIndex + i));
                }
            }
            case SHUFFLE -> {
                for (int i = 1; i <= count && shuffleIndex + i < shuffleOrder.size(); i++) {
                    int idx = shuffleOrder.get(shuffleIndex + i);
                    if (idx < playlist.size()) result.add(playlist.get(idx));
                }
            }
            case REPEAT_ONE -> {
                for (int i = 0; i < count; i++) {
                    result.add(playlist.get(currentIndex));
                }
            }
            case REPEAT_ALL -> {
                for (int i = 1; i <= count; i++) {
                    result.add(playlist.get((currentIndex + i) % playlist.size()));
                }
            }
        }
        return result;
    }

    private List<SongInfo> upNextVanilla(int count) {
        List<VanillaTrack> list = vanillaList();
        if (list.isEmpty() || vanillaIndex < 0 || vanillaIndex >= list.size()) return List.of();
        List<SongInfo> result = new ArrayList<>(count);
        switch (config.getPlaybackMode()) {
            case SEQUENTIAL -> {
                for (int i = 1; i <= count && vanillaIndex + i < list.size(); i++) {
                    result.add(vanillaInfo(list.get(vanillaIndex + i)));
                }
            }
            case SHUFFLE -> {
                syncVanillaShuffle(vanillaIndex);
                for (int i = 1; i <= count && vanillaShufflePos + i < vanillaShuffle.size(); i++) {
                    int idx = vanillaShuffle.get(vanillaShufflePos + i);
                    if (idx < list.size()) result.add(vanillaInfo(list.get(idx)));
                }
            }
            case REPEAT_ONE -> {
                for (int i = 0; i < count; i++) {
                    result.add(vanillaInfo(list.get(vanillaIndex)));
                }
            }
            case REPEAT_ALL -> {
                for (int i = 1; i <= count; i++) {
                    result.add(vanillaInfo(list.get((vanillaIndex + i) % list.size())));
                }
            }
        }
        return result;
    }

    private static SongInfo vanillaInfo(VanillaTrack vt) {
        return new SongInfo(vt.title(), vt.artist(), vt.id(), SongInfo.SourceType.LOCAL);
    }

    public Playlist buildLikedSongsPlaylist() {
        Playlist p = new Playlist("Liked Songs");
        Set<String> liked = config.getLikedSongPaths();
        for (SongInfo song : getAllKnownSongs()) {
            if (liked.contains(song.getPath())) {
                p.addSong(song);
            }
        }
        return p;
    }

    private List<SongInfo> getAllKnownSongs() {
        ensureVanillaTracksLoaded();
        List<SongInfo> all = new ArrayList<>(playlist);
        Set<String> known = new HashSet<>();
        for (SongInfo s : all) known.add(s.getPath());
        for (Playlist pl : PlaylistManager.getInstance().getPlaylists()) {
            for (SongInfo s : pl.getSongs()) {
                if (known.add(s.getPath())) {
                    all.add(s);
                }
            }
        }
        for (VanillaTrack vt : vanillaTracks) {
            if (known.add(vt.id())) {
                all.add(new SongInfo(vt.title(), vt.artist(), vt.id(), SongInfo.SourceType.VANILLA));
            }
        }
        return all;
    }

    private void ensureVanillaTracksLoaded() {
        if (vanillaTracks.isEmpty()) {
            TrackRegistryPort reg = Ports.registry();
            reg.discoverTracks();
            vanillaTracks.clear();
            vanillaTracks.addAll(reg.getAllTracks());
        }
    }

    public float getProgress() {
        if (currentSource == SongSource.MINECRAFT) {
            VanillaTrack vt = Ports.vanilla().getCurrentTrack();
            if (vt == null) return 0f;
            long elapsed = Ports.vanilla().getElapsedMs();
            long dur = vt.durationSeconds() * 1000L;
            if (dur <= 0) return 0f;
            return Math.min(1f, (float) elapsed / dur);
        }
        long duration = engine.getDurationMs();
        if (duration <= 0) return 0f;
        return Math.min(1f, (float) engine.getPositionMs() / duration);
    }

    public float getVolume() {
        if (currentSource == SongSource.MINECRAFT) {
            return Ports.vanilla().getVolume();
        }
        return engine.getVolume();
    }

    // ---- Private helpers ----

    private void regenerateShuffleOrder() {
        shuffleOrder.clear();
        for (int i = 0; i < playlist.size(); i++) {
            shuffleOrder.add(i);
        }
        Collections.shuffle(shuffleOrder);
    }

    private void onTrackFinished() {
        com.musicplayer.stats.ListeningStatisticsManager.getInstance().stopTrack();
        nextTrack();
    }

    public static String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    public static long estimateDuration(File file) {
        return 0; // Handled by AudioEngine/LavaPlayer
    }
}
