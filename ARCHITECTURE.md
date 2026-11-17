# Melodix architecture

Read this first in a fresh chat. Build/deploy details, Prism instance mapping and 26.3 (GLFW→SDL) porting notes are in `AGENTS.md`. Mod id: `musicplayer`, package: `com.musicplayer`.

## Modules

| Module | Java | Minecraft imports | Contents |
|---|---|---|---|
| `core/` | 17 | none (enforced: `./gradlew :core:compileJava`) | audio, config, playlists, EQ, export, `MusicPlayerManager`, ports, addon API |
| `ui/` | 17 | none (enforced) | Ore UI screens and HUD, drawn through `Gfx` / `UiHost` |
| `26.3/` | 21 | yes | **main dev version**: adapter only (see below) |
| `Backports/1.21.11`, `1.21.1`, `1.20.1` | | yes | maintained backports, **generated** from 26.3 by `tools/port_adapter.py` |
| `26.2`, `26.1.2`, `Backports/1.21.4`, `1.19.2`, `1.18.2`, `1.16.5` | | yes | frozen, may not compile |

Every version project adds `core/src/main/java` and `ui/src/main/java` as extra `srcDir`s, so shared code is compiled into each jar. A version project only holds the adapter.

**Where does a change go?**
- Behaviour, logic, UI layout → `core/` or `ui/`, then build all versions. Nothing else to do.
- Sound/texture/mixin glue, `MusicPlayerMod`, `host/*` → edit in `26.3/`, run `python3 tools/port_adapter.py <1.21.11|1.21.1|1.20.1>`, build the backport.
- Never hand-edit `Backports/<v>/src/main/java`: the tool wipes and regenerates it. A new API difference becomes a patch in `tools/port_adapter.py` (the `VERSIONS` dict holds per-version flags; the `host/` classes for pre-1.21.9 live there as templates). `Backports/` is gitignored.
- Backports have Ore UI only; the classic screens exist only in 26.x.

## The seam: ports (core → game)

`core/.../port/Ports` is a static holder. On first use it reflectively calls `com.musicplayer.McBootstrap.install()` (class lives in each version project), which calls `Ports.install(...)` and `UiHost.install(...)`. Rule: `install()` must stay thin and lazy, no singleton may be touched during it (singletons load config, which needs the host).

| Port | Purpose |
|---|---|
| `HostPort` | config dir, mod version, run on main thread, stop game music, notifications, mini-player slide-in, now-playing toast, link-confirm screen |
| `VanillaPort` | play/pause/seek/volume/tick for Minecraft soundtrack tracks (implemented by `VanillaMusicPlayer`) |
| `SoundtrackPort` | state of the vanilla soundtrack engine: visualizer bars, ducking, elapsed time, auto-music (implemented by `SoundtrackStateManager`) |
| `TrackRegistryPort` | discover/list vanilla tracks (`SoundtrackRegistry`) |
| `ArtPort` | album art lookup/registration, textures (`AlbumArtManager`) |

`UiScreen` is an empty marker interface so core can hold/open screens without knowing the game's `Screen`.

## Playback

Two engines, switched by `MusicPlayerManager.SongSource` (`USER` / `MINECRAFT`):

- **User files (MP3/WAV, plus addon streams):** `core/AudioEngine` (JLayer / javax.sound, `PlaybackSlot` based, `AudioDSP` for EQ). Handles fade, duck, crossfade, seek, visualizer. `play(File, seekMs)` and `play(String url, seekMs)`.
- **Minecraft soundtrack:** `26.3/player/*` drives Mojang's `SoundManager`. `SoundtrackStateManager` (state, `confirmedActive`, pending pause/fade, visualizer), `SoundtrackRegistry` (discovery, per-variant failure tracking), `FixedSoundInstance` (play a specific variant), `SoundThreadExecutor` + `FullPcmDecoder` + `PcmStream` (seeking via OpenAL buffer swap), `VanillaPCMCache` (amplitude envelope for the visualizer), `MusicMetadataRegistry`.
- **Mixins** (`26.3/mixin`, config `musicplayer.mixins.json`): accessors for `SoundManager`, `SoundEngine`, `Channel.source`, `WeighedSoundEvents`, and `MusicManagerMixin` (keeps vanilla environmental music from fighting the mod).

