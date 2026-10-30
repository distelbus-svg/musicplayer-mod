# Melodix YouTube Extension — Implementation Specification

## Overview

A Fabric client-side addon mod that adds YouTube search, browsing, and streaming to the **Melodix Music Player** mod. Distributed as a separate Modrinth project that depends on `musicplayer`.

**Repo:** Separate repo at `/Volumes/Samsung/Dokumente/Opencode/Melodix/musicplayer-mod-YouTube-extension` (currently empty)
**Mod ID:** `musicplayer-youtube`
**Dependencies:** `musicplayer` (Melodix base), `fabric-api`, `fabricloader`, `minecraft >=26.2`, `java >=21`

---

## Architecture

The extension uses **mixins** to hook into the base mod — no base mod source changes needed. It adds:

1. **A "YouTube" sidebar entry** (between Minecraft and Liked Songs)
2. **A dedicated YouTube view** that replaces the song list area when selected
3. **YouTube search** via yt-dlp `ytsearchN` (see decision log in §4)
4. **YouTube audio streaming** via bundled yt-dlp + ffmpeg → pipe PCM into AudioEngine
5. **Channel + playlist browsing** via yt-dlp (`ytsearchN:channel:` / playlist URL resolution)
6. **Play/pause/seek/progress/volume** for YouTube tracks via mixed-in hooks

---

## Key Files in Base Mod (for reference)

All paths relative to `26.2/src/main/java/com/musicplayer/` in the base repo.

### SongInfo.java (lines 1-56)
Simple data class with fields: `title`, `artist`, `path` (String), `source` (SourceType enum: LOCAL, YOUTUBE, VANILLA). No equals/hashCode override — identity-based comparison. Constructor: `SongInfo(String title, String artist, String path, SourceType source)`. The YOUTUBE SourceType already exists. Used throughout the mod for all song references.

### MusicPlayerManager.java (806 lines)
Singleton (getInstance()). Key fields:
- `playlist` (ArrayList<SongInfo>) — the active play queue
- `currentIndex` (int) — index into playlist
- `currentSource` (SongSource enum: USER, MINECRAFT) — high-level source toggle
- `currentCustomPlaylist` (Playlist) — non-null when viewing a saved playlist
- `engine` (AudioEngine) — handles LOCAL file playback
- `loaded` (boolean)

Key methods to hook via mixin:
- **`playSong(int index)`** (line 208): Routes by `song.getSource()`:
  - `VANILLA` → sets `currentSource = MINECRAFT`, calls `playVanillaTrackById()`
  - `LOCAL` → sets `currentSource = USER`, calls `engine.play(new File(path), 0)`
  - `YOUTUBE` → else branch, only clears album art (no-op). **This is the primary hook point.**
- **`togglePlayPause()`** (line 253): Checks `currentSource == MINECRAFT` first, then `engine.isActive()`. No YOUTUBE branch.
- **`seek(float percent)`** (line 488): Routes by `currentSource == MINECRAFT` vs LOCAL. No YOUTUBE branch.
- **`getProgress()`** (line 759): Same split. Returns 0-1 float.
- **`getVolume()`** (line 773): Same split.
- **`stop()`** (line 293): Stops engine + VanillaMusicPlayer.
- **`setCurrentSource(SongSource)`** (line 173): Switches USER↔MINECRAFT, initializes appropriate engine.

