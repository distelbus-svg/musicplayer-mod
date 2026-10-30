package com.musicplayer;

import com.musicplayer.config.MusicPlayerConfig;
import com.musicplayer.config.MusicPlayerConfig.PlaybackMode;
import com.musicplayer.player.SoundtrackStateManager;
import net.minecraft.client.Minecraft;

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
        SoundtrackStateManager.getInstance().setVolume(config.getVolume());
        engine.setOnFinished(this::onTrackFinished);
        SoundtrackStateManager.getInstance().setOnTrackFinished(this::nextTrack);
        engine.setOnTrackStart(() -> {
            SongInfo current = getCurrentSong();
            if (current != null) {
                com.musicplayer.stats.ListeningStatisticsManager.getInstance().startTrack(current.getPath());
                var style = config.getNowPlayingStyle();
                if (style == com.musicplayer.config.MusicPlayerConfig.NowPlayingStyle.WIDGET) {
                    com.musicplayer.hud.MiniPlayerHud.triggerSlideIn();
                } else if (style == com.musicplayer.config.MusicPlayerConfig.NowPlayingStyle.ABOVE_HOTBAR) {
                    com.musicplayer.hud.NowPlayingHud.show(current);
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
        for (File f : entries) {
            if (f.isDirectory()) {
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
            VanillaMusicPlayer.setActive(true);
            com.musicplayer.player.SoundtrackRegistry reg = com.musicplayer.player.SoundtrackRegistry.getInstance();
            reg.resetDiscovery();
            reg.discoverTracks();
            vanillaTracks.clear();
            vanillaTracks.addAll(reg.getAllTracks());
            vanillaIndex = -1;
            if (!vanillaTracks.isEmpty()) {
                playVanillaTrack(0);
            }
        } else {
            Minecraft.getInstance().getMusicManager().stopPlaying();
            VanillaMusicPlayer.setActive(false);
            VanillaMusicPlayer.getInstance().stop();
            vanillaIndex = -1;
        }
        currentSource = source;
        currentSongIsVanilla = false;
    }

    public void setVanillaTracks(List<VanillaTrack> tracks) {
        vanillaTracks.clear();
        vanillaTracks.addAll(tracks);
    }

    public List<VanillaTrack> getVanillaTracks() { return Collections.unmodifiableList(vanillaTracks); }

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
            Minecraft.getInstance().getMusicManager().stopPlaying();
            ensureVanillaTracksLoaded();
            playVanillaTrackById(song.getPath());
        } else {
            currentSongIsVanilla = false;
            currentSource = SongSource.USER;
            VanillaMusicPlayer.getInstance().stop();
            Minecraft.getInstance().getMusicManager().stopPlaying();
            if (song.getSource() == SongInfo.SourceType.LOCAL) {
                engine.play(new File(song.getPath()), 0);
                AlbumArtManager.getInstance().loadForSong(new File(song.getPath()));
            } else {
                AlbumArtManager.getInstance().clear();
            }
        }
    }

    public void playVanillaTrack(int index) {
        if (index < 0 || index >= vanillaTracks.size()) return;
        vanillaIndex = index;
        VanillaTrack track = vanillaTracks.get(index);
        VanillaMusicPlayer vmp = VanillaMusicPlayer.getInstance();
        vmp.playTrack(track);
        AlbumArtManager.getInstance().clear();
    }

    public void playVanillaTrackById(String soundId) {
        for (int i = 0; i < vanillaTracks.size(); i++) {
            if (vanillaTracks.get(i).id().equals(soundId)) {
                playVanillaTrack(i);
                return;
            }
        }
    }

    public void togglePlayPause() {
        if (isUsingCustomPlaylist() && currentSongIsVanilla) {
            VanillaMusicPlayer vmp = VanillaMusicPlayer.getInstance();
            if (vmp.isPlaying() || vmp.isPaused()) {
                vmp.togglePause();
            } else if (!vanillaTracks.isEmpty()) {
                SongInfo current = getCurrentSong();
                if (current != null) playVanillaTrackById(current.getPath());
            }
            return;
        }
        if (currentSource == SongSource.MINECRAFT) {
            VanillaMusicPlayer vmp = VanillaMusicPlayer.getInstance();
            SoundtrackStateManager ssm = SoundtrackStateManager.getInstance();
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
        VanillaMusicPlayer.getInstance().stop();
        SoundtrackStateManager ssm = SoundtrackStateManager.getInstance();
        if (ssm.isAutoPlaying()) {
            Minecraft.getInstance().getMusicManager().stopPlaying();
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
            if (isUsingCustomPlaylist()) {
                if (playlist.isEmpty()) return;
                PlaybackMode mode = config.getPlaybackMode();
                switch (mode) {
                    case SHUFFLE:
                        shuffleIndex++;
                        if (shuffleIndex >= shuffleOrder.size()) {
                            regenerateShuffleOrder();
                            shuffleIndex = 0;
                        }
                        playSong(shuffleOrder.get(shuffleIndex));
                        break;
                    case REPEAT_ONE:
                        playSong(currentIndex >= 0 ? currentIndex : 0);
                        break;
                    case REPEAT_ALL:
                        currentIndex = (currentIndex + 1) % playlist.size();
                        playSong(currentIndex);
                        break;
                    default:
                        if (currentIndex + 1 < playlist.size()) {
                            playSong(currentIndex + 1);
                        } else {
                            stop();
                        }
                        break;
                }
                return;
            }
            if (currentSource == SongSource.MINECRAFT) {
                if (vanillaTracks.isEmpty()) return;
                SoundtrackStateManager ssm = SoundtrackStateManager.getInstance();
                if (ssm.isAutoPlaying()) {
                    Minecraft.getInstance().getMusicManager().stopPlaying();
                    ssm.clearAutoMusic();
                }
                PlaybackMode mode = config.getPlaybackMode();
                switch (mode) {
                    case SHUFFLE -> {
                        int ri;
                        do {
                            ri = (int)(Math.random() * vanillaTracks.size());
                        } while (ri == vanillaIndex && vanillaTracks.size() > 1);
                        playVanillaTrack(ri);
                    }
                    case REPEAT_ONE -> playVanillaTrack(vanillaIndex >= 0 ? vanillaIndex : 0);
                    case REPEAT_ALL -> playVanillaTrack((vanillaIndex + 1) % vanillaTracks.size());
                    default -> {
                        if (vanillaIndex + 1 < vanillaTracks.size()) {
                            playVanillaTrack(vanillaIndex + 1);
                        } else {
                            stop();
                        }
                    }
                }
                return;
            }
            if (playlist.isEmpty()) return;

            PlaybackMode mode = config.getPlaybackMode();
            switch (mode) {
                case SHUFFLE:
                    shuffleIndex++;
                    if (shuffleIndex >= shuffleOrder.size()) {
                        regenerateShuffleOrder();
                        shuffleIndex = 0;
                    }
                    playSong(shuffleOrder.get(shuffleIndex));
                    break;
                case REPEAT_ONE:
                    playSong(currentIndex >= 0 ? currentIndex : 0);
                    break;
                case REPEAT_ALL:
                    currentIndex = (currentIndex + 1) % playlist.size();
                    playSong(currentIndex);
                    break;
                default: // SEQUENTIAL
                    if (currentIndex + 1 < playlist.size()) {
                        playSong(currentIndex + 1);
                    } else {
                        stop();
                    }
                    break;
            }
        } finally {
            nextTrackInProgress = false;
        }
    }

    public void previousTrack() {
        if (isUsingCustomPlaylist()) {
            if (playlist.isEmpty()) return;
            if (currentSongIsVanilla) {
                VanillaMusicPlayer vmp = VanillaMusicPlayer.getInstance();
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
        if (currentSource == SongSource.MINECRAFT) {
            if (vanillaTracks.isEmpty()) return;
            SoundtrackStateManager ssm = SoundtrackStateManager.getInstance();
            if (ssm.isAutoPlaying()) {
                Minecraft.getInstance().getMusicManager().stopPlaying();
                ssm.clearAutoMusic();
            }
            VanillaMusicPlayer vmp = VanillaMusicPlayer.getInstance();
            if (vmp.getElapsedMs() > 3000 && vanillaIndex >= 0) {
                playVanillaTrack(vanillaIndex);
                return;
            }
            PlaybackMode mode = config.getPlaybackMode();
            if (mode == PlaybackMode.SHUFFLE) {
                int ri;
                do {
                    ri = (int)(Math.random() * vanillaTracks.size());
                } while (ri == vanillaIndex && vanillaTracks.size() > 1);
                playVanillaTrack(ri);
            } else {
                if (vanillaIndex > 0) {
                    playVanillaTrack(vanillaIndex - 1);
                } else if (mode == PlaybackMode.REPEAT_ALL) {
                    playVanillaTrack(vanillaTracks.size() - 1);
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
            VanillaTrack vt = VanillaMusicPlayer.getInstance().getCurrentTrack();
            if (vt == null) return;
            float targetSec = vt.durationSeconds() * Math.max(0, Math.min(1, percent));
            VanillaMusicPlayer.getInstance().seek(targetSec);
            return;
        }
        if (currentIndex < 0 || currentIndex >= playlist.size()) return;
        Minecraft.getInstance().getMusicManager().stopPlaying();
        long targetMs = (long) (engine.getDurationMs() * Math.max(0, Math.min(1, percent)));
        SongInfo song = playlist.get(currentIndex);
        if (song.getSource() == SongInfo.SourceType.LOCAL) {
            engine.play(new File(song.getPath()), targetMs);
        }
    }

    public void adjustVolume(float delta) {
        if (currentSource == SongSource.MINECRAFT) {
            VanillaMusicPlayer vmp = VanillaMusicPlayer.getInstance();
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
            VanillaMusicPlayer.getInstance().setVolume(vol);
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
            VanillaTrack vt = VanillaMusicPlayer.getInstance().getCurrentTrack();
            if (vt == null) return null;
            return new SongInfo(vt.title(), vt.artist(), vt.id(), isUsingCustomPlaylist() ? SongInfo.SourceType.VANILLA : SongInfo.SourceType.LOCAL);
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
        for (VanillaTrack vt : vanillaTracks) {
            boolean exists = result.stream().anyMatch(s -> s.getPath().equals(vt.id()));
            if (!exists) {
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
        if (isUsingCustomPlaylist()) {
            if (playlist.isEmpty() || count <= 0 || currentIndex < 0) return List.of();
            List<SongInfo> result = new ArrayList<>();
            PlaybackMode mode = config.getPlaybackMode();
            switch (mode) {
                case SEQUENTIAL -> {
                    for (int i = 1; i <= count && currentIndex + i < playlist.size(); i++) {
                        result.add(playlist.get(currentIndex + i));
                    }
                }
                case SHUFFLE -> {
                    for (int i = 1; i <= count && shuffleIndex + i < shuffleOrder.size(); i++) {
                        result.add(playlist.get(shuffleOrder.get(shuffleIndex + i)));
                    }
                }
                case REPEAT_ONE -> {
                    for (int i = 0; i < count && currentIndex >= 0; i++) {
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
        if (currentSource == SongSource.MINECRAFT) {
            if (vanillaTracks.isEmpty() || count <= 0 || vanillaIndex < 0) return List.of();
            List<SongInfo> result = new ArrayList<>();
            PlaybackMode mode = config.getPlaybackMode();
            switch (mode) {
                case SEQUENTIAL -> {
                    for (int i = 1; i <= count && vanillaIndex + i < vanillaTracks.size(); i++) {
                        VanillaTrack vt = vanillaTracks.get(vanillaIndex + i);
                        result.add(new SongInfo(vt.title(), vt.artist(), vt.id(), SongInfo.SourceType.LOCAL));
                    }
                }
                case SHUFFLE -> {
                    for (int i = 1; i <= count && shuffleIndex + i < shuffleOrder.size(); i++) {
                        VanillaTrack vt = vanillaTracks.get(shuffleOrder.get(shuffleIndex + i));
                        result.add(new SongInfo(vt.title(), vt.artist(), vt.id(), SongInfo.SourceType.LOCAL));
                    }
                }
                case REPEAT_ONE -> {
                    for (int i = 0; i < count && vanillaIndex >= 0; i++) {
                        VanillaTrack vt = vanillaTracks.get(vanillaIndex);
                        result.add(new SongInfo(vt.title(), vt.artist(), vt.id(), SongInfo.SourceType.LOCAL));
                    }
                }
                case REPEAT_ALL -> {
                    for (int i = 1; i <= count; i++) {
                        VanillaTrack vt = vanillaTracks.get((vanillaIndex + i) % vanillaTracks.size());
                        result.add(new SongInfo(vt.title(), vt.artist(), vt.id(), SongInfo.SourceType.LOCAL));
                    }
                }
            }
            return result;
        }
        if (playlist.isEmpty() || count <= 0 || currentIndex < 0) return List.of();
        List<SongInfo> result = new ArrayList<>();
        PlaybackMode mode = config.getPlaybackMode();
        switch (mode) {
            case SEQUENTIAL -> {
                for (int i = 1; i <= count && currentIndex + i < playlist.size(); i++) {
                    result.add(playlist.get(currentIndex + i));
                }
            }
            case SHUFFLE -> {
                for (int i = 1; i <= count && shuffleIndex + i < shuffleOrder.size(); i++) {
                    result.add(playlist.get(shuffleOrder.get(shuffleIndex + i)));
                }
            }
            case REPEAT_ONE -> {
                for (int i = 0; i < count && currentIndex >= 0; i++) {
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
        for (Playlist pl : PlaylistManager.getInstance().getPlaylists()) {
            for (SongInfo s : pl.getSongs()) {
                if (all.stream().noneMatch(x -> x.getPath().equals(s.getPath()))) {
                    all.add(s);
                }
            }
        }
        for (VanillaTrack vt : vanillaTracks) {
            if (all.stream().noneMatch(x -> x.getPath().equals(vt.id()))) {
                all.add(new SongInfo(vt.title(), vt.artist(), vt.id(), SongInfo.SourceType.VANILLA));
            }
        }
        return all;
    }

    private void ensureVanillaTracksLoaded() {
        if (vanillaTracks.isEmpty()) {
            com.musicplayer.player.SoundtrackRegistry reg = com.musicplayer.player.SoundtrackRegistry.getInstance();
            reg.discoverTracks();
            vanillaTracks.clear();
            vanillaTracks.addAll(reg.getAllTracks());
        }
    }

    public float getProgress() {
        if (currentSource == SongSource.MINECRAFT) {
            VanillaTrack vt = VanillaMusicPlayer.getInstance().getCurrentTrack();
            if (vt == null) return 0f;
            long elapsed = VanillaMusicPlayer.getInstance().getElapsedMs();
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
            return VanillaMusicPlayer.getInstance().getVolume();
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
