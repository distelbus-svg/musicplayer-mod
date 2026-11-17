# Melodix YouTube Extension: implementation spec

Rewritten after the core/ui split. The old version of this spec planned mixins into `MusicPlayerScreen`, `SidebarWidget` and `MusicPlayerManager`; none of that is needed any more. The base mod now exposes `MelodixApi` and the addon only registers into it. See `ARCHITECTURE.md` for the base mod layout.

## Overview

A client-side Fabric addon (separate repo, separate Modrinth project "Melodix YouTube") that adds YouTube search, browsing and streaming to Melodix.

**Repo:** `/Volumes/Samsung/Dokumente/Opencode/Melodix/musicplayer-mod-YouTube-extension` (currently empty)
**Mod id:** `musicplayer-youtube`, package `com.musicplayer.youtube`
**Depends on:** `musicplayer`, `fabricloader`, `fabric-api`. Minecraft version range: see open question 1.

## What the base mod already provides (do not reimplement)

All in `core/src/main/java/com/musicplayer/MelodixApi.java` unless noted.

| Need | Base mod hook |
|---|---|
| Play non-file tracks | `registerStreamSourceProvider(StreamSourceProvider)`. `AudioEngine.play(String url, long seekMs)` looks the provider up with `streamSourceFor(path)` and runs the returned `StreamSource` through the normal slot pipeline: DSP/EQ, fade, duck, crossfade, visualizer. |
| Cover art for YouTube songs | `registerArtProvider(Function<String, byte[]>)`. Called off the render thread by `AlbumArtManager` with `SongInfo.getPath()`; return image bytes already cropped, or null if the path is not yours. |
| Own UI section | `registerTab(id, label, Supplier<UiScreen>)`: adds a header tab after Home / Minecraft / Playlists. The screen extends `OreScreen` (module `ui`) and overrides `activeCustomTab()` to return the tab id. |
| Settings | `registerSettingsCategory(id, name, blurb, Supplier<List<SettingsRow>>)` with `SettingsRow.toggle/button/info`. |
| Song model | `SongInfo(title, artist, path, SourceType.YOUTUBE)`; `YOUTUBE` already exists and is already styled in the Ore UI (search results, playlists, HUD). |
| "Addon not installed" prompt | Home and Settings show it while `hasStreamSourceProviders()` is false and link to `YOUTUBE_ADDON_URL` (currently a placeholder, set it when the Modrinth page exists). |

`MusicPlayerManager.playSong()` and seek already have a `SourceType.YOUTUBE` branch that routes to the engine's stream path. Stream contract:

- `StreamSourceProvider.accepts(path)`: cheap, no I/O.
- `StreamSourceProvider.open(path, seekToMs)`: runs on the playback thread, may block; throw `IOException` with a user-readable message (shown as a notification).
- `StreamSource`: `pcm()` = raw interleaved **s16le** PCM, plus `sampleRate()`, `channels()`, `durationMs()` (0 if unknown), `startMs()` (the position actually started at: the requested seek, or 0 if the source could not seek). `close()` must kill any child process and unblock a pending `pcm().read()`.

## Components

```
com.musicplayer.youtube
  YoutubeMod              ClientModInitializer: registers provider, art provider, tab, settings category
  YoutubeStreamProvider   StreamSourceProvider: accepts YouTube URLs/ids; open() = yt-dlp | ffmpeg -> PCM
  YoutubeSource           StreamSource implementation (owns the processes, close() destroys them)
  YoutubeResolver         search()/browse() via yt-dlp, resolves stream URLs at play time
  YoutubeVideoInfo        search result data (id, title, channel, duration, thumbnail)
  YoutubeArtProvider      thumbnail download + crop -> bytes
  YoutubeTabScreen        extends OreScreen: search box, results, channel/playlist view
  YoutubeConfig           extension config (own JSON file)
  BinaryExtractor         extracts bundled yt-dlp/ffmpeg per OS/arch
```

### Streaming (`YoutubeStreamProvider.open`)