### AudioEngine.java (526 lines)
Handles MP3 via JLayer (javazoom) and WAV via javax.sound. Architecture:
- `PlaybackSlot` (inner class, line 242): Each slot = Thread + SourceDataLine + fade/volume state.
- `play(File, long seekToMs)` (line 77): Creates new PlaybackSlot thread.
- `stop()`, `togglePause()`, `setVolume()`, `getPositionMs()`, `getDurationMs()`
- `PlaybackSlot.runPlayback()` (line 345): Decides MP3 vs WAV, runs decode → PCM → SourceDataLine loop.
- `PlaybackSlot.playMp3()` (line 392): JLayer decode → short[] → byte[] → line.write().
- `PlaybackSlot.playWav()` (line 466): javax.sound AudioInputStream → byte[] → line.write().
- **Per-sample fade**: `advanceFade()`, `applyFadeToBuffer()`, `applyFadeToBytes()`, `currentOutputGain()` — all in PlaybackSlot.
- **Visualizer**: `vizBuffer[256]` filled in playMp3/playWav, exposed via `getVisualizerData()` and `getVisualizerBars()`.
- **DSP**: `AudioDSP.processSamples()` / `processBytes()` called per chunk.
- **Ducking**: `tickDuck()` called per iteration.
- **YouTube stub** (line 525): `public void play(String url, long seekToMs) {}` — ready for implementation.

To stream YouTube: Create a third `playYoutube(String url, long seekToMs)` method in PlaybackSlot that spawns `yt-dlp | ffmpeg` process, reads PCM from stdout pipe, and writes to the same SourceDataLine. All existing infrastructure (fade, viz, DSP, ducking) works automatically.

### MusicPlayerScreen.java (2051 lines)
Full-screen GUI. Three-panel layout: Sidebar (left) | Song list (center) | Hero (right album art).

Key inner types and methods:
- **`PanelMode` enum** (line 36): `NONE, SETTINGS, PLAYLISTS, UP_NEXT, PLAYLIST_EDIT` — overlay panels.
- **`onSidebarSelect(Kind, Playlist)`** (line 824): Switch on Kind → sets source, populates song list.
- **`populateSongList()`** (line 1567): For MINECRAFT source → group by disc/soundtrack and call `songList.setSongsGrouped(categories)`. For USER source → call `songList.setSongs(manager.getPlaylist())`.
- **`refreshPanel()`** (line 385): Shows/hides widgets per PanelMode.
- **`extractRenderState()`** (line 864): Main render method. Draws panels, song list, hero, context menu.
- **`mouseClicked()`** (line 1647): Dispatches to children (widgets) at line 1796. Skips songList when panel is open.
- **Fields**: `songList`, `sidebar`, `searchBox`, panel widgets, layout dimensions, context menu state.
- **Context menu**: `ctxMenuActive`, `ctxSong`, `renderContextMenu()`, `handleContextMenuClick()`, `buildCtxItems()`.

### SidebarWidget.java (206 lines)
Extends `ObjectSelectionList<SidebarEntry>`. 
- **`Kind` enum** (line 25): `LIBRARY, MINECRAFT, LIKED, HEADER, PLAYLIST`
- **`refresh()`** (line 42): Clears and rebuilds entries in fixed order.
- **`computeActiveKey()`** (line 58): Returns `"minecraft"`, `"liked"`, `"pl:<name>"`, or `"library"`.
- **`syncSelection()`** (line 71): Recomputes active key.
- **Entry rendering** (line 117): Shows label, active state, accent bar, playlist count, small cover for playlists.
- **`mouseClicked()`** (line 185): Calls `onSelect.run(kind, playlist)`.

To add YouTube: Mixin `@Inject` into `refresh()` after the MINECRAFT entry to add a new `Kind.YOUTUBE` entry. Also handle the new kind in `computeActiveKey()` and in `MusicPlayerScreen.onSidebarSelect()`.

### SongListWidget.java (311 lines)
Extends `ObjectSelectionList<SongEntry>`. The main song display.
- **`setSongs(List<SongInfo>)`** (line 37): Flat list.
- **`setSongsGrouped(Map<String, List<SongInfo>>)`** (line 46): Categorized view with header entries.
- **`setFilter(String query)`** (line 63): Filters by title/artist, searches `allSongs`.
- **`SongEntry.mouseClicked()`** (line 258): Right-click → callback. Left-click → play via `manager.playSong(trackIndex >= 0 ? trackIndex : index)` for USER source, or `manager.playVanillaTrackById()` for MINECRAFT.
- **SongEntry rendering** (line 173): Shows title, artist, visualizer bars for current song, like heart, play indicator.