`core/MusicPlayerManager` (singleton, ~950 lines) is the facade the UI talks to: queue (`getPlaylist`, `getCurrentIndex`), `playSong`, `nextTrack`/`previousTrack`, `seek(percent)`, volume, shuffle/repeat/`PlaybackMode`, custom playlist loading, `getUpNextQueue`, liked songs. It owns an `AudioEngine`, reads `MusicPlayerConfig`, and reaches the game only through `Ports`.

Data model: `SongInfo` (title, artist, path, `SourceType`), `VanillaTrack` (record), `PlayableTrack` (interface), `Playlist`/`PlaylistManager`, `TrackMetadata`. Stats: `stats/ListeningStatisticsManager`. Theme: `theme/ColorTheme`.

## UI

- `ui/ui/*`: the abstraction layer. `Gfx` (drawing), `UiHost` (game services: open screen, fullscreen, mouse, text fields, key bindings, file dialogs, HUD registration, classic-UI hooks), `UiTextField`, small input records (`UiKey`, `UiChar`, `UiMouse`).
- `ui/ore/*`: Ore UI. `OreScreen` is the base class (`activeTab`, `renderBody`; tabs: home, minecraft, playlists, addon tab). Screens: Home, Player (`OrePlayerScreen` base, `OreMinecraftScreen`, `OrePlaylistScreen`), Playlists, PlaylistEdit, Queue, Search, Settings, Setup, Stats. `OreTheme`, `OreIcons`, `OreHud`.
- `ui/hud/*`: `MiniPlayerHud`, `AudioVisualizerHud`, `NowPlayingHud`, `NotificationManager`.
- `26.3/host/*` implements the UI abstractions with real Minecraft classes: `McUiHost`, `GfxImpl`, `HostScreen` (a `Screen` that wraps an `OreScreen` via `OreScreen.Host`), `EditBoxField`.
- Classic (non-Ore) UI is 26.x only and lives in `26.3/`: `MusicPlayerScreen` (~2100 lines), `MusicPlayerScreenLegacy`, `MusicSettingsScreen`, `PlaylistEditScreen`, `PlaylistListScreen`, `UpNextScreen`, `ListeningStatsScreen`, plus `widget/*`.

## Entry points and lifecycle

- `fabric.mod.json` client entrypoint: `26.3/.../MusicPlayerMod` (`ClientModInitializer`). Registers the 7 key mappings (open, play/pause, next, prev, volume up/down, custom volume) and a client-tick handler (focus polling, world-leave handling so music survives server hops).
- Config: `.minecraft/config/musicplayer.json` (`MusicPlayerConfig`); playlists and album-art cache under `.minecraft/config/musicplayer/`.

## Addon API (`core/MelodixApi`)

Static registries; core never depends on an addon being present.
- `registerStreamSourceProvider(StreamSourceProvider)`: `accepts(path)` + `open(path, seekMs)` returning a `StreamSource` (raw s16le PCM + sampleRate/channels/duration/startMs). The engine runs it through the normal pipeline (EQ, fade, duck, crossfade, visualizer).
- `registerArtProvider(Function<String, byte[]>)`: cover art for non-file paths.
- `registerTab(id, label, Supplier<UiScreen>)`: extra Ore header tab (screen extends `OreScreen`, overrides `activeCustomTab()`).
- `registerSettingsCategory(...)` with `SettingsRow.toggle/button/info`.
- `YOUTUBE_ADDON_URL` is a placeholder. `MELODIX_YOUTUBE_SPEC.md` is the plan for the YouTube addon built on this API (no mixins into the base mod).

## Known stale spots

- `sync-source.sh` is obsolete (superseded by `tools/port_adapter.py`); the file is still in the repo.
- `26.3/src/.../` contains empty `config/`, `hud/`, `ore/`, `stats/`, `theme/` dirs: leftovers, the real code is in `core/` and `ui/`.
- `README.md` says Minecraft >= 26.1, which does not describe the backports.
