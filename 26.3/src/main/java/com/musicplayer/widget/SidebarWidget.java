package com.musicplayer.widget;

import com.musicplayer.MusicPlayerManager;
import com.musicplayer.Playlist;
import com.musicplayer.PlaylistManager;
import com.musicplayer.theme.ColorTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Left-rail navigation list for the full-screen player.
 *
 * Shows fixed sources (Music Library, Minecraft Soundtrack, Liked Songs),
 * a section divider, then the user's saved playlists. Selecting an entry
 * re-points the player's active source/queue via {@link SidebarAction}.
 */
public class SidebarWidget extends ObjectSelectionList<SidebarWidget.SidebarEntry> {

    public enum Kind { LIBRARY, MINECRAFT, LIKED, HEADER, PLAYLIST }

    private final MusicPlayerManager manager;
    private SidebarAction onSelect;
    private String selectedKey;

    public SidebarWidget(Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
        this.manager = MusicPlayerManager.getInstance();
        refresh();
    }

    public void setOnSelect(SidebarAction onSelect) {
        this.onSelect = onSelect;
    }

    /** Rebuilds the entry list from the current sources + saved playlists. */
    public void refresh() {
        this.clearEntries();
        addEntry(new SidebarEntry(Kind.LIBRARY,  "library",  "\u266B Music Library"));
        addEntry(new SidebarEntry(Kind.MINECRAFT, "minecraft", "\u25B7 Minecraft"));
        addEntry(new SidebarEntry(Kind.LIKED,    "liked",    "\u2665 Liked Songs"));
        addEntry(new SidebarEntry(Kind.HEADER,   "header",   "Playlists"));
        for (Playlist pl : PlaylistManager.getInstance().getPlaylists()) {
            addEntry(new SidebarEntry(Kind.PLAYLIST, "pl:" + pl.getName(),
                    "\u2630 " + pl.getName(), pl));
        }
        // Default the selection to whatever is currently active so the highlight matches state.
        if (selectedKey == null) {
            selectedKey = computeActiveKey();
        }
    }

    private String computeActiveKey() {
        if (manager.getCurrentSource() == MusicPlayerManager.SongSource.MINECRAFT) return "minecraft";
        if (manager.isUsingCustomPlaylist()) {
            Playlist cp = manager.getCurrentCustomPlaylist();
            if (cp != null) {
                if ("Liked Songs".equals(cp.getName())) return "liked";
                return "pl:" + cp.getName();
            }
        }
        return "library";
    }

    /** Highlights the active entry (called whenever the source/playlist changes). */
    public void syncSelection() {
        this.selectedKey = computeActiveKey();
    }

    public void setSelectedByKey(String key) {
        this.selectedKey = key;
    }

    @Override
    public void setFocused(boolean focused) {
        // Don't steal focus from EditBox
    }

    @Override
    public int getRowWidth() {
        return this.width - 14;
    }

    @Override
    protected int scrollBarX() {
        return this.getX() + this.width - 6;
    }

    public class SidebarEntry extends ObjectSelectionList.Entry<SidebarEntry> {
        private final Kind kind;
        private final String key;
        private final String label;
        private final Playlist playlist; // null unless Kind.PLAYLIST

        public SidebarEntry(Kind kind, String key, String label) {
            this(kind, key, label, null);
        }

        public SidebarEntry(Kind kind, String key, String label, Playlist playlist) {
            this.kind = kind;
            this.key = key;
            this.label = label;
            this.playlist = playlist;
        }

        @Override
        public Component getNarration() {
            return Component.literal(label);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int entryIndex, int top, boolean isSelected, float partialTick) {
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();
            var font = Minecraft.getInstance().font;
            ColorTheme th = ColorTheme.get(manager.getConfig().getThemeId());

            if (kind == Kind.HEADER) {
                graphics.text(font, label, x + 4, y + 2, th.dimText(), true);
                graphics.fill(x + 4, y + h - 2, x + w - 8, y + h - 1, th.titleBarBorder());
                return;
            }

            boolean active = key.equals(selectedKey);
            boolean hovered = SidebarWidget.this.getHovered() == this;

            int bg;
            if (active) {
                bg = th.listSelected();
            } else if (hovered) {
                bg = th.listHover();
            } else {
                bg = 0x00000000; // transparent; the sidebar panel bg shows through
            }
            if (bg != 0) {
                graphics.fill(x, y, x + w, y + h, bg);
            }

            // Active accent bar on the left edge
            if (active) {
                graphics.fill(x, y, x + 2, y + h, th.accent());
            }

            int textColor = active ? th.accent() : th.text();
            int labelX = x + 6;

            // Small cover next to playlist entries
            if (kind == Kind.PLAYLIST && playlist != null) {
                int coverSize = 14;
                int coverX = x + 3;
                int coverY = y + 2;
                PlaylistCoverWidget.renderCover(graphics, playlist, coverX, coverY, coverSize);
                labelX = x + 22;
            }

            String meta = null;
            if (kind == Kind.PLAYLIST && playlist != null) {
                meta = playlist.getSongs().size() + "";
            } else if (kind == Kind.LIKED) {
                meta = manager.getConfig().getLikedSongPaths().size() + "";
            }

            // Truncate playlist label to avoid overlapping the count
            String displayLabel = label;
            int maxLabelW = w - labelX - 10;
            if (meta != null) maxLabelW -= font.width(meta) + 6;
            if (font.width(displayLabel) > maxLabelW && maxLabelW > 20) {
                displayLabel = font.plainSubstrByWidth(displayLabel, maxLabelW - 4) + "...";
            }
            graphics.text(font, displayLabel, labelX, y + 3, textColor, true);

            if (meta != null) {
                graphics.text(font, meta, x + w - 6 - font.width(meta), y + 3, th.dimText(), true);
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
            if (kind == Kind.HEADER) return false;
            if (event.buttonInfo().button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
                SidebarWidget.this.setSelected(this);
                selectedKey = key;
                if (onSelect != null) {
                    onSelect.run(kind, playlist);
                }
                return true;
            }
            return false;
        }

        public Kind kind() { return kind; }
        public String key() { return key; }
    }

    @FunctionalInterface
    public interface SidebarAction {
        void run(Kind kind, Playlist playlist);
    }
}
