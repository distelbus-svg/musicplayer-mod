package com.musicplayer.widget;

import com.musicplayer.Playlist;
import com.musicplayer.SongInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CheckboxSongListWidget extends ObjectSelectionList<CheckboxSongListWidget.SongCheckEntry> {

    private final Set<String> checkedPaths;
    private Playlist editingPlaylist;
    private RightClickCallback onRightClick = null;

    public void setOnRightClick(RightClickCallback cb) {
        this.onRightClick = cb;
    }

    public interface RightClickCallback {
        void run(SongInfo song, int mouseX, int mouseY);
    }

    // Drag state
    SongCheckEntry dragSource;
    boolean dragging;
    double dragStartY;
    int dragAccumulatedSwaps;
    private final int rowHeight;

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

    public List<String> getOrderedPaths() {
        return children().stream()
            .map(e -> e.song.getPath())
            .filter(checkedPaths::contains)
            .collect(Collectors.toList());
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
        if (editingPlaylist != null) editingPlaylist.moveSongUp(entry.song);
        children().set(idx, above);
        children().set(idx - 1, entry);
    }

    void moveEntryDown(SongCheckEntry entry) {
        int idx = children().indexOf(entry);
        if (idx < 0 || idx >= children().size() - 1) return;
        SongCheckEntry below = children().get(idx + 1);
        if (!checkedPaths.contains(below.song.getPath())) return;
        if (editingPlaylist != null) editingPlaylist.moveSongDown(entry.song);
        children().set(idx, below);
        children().set(idx + 1, entry);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging && dragSource != null && event.buttonInfo().button() == 0) {
            int idx = children().indexOf(dragSource);
            if (idx < 0) return true;

            double deltaY = event.y() - dragStartY;
            int desiredSwaps = (int) Math.round(deltaY / rowHeight);
            int swapsToDo = desiredSwaps - dragAccumulatedSwaps;

            if (swapsToDo != 0) {
                boolean goDown = swapsToDo > 0;
                for (int s = 0; s < Math.abs(swapsToDo); s++) {
                    int ci = children().indexOf(dragSource);
                    if (ci < 0) break;
                    int targetIdx = goDown ? ci + 1 : ci - 1;
                    if (targetIdx < 0 || targetIdx >= children().size()) break;
                    SongCheckEntry target = children().get(targetIdx);
                    if (!checkedPaths.contains(target.song.getPath())) break;
                    if (goDown) moveEntryDown(dragSource);
                    else moveEntryUp(dragSource);
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
            if (event.buttonInfo().button() == 1 && onRightClick != null) {
                onRightClick.run(song, (int)event.x(), (int)event.y());
                return true;
            }
            if (event.buttonInfo().button() == 0) {
                double relX = event.x() - this.getX();
                double relY = event.y() - this.getY();
                boolean checked = checkedPaths.contains(song.getPath());

                if (checked && editingPlaylist != null && relX < 14) {
                    if (relY < this.getHeight() / 2) {
                        moveEntryUp(this);
                    } else {
                        moveEntryDown(this);
                    }
                    return true;
                }

                int thisIndex = children().indexOf(this);
                long windowHandle = Minecraft.getInstance().getWindow().handle();
                boolean shiftHeld = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                                 || GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

                if (relX > this.getWidth() - 25 || !(checked && editingPlaylist != null)) {
                    String path = song.getPath();
                    boolean newChecked = !checked;

                    if (shiftHeld && lastClickedIndex >= 0 && thisIndex >= 0) {
                        int start = Math.min(lastClickedIndex, thisIndex);
                        int end = Math.max(lastClickedIndex, thisIndex);
                        for (int i = start; i <= end; i++) {
                            SongCheckEntry entry = children().get(i);
                            if (newChecked) {
                                checkedPaths.add(entry.song.getPath());
                            } else {
                                checkedPaths.remove(entry.song.getPath());
                            }
                        }
                    } else {
                        if (newChecked) {
                            checkedPaths.add(path);
                        } else {
                            checkedPaths.remove(path);
                        }
                    }

                    lastClickedIndex = thisIndex;
                    return true;
                }

                dragging = true;
                dragSource = this;
                dragStartY = event.y();
                dragAccumulatedSwaps = 0;
                return true;
            }
            return false;
        }
    }
}
