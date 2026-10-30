package com.musicplayer.widget;

import com.musicplayer.Playlist;
import com.musicplayer.SongInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CheckboxSongListWidget extends ObjectSelectionList<CheckboxSongListWidget.SongCheckEntry> {

    private final Set<String> checkedPaths;
    private Playlist editingPlaylist;

    // Drag state
    SongCheckEntry dragSource;
    boolean dragging;
    double dragStartY;
    int dragAccumulatedSwaps;
    int dragIdx;
    private final int rowHeight;

    // Shift-click range
    private int lastClickedIndex = -1;

    public CheckboxSongListWidget(Minecraft minecraft, int width, int height, int y, int itemHeight, Set<String> initiallyChecked) {
        super(minecraft, width, height, y, itemHeight);
        this.checkedPaths = new HashSet<>(initiallyChecked);
        this.rowHeight = itemHeight;
    }

    public void setPlaylistSongs(Playlist playlist, List<SongInfo> allSongs) {
        this.editingPlaylist = playlist;
        this.clearEntries();

        checkedPaths.clear();
        for (SongInfo s : playlist.getSongs()) {
            checkedPaths.add(s.getPath());
        }

        for (SongInfo s : playlist.getSongs()) {
            addEntry(new SongCheckEntry(s, true));
        }

        for (SongInfo s : allSongs) {
            if (!checkedPaths.contains(s.getPath())) {
                addEntry(new SongCheckEntry(s, false));
            }
        }
    }

    public Set<String> getCheckedPaths() {
        return checkedPaths;
    }

    @Override
    public int getRowWidth() {
        return this.width - 20;
    }

    @Override
    protected int scrollBarX() {
        return this.getX() + this.width - 6;
    }

    void moveEntryUp(SongCheckEntry entry) {
        int idx = children().indexOf(entry);
        if (idx <= 0) return;
        SongCheckEntry above = children().get(idx - 1);
        if (!checkedPaths.contains(above.song.getPath())) return;
        children().set(idx, above);
        children().set(idx - 1, entry);
    }

    void moveEntryDown(SongCheckEntry entry) {
        int idx = children().indexOf(entry);
        if (idx < 0 || idx >= children().size() - 1) return;
        SongCheckEntry below = children().get(idx + 1);
        if (!checkedPaths.contains(below.song.getPath())) return;
        children().set(idx, below);
        children().set(idx + 1, entry);
    }

    void moveEntryUp(int idx) {
        if (idx <= 0) return;
        SongCheckEntry entry = children().get(idx);
        SongCheckEntry above = children().get(idx - 1);
        if (!checkedPaths.contains(above.song.getPath())) return;
        children().set(idx, above);
        children().set(idx - 1, entry);
    }

    void moveEntryDown(int idx) {
        if (idx < 0 || idx >= children().size() - 1) return;
        SongCheckEntry entry = children().get(idx);
        SongCheckEntry below = children().get(idx + 1);
        if (!checkedPaths.contains(below.song.getPath())) return;
        children().set(idx, below);
        children().set(idx + 1, entry);
    }

    public List<String> getOrderedPaths() {
        return children().stream()
            .map(e -> e.song.getPath())
            .filter(checkedPaths::contains)
            .collect(Collectors.toList());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging && dragSource != null && event.buttonInfo().button() == 0) {
            if (dragIdx < 0) return true;

            double deltaY = event.y() - dragStartY;
            int desiredSwaps = (int) Math.round(deltaY / rowHeight);
            int swapsToDo = desiredSwaps - dragAccumulatedSwaps;

            if (swapsToDo != 0) {
                boolean goDown = swapsToDo > 0;
                int limit = Math.abs(swapsToDo);
                for (int s = 0; s < limit; s++) {
                    int targetIdx = goDown ? dragIdx + 1 : dragIdx - 1;
                    if (targetIdx < 0 || targetIdx >= children().size()) break;
                    SongCheckEntry target = children().get(targetIdx);
                    if (!checkedPaths.contains(target.song.getPath())) break;
                    if (goDown) {
                        moveEntryDown(dragIdx);
                        dragIdx++;
                    } else {
                        moveEntryUp(dragIdx);
                        dragIdx--;
                    }
                    dragAccumulatedSwaps += goDown ? 1 : -1;
                }
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging && event.buttonInfo().button() == 0) {
            dragging = false;
            dragSource = null;
            dragIdx = -1;
            return true;
        }
        return super.mouseReleased(event);
    }

    public class SongCheckEntry extends ObjectSelectionList.Entry<SongCheckEntry> {
        private final SongInfo song;
        private final boolean inPlaylist;

        public SongCheckEntry(SongInfo song, boolean inPlaylist) {
            this.song = song;
            this.inPlaylist = inPlaylist;
        }

        @Override
        public Component getNarration() {
            return Component.literal(song.getTitle());
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int entryIndex, int top, boolean isSelected, float partialTick) {
            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();

            boolean hovered = CheckboxSongListWidget.this.getHovered() == this;
            boolean checked = checkedPaths.contains(song.getPath());

            int bgColor = isSelected || hovered ? 0x60FFFFFF : (entryIndex % 2 == 0 ? 0x30000000 : 0x20000000);
            if (dragging && dragSource == this) {
                bgColor = 0x804444FF;
            }
            graphics.fill(x, y, x + width, y + height, bgColor);

            var font = Minecraft.getInstance().font;

            int leftOffset = 4;
            if (checked && editingPlaylist != null) {
                graphics.text(font, "\u25B2", x + 4, y + 1, 0xFF888888, true);
                graphics.text(font, "\u25BC", x + 4, y + 8, 0xFF888888, true);
                leftOffset = 14;
            }

            int cbSize = 12;
            int cbX = x + width - cbSize - 4;
            int cbY = y + (height - cbSize) / 2;
            graphics.fill(cbX, cbY, cbX + cbSize, cbY + cbSize, 0xFF555555);
            if (checked) {
                graphics.fill(cbX + 2, cbY + 2, cbX + cbSize - 2, cbY + cbSize - 2, 0xFF00AA00);
            }

            int maxTitleW = cbX - leftOffset - 8;
            String title = song.getTitle();
            if (font.width(title) > maxTitleW) {
                while (font.width(title) > maxTitleW - 2 && title.length() > 2) {
                    title = title.substring(0, title.length() - 2) + "..";
                }
            }
            graphics.text(font, title, x + leftOffset, y + 2, 0xFFFFFFFF, true);

            String artist = song.getArtist();
            if (artist != null) {
                int artistRight = cbX - 8;
                int artistW = font.width(artist);
                int titleW = font.width(title);
                if (artistW < artistRight - x - titleW - leftOffset - 8) {
                    graphics.text(font, artist, artistRight - artistW, y + 2, 0xFFAAAAAA, true);
                }
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
            if (event.buttonInfo().button() == 0) {
                double relX = event.x() - this.getX();
                double relY = event.y() - this.getY();
                boolean checked = checkedPaths.contains(song.getPath());
                int myIdx = children().indexOf(this);
                boolean shift = event.hasShiftDown();

                // Arrow reorder area (checked songs only) — no shift behavior
                if (checked && editingPlaylist != null && relX < 14) {
                    if (relY < this.getHeight() / 2) {
                        moveEntryUp(this);
                    } else {
                        moveEntryDown(this);
                    }
                    lastClickedIndex = myIdx;
                    return true;
                }

                // Checkbox area (right side) — toggle with shift support
                if (relX > this.getWidth() - 25) {
                    if (shift && lastClickedIndex >= 0 && myIdx != lastClickedIndex) {
                        applyShiftRange(myIdx, !checked);
                    } else {
                        String path = song.getPath();
                        if (checked) {
                            checkedPaths.remove(path);
                        } else {
                            checkedPaths.add(path);
                        }
                    }
                    lastClickedIndex = myIdx;
                    return true;
                }

                // Middle area — drag for checked songs, toggle for unchecked
                if (checked && editingPlaylist != null) {
                    if (shift && lastClickedIndex >= 0 && myIdx != lastClickedIndex) {
                        applyShiftRange(myIdx, false);
                        lastClickedIndex = myIdx;
                        return true;
                    }
                    dragging = true;
                    dragSource = this;
                    dragStartY = event.y();
                    dragAccumulatedSwaps = 0;
                    dragIdx = myIdx;
                    return true;
                }

                // Unchecked item in middle area — toggle with shift support
                if (shift && lastClickedIndex >= 0 && myIdx != lastClickedIndex) {
                    applyShiftRange(myIdx, true);
                } else {
                    String path = song.getPath();
                    if (checked) {
                        checkedPaths.remove(path);
                    } else {
                        checkedPaths.add(path);
                    }
                }
                lastClickedIndex = myIdx;
                return true;
            }
            return false;
        }

        private void applyShiftRange(int targetIdx, boolean newState) {
            int start = Math.min(lastClickedIndex, targetIdx);
            int end = Math.max(lastClickedIndex, targetIdx);
            for (int i = start; i <= end; i++) {
                SongCheckEntry entry = children().get(i);
                String p = entry.song.getPath();
                if (newState) {
                    checkedPaths.add(p);
                } else {
                    checkedPaths.remove(p);
                }
            }
        }
    }
}
