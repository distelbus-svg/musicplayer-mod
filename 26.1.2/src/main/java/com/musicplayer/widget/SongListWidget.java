package com.musicplayer.widget;

import com.musicplayer.AlbumArtManager;
import com.musicplayer.AudioEngine;
import com.musicplayer.MusicPlayerManager;
import com.musicplayer.SongInfo;
import com.musicplayer.VanillaMusicPlayer;
import com.musicplayer.VanillaTrack;
import com.musicplayer.config.MusicPlayerConfig;
import com.musicplayer.player.SoundtrackStateManager;
import com.musicplayer.theme.ColorTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

public class SongListWidget extends ObjectSelectionList<SongListWidget.SongEntry> {

    private final MusicPlayerManager manager;
    private List<SongInfo> allSongs = List.of();
    private Map<String, List<SongInfo>> allCategories = null;
    // Pre-computed lowercase title/artist for fast filtering
    private String[] lowerTitles = new String[0];
    private String[] lowerArtists = new String[0];
    private SongAction onSelect = null;
    private boolean scrolling = false;

    public SongListWidget(Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
        this.manager = MusicPlayerManager.getInstance();
    }

    public void setOnSelect(SongAction onSelect) {
        this.onSelect = onSelect;
    }

    public void setSongs(List<SongInfo> songs) {
        this.allCategories = null;
        this.allSongs = List.copyOf(songs);
        rebuildLowerCache();
        this.clearEntries();
        for (int i = 0; i < songs.size(); i++) {
            addEntry(new SongEntry(songs.get(i), i, -1));
        }
    }

    public void setSongsGrouped(Map<String, List<SongInfo>> categories) {
        this.allCategories = categories;
        this.allSongs = categories.values().stream().flatMap(List::stream).toList();
        rebuildLowerCache();
        this.clearEntries();
        int globalTrackIndex = 0;
        for (var entry : categories.entrySet()) {
            String category = entry.getKey();
            List<SongInfo> songs = entry.getValue();
            if (songs.isEmpty()) continue;
            addEntry(new SongEntry(null, -1, -1, category));
            for (SongInfo song : songs) {
                addEntry(new SongEntry(song, globalTrackIndex, globalTrackIndex));
                globalTrackIndex++;
            }
        }
    }

    private void rebuildLowerCache() {
        int n = allSongs.size();
        lowerTitles = new String[n];
        lowerArtists = new String[n];
        for (int i = 0; i < n; i++) {
            SongInfo s = allSongs.get(i);
            lowerTitles[i] = s.getTitle() != null ? s.getTitle().toLowerCase() : "";
            lowerArtists[i] = s.getArtist() != null ? s.getArtist().toLowerCase() : "";
        }
    }

    public void setFilter(String query) {
        if (query == null || query.isEmpty()) {
            if (allCategories != null) {
                setSongsGrouped(allCategories);
            } else {
                setSongs(allSongs);
            }
            return;
        }
        this.clearEntries();
        String lower = query.toLowerCase();
        for (int i = 0; i < allSongs.size(); i++) {
            if (lowerTitles[i].contains(lower) || lowerArtists[i].contains(lower)) {
                addEntry(new SongEntry(allSongs.get(i), -1, -1));
            }
        }
    }

    public int getEntryCount() {
        return this.children().size();
    }

    @Override
    public void setFocused(boolean focused) {
        // Don't let SongListWidget steal focus from EditBox
    }

    public void scrollToIndex(int index) {
        if (index < 0 || index >= getItemCount()) return;
        centerScrollOn(this.children().get(index));
    }

    @Override
    public int getRowWidth() {
        return this.width - 20;
    }

