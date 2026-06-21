# Melodix Music Player

## Build (all versions)
```sh
./gradlew build         # builds all 6 versions in parallel
```

Multi-project structure: `./gradlew :26.1.2:build` or `./gradlew :Backports:1.21.4:build` for individual versions.

JARs are output to each subproject's `build/libs/` directory:
- `26.1.2/build/libs/`
- `Backports/*/build/libs/`

## Syncing source across versions
Use `sync-source.sh` to copy a file from 26.1.2 to all backports that already have it:
```sh
./sync-source.sh src/main/java/com/musicplayer/AudioEngine.java
```
Only copies to backports where the target file already exists. To add a new file to a backport, create it manually first.

## Volume fix (cross-version)
Three changes needed in each backport:
1. **AudioEngine.play()** — `primary.fadeVolume = this.volume;` after `primary = new PlaybackSlot();`
2. **SoundtrackStateManager.tick()** — when `active && !confirmedActive`, call `soundExecutor.setVolume(manualInstance, volume)` if `volume < 1.0f`
3. **MusicPlayerManager()** — `SoundtrackStateManager.getInstance().setVolume(config.getVolume());` in constructor

See `26.1.2` for reference implementation. Backports 1.21.4, 1.20.1, 1.19.2, 1.16.5 share the same PlaybackSlot-based AudioEngine. 1.18.2 has a simpler engine and different SSM.

## Project Structure
- `26.1.2/` — latest Minecraft version (unobfuscated, `net.fabricmc.fabric-loom`)
- `Backports/*/` — each backport version (uses `net.fabricmc.fabric-loom-remap`)
- `sync-source.sh` — copy a source file from 26.1.2 to matching backport files
- `reference/` — reference mods (overtune-1.0.0.jar), icon.png
- Config lives at `.minecraft/config/musicplayer.json`
- Album art cache at `.minecraft/config/musicplayer/albumart/`
- Playlists at `.minecraft/config/musicplayer/playlists/`

## Key Architecture
- **Two engines**: `AudioEngine` (JLayer/javax.sound for user MP3/WAV) and `VanillaMusicPlayer` (SoundManager for Minecraft tracks)
- **Source toggle**: `MusicPlayerManager.SongSource.USER` / `MINECRAFT`
- **UI**: Screen-based GUI (`MusicPlayerScreen`), HudElement overlay for notifications/visualizer/mini-player (`HudElementRegistry.addLast(id, this::render)`)
- **Mixin config**: `musicplayer.mixins.json` in `com.musicplayer.mixin` package
- **Theme system**: `ColorTheme` record with 5 presets, applied via `ColorTheme.get(themeId)`

## Fixed Bugs
- **Game freeze on Creator (Music Box) / Comforting Memories**: Root cause was `NotificationManager.renderPanel()` rendering `♪` (U+266A), which triggered a Minecraft font fallback deadlock on macOS. Fixed by removing Unicode special characters and using char-count text truncation instead of `font.width()` calls. The freeze was never in the sound engine.
- **Search plays wrong song**: Fixed with `allCategories` field + `trackIndex = -1` for filtered results.
- **Clicking soundtrack plays random variant**: Fixed with `FixedSoundInstance` (per-variant resolution via `WeighedSoundEventsAccessor`).
- **Failed-track cascade freeze**: Fixed with per-variant failure API in `SoundtrackRegistry` (`markVariantFailed`/`isVariantFailed` keyed on variant ID, not event ID).
- **Wrong play/pause button state during transitions**: Fixed with `confirmedActive` flag in `SoundtrackStateManager` (prevents premature completion detection before sound engine confirms playback).
- **Fade-on-pause for vanilla tracks**: Added `pendingPause` flag; `togglePause()` triggers `startFade(0)` then pauses channel on fade completion. Resume fades back up.
- **Audio visualizer for vanilla tracks**: Added `VanillaPCMCache` — decodes OGG via jOrbis (Minecraft runtime dep) to a compact amplitude envelope per ~100ms. `SoundtrackStateManager.getVisualizerBars()` reads from cache. Falls back silently on decode failure.
- **Environmental music plays while mod plays custom music**: `MusicManager` can start/continue playing its own auto tracks while the mod plays USER source MP3s. `SoundManager.stop(autoInstance)` alone doesn't tell `MusicManager` to stop — it might clear `currentMusic` lazily. Fixed by calling `Minecraft.getInstance().getMusicManager().stopPlaying()` directly in `MusicPlayerManager.playSong()`, `SoundtrackStateManager.playTrack()`, and `MusicPlayerManager.seek()`.

## Known Bug Areas to Investigate
- Mini player controls don't respond to clicks (HudElement has no mouse handler — consider using Screen overlay or Minecraft's input events)
- Mini player drag/resize only works when already pressed before render (GLFW poll may miss quick clicks)
- Album art not rendered for user MP3 files that have ID3v2 APIC tags (APIC extraction works, but no texture registration for display)
- Switching source while a song is playing may cause state desync

## Implemented Features
- **Vanilla track seeking** (`SoundManager` limitation workaround):
  - `ChannelSourceAccessor` mixin exposes `Channel.source` (OpenAL source ID)
  - `FullPcmDecoder` decodes OGG from seek time to end via jOrbis `VorbisFile.pcmSeek()`
  - `PcmStream` `AudioStream` wraps the decoded PCM `ByteBuffer`
  - `SoundThreadExecutor.seek()` pauses channel, unqueues/deletes old OpenAL buffers, attaches `PcmStream`, optionally resumes
  - `SoundtrackStateManager.seekManual(float seconds)` runs decode on a background thread
  - `ProgressBarWidget` already had click/drag — now calls `manager.seek(percent)` which delegates to `VanillaMusicPlayer.seek(seconds)` → `SSM.seekManual(seconds)` for MINECRAFT source
  - Seek works while paused (channel stays paused after seek, correct time tracking)
  - Uses Mojang's `com.jcraft.jorbis.VorbisFile` (Minecraft runtime dep) via reflection, same pattern as `VanillaPCMCache`

## Build Dependencies
- `com.jcraft:jorbis:0.0.17` — OGG Vorbis decoder (Minecraft runtime dep, compile-only via local jar in `build.gradle`). Used by `VanillaPCMCache` for visualizer data.
- `javazoom:jlayer:1.0.1` — MP3 decoder for user files.
- `net.jthink:jaudiotagger:3.0.1` — ID3/album art extraction.

## Common Fixes
- `Identifier.of()` → `Identifier.fromNamespaceAndPath()` or `Identifier.parse()`
- `Window.getWindow()` → `Window.handle()` for GLFW handle
- `mc.getLevel()` → `mc.level` (public field)
- `Minecraft.getWindow().getGuiScaledWidth()` → `Window.getGuiScaledWidth()`
- HudElement render signature: `(GuiGraphicsExtractor, DeltaTracker)`

## mod id
`musicplayer` (used in assets path, mixin config)
