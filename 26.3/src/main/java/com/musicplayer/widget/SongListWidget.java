package com.musicplayer.widget;

import com.musicplayer.AlbumArtManager;
import com.musicplayer.MusicPlayerManager;
import com.musicplayer.SongInfo;
import com.musicplayer.player.SoundtrackStateManager;
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
    private SongAction onSelect = null;
    private RightClickCallback onRightClick = null;
    private boolean scrolling = false;

    public SongListWidget(Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
        this.manager = MusicPlayerManager.getInstance();
    }

    public void setOnSelect(SongAction onSelect) {
        this.onSelect = onSelect;
    }

    public void setOnRightClick(RightClickCallback cb) {
        this.onRightClick = cb;
    }

    public void setSongs(List<SongInfo> songs) {
        this.allCategories = null;
        this.allSongs = List.copyOf(songs);
        this.clearEntries();
        for (int i = 0; i < songs.size(); i++) {
            addEntry(new SongEntry(songs.get(i), i, -1));
        }
    }

    public void setSongsGrouped(Map<String, List<SongInfo>> categories) {
        this.allCategories = categories;
        this.allSongs = categories.values().stream().flatMap(List::stream).toList();
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
            SongInfo song = allSongs.get(i);
            String title = song.getTitle();
            String artist = song.getArtist();
            if ((title != null && title.toLowerCase().contains(lower)) ||
                (artist != null && artist.toLowerCase().contains(lower))) {
                addEntry(new SongEntry(song, i, -1));
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
        if (event.buttonInfo().button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
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
        if (this.scrolling && event.buttonInfo().button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            double sy = event.y();
            double scrollRange = Math.max(1, this.maxScrollAmount());
            double relY = (sy - getY()) / (double) getHeight();
            this.setScrollAmount(relY * scrollRange);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.buttonInfo().button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && this.scrolling) {
            this.scrolling = false;
            return true;
        }
        return super.mouseReleased(event);
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

            boolean isPlaying = false;
            if (manager.getCurrentSong() != null && manager.getCurrentSong().getPath().equals(song.getPath())) {
                isPlaying = manager.getEngine().isActive();
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

            int textColor = isPlaying ? 0xFF55FF55 : (song.getSource() == SongInfo.SourceType.YOUTUBE ? 0xFFFF5555 : 0xFFFFFFFF);

            var font = Minecraft.getInstance().font;
            int rightEdge = left + entryWidth;
            int heartArea = 16;

            int prefixW;
            if (isPlaying) {
                prefixW = 12;
                int barH = entryHeight - 6;
                int barY = y + 3;
                float[] bars = new float[3];
                boolean hasViz = false;
                if (manager.getCurrentSource() == MusicPlayerManager.SongSource.MINECRAFT) {
                    hasViz = SoundtrackStateManager.getInstance().getVisualizerBars(bars, 3);
                } else {
                    manager.getEngine().getVisualizerBars(bars, 3);
                    for (float b : bars) if (b > 0.01f) { hasViz = true; break; }
                }
                int barW = 2;
                int gap = 1;
                for (int i = 0; i < 3; i++) {
                    int bx = left + 4 + i * (barW + gap);
                    int bh = hasViz ? Math.max(2, (int)(bars[i] * barH)) : barH;
                    graphics.fill(bx, barY + barH - bh, bx + barW, barY + barH, 0xFF55FF55);
                }
            } else {
                String prefix = song.getSource() == SongInfo.SourceType.YOUTUBE ? "\u25B7 " : "   ";
                prefixW = font.width(prefix);
            }

            int titleOffset = left + 4 + (isPlaying ? prefixW : 0);

            // Album art thumbnail
            int artSize = Math.min(entryHeight - 4, 14);
            String songPath = song.getPath();
            Identifier artId = songPath != null ? AlbumArtManager.getInstance().getArtForSong(songPath, null) : null;
            if (artId != null) {
                int artX = titleOffset;
                int artY = y + (entryHeight - artSize) / 2;
                graphics.blit(artId, artX, artY, artX + artSize, artY + artSize, 0.0f, 1.0f, 0.0f, 1.0f);
            }
            int artArea = (artId != null) ? (artSize + 4) : 0;
            titleOffset += artArea;

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
                int titleW = font.width(song.getTitle());
                if (subX > titleOffset + titleW + 8) {
                    graphics.text(font, subText, subX, y + 2, 0xFFAAAAAA, true);
                }
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
            if (isHeader) return false;
            if (event.buttonInfo().button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT && onRightClick != null) {
                onRightClick.run(song, (int)event.x(), (int)event.y());
                return true;
            }
            if (event.buttonInfo().button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
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

    @FunctionalInterface
    public interface RightClickCallback {
        void run(SongInfo song, int mouseX, int mouseY);
    }
}