### Playlist.java (101 lines)
Simple data class: `name`, `songs` (ArrayList<SongInfo>), `sortOrder`, album art fields. Methods: `addSong()`, `removeSong()`, `save()`, `load()`.

### PlaylistManager.java (190 lines)
Singleton. Manages JSON playlist files. `getPlaylists()` returns all saved playlists. `savePlaylist()`, `deletePlaylist()`, `renamePlaylist()`.

### Build config
**26.2/build.gradle**: Fabric Loom, Java 25, bundles JLayer + jaudiotagger via `include`.
**fabric.mod.json**: id=`musicplayer`, entrypoint=`com.musicplayer.MusicPlayerMod`, mixins in `musicplayer.mixins.json`, depends on `fabricloader>=0.19.2`, `minecraft>=26.2`, `java>=21`, `fabric-api:*`.

---

## Implementation Plan

### 1. Project Setup

**Directory**: Create under repo root (or independent — user has empty repo at `musicplayer-mod-YouTube-extension`).
**build.gradle**: Use Fabric Loom, set up exactly like 26.2/ but with:
- Mod ID `musicplayer-youtube`, name `Melodix YouTube`
- `fabric.mod.json` with `depends: { "musicplayer": "*" }`
- Target Java 25, Minecraft 26.2+
- Bundle yt-dlp + ffmpeg binaries for Windows, macOS, Linux (x86_64 + arm64)
- No dependencies on JLayer/jaudiotagger (those come from base mod)
- `compileOnly` on base mod classes (or just rely on runtime classpath)

**Package structure**: `com.musicplayer.youtube`

### 2. Sidebar Integration (Mixin into SidebarWidget)

**Target class**: `com.musicplayer.widget.SidebarWidget`
**Mixin 1** — Add YOUTUBE to Kind enum:
```java
@Inject(method = "<clinit>", at = @At("TAIL"))
// Can't inject into enum initializer. Instead, use @Shadow to access the enum.
// Alternative: Add YOUTUBE via enum constant injection mixin.
```
Actually, you can't add enum constants via mixin. Instead:
- **Approach**: Don't modify the enum. Use `Kind.LIBRARY` or a sentinel value for the sidebar entry, and handle it specially. Or better: inject into `refresh()` to add an entry with `Kind.LIBRARY` and a custom marker, and handle it in `onSidebarSelect()` by checking the entry's key string (e.g., `"youtube"`).

Simpler approach: Since `SidebarEntry` stores a String `key`, add an entry with key `"youtube"` and an existing kind, then check the key in `onSidebarSelect()`.

**Mixin into `refresh()`**:
```java
@Inject(method = "refresh", at = @At("RETURN"))
// After the method runs, add: addEntry(new SidebarEntry(Kind.LIBRARY, "youtube", "\u25B6 YouTube"));
```

**Mixin into `computeActiveKey()`**:
```java
@Inject(method = "computeActiveKey", at = @At("HEAD"), cancellable = true)
// If a field `isYoutubeActive` (added via @Shadow or custom field) is true, return "youtube".
```

**Mixin into `MusicPlayerScreen.onSidebarSelect()`**:
```java
@Inject(method = "onSidebarSelect", at = @At("HEAD"), cancellable = true)
// If kind is the youtube entry (check key string), set YouTube mode and cancel original.
```

### 3. YouTube View UI

When "YouTube" is selected in sidebar:
- `onSidebarSelect` sets a flag (`youtubeMode = true`) and calls a method to show the YouTube UI
- The song list area is replaced with YouTube-specific content
- Two modes within YouTube view:
  - **Search**: Search box + results list showing video title, channel, duration, thumbnail
  - **Channel/Playlist**: When a channel or playlist is selected, show its contents