    @Override
    protected int scrollBarX() {
        return this.getX() + this.width - 6;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
        if (event.buttonInfo().button() == 0) {
            double sx = event.x();
            double sy = event.y();
            int sbx = scrollBarX();
            if (sx >= sbx && sx < sbx + 6 && sy >= getY() && sy < getY() + getHeight()) {
                this.scrolling = true;
                double scrollRange = Math.max(1, this.maxScrollAmount());
                double relY = (sy - getY()) / (double) getHeight();
                this.setScrollAmount(relY * scrollRange);
                return true;
            }
        }
        return super.mouseClicked(event, isFocused);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.scrolling && event.buttonInfo().button() == 0) {
            double sy = event.y();
            double scrollRange = Math.max(1, this.maxScrollAmount());
            double relY = (sy - getY()) / (double) getHeight();
            this.setScrollAmount(relY * scrollRange);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.buttonInfo().button() == 0 && this.scrolling) {
            this.scrolling = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    /**
     * Draws a tiny 3-bar equalizer (Spotify-style "now playing" indicator).
     * Bars are driven by the live visualizer data so they bounce with the audio.
     */
    private static void drawTinyEqualizer(GuiGraphicsExtractor graphics, int x, int y,
                                          int entryHeight, boolean isMc, int color) {
        // Pull a few bars from the active backend. If no data (e.g. paused/no
        // spectrum yet), fall back to a gentle idle animation based on the clock.
        float[] bars = new float[3];
        boolean hasData;
        if (isMc) {
            float[] src = new float[8];
            hasData = SoundtrackStateManager.getInstance().getVisualizerBars(src, src.length);
            if (hasData) {
                bars[0] = src[0];
                bars[1] = src[src.length / 2];
                bars[2] = src[src.length - 1];
            }
        } else {
            AudioEngine engine = MusicPlayerManager.getInstance().getEngine();
            float[] src = new float[8];
            engine.getVisualizerBars(src, src.length);
            hasData = false;
            for (float v : src) if (v > 0.01f) { hasData = true; break; }
            if (hasData) {
                bars[0] = src[0];
                bars[1] = src[src.length / 2];
                bars[2] = src[src.length - 1];
            }
        }

        if (!hasData) {
            // Idle shimmer so the indicator isn't static silence.
            long t = System.currentTimeMillis() / 120L;
            for (int i = 0; i < 3; i++) {
                bars[i] = 0.30f + 0.25f * (float) Math.sin((t + i * 2) * 0.8);
            }
        }

        int barW = 2;
        int gap = 1;
        int maxH = Math.max(4, entryHeight - 6);
        int baseY = y + entryHeight - 3;
        for (int i = 0; i < bars.length; i++) {
            float v = Math.max(0.12f, Math.min(1f, bars[i]));
            int h = Math.max(2, Math.round(v * maxH));
            int bx = x + i * (barW + gap);
            graphics.fill(bx, baseY - h, bx + barW, baseY, color);
        }
    }

    public class SongEntry extends ObjectSelectionList.Entry<SongEntry> {
        private final SongInfo song;
        private final int index;
        private final int trackIndex;
        private final String headerText;
        private final boolean isHeader;

        public SongEntry(SongInfo song, int index, int trackIndex) {
            this(song, index, trackIndex, null);
        }

        public SongEntry(SongInfo song, int index, int trackIndex, String headerText) {
            this.song = song;
            this.index = index;
            this.trackIndex = trackIndex;
            this.headerText = headerText;
            this.isHeader = headerText != null;
        }

        @Override
        public Component getNarration() {
            if (isHeader) return Component.literal(headerText);
            return Component.literal(song.getTitle());
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int entryIndex, int top, boolean isSelected, float partialTick) {
            int left = this.getX();
            int y = this.getY();
            int entryWidth = this.getWidth();
            int entryHeight = this.getHeight();

            if (isHeader) {
                graphics.fill(left, y, left + entryWidth, y + entryHeight, 0x88000000);
                var font = Minecraft.getInstance().font;
                graphics.centeredText(font, Component.literal(headerText).withStyle(s -> s.withBold(true)),
                    left + entryWidth / 2, y + 2, 0xFFFFAA00);
                return;
            }

            // Determine if this row is the currently-playing track.
            // Works for both backends: USER (local files via AudioEngine) and
            // MINECRAFT (vanilla soundtrack via VanillaMusicPlayer).
            boolean isMc = manager.getCurrentSource() == MusicPlayerManager.SongSource.MINECRAFT;
            boolean isPlaying = false;
            if (isMc) {
                VanillaTrack vt = VanillaMusicPlayer.getInstance().getCurrentTrack();
                boolean vp = VanillaMusicPlayer.getInstance().isPlaying() || VanillaMusicPlayer.getInstance().isPaused();
                if (vt != null && vp && vt.id().equals(song.getPath())) {
                    isPlaying = VanillaMusicPlayer.getInstance().isPlaying();
                }
            } else if (manager.getCurrentSong() != null
                    && manager.getCurrentSong().getPath().equals(song.getPath())) {
                isPlaying = manager.getEngine().isActive() && !manager.getEngine().isPaused();
            }

            boolean hovered = SongListWidget.this.getHovered() == this;

            int bgColor;
            if (isPlaying) {
                bgColor = 0x6000AA00;
            } else if (isSelected || hovered) {
                bgColor = 0x60FFFFFF;
            } else if (entryIndex % 2 == 0) {
                bgColor = 0x30000000;
            } else {
                bgColor = 0x20000000;
            }
            graphics.fill(left, y, left + entryWidth, y + entryHeight, bgColor);

            var font = Minecraft.getInstance().font;
            ColorTheme th = ColorTheme.get(manager.getConfig().getThemeId());
            int accent = isMc ? th.accent() : 0xFF55FF55;

            // Left-edge indicator: tiny animated equalizer on the playing row
            // (when enabled), otherwise the static ▶ glyph.
            int indicatorWidth;
            if (isPlaying) {
                MusicPlayerConfig cfg = manager.getConfig();
                if (cfg.isMiniVisualizerEnabled()) {
                    drawTinyEqualizer(graphics, left + 4, y, entryHeight, isMc, th.accent());
                    indicatorWidth = 14;
                } else {
                    graphics.text(font, "\u25B6 ", left + 4, y + 2, accent, true);
                    indicatorWidth = 10;
                }
            } else {
                String prefix = (song.getSource() == SongInfo.SourceType.YOUTUBE) ? "\u25B7 " : "   ";
                graphics.text(font, prefix, left + 4, y + 2,
                        song.getSource() == SongInfo.SourceType.YOUTUBE ? 0xFFFF5555 : 0xFFFFFFFF, true);
                indicatorWidth = 10;
            }

            // Album art thumbnail
            int artSize = Math.min(entryHeight - 4, 14);
            String songPath = song.getPath();
            Identifier artId = songPath != null ? AlbumArtManager.getInstance().getArtForSong(songPath, null) : null;
            if (artId != null) {
                int artX = left + 4 + indicatorWidth;
                int artY = y + (entryHeight - artSize) / 2;
                graphics.blit(artId, artX, artY, artX + artSize, artY + artSize, 0.0f, 1.0f, 0.0f, 1.0f);
            }
            int artArea = (artId != null) ? (artSize + 4) : 0;
            int titleOffset = left + 4 + indicatorWidth + artArea;

            int textColor = isPlaying ? accent : (song.getSource() == SongInfo.SourceType.YOUTUBE ? 0xFFFF5555 : 0xFFFFFFFF);

            int rightEdge = left + entryWidth;
            int heartArea = 16;

            if (onSelect == null) {
                boolean liked = manager.getConfig().isLiked(song.getPath());
                int heartColor = liked ? 0xFFFF4444 : 0xFF555555;
                graphics.text(font, "\u2665", rightEdge - heartArea - 4, y + 2, heartColor, true);
            }

            graphics.text(font, song.getTitle(), titleOffset, y + 2, textColor, true);

            String subText = song.getArtist();
            if (subText != null) {
                int artistRight = onSelect == null ? rightEdge - heartArea - 16 : rightEdge - 8;
                int subWidth = font.width(subText);
                int subX = artistRight - subWidth;
                if (subX > titleOffset + font.width(song.getTitle()) + 8) {
                    graphics.text(font, subText, subX, y + 2, 0xFFAAAAAA, true);
                }
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
            if (isHeader) return false;
            if (event.buttonInfo().button() == 0) {
                SongListWidget.this.setSelected(this);
                if (onSelect != null) {
                    onSelect.run(song, trackIndex >= 0 ? trackIndex : index);
                } else {
                    int entryWidth = this.getWidth();
                    double relX = event.x() - this.getX();
                    if (relX > entryWidth - 22) {
                        var config = manager.getConfig();
                        String path = song.getPath();
                        if (config.isLiked(path)) {
                            config.unlikeSong(path);
                        } else {
                            config.likeSong(path);
                        }
                    } else {
                        if (manager.getCurrentSource() == MusicPlayerManager.SongSource.MINECRAFT) {
                            manager.playVanillaTrackById(song.getPath());
                        } else {
                            int playIdx = trackIndex >= 0 ? trackIndex : index;
                            manager.playSong(playIdx);
                        }
                    }
                }
                return true;
            }
            return false;
        }

        public SongInfo getSong() {
            return song;
        }

        public boolean isHeader() {
            return isHeader;
        }
    }

    @FunctionalInterface
    public interface SongAction {
        void run(SongInfo song, int index);
    }
}
