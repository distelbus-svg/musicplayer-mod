package com.musicplayer;

import com.musicplayer.widget.CheckboxSongListWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PlaylistEditScreen extends Screen {
    private final Screen parent;
    private final Playlist playlist;
    private EditBox nameInput;
    private CheckboxSongListWidget songList;

    public PlaylistEditScreen(Screen parent, Playlist playlist) {
        super(Component.literal("Editing Playlist"));
        this.parent = parent;
        this.playlist = playlist;
    }

    @Override
    protected void init() {
        int panelWidth = 360;
        int panelHeight = 260;
        int panelLeft = (this.width - panelWidth) / 2;
        int panelTop = (this.height - panelHeight) / 2;

        // Playlist name input
        nameInput = new EditBox(this.font, panelLeft + 20, panelTop + 32, panelWidth - 40, 16, Component.literal("Playlist Name"));
        nameInput.setValue(playlist.getName());
        nameInput.setMaxLength(64);
        addRenderableWidget(nameInput);

        // Checkbox list showing playlist songs first (with reorder), then remaining library + vanilla songs
        Set<String> checked = new HashSet<>();
        for (SongInfo s : playlist.getSongs()) {
            checked.add(s.getPath());
        }
        List<SongInfo> allSongs = MusicPlayerManager.getInstance().getCachedEditAllSongs();
        int listY = panelTop + 56;
        int listHeight = panelHeight - 100;
        songList = new CheckboxSongListWidget(this.minecraft, panelWidth - 40, listHeight, listY, 16, checked);
        songList.setX(panelLeft + 20);
        songList.setY(listY);
        songList.setPlaylistSongs(playlist, allSongs);
        addRenderableWidget(songList);

        // Done button
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> {
            String newName = nameInput.getValue().trim();
            if (!newName.isEmpty()) {
                PlaylistManager.getInstance().renamePlaylist(playlist, newName);
            }

            Set<String> checkedPaths = songList.getCheckedPaths();

            // Remove songs not in checked set
            playlist.getSongs().removeIf(s -> !checkedPaths.contains(s.getPath()));

            // Add songs that are checked but missing from playlist
            List<SongInfo> fullList = MusicPlayerManager.getInstance().getCachedEditAllSongs();
            for (SongInfo song : fullList) {
                if (checkedPaths.contains(song.getPath()) &&
                    playlist.getSongs().stream().noneMatch(s -> s.getPath().equals(song.getPath()))) {
                    playlist.addSong(song);
                }
            }
            // Reorder to match widget display order
            List<String> ordered = songList.getOrderedPaths();
            if (!ordered.isEmpty()) {
                List<SongInfo> reordered = new ArrayList<>();
                for (String path : ordered) {
                    for (SongInfo s : playlist.getSongs()) {
                        if (s.getPath().equals(path)) {
                            reordered.add(s);
                            break;
                        }
                    }
                }
                playlist.getSongs().clear();
                playlist.getSongs().addAll(reordered);
            }

            PlaylistManager.getInstance().savePlaylist(playlist);
            this.minecraft.setScreen(parent);
        }).bounds(panelLeft + panelWidth - 80, panelTop + panelHeight - 30, 60, 20).build());

        // Export .m3u button
        addRenderableWidget(Button.builder(Component.literal("Export .m3u"), button -> {
            if (this.minecraft.getWindow().isFullscreen()) {
                return;
            }
            String initial = playlist.getName().replaceAll("[^a-zA-Z0-9.-]", "_") + ".m3u";
            String result = TinyFileDialogs.tinyfd_saveFileDialog("Export Playlist", initial, (org.lwjgl.PointerBuffer) null, (CharSequence) null);
            if (result != null) {
                if (M3UHelper.exportToM3U(playlist, new java.io.File(result))) {
                    com.musicplayer.hud.NotificationManager.showSystem("Playlist exported: " + playlist.getName());
                }
            }
        }).bounds(panelLeft + 20, panelTop + panelHeight - 30, 80, 20).build());

        // Cancel button
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> {
            this.minecraft.setScreen(parent);
        }).bounds(panelLeft + 110, panelTop + panelHeight - 30, 60, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractMenuBackground(graphics);
        int panelWidth = 360;
        int panelHeight = 260;
        int panelLeft = (this.width - panelWidth) / 2;
        int panelTop = (this.height - panelHeight) / 2;

        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xDD222222);
        graphics.centeredText(this.font, "Editing Playlist", panelLeft + panelWidth / 2, panelTop + 8, 0xFFFFFFFF);
        graphics.text(this.font, "Name:", panelLeft + 20, panelTop + 22, 0xFFAAAAAA, true);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return MusicPlayerManager.getInstance().getConfig().isPauseOnOpen() && minecraft != null && minecraft.level != null;
    }
}