The YouTube view should be rendered in the same area as the song list (middle panel). Simplest approach: 
- Use a `SongListWidget` (from base mod) populated with `SongInfo` objects having `SourceType.YOUTUBE`
- Override/add a custom widget that extends `ObjectSelectionList` for YouTube results
- The search box for YouTube can be the existing `searchBox` (relabeled) or a dedicated one

**Render flow**:
1. In `MusicPlayerScreen.extractRenderState()`, detect YouTube mode and render YouTube-specific content instead of the normal song list
2. Use mixin `@Inject` into `extractRenderState()` before/after the song list rendering
3. Or better: Add a `youtubeSongList` widget and show/hide it alongside `songList`

### 4. YouTube Search (yt-dlp ytsearch — DECISION LOG, supersedes Invidious)

**Original requirement**: Invidious API, no API key, "works OOTB".

**Decision (2026-07-31)**: Search is implemented with `yt-dlp` in flat-playlist mode
(`ytsearchN:<query>`), NOT the Invidious API. Rationale:

- Invidious's core problem is operational reliability outside our control: public
  instances get rate-limited, blocked by YouTube, or shut down on a rolling basis,
  and there is no single stable instance to hardcode as a default. Search must not
  depend on "whichever instance happens to be up today".
- One code path for both "paste a URL" and "type a search": same tool, same failure
  modes, same update story (yt-dlp updates). Two backends for two features in the
  same tab is more to maintain and more ways for behavior to diverge.
- Cookies-from-browser works for search results too (age-gated content); Invidious
  has no equivalent.
- No new dependency, no new config field for an Invidious instance.

**Implementation**:
- `GET`-free: `yt-dlp --flat-playlist --no-warnings --dump-single-json ytsearchN:<query>`
  returns lightweight per-result JSON (id, title, duration, thumbnail, channel)
  without resolving each video's stream URL.
