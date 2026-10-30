package com.musicplayer.widget;

import com.musicplayer.Playlist;
import com.musicplayer.PlaylistManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

public class PlaylistListWidget extends ObjectSelectionList<PlaylistListWidget.PlaylistEntry> {

    public enum SortMode { NAME, DATE, CUSTOM }

    private SortMode sortMode = SortMode.CUSTOM;
    private PlaylistAction moveUpAction;
    private PlaylistAction moveDownAction;

    public PlaylistListWidget(Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
    }

    public void setSortMode(SortMode mode) {
        this.sortMode = mode;
    }

    public SortMode getSortMode() {
        return sortMode;
    }

    public void refreshEntries(PlaylistAction playAction, PlaylistAction editAction, PlaylistAction deleteAction,
                                PlaylistAction exportAction, PlaylistAction moveUpAction, PlaylistAction moveDownAction) {
        this.moveUpAction = moveUpAction;
        this.moveDownAction = moveDownAction;
        this.clearEntries();
        List<Playlist> playlists = PlaylistManager.getInstance().getPlaylists();
        for (Playlist playlist : playlists) {
            this.addEntry(new PlaylistEntry(playlist, playAction, editAction, deleteAction, exportAction));
        }
    }

    public void refreshEntries(PlaylistAction playAction, PlaylistAction editAction, PlaylistAction deleteAction) {
        refreshEntries(playAction, editAction, deleteAction, null, null, null);
    }

    public void refreshEntries(PlaylistAction playAction, PlaylistAction editAction, PlaylistAction deleteAction,
                                PlaylistAction exportAction) {
        refreshEntries(playAction, editAction, deleteAction, exportAction, null, null);
    }

    public void addListEntry(PlaylistListWidget.PlaylistEntry entry) {
        this.addEntry(entry);
    }

    public class PlaylistEntry extends ObjectSelectionList.Entry<PlaylistEntry> {
        private final Playlist playlist;
        private final PlaylistAction playAction;
        private final PlaylistAction editAction;
        private final PlaylistAction deleteAction;
        private final PlaylistAction exportAction;

        public PlaylistEntry(Playlist playlist, PlaylistAction playAction, PlaylistAction editAction, PlaylistAction deleteAction, PlaylistAction exportAction) {
            this.playlist = playlist;
            this.playAction = playAction;
            this.editAction = editAction;
            this.deleteAction = deleteAction;
            this.exportAction = exportAction;
        }

        @Override
        public Component getNarration() {
            return Component.literal(playlist.getName());
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int entryIndex, int top, boolean isSelected, float partialTick) {
            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();

            boolean isLikedSongs = "Liked Songs".equals(playlist.getName());
            int bgColor = isLikedSongs ? 0x604444FF : (isSelected ? 0x60FFFFFF : (entryIndex % 2 == 0 ? 0x30000000 : 0x20000000));
            graphics.fill(x, y, x + width, y + height, bgColor);

            int leftOffset = 4;
            if (sortMode == SortMode.CUSTOM && !isLikedSongs) {
                graphics.text(Minecraft.getInstance().font, "\u25B2", x + 4, y + 2, 0xFF888888, true);
                graphics.text(Minecraft.getInstance().font, "\u25BC", x + 4, y + 10, 0xFF888888, true);
                leftOffset = 14;
            }

            String prefix = isLikedSongs ? "\u2665 " : "";
            String name = prefix + playlist.getName();
            int maxWidth = width - 140 - leftOffset;
            if (Minecraft.getInstance().font.width(name) > maxWidth) {
                name = Minecraft.getInstance().font.plainSubstrByWidth(name, maxWidth - 10) + "...";
            }
            int nameColor = isLikedSongs ? 0xFFFF4444 : 0xFFFFFFFF;
            graphics.text(Minecraft.getInstance().font, name, x + leftOffset, y + 2, nameColor, true);

            String info = playlist.getSongs().size() + " songs";
            graphics.text(Minecraft.getInstance().font, info, x + width - 110, y + 2, 0xFFAAAAAA, true);

            if (isLikedSongs) {
                graphics.text(Minecraft.getInstance().font, "Play", x + width - 50, y + 12, 0xFF888888, true);
            } else {
                graphics.text(Minecraft.getInstance().font, "Play | Exp | Edit | Del", x + width - 110, y + 12, 0xFF888888, true);
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
            if (event.buttonInfo().button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
                double relX = event.x() - this.getX();
                double relY = event.y() - this.getY();
                boolean isLiked = "Liked Songs".equals(playlist.getName());

                if (sortMode == SortMode.CUSTOM && !isLiked && relX < 14) {
                    if (relY < this.getHeight() / 2 && moveUpAction != null) {
                        moveUpAction.run(playlist);
                    } else if (moveDownAction != null) {
                        moveDownAction.run(playlist);
                    }
                    return true;
                }

                if (relX > this.getWidth() - 30) {
                    deleteAction.run(playlist);
                } else if (!isLiked && relX > this.getWidth() - 55) {
                    editAction.run(playlist);
                } else if (!isLiked && relX > this.getWidth() - 80) {
                    if (exportAction != null) exportAction.run(playlist);
                } else {
                    playAction.run(playlist);
                }
                return true;
            }
            return false;
        }
    }

    @FunctionalInterface
    public interface PlaylistAction {
        void run(Playlist playlist);
    }
}
