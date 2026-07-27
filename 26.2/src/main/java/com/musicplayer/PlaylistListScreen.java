package com.musicplayer;

import com.musicplayer.widget.PlaylistListWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public class PlaylistListScreen extends Screen {
    private final Screen parent;
    private PlaylistListWidget list;
    private EditBox nameInput;
    private PlaylistListWidget.SortMode sortMode = PlaylistListWidget.SortMode.NAME;

    public PlaylistListScreen(Screen parent) {
        super(Component.literal("Playlists"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int panelWidth = 320;
        int panelHeight = 260;
        int panelLeft = (this.width - panelWidth) / 2;
        int panelTop = (this.height - panelHeight) / 2;

        list = new PlaylistListWidget(this.minecraft, panelWidth - 20, panelHeight - 120, panelTop + 50, 24);
        list.setX(panelLeft + 10);
        list.setY(panelTop + 50);
        list.setSortMode(sortMode);
        refreshList();

        addRenderableWidget(list);

        // Sort mode buttons
        int sortBtnY = panelTop + 28;
        addRenderableWidget(Button.builder(Component.literal("Name"), b -> {
            sortMode = PlaylistListWidget.SortMode.NAME;
            PlaylistManager.getInstance().sortPlaylistsByName();
            refreshList();
        }).bounds(panelLeft + 10, sortBtnY, 50, 18).build());

        addRenderableWidget(Button.builder(Component.literal("Date"), b -> {
            sortMode = PlaylistListWidget.SortMode.DATE;
            PlaylistManager.getInstance().sortPlaylistsByDate();
            refreshList();
        }).bounds(panelLeft + 65, sortBtnY, 50, 18).build());

        addRenderableWidget(Button.builder(Component.literal("Order"), b -> {
            sortMode = PlaylistListWidget.SortMode.CUSTOM;
            refreshList();
        }).bounds(panelLeft + 120, sortBtnY, 50, 18).build());

        // New playlist input
        nameInput = new EditBox(this.font, panelLeft + 10, panelTop + panelHeight - 45, 180, 20, Component.literal("Name"));
        addRenderableWidget(nameInput);

        addRenderableWidget(Button.builder(Component.literal("Create"), button -> {
            String name = nameInput.getValue().trim();
            if (!name.isEmpty()) {
                Playlist p = new Playlist(name);
                PlaylistManager.getInstance().savePlaylist(p);
                nameInput.setValue("");
                refreshList();
            }
        }).bounds(panelLeft + 195, panelTop + panelHeight - 45, 60, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Main Library"), button -> {
            MusicPlayerManager.getInstance().switchToMainLibrary();
            this.minecraft.gui.setScreen(parent);
        }).bounds(panelLeft + 10, panelTop + panelHeight - 22, 90, 20).build());

        // Import button (.m3u / .json)
        addRenderableWidget(Button.builder(Component.literal("Import"), button -> {
            if (this.minecraft.getWindow().isFullscreen()) return;
            String initial = MusicPlayerManager.getInstance().getConfig().getMusicFolderPath();
            if (initial.isEmpty()) initial = System.getProperty("user.home");
            String result = TinyFileDialogs.tinyfd_openFileDialog("Import Playlist", initial, (org.lwjgl.PointerBuffer) null, (CharSequence) null, false);
            if (result != null) {
                java.io.File file = new java.io.File(result);
                String lower = file.getName().toLowerCase();
                Playlist imported = null;
                if (lower.endsWith(".json")) {
                    try {
                        imported = PlaylistManager.importFromJson(file.toPath());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else if (lower.endsWith(".m3u")) {
                    imported = M3UHelper.importFromM3U(file);
                }
                if (imported != null && !imported.getSongs().isEmpty()) {
                    PlaylistManager.getInstance().savePlaylist(imported);
                    refreshList();
                    com.musicplayer.hud.NotificationManager.showSystem("Imported: " + imported.getName() + " (" + imported.getSongs().size() + " songs)");
                }
            }
        }).bounds(panelLeft + 105, panelTop + panelHeight - 22, 60, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
            this.minecraft.gui.setScreen(parent);
        }).bounds(panelLeft + panelWidth - 50, panelTop + panelHeight - 22, 40, 20).build());
    }

    private void refreshList() {
        list.refreshEntries(this::playPlaylist, this::editPlaylist, this::deletePlaylist, this::exportPlaylist,
            sortMode == PlaylistListWidget.SortMode.CUSTOM ? this::moveUp : null,
            sortMode == PlaylistListWidget.SortMode.CUSTOM ? this::moveDown : null);
        MusicPlayerManager mgr = MusicPlayerManager.getInstance();
        Playlist likedSongs = mgr.buildLikedSongsPlaylist();
        list.addListEntry(list.new PlaylistEntry(likedSongs, p -> {
            mgr.loadCustomPlaylist(likedSongs);
            this.minecraft.gui.setScreen(parent);
        }, p -> {}, p -> {}, null));
        list.setSortMode(sortMode);
    }

    private void playPlaylist(Playlist p) {
        MusicPlayerManager.getInstance().loadCustomPlaylist(p);
        this.minecraft.gui.setScreen(parent);
    }

    private void editPlaylist(Playlist p) {
        this.minecraft.gui.setScreen(new PlaylistEditScreen(this, p));
    }

    private void deletePlaylist(Playlist p) {
        PlaylistManager.getInstance().deletePlaylist(p);
        refreshList();
    }

    private void exportPlaylist(Playlist p) {
        if (this.minecraft.getWindow().isFullscreen()) return;
        String initial = p.getName().replaceAll("[^a-zA-Z0-9.-]", "_") + ".json";
        String result = TinyFileDialogs.tinyfd_saveFileDialog("Export Playlist", initial, (org.lwjgl.PointerBuffer) null, (CharSequence) null);
        if (result != null) {
            try {
                PlaylistManager.exportToJson(p, new java.io.File(result).toPath());
                com.musicplayer.hud.NotificationManager.showSystem("Exported: " + p.getName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void moveUp(Playlist p) {
        PlaylistManager.getInstance().movePlaylistUp(p);
        refreshList();
    }

    private void moveDown(Playlist p) {
        PlaylistManager.getInstance().movePlaylistDown(p);
        refreshList();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractMenuBackground(graphics);
        int panelWidth = 320;
        int panelHeight = 260;
        int panelLeft = (this.width - panelWidth) / 2;
        int panelTop = (this.height - panelHeight) / 2;

        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xDD222222);
        graphics.centeredText(this.font, "My Playlists", panelLeft + panelWidth / 2, panelTop + 10, 0xFFFFFFFF);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return MusicPlayerManager.getInstance().getConfig().isPauseOnOpen() && minecraft != null && minecraft.level != null;
    }
}