- The stream URL is resolved fresh only when the user picks a result and it is
  added to a playlist (same "resolve at play time, never persist the ephemeral
  URL" rule as the resolve path).
- All calls run on a background thread; results are pushed to the render thread.

**Reserved for later**: If Invidious reliability ever becomes a non-issue, its
endpoints can be added behind the same `YoutubeResolver.search()` surface:
`/api/v1/search?q={query}&type=video|channel|playlist`, `/api/v1/videos/{id}`, etc.

### 5. YouTube Audio Streaming (yt-dlp + ffmpeg)

**Bundled binaries**: Include platform-specific yt-dlp and ffmpeg binaries in the mod jar. Extract them on first run to a known location (e.g., `<config-dir>/musicplayer/youtube/bin/`).

**Streaming flow**:
1. User clicks a YouTube search result
2. Create `SongInfo` with `SourceType.YOUTUBE`, path = YouTube video URL or ID
3. `MusicPlayerManager.playSong()` is called  
4. **Mix-in into `playSong()`**: When `song.getSource() == YOUTUBE`:
   a. Stop current playback
   b. Create a `YoutubePlaybackSlot` (or extend `AudioEngine.PlaybackSlot`)
   c. The slot spawns: `yt-dlp -f bestaudio -g <URL>` to get the direct audio URL
   d. Then spawns: `yt-dlp -f bestaudio -o - <URL> | ffmpeg -i pipe:0 -f s16le -ac 2 -ar 44100 -`
   e. Reads PCM `byte[]` from the process stdout, writes to a `SourceDataLine`
   f. Reuses AudioEngine's fade/visualizer/DSP infrastructure

**Alternative (simpler, if you bundle yt-dlp but want to avoid ffmpeg)**:
- Use yt-dlp's `--extract-audio --audio-format wav` to decode to WAV on-the-fly  
- Pipe WAV into javax.sound's AudioInputStream → use existing playWav path

**Play/pause**: Pause the SourceDataLine. Kill/resume the process on resume (or keep it running and just pause the line).
**Seek**: Kill current process, restart with `--download-sections *<start_time>-` and `--force-keyframes-at-cutsoffs-secs`.
**Progress**: Monitor bytes written vs expected content length / duration from yt-dlp metadata.
**Visualizer**: Same as MP3/WAV — fill `vizBuffer[]` in the playback loop.

### 6. Mixin Hooks Required

| Target | Method | Injection Point | Purpose |
|---|---|---|---|
| `SidebarWidget` | `refresh()` | `@At("RETURN")` | Add YouTube entry |
| `SidebarWidget` | `computeActiveKey()` | `@At("HEAD")`, cancellable | Return "youtube" when YouTube mode active |
| `MusicPlayerScreen` | `onSidebarSelect()` | `@At("HEAD")`, cancellable | Handle YouTube sidebar click |
| `MusicPlayerScreen` | `extractRenderState()` | Multiple `@Inject` | Render YouTube UI panel, hide normal song list |
| `MusicPlayerScreen` | `populateSongList()` | `@At("HEAD")`, cancellable | Skip when in YouTube mode |
| `MusicPlayerScreen` | `mouseClicked()` | `@At("HEAD")`, cancellable | Handle YouTube UI clicks |
| `MusicPlayerScreen` | `keyPressed()` | `@At("HEAD")`, cancellable | Handle YouTube search keystrokes |
| `MusicPlayerManager` | `playSong(int)` | `@At("HEAD")`, cancellable | Intercept YOUTUBE source |
| `MusicPlayerManager` | `togglePlayPause()` | `@At("HEAD")`, cancellable | Handle YouTube play/pause |
| `MusicPlayerManager` | `seek(float)` | `@At("HEAD")`, cancellable | Handle YouTube seeking |
| `MusicPlayerManager` | `getProgress()` | `@At("RETURN")`, cancellable | Return YouTube progress |
| `MusicPlayerManager` | `getVolume()` | `@At("RETURN")`, cancellable | Return YouTube volume |
| `MusicPlayerManager` | `stop()` | `@At("TAIL")` | Stop YouTube playback |
| `MusicPlayerManager` | `nextTrack()` | `@At("HEAD")` | Handle next with YOUTUBE in playlist |
| `MusicPlayerManager` | `previousTrack()` | `@At("HEAD")` | Handle prev with YOUTUBE in playlist |

### 7. Data Flow

**Search**:
```
User types in YouTube search box
  → debounce 300ms
  → background thread: yt-dlp ytsearchN:... (flat-playlist)
  → parse JSON response into List<YouTubeVideoInfo>
  → convert to List<SongInfo> with SourceType.YOUTUBE
  → populate songList (or custom YouTube results widget)
```

**Play**:
```
User clicks YouTube song
  → SongEntry.mouseClicked() calls manager.playSong(index)
  → [mixin @Inject] detects SourceType.YOUTUBE
  → calls YoutubeAudioEngine.play(videoUrl, 0)
  → YoutubeAudioEngine spawns: yt-dlp + ffmpeg process
  → reads PCM from pipe, writes to SourceDataLine
  → sets currentSource = SongSource.USER (or a new YOUTUBE source)
  → updates MusicPlayerManager.youtubeEngine reference
```

**Playlist**: YouTube songs added to playlists via existing "Add to Playlist" context menu. They persist as `SongInfo(path=videoUrl, source=YOUTUBE)` in the playlist JSON. When played from a playlist, the same YOUTUBE path is used.

### 8. YouTubeAudioEngine Class

Create `com.musicplayer.youtube.YoutubeAudioEngine`:

```java
public class YoutubeAudioEngine {
    private SourceDataLine line;
    private Process ytDlpProcess;
    private Process ffmpegProcess;
    private Thread playbackThread;
    private volatile boolean playing;
    private volatile boolean paused;
    private volatile boolean stopRequested;
    private float volume = 1.0f;
    private long positionMs;
    private long durationMs;
    private String currentUrl;
    
    public void play(String videoUrl, long seekMs) { ... }
    public void stop() { ... }
    public void togglePause() { ... }
    public void seek(float percent) { ... }
    public long getPositionMs() { ... }
    public long getDurationMs() { ... }
    public float getVolume() { ... }
    public void setVolume(float vol) { ... }
    public float[] getVisualizerData() { ... } // reuse vizBuffer pattern
}
```

Store a reference in `MusicPlayerManager` as a new field (or use @Shadow to add one).
Mix `youtubeEngine` into all relevant MusicPlayerManager methods.

### 9. Bundling External Binaries

Create directory `src/main/resources/bin/`:
```
bin/
  windows-x86_64/
    yt-dlp.exe
    ffmpeg.exe
  linux-x86_64/
    yt-dlp
    ffmpeg
  linux-aarch64/
    yt-dlp
    ffmpeg
  macos-x86_64/
    yt-dlp
    ffmpeg
  macos-aarch64/
    yt-dlp
    ffmpeg
```

On first run, detect OS + arch, extract the matching binaries to:
```
<minecraft-run-dir>/config/musicplayer/youtube/bin/
```

Set executable permissions on Linux/macOS.

### 10. Configuration

Add to `MusicPlayerConfig` (base mod) via mixin @Shadow or a separate config file:
- `youtubeAutoDownload` (boolean, default: true — download yt-dlp if not found)
- `youtubeCustomBinPath` (String, default: empty — custom path to yt-dlp/ffmpeg)

Since the base config is loaded from JSON, the extension can write a separate config file or mix into the base config.

### 11. Error Handling

- yt-dlp not found → download automatically, show notification
- Video unavailable/blocked → show notification, skip to next track
- FFmpeg pipe error → retry with different format, fallback to audio-only URL
- No playable formats → skip track, notify user

---

## Files to Create

```
src/main/java/com/musicplayer/youtube/
├── YoutubeMod.java                    # ClientModInitializer entry point
├── YoutubeAudioEngine.java           # Streaming engine (yt-dlp+ffmpeg → SourceDataLine)
├── YoutubeSearchProvider.java        # search results (yt-dlp ytsearchN)
├── YoutubeVideoInfo.java             # Data class for search results
├── YoutubeSidebarMixin.java          # SidebarWidget mixins
├── YoutubeScreenMixin.java           # MusicPlayerScreen mixins
├── YoutubeManagerMixin.java          # MusicPlayerManager mixins
├── YoutubeConfig.java                # Extension config (read/write JSON)
├── BinaryExtractor.java              # Extract bundled yt-dlp/ffmpeg
├── widget/
│   ├── YoutubeSearchWidget.java      # YouTube-specific search + results list
│   └── YoutubeResultEntry.java       # Individual result rendering

src/main/resources/
├── fabric.mod.json                   # Extension mod declaration
├── musicplayer-youtube.mixins.json   # Mixin config
├── assets/musicplayer-youtube/
│   └── icon.png                      # Mod icon
├── bin/
│   ├── windows-x86_64/...
│   ├── linux-x86_64/...
│   ├── linux-aarch64/...
│   ├── macos-x86_64/...
│   └── macos-aarch64/...

build.gradle                          # Fabric Loom build
gradle.properties                     # Version props
```

---

## Key Design Decisions (from user)

| Question | Decision |
|---|---|
| Search UI | **Dedicated YouTube section** like "Minecraft" section, with own search bar + results |
| Browsing | **Channel + playlist support** via yt-dlp resolution |
| API | **yt-dlp ytsearchN (no key)** — see decision log §4 |
| External deps | **Bundle yt-dlp + ffmpeg binaries** in mod jar |
| Audio engine | **Reuse AudioEngine infrastructure** (fade, viz, DSP, ducking) by piping PCM into SourceDataLine |
| Mod distribution | **Separate Modrinth project** "Melodix YouTube" depending on "Melodix" |