1. Spawn `yt-dlp -f bestaudio -o - <url>` piped into `ffmpeg -i pipe:0 -f s16le -ac 2 -ar 44100 -` (add `-ss` / yt-dlp section options when `seekToMs > 0`).
2. Return a `YoutubeSource` whose `pcm()` is ffmpeg's stdout. `startMs()` = `seekToMs` if the seek worked, else 0.
3. `durationMs()` from yt-dlp metadata (the search result already has it).
4. Pause, progress, fade, visualizer, volume are the engine's job; the addon provides no playback code of its own. Seek is a re-`open()` with a new `seekToMs`.

### Search and browsing (`YoutubeResolver`)

Search is done with yt-dlp, not Invidious (decision log, 2026-07-31). Reasons: public Invidious instances are rate-limited, blocked or shut down on a rolling basis and there is no stable default instance; one tool for both "paste a URL" and "search" means one failure mode and one update story; cookies-from-browser also helps age-gated search results; no extra dependency or config field.

- Search: `yt-dlp --flat-playlist --no-warnings --dump-single-json ytsearchN:<query>` (id, title, duration, thumbnail, channel, no stream URL resolution).
- Channel/playlist: resolve the URL with the same flat-playlist mode.
- Stream URLs are never persisted: a playlist stores `SongInfo(path = video URL, source = YOUTUBE)` and the stream is resolved when played.
- All calls run on a background thread; push results to the render thread via `UiHost.runOnMain`. Debounce typing ~300 ms.
- Reserved for later: Invidious endpoints behind the same `YoutubeResolver.search()` surface.

### UI (`YoutubeTabScreen`)

A normal Ore screen opened from the registered tab: search box and result list (title, channel, duration), a channel/playlist view, click = build `SongInfo` with `SourceType.YOUTUBE` and play it through `MusicPlayerManager`. Adding results to playlists goes through `PlaylistManager` (see open question 2).

### Bundled binaries

`src/main/resources/bin/<os>-<arch>/{yt-dlp,ffmpeg}` for windows-x86_64, linux-x86_64, linux-aarch64, macos-x86_64, macos-aarch64. Extract on first run to `<config-dir>/musicplayer/youtube/bin/` (config dir via the host), set the executable bit on Linux/macOS. Config: `youtubeAutoDownload` (download yt-dlp if missing), `youtubeCustomBinPath` (override), stored in the addon's own JSON file next to the base config (no mixin into `MusicPlayerConfig`).

### Error handling

- yt-dlp missing: download automatically (if enabled) and show a notification.
- Video unavailable/blocked, no playable format: throw `IOException` from `open()` with a readable message, the engine shows it as a notification; skip to the next track.
- ffmpeg pipe error: retry once with the audio-only URL, then fail as above.

## Open questions (verify before building)

1. **Version coverage.** Each Melodix jar embeds `core` and `ui` (Java 17 source) per Minecraft version. If the addon touches only `core`/`ui`/Java it might build once (Java 17 bytecode, `compileOnly` against one Melodix jar) and run on every version, or it might need a jar per version. Check binary compatibility across the 26.3 and backport jars first. This also decides the `minecraft` constraint in `fabric.mod.json`.
2. **Add-to-playlist for YouTube results.** Confirm which Ore screen actions already work for `YOUTUBE` songs; the tab screen needs its own action if there is no reusable one.
3. **Ore UI header tab limit.** `OreScreen` lays custom tabs out after the three built-in ones; check how it behaves with a long label on narrow windows.
4. **Binary size.** Five OS/arch bundles of yt-dlp + ffmpeg make a large jar (Modrinth limits apply); consider download-on-first-run instead of bundling.

## Decisions (from user)

| Question | Decision |
|---|---|
| UI | Dedicated YouTube section with its own search bar and results (now an Ore header tab) |
| Browsing | Channel + playlist support via yt-dlp |
| Search backend | yt-dlp `ytsearchN`, no API key |
| External deps | Bundle yt-dlp + ffmpeg (see open question 4) |
| Audio engine | Reuse the base engine by returning PCM through `StreamSource` |
| Distribution | Separate Modrinth project "Melodix YouTube" depending on "Melodix" |
