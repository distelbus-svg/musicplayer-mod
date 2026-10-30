package com.musicplayer;

import com.musicplayer.Playlist;
import com.musicplayer.config.MusicPlayerConfig;
import com.musicplayer.ListeningStatsScreen;
import com.musicplayer.config.MusicPlayerConfig.PlaybackMode;
import com.musicplayer.hud.MiniPlayerHud;
import com.musicplayer.player.SoundtrackRegistry;
import com.musicplayer.theme.ColorTheme;
import com.musicplayer.widget.CheckboxSongListWidget;
import com.musicplayer.widget.FlatButton;
import com.musicplayer.widget.PlaylistCoverWidget;
import com.musicplayer.widget.PlaylistListWidget;
import com.musicplayer.widget.ProgressBarWidget;
import com.musicplayer.widget.SidebarWidget;
import com.musicplayer.widget.SongListWidget;
import com.musicplayer.widget.VolumeSliderWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import java.awt.Desktop;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class MusicPlayerScreen extends Screen {

    public enum PanelMode { NONE, SETTINGS, PLAYLISTS, UP_NEXT, PLAYLIST_EDIT }

    private final MusicPlayerManager manager;
    private SongListWidget songList;
    private SidebarWidget sidebar;
    private ProgressBarWidget progressBar;
    private VolumeSliderWidget volumeSlider;
    private EditBox searchBox;

    private FlatButton prevButton;
    private FlatButton playPauseButton;
    private FlatButton nextButton;
    private FlatButton shuffleButton;
    private FlatButton repeatButton;
    private FlatButton speedButton;
    private boolean speedDropdownOpen = false;
    private static final float[] SPEED_PRESETS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    private FlatButton assignArtButton;

    private FlatButton settingsBtn;
    private FlatButton upNextBtn;
    private FlatButton playlistsBtn;
    private FlatButton closeBtn;

    private EditBox folderSetupBox;
    private FlatButton folderBrowseBtn;

    private PanelMode panelMode = PanelMode.NONE;
    private Playlist editingPlaylist = null;
    private List<SongInfo> editingAllSongs = List.of();
    private EditBox renameBox;
    private CheckboxSongListWidget checkboxList;
    private String editSongSource = "All Songs";
    private final List<String> editFolderPaths = new ArrayList<>();
    private final List<FlatButton> editSourceButtons = new ArrayList<>();
    private final List<FlatButton> editRemoveButtons = new ArrayList<>();
    private PlaylistListWidget playlistList;
    private int settingsScrollY = 0;
    private String settingsFilter = "";
    private EditBox settingsSearchBox;
    private final List<AbstractWidget> panelWidgets = new ArrayList<>();
    private int topBarH;
    private int bottomBarH;
    private int sidebarW;
    private int heroW;
    private int contentTop;
    private int contentBottom;
    private int middleLeft;
    private int middleWidth;
    private int heroLeft;

    private String hoveredSettingId = null;
    private long hoverStartTime = 0;

    private String draggingSliderId = null;
    private boolean isDraggingSidebar = false;
    private boolean isDraggingHero = false;
    private int dragStartX = 0;
    private long resizeCursor = 0;
    private boolean cursorIsResize = false;
    private int dragStartSidebarW = 0;
    private int dragStartHeroLeft = 0;
    private static final int DRAG_BORDER = 4;
    private static final int MIN_SIDEBAR = 80;
    private static final int MIN_HERO = 160;

    // Context menu state
    private boolean ctxMenuActive;
    private SongInfo ctxSong;
    private int ctxX, ctxY;
    private boolean ctxShowPlaylistSub;
    private int ctxHoveredIdx = -1;
    private int ctxHoveredPlaylist = -1;

    private ColorTheme theme() {
        return ColorTheme.get(manager.getConfig().getThemeId());
    }

    private boolean shadow() {
        return theme().textHasShadow();
    }

    private void updateLayout() {
        middleLeft = sidebarW + 1;
        middleWidth = width - sidebarW - heroW - 2;
        heroLeft = width - heroW;
        if (sidebar != null) {
            sidebar.setWidth(sidebarW - 1);
        }
        if (songList != null) {
            songList.setX(middleLeft);
            songList.setWidth(middleWidth);
            boolean mcSource = manager.getCurrentSource() == MusicPlayerManager.SongSource.MINECRAFT;
            boolean showCover = !mcSource && manager.isUsingCustomPlaylist()
                    && manager.getCurrentCustomPlaylist() != null
                    && !"Liked Songs".equals(manager.getCurrentCustomPlaylist().getName());
            int coverH = showCover ? 54 : 0;
            songList.setY(contentTop + coverH);
            songList.setHeight(contentBottom - contentTop - coverH);
        }
        if (assignArtButton != null) {
            assignArtButton.setX(heroLeft + 8);
        }
        int btnSize = 20;
        int speedBtnWidth = 34;
        int clusterWidth = btnSize * 5 + 4 * 4 + speedBtnWidth;
        int newClusterLeft = Math.max(middleLeft + 6, middleLeft + (heroLeft - middleLeft - clusterWidth) / 2);
        repositionTransportButtons(newClusterLeft, btnSize);
    }

    private void repositionTransportButtons(int clusterLeft, int btnSize) {
        int by = (height - bottomBarH) + bottomBarH / 2 - btnSize / 2 + 3;
        if (prevButton != null)     prevButton.setX(clusterLeft);
        if (prevButton != null)     prevButton.setY(by);
        if (playPauseButton != null) { playPauseButton.setX(clusterLeft + (btnSize + 4) * 1); playPauseButton.setY(by); }
        if (nextButton != null)     { nextButton.setX(clusterLeft + (btnSize + 4) * 2); nextButton.setY(by); }
        if (shuffleButton != null)  { shuffleButton.setX(clusterLeft + (btnSize + 4) * 3); shuffleButton.setY(by); }
        if (repeatButton != null)   { repeatButton.setX(clusterLeft + (btnSize + 4) * 4); repeatButton.setY(by); }
        if (speedButton != null)    { speedButton.setX(clusterLeft + (btnSize + 4) * 5); speedButton.setY(by); }
    }

    public MusicPlayerScreen() {
        super(Component.literal("\u266B Music Player"));
        this.manager = MusicPlayerManager.getInstance();
    }

    private void updateResizeCursor(boolean show) {
        if (show == cursorIsResize) return;
        cursorIsResize = show;
        try {
            long window = minecraft.getWindow().handle();
            if (show) {
                if (resizeCursor == 0) resizeCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HRESIZE_CURSOR);
                GLFW.glfwSetCursor(window, resizeCursor);
            } else {
                GLFW.glfwSetCursor(window, 0);
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void init() {
        if (!manager.isLoaded()) {
            manager.reloadLibraryFromDisk();
        }

        float guiScale = Math.min(1f, this.width / 960f);
        topBarH = Math.max(16, (int)(26 * guiScale));
        bottomBarH = Math.max(34, (int)(54 * guiScale));
        int tbSize = Math.max(12, (int)(18 * guiScale));
        int searchH = Math.max(12, (int)(16 * guiScale));
        MusicPlayerConfig cfg = manager.getConfig();
        if (cfg.isRememberResizing() && cfg.getSidebarWidth() > 0) {
            sidebarW = Math.min(cfg.getSidebarWidth(), (int)(190 * guiScale));
        } else {
            sidebarW = Math.max((int)(140 * guiScale), Math.min((int)(190 * guiScale), this.width * 14 / 100));
        }
        if (cfg.isRememberResizing() && cfg.getHeroWidth() > 0) {
            heroW = Math.min(cfg.getHeroWidth(), (int)(280 * guiScale));
        } else {
            heroW = Math.max((int)(200 * guiScale), Math.min((int)(280 * guiScale), this.width * 26 / 100));
        }
        contentTop = topBarH + 2;
        contentBottom = this.height - bottomBarH;
        middleLeft = sidebarW + 1;
        middleWidth = this.width - sidebarW - heroW - 2;
        heroLeft = this.width - heroW;

        editFolderPaths.clear();
        editFolderPaths.addAll(cfg.getFolderPaths());

        int searchRightLimit = this.width - (tbSize * 4 + 4 * 3) - 8;
        int searchX = 8;
        int searchW = Math.max(80, searchRightLimit - searchX);
        int searchY = Math.max(3, (int)(5 * guiScale));
        searchBox = new EditBox(
                this.font, searchX, searchY, searchW, searchH,
                Component.literal("Search")
        );
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.literal("Search..."));
        searchBox.setResponder(query -> {
            if (panelMode == PanelMode.NONE) {
                songList.setFilter(query);
            }
            MusicPlayerScreen.this.setFocused(searchBox);
        });
        addRenderableWidget(searchBox);

        int tbY = Math.max(2, (int)(4 * guiScale));
        int tbX = this.width - tbSize - 6;
        closeBtn = new FlatButton(tbX, tbY, tbSize, tbSize, Component.literal("X"), this::onClose);
        addRenderableWidget(closeBtn);
        tbX -= tbSize + 3;
        settingsBtn = new FlatButton(tbX, tbY, tbSize, tbSize, Component.literal("\u2699"), () -> {
            if (panelMode == PanelMode.SETTINGS) {
                setPanelMode(PanelMode.NONE);
            } else {
                setPanelMode(PanelMode.SETTINGS);
            }
        });
        addRenderableWidget(settingsBtn);
        tbX -= tbSize + 3;
        upNextBtn = new FlatButton(tbX, tbY, tbSize, tbSize, Component.literal("\u22EE"), () -> {
            if (panelMode == PanelMode.UP_NEXT) {
                setPanelMode(PanelMode.NONE);
            } else {
                setPanelMode(PanelMode.UP_NEXT);
            }
        });
        addRenderableWidget(upNextBtn);
        tbX -= tbSize + 3;
        playlistsBtn = new FlatButton(tbX, tbY, tbSize, tbSize, Component.literal("\u2630"), () -> {
            if (panelMode == PanelMode.PLAYLISTS) {
                setPanelMode(PanelMode.NONE);
            } else {
                setPanelMode(PanelMode.PLAYLISTS);
            }
        });
        addRenderableWidget(playlistsBtn);

        int sidebarH = contentBottom - contentTop;
        int sidebarItemH = Math.max(14, (int)(18 * guiScale));
        sidebar = new SidebarWidget(this.minecraft, sidebarW - 1, sidebarH, contentTop, sidebarItemH);
        sidebar.setX(0);
        sidebar.setY(contentTop);
        sidebar.setOnSelect((kind, playlist) -> {
            if (panelMode != PanelMode.NONE) {
                setPanelMode(PanelMode.NONE);
            }
            onSidebarSelect(kind, playlist);
        });
        addRenderableWidget(sidebar);

        int listTop = contentTop;
        int listHeight = contentBottom - listTop;
        int cfgEntryH = cfg.getSongEntryHeight();
        int songItemH = cfgEntryH > 0 ? cfgEntryH : Math.max(12, (int)(16 * guiScale));
        songList = new SongListWidget(this.minecraft, middleWidth, listHeight, listTop, songItemH);
        songList.setX(middleLeft);
        songList.setY(listTop);
        songList.setOnRightClick((song, mx, my) -> {
            ctxSong = song;
            ctxX = mx;
            ctxY = my;
            ctxShowPlaylistSub = false;
            ctxHoveredIdx = -1;
            ctxHoveredPlaylist = -1;
            ctxMenuActive = true;
        });
        populateSongList();
        restoreSongListScroll();
        addRenderableWidget(songList);

        int barTop = this.height - bottomBarH;
        int barCenterY = barTop + bottomBarH / 2;

        int btnSize = Math.max(14, (int)(20 * guiScale));
        int speedBtnWidth = Math.max(24, (int)(34 * guiScale));
        int volWidth = Math.max(50, (int)(80 * guiScale));
        int clusterWidth = btnSize * 5 + 4 * 4 + speedBtnWidth;
        int volX = this.width - volWidth - 10;
        int clusterLeft = Math.max(middleLeft + 6,
                middleLeft + (heroLeft - middleLeft - clusterWidth) / 2);
        int btnY = barCenterY - btnSize / 2 + 3;

        prevButton = new FlatButton(clusterLeft, btnY, btnSize, btnSize,
                Component.literal("\u23EE"), () -> manager.previousTrack());
        addRenderableWidget(prevButton);
        playPauseButton = new FlatButton(clusterLeft + (btnSize + 4), btnY, btnSize, btnSize,
                Component.literal("\u25B6"), () -> manager.togglePlayPause());
        addRenderableWidget(playPauseButton);
        nextButton = new FlatButton(clusterLeft + (btnSize + 4) * 2, btnY, btnSize, btnSize,
                Component.literal("\u23ED"), () -> manager.nextTrack());
        addRenderableWidget(nextButton);
        shuffleButton = new FlatButton(clusterLeft + (btnSize + 4) * 3, btnY, btnSize, btnSize,
                Component.literal("\u21C6"), () -> {
            PlaybackMode mode = manager.getConfig().getPlaybackMode();
            manager.setPlaybackMode(mode == PlaybackMode.SHUFFLE ? PlaybackMode.SEQUENTIAL : PlaybackMode.SHUFFLE);
        });
        addRenderableWidget(shuffleButton);
        repeatButton = new FlatButton(clusterLeft + (btnSize + 4) * 4, btnY, btnSize, btnSize,
                Component.literal("\u21BB"), () -> {
            PlaybackMode mode = manager.getConfig().getPlaybackMode();
            if (mode == PlaybackMode.REPEAT_ALL) {
                manager.setPlaybackMode(PlaybackMode.REPEAT_ONE);
            } else if (mode == PlaybackMode.REPEAT_ONE) {
                manager.setPlaybackMode(PlaybackMode.SEQUENTIAL);
            } else {
                manager.setPlaybackMode(PlaybackMode.REPEAT_ALL);
            }
        });
        addRenderableWidget(repeatButton);
        int speedBtnX = clusterLeft + (btnSize + 4) * 5;
        speedButton = new FlatButton(speedBtnX, btnY, speedBtnWidth, btnSize,
                Component.literal(formatSpeed(manager.getConfig().getPlaybackSpeed())),
                () -> speedDropdownOpen = !speedDropdownOpen);
        addRenderableWidget(speedButton);

        int progressX = middleLeft + 8;
        int progressW = volX - progressX - 8;
        progressBar = new ProgressBarWidget(progressX, barTop + 4, progressW, 6);
        addRenderableWidget(progressBar);

        volumeSlider = new VolumeSliderWidget(volX, btnY + 4, volWidth, 12);
        addRenderableWidget(volumeSlider);

        int artBtnSize = Math.max(32, (int)(48 * guiScale));
        assignArtButton = new FlatButton(heroLeft + 8, contentTop + 8, artBtnSize, artBtnSize,
                Component.literal("+"), this::assignAlbumArt);
        assignArtButton.setTooltip(Tooltip.create(Component.literal("Assign album art (PNG)")));
        addRenderableWidget(assignArtButton);

        int setupW = Math.min(200, middleWidth - 60);
        int btnW = 56;
        int gap = 4;
        int groupW = setupW + gap + btnW;
        int groupX = middleLeft + (middleWidth - groupW) / 2;
        int setupY = contentTop + 60;
        folderSetupBox = new EditBox(this.font, groupX, setupY, setupW, 16,
                Component.literal("Music Folder"));
        folderSetupBox.setValue(cfg.getMusicFolderPath());
        folderSetupBox.setMaxLength(512);
        folderSetupBox.setHint(Component.literal("Path to your music folder..."));
        folderSetupBox.visible = false;
        addRenderableWidget(folderSetupBox);

        folderBrowseBtn = new FlatButton(groupX + setupW + gap, setupY, btnW, 16,
                Component.literal("Browse"), () -> {
            if (this.minecraft.getWindow().isFullscreen()) return;
            String initial = manager.getConfig().getMusicFolderPath();
            if (initial.isEmpty()) initial = System.getProperty("user.home");
            String result = TinyFileDialogs.tinyfd_selectFolderDialog("Select music folder", initial);
            if (result != null && !result.isEmpty()) {
                manager.getConfig().setMusicFolderPath(result);
                folderSetupBox.setValue(result);
                manager.reloadLibraryFromDisk();
                refreshSongList();
            }
        });
        folderBrowseBtn.visible = false;
        addRenderableWidget(folderBrowseBtn);

    }

    private void setPanelMode(PanelMode mode) {
        this.panelMode = mode;
        refreshPanel();
    }

    private void refreshPanel() {
        for (AbstractWidget w : panelWidgets) {
            removeWidget(w);
        }
        panelWidgets.clear();

        renameBox = null;
        checkboxList = null;
        playlistList = null;
        editSourceButtons.clear();
        editRemoveButtons.clear();

        if (settingsSearchBox != null) {
            settingsSearchBox = null;
        }

        settingsScrollY = 0;

        boolean panelActive = panelMode != PanelMode.NONE;
        songList.visible = !panelActive;
        searchBox.visible = !panelActive;

        if (!panelActive) {
            editingPlaylist = null;
            return;
        }

        if (panelMode == PanelMode.SETTINGS) {
            editingPlaylist = null;
            int sbW = Math.min(140, middleWidth - 40);
            int sbX = middleLeft + middleWidth - sbW - 10;
            settingsSearchBox = new EditBox(this.font, sbX, contentTop + 21, sbW, 14, Component.literal("Filter"));
            settingsSearchBox.setMaxLength(32);
            settingsSearchBox.setHint(Component.literal("Filter..."));
            settingsSearchBox.setResponder(q -> { settingsFilter = q != null ? q : ""; });
            addRenderableWidget(settingsSearchBox);
            panelWidgets.add(settingsSearchBox);
        }

        switch (panelMode) {
            case PLAYLISTS -> {
                editingPlaylist = null;
                setupPlaylistsPanel();
            }
            case PLAYLIST_EDIT -> setupPlaylistEditPanel();
            default -> editingPlaylist = null;
        }
    }

    private void setupPlaylistsPanel() {
        int listY = contentTop + 28;
        int listH = contentBottom - listY - 48;

        playlistList = new PlaylistListWidget(this.minecraft, middleWidth - 20, listH, listY, 24);
        playlistList.setX(middleLeft + 10);
        playlistList.setY(listY);

        var deleteAction = new PlaylistListWidget.PlaylistAction[1];
        PlaylistListWidget.PlaylistAction playAction = p -> {
            manager.loadCustomPlaylist(p);
            setPanelMode(PanelMode.NONE);
        };
        PlaylistListWidget.PlaylistAction editAction = p -> {
            editingPlaylist = p;
            setPanelMode(PanelMode.PLAYLIST_EDIT);
        };
        PlaylistListWidget.PlaylistAction exportAction = p -> {
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
        };

        deleteAction[0] = p -> {
            PlaylistManager.getInstance().deletePlaylist(p);
            playlistList.refreshEntries(playAction, editAction, deleteAction[0], exportAction);
            sidebar.refresh();
        };
        playlistList.refreshEntries(playAction, editAction, deleteAction[0], exportAction);
        addRenderableWidget(playlistList);
        panelWidgets.add(playlistList);

        EditBox newNameBox = new EditBox(this.font, middleLeft + 10, contentBottom - 22, 120, 16, Component.literal("New name"));
        newNameBox.setMaxLength(32);
        newNameBox.setHint(Component.literal("Playlist name..."));
        addRenderableWidget(newNameBox);
        panelWidgets.add(newNameBox);

        FlatButton createBtn = new FlatButton(middleLeft + 136, contentBottom - 22, 60, 16, Component.literal("New +"), () -> {
            String name = newNameBox.getValue().trim();
            if (!name.isEmpty()) {
                Playlist p = new Playlist(name);
                PlaylistManager.getInstance().savePlaylist(p);
                newNameBox.setValue("");
                playlistList.refreshEntries(playAction, editAction, deleteAction[0], exportAction);
                sidebar.refresh();
            }
        });
        addRenderableWidget(createBtn);
        panelWidgets.add(createBtn);

        FlatButton importBtn = new FlatButton(middleLeft + 200, contentBottom - 22, 50, 16, Component.literal("Import"), () -> {
            if (this.minecraft.getWindow().isFullscreen()) return;
            String initialPath = manager.getConfig().getMusicFolderPath();
            if (initialPath.isEmpty()) initialPath = System.getProperty("user.home");
            String result = TinyFileDialogs.tinyfd_openFileDialog("Import Playlist", initialPath, (org.lwjgl.PointerBuffer) null, (CharSequence) null, false);
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
                    playlistList.refreshEntries(playAction, editAction, deleteAction[0], exportAction);
                    sidebar.refresh();
                    com.musicplayer.hud.NotificationManager.showSystem("Imported: " + imported.getName() + " (" + imported.getSongs().size() + " songs)");
                }
            }
        });
        addRenderableWidget(importBtn);
        panelWidgets.add(importBtn);
    }

    private void setupPlaylistEditPanel() {
        if (editingPlaylist == null) return;

        int editY = contentTop + 24;
        int editBoxW = middleWidth - 20;

        renameBox = new EditBox(this.font, middleLeft + 10, editY, editBoxW, 16, Component.literal("Playlist Name"));
        renameBox.setValue(editingPlaylist.getName());
        renameBox.setMaxLength(64);
        addRenderableWidget(renameBox);
        panelWidgets.add(renameBox);

        editingAllSongs = manager.getCachedEditAllSongs();
        editSongSource = "All Songs";

        int srcBottom = buildEditSourceRow();
        int listY = srcBottom;
        int listH = contentBottom - listY - 32;
        checkboxList = new CheckboxSongListWidget(this.minecraft, middleWidth - 20, listH, listY, 16, new HashSet<>());
        checkboxList.setX(middleLeft + 10);
        checkboxList.setY(listY);
        checkboxList.setOnRightClick((song, mx, my) -> {
            ctxSong = song;
            ctxX = mx;
            ctxY = my;
            ctxShowPlaylistSub = false;
            ctxHoveredIdx = -1;
            ctxHoveredPlaylist = -1;
            ctxMenuActive = true;
        });
        rebuildEditSongList();
        addRenderableWidget(checkboxList);
        panelWidgets.add(checkboxList);

        FlatButton setCoverBtn = new FlatButton(middleLeft + 10, contentBottom - 22, 70, 18,
                Component.literal("Set Cover"), () -> {
            if (this.minecraft.getWindow().isFullscreen()) return;
            String initial = editingPlaylist.getCustomCoverPath();
            if (initial == null || initial.isEmpty()) {
                initial = manager.getConfig().getMusicFolderPath();
                if (initial.isEmpty()) initial = System.getProperty("user.home");
            }
            String result = TinyFileDialogs.tinyfd_openFileDialog(
                "Select playlist cover PNG",
                initial,
                (org.lwjgl.PointerBuffer) null,
                (CharSequence) null,
                false
            );
            if (result != null && !result.isEmpty()) {
                File f = new File(result);
                if (f.exists() && com.musicplayer.AlbumArtManager.getInstance() != null) {
                    editingPlaylist.setCustomCoverPath(result);
                    PlaylistManager.getInstance().savePlaylist(editingPlaylist);
                }
            }
        });
        addRenderableWidget(setCoverBtn);
        panelWidgets.add(setCoverBtn);

        FlatButton resetCoverBtn = new FlatButton(middleLeft + 85, contentBottom - 22, 72, 18,
                Component.literal("Reset Cover"), () -> {
            editingPlaylist.setCustomCoverPath(null);
            editingPlaylist.setCachedCoverId(null);
            PlaylistManager.getInstance().savePlaylist(editingPlaylist);
        });
        addRenderableWidget(resetCoverBtn);
        panelWidgets.add(resetCoverBtn);

        FlatButton saveBtn = new FlatButton(middleLeft + middleWidth - 140, contentBottom - 22, 60, 18,
                Component.literal("Save"), () -> {
            try {
                String newName = renameBox.getValue().trim();
                if (!newName.isEmpty()) {
                    PlaylistManager.getInstance().renamePlaylist(editingPlaylist, newName);
                }
                Set<String> checkedPaths = checkboxList.getCheckedPaths();
                editingPlaylist.getSongs().removeIf(s -> !checkedPaths.contains(s.getPath()));
                for (SongInfo song : getFilteredEditSongs()) {
                    if (checkedPaths.contains(song.getPath()) &&
                        editingPlaylist.getSongs().stream().noneMatch(s -> s.getPath().equals(song.getPath()))) {
                        editingPlaylist.addSong(song);
                    }
                }
                List<String> ordered = checkboxList.getOrderedPaths();
                if (!ordered.isEmpty()) {
                    java.util.List<SongInfo> reordered = new java.util.ArrayList<>();
                    for (String path : ordered) {
                        for (SongInfo s : editingPlaylist.getSongs()) {
                            if (s.getPath().equals(path)) {
                                reordered.add(s);
                                break;
                            }
                        }
                    }
                    editingPlaylist.getSongs().clear();
                    editingPlaylist.getSongs().addAll(reordered);
                }
                PlaylistManager.getInstance().savePlaylist(editingPlaylist);
                com.musicplayer.widget.PlaylistCoverWidget.invalidateCover(editingPlaylist);
                manager.loadCustomPlaylist(editingPlaylist);
                sidebar.refresh();
                setPanelMode(PanelMode.NONE);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        addRenderableWidget(saveBtn);
        panelWidgets.add(saveBtn);
    }

    private List<SongInfo> getFilteredEditSongs() {
        if ("All Songs".equals(editSongSource)) return editingAllSongs;
        if (editSongSource != null && editSongSource.startsWith("folder:")) {
            String folderPath = editSongSource.substring("folder:".length());
            File folder = new File(folderPath);
            if (folder.isDirectory()) {
                return scanFolderForSongs(folder);
            }
            return List.of();
        }
        for (Playlist pl : PlaylistManager.getInstance().getPlaylists()) {
            if (pl.getName().equals(editSongSource)) return pl.getSongs();
        }
        return editingAllSongs;
    }

    private List<SongInfo> scanFolderForSongs(File folder) {
        List<SongInfo> result = new ArrayList<>();
        scanFolderRecursive(folder, result);
        result.sort(java.util.Comparator.comparing(s -> s.getTitle().toLowerCase()));
        return result;
    }

    private void scanFolderRecursive(File dir, List<SongInfo> out) {
        File[] entries = dir.listFiles();
        if (entries == null) return;
        for (File f : entries) {
            if (f.isDirectory()) {
                scanFolderRecursive(f, out);
            } else {
                String lower = f.getName().toLowerCase();
                if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".flac") || lower.endsWith(".m4a") || lower.endsWith(".aac")) {
                    out.add(SongInfo.fromFile(f));
                }
            }
        }
    }

    private void rebuildEditSongList() {
        if (checkboxList == null || editingPlaylist == null) return;
        List<SongInfo> filtered = getFilteredEditSongs();
        Set<String> checked = new HashSet<>();
        for (SongInfo s : editingPlaylist.getSongs()) checked.add(s.getPath());
        checkboxList.setPlaylistSongs(editingPlaylist, filtered);
    }

    private void updateEditSourceButtons() {
        for (FlatButton btn : editSourceButtons) {
            btn.setActive(btn.getMessage().getString().equals(editSongSource));
        }
    }

    private int buildEditSourceRow() {
        int srcY = (renameBox != null ? renameBox.getY() + renameBox.getHeight() : contentTop + 24) + 2;

        for (FlatButton btn : editSourceButtons) {
            removeWidget(btn);
            panelWidgets.remove(btn);
        }
        editSourceButtons.clear();
        for (FlatButton btn : editRemoveButtons) {
            removeWidget(btn);
            panelWidgets.remove(btn);
        }
        editRemoveButtons.clear();

        int srcX = middleLeft + 10;
        var ft = this.font;

        FlatButton allBtn = new FlatButton(srcX, srcY, ft.width("All Songs") + 10, 14,
                Component.literal("All Songs"), () -> {
            editSongSource = "All Songs";
            updateEditSourceButtons();
            rebuildEditSongList();
        });
        allBtn.setActive("All Songs".equals(editSongSource));
        addRenderableWidget(allBtn);
        panelWidgets.add(allBtn);
        editSourceButtons.add(allBtn);
        srcX += allBtn.getWidth() + 4;

        for (Playlist pl : PlaylistManager.getInstance().getPlaylists()) {
            if (pl == editingPlaylist) continue;
            String pn = pl.getName();
            int bw = ft.width(pn) + 10;
            if (srcX + bw + 24 > heroLeft - 10) {
                srcX = middleLeft + 10;
                srcY += 16;
            }
            FlatButton btn = new FlatButton(srcX, srcY, bw, 14,
                    Component.literal(pn), () -> {
                editSongSource = pn;
                updateEditSourceButtons();
                rebuildEditSongList();
            });
            btn.setActive(pn.equals(editSongSource));
            addRenderableWidget(btn);
            panelWidgets.add(btn);
            editSourceButtons.add(btn);
            srcX += bw + 4;
        }

        for (int fi = 0; fi < editFolderPaths.size(); fi++) {
            String fp = editFolderPaths.get(fi);
            String fname = new File(fp).getName();
            int bw = Math.min(ft.width(fname) + 10, 120);
            int xBtnW = ft.width("\u00D7") + 6;
            if (srcX + bw + xBtnW + 8 > heroLeft - 10) {
                srcX = middleLeft + 10;
                srcY += 16;
            }

            int fIdx = fi;
            String label = ft.width(fname) > bw - 10 ? ft.plainSubstrByWidth(fname, bw - 10) + "\u2026" : fname;
            FlatButton fBtn = new FlatButton(srcX, srcY, bw, 14,
                    Component.literal(label), () -> {
                editSongSource = "folder:" + editFolderPaths.get(fIdx);
                updateEditSourceButtons();
                rebuildEditSongList();
            });
            fBtn.setActive(editSongSource != null && editSongSource.equals("folder:" + fp));
            addRenderableWidget(fBtn);
            panelWidgets.add(fBtn);
            editSourceButtons.add(fBtn);
            srcX += bw + 2;

            if (srcX + xBtnW + 4 > heroLeft - 10) {
                srcX = middleLeft + 10;
                srcY += 16;
            }

            FlatButton rmBtn = new FlatButton(srcX, srcY, xBtnW, 14,
                    Component.literal("\u00D7"), () -> {
                editFolderPaths.remove(fIdx);
                manager.getConfig().setFolderPaths(editFolderPaths);
                editSongSource = "All Songs";
                int sourceBottom = buildEditSourceRow();
                if (checkboxList != null) {
                    checkboxList.setY(sourceBottom);
                    checkboxList.setHeight(contentBottom - sourceBottom - 32);
                }
                rebuildEditSongList();
            });
            addRenderableWidget(rmBtn);
            panelWidgets.add(rmBtn);
            editRemoveButtons.add(rmBtn);
            srcX += xBtnW + 4;
        }

        int plusW = 20;
        if (srcX + plusW + 4 > heroLeft - 10) {
            srcX = middleLeft + 10;
            srcY += 16;
        }
        FlatButton plusBtn = new FlatButton(srcX, srcY, plusW, 14,
                Component.literal("+"), () -> {
            try {
                if (this.minecraft.getWindow().isFullscreen()) return;
                String initial = manager.getConfig().getMusicFolderPath();
                if (initial.isEmpty()) initial = System.getProperty("user.home");
                String result = TinyFileDialogs.tinyfd_selectFolderDialog(
                    "Select a music folder", initial
                );
                System.out.println("[Melodix] Folder dialog returned: " + result);
            if (result != null && !result.isEmpty() && new File(result).isDirectory()) {
                String canon = result.replaceAll("/+$", "");
                if (!editFolderPaths.contains(canon)) {
                    editFolderPaths.add(canon);
                    manager.getConfig().setFolderPaths(editFolderPaths);
                    editSongSource = "folder:" + canon;
                    int sourceBottom = buildEditSourceRow();
                    if (checkboxList != null) {
                        checkboxList.setY(sourceBottom);
                        checkboxList.setHeight(contentBottom - sourceBottom - 32);
                    }
                    rebuildEditSongList();
                    System.out.println("[Melodix] Folder tab built, songs=" + (checkboxList != null ? checkboxList.children().size() : -1));
                }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        addRenderableWidget(plusBtn);
        panelWidgets.add(plusBtn);
        editSourceButtons.add(plusBtn);

        return srcY + 16;
    }

    private void onSidebarSelect(SidebarWidget.Kind kind, Playlist playlist) {
        switch (kind) {
            case LIBRARY -> {
                if (manager.getCurrentSource() != MusicPlayerManager.SongSource.USER) {
                    manager.setCurrentSource(MusicPlayerManager.SongSource.USER);
                }
                if (manager.isUsingCustomPlaylist()) {
                    manager.switchToMainLibrary();
                }
            }
            case MINECRAFT -> {
                if (manager.getCurrentSource() != MusicPlayerManager.SongSource.MINECRAFT) {
                    manager.setCurrentSource(MusicPlayerManager.SongSource.MINECRAFT);
                }
            }
            case LIKED -> {
                if (manager.getCurrentSource() != MusicPlayerManager.SongSource.USER) {
                    manager.setCurrentSource(MusicPlayerManager.SongSource.USER);
                }
                Playlist liked = manager.buildLikedSongsPlaylist();
                manager.loadCustomPlaylist(liked);
            }
            case PLAYLIST -> {
                if (manager.getCurrentSource() != MusicPlayerManager.SongSource.USER) {
                    manager.setCurrentSource(MusicPlayerManager.SongSource.USER);
                }
                if (playlist != null) {
                    manager.loadCustomPlaylist(playlist);
                }
            }
            case HEADER -> { }
        }
        if (searchBox != null) {
            searchBox.setValue("");
        }
        populateSongList();
        restoreSongListScroll();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ColorTheme th = theme();
        boolean isMc = manager.getCurrentSource() == MusicPlayerManager.SongSource.MINECRAFT;

        graphics.fill(0, 0, this.width, this.height, th.panelBg());

        graphics.fill(0, contentTop, sidebarW, contentBottom, th.titleBarBg());

        boolean nearSidebar = mouseX >= sidebarW && mouseX <= sidebarW + DRAG_BORDER && mouseY >= contentTop && mouseY <= contentBottom;
        boolean nearHero = mouseX >= heroLeft && mouseX <= heroLeft + DRAG_BORDER && mouseY >= contentTop && mouseY <= contentBottom;
        updateResizeCursor(nearSidebar || nearHero);
        int handleColor = 0x60FFFFFF;
        if (nearSidebar) graphics.fill(sidebarW - 1, contentTop, sidebarW + 1, contentBottom, handleColor);
        if (nearHero) graphics.fill(heroLeft - 1, contentTop, heroLeft + 1, contentBottom, handleColor);
        graphics.fill(sidebarW, contentTop, sidebarW + DRAG_BORDER, contentBottom, 0x10FFFFFF);
        graphics.fill(heroLeft, contentTop, heroLeft + DRAG_BORDER, contentBottom, 0x10FFFFFF);

        graphics.fill(0, 0, this.width, topBarH, th.titleBarBg());
        graphics.fill(0, topBarH, this.width, topBarH + 1, th.titleBarBorder());

        int barTop = this.height - bottomBarH;
        graphics.fill(0, barTop, this.width, this.height, th.titleBarBg());
        graphics.fill(0, barTop, this.width, barTop + 1, th.titleBarBorder());

        graphics.fill(sidebarW, contentTop, sidebarW + 1, contentBottom, th.titleBarBorder());
        graphics.fill(heroLeft - 1, contentTop, heroLeft, contentBottom, th.titleBarBorder());

        renderHero(graphics, th, isMc);

        sidebar.syncSelection();

        boolean playing = isMc
                ? VanillaMusicPlayer.getInstance().isPlaying()
                : manager.getEngine().isActive() && !manager.getEngine().isPaused();
        playPauseButton.setMessage(Component.literal(playing ? "\u23F8" : "\u25B6"));

        repeatButton.setMessage(Component.literal(
                manager.getConfig().getPlaybackMode() == PlaybackMode.REPEAT_ONE ? "\u21BB1" : "\u21BB"));

        shuffleButton.setActive(manager.getConfig().getPlaybackMode() == PlaybackMode.SHUFFLE);

        speedButton.visible = !isMc;
        if (!isMc) {
            speedButton.setMessage(Component.literal(formatSpeed(manager.getConfig().getPlaybackSpeed())));
        } else {
            speedDropdownOpen = false;
        }

        if (panelMode != PanelMode.NONE) {
            if (folderSetupBox != null) folderSetupBox.visible = false;
            if (folderBrowseBtn != null) folderBrowseBtn.visible = false;
            int panelBg = 0xDD000000;
            graphics.fill(middleLeft, contentTop, heroLeft, contentBottom, panelBg);

            String panelTitle = switch (panelMode) {
                case SETTINGS -> "\u2699 Settings";
                case PLAYLISTS -> "\u2630 Playlists";
                case UP_NEXT -> "\u22EE Up Next";
                case PLAYLIST_EDIT -> "\u270E Edit Playlist";
                default -> "";
            };
            graphics.text(this.font, panelTitle, middleLeft + 10, contentTop + 4, th.accent(), shadow());

            String backText = "\u2190 Back";
            int backX = middleLeft + middleWidth - this.font.width(backText) - 10;
            int backColor = (mouseX >= backX && mouseX <= backX + this.font.width(backText) && mouseY >= contentTop + 2 && mouseY <= contentTop + 16) ? th.accent() : th.dimText();
            graphics.text(this.font, backText, backX, contentTop + 4, backColor, shadow());
            graphics.fill(middleLeft, contentTop + 18, heroLeft, contentTop + 19, th.titleBarBorder());

            switch (panelMode) {
                case SETTINGS -> renderSettingsPanel(graphics, mouseX, mouseY, th);
                case UP_NEXT -> renderUpNextPanel(graphics, mouseX, mouseY, th);
                default -> {}
            }
        } else {
            boolean showCover = !isMc && manager.isUsingCustomPlaylist()
                    && manager.getCurrentCustomPlaylist() != null
                    && !"Liked Songs".equals(manager.getCurrentCustomPlaylist().getName());
            int coverHeaderH = 0;
            if (showCover) {
                Playlist pl = manager.getCurrentCustomPlaylist();
                coverHeaderH = 54;
                int coverSize = 44;
                int coverX = middleLeft + 8;
                int coverY = contentTop + 4;
                PlaylistCoverWidget.renderCover(graphics, pl, coverX, coverY, coverSize);
                String pn = pl.getName();
                if (this.font.width(pn) > middleWidth - coverSize - 24) {
                    pn = this.font.plainSubstrByWidth(pn, middleWidth - coverSize - 28) + "...";
                }
                graphics.text(this.font, pn, coverX + coverSize + 8, coverY + (coverSize - 8) / 2, th.accent(), shadow());
                graphics.fill(middleLeft, contentTop + coverHeaderH, heroLeft, contentTop + coverHeaderH + 1, th.titleBarBorder());
            }

            songList.setY(contentTop + coverHeaderH);
            songList.setHeight(contentBottom - contentTop - coverHeaderH);

            boolean noFolder = manager.getConfig().getMusicFolderPath().isEmpty();
            boolean emptyList = isMc ? manager.getVanillaTracks().isEmpty() : manager.getPlaylist().isEmpty();
            if (emptyList && searchBox.getValue().isEmpty()) {
                if (isMc) {
                    graphics.centeredText(this.font, "No Minecraft music tracks found",
                            middleLeft + middleWidth / 2, contentTop + 40, th.dimText());
                } else if (noFolder) {
                    graphics.centeredText(this.font, "Welcome!",
                            middleLeft + middleWidth / 2, contentTop + 26, th.text());
                    graphics.centeredText(this.font, "Set your music folder to get started:",
                            middleLeft + middleWidth / 2, contentTop + 38, th.text());
                    if (folderSetupBox != null) {
                        folderSetupBox.visible = true;
                        folderSetupBox.setY(contentTop + 50);
                    }
                    if (folderBrowseBtn != null) {
                        folderBrowseBtn.visible = true;
                        folderBrowseBtn.setY(contentTop + 50);
                    }
                } else {
                    graphics.centeredText(this.font, "No MP3/WAV files found in folder",
                            middleLeft + middleWidth / 2, contentTop + 40, th.dimText());
                }
            } else {
                if (folderSetupBox != null) folderSetupBox.visible = false;
                if (folderBrowseBtn != null) folderBrowseBtn.visible = false;
            }
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        if (ctxMenuActive) {
            renderContextMenu(graphics, mouseX, mouseY);
        }

        if (speedDropdownOpen) {
            renderSpeedDropdown(graphics);
        }
    }

    private void renderSettingsPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, ColorTheme th) {
        int x = middleLeft + 10;
        int y = contentTop + 26;
        int w = middleWidth - 20;
        int itemH = 22;
        int catH = 18;
        int gap = 1;

        int clipTop = y;
        int clipBottom = contentBottom - 10;
        int visH = clipBottom - clipTop;

        List<Object[]> settings = buildSettingsList();
        if (settings.isEmpty()) {
            graphics.centeredText(this.font, "No settings match", x + w / 2, y + 20, th.dimText());
            return;
        }

        int totalH = 0;
        for (Object[] s : settings) {
            totalH += (s.length == 1 ? catH : itemH) + gap;
        }

        int maxScroll = Math.max(0, totalH - visH);
        settingsScrollY = Math.max(-maxScroll, Math.min(0, settingsScrollY));

        if (totalH > visH) {
            float thumbRatio = (float) visH / totalH;
            int thumbH = Math.max(8, (int) (visH * thumbRatio));
            float scrollFrac = (float) -settingsScrollY / (totalH - visH);
            int sbX = heroLeft - 8;
            int thumbY = clipTop + (int) (scrollFrac * (visH - thumbH));
            graphics.fill(sbX, clipTop, sbX + 4, clipBottom, 0x40000000);
            graphics.fill(sbX, thumbY, sbX + 4, thumbY + thumbH, 0x80808080);
        }

        int renderY = y + settingsScrollY;
        for (Object[] s : settings) {
            if (s.length == 1) {
                int sy = renderY;
                renderY += catH + gap;
                if (sy + catH < clipTop || sy > clipBottom) continue;
                graphics.text(this.font, (String) s[0], x + 4, sy + 3, th.accent(), shadow());
                graphics.fill(x, sy + catH, x + w, sy + catH + 1, th.titleBarBorder());
            } else {
                String id = (String) s[0];
                String label = (String) s[1];
                String type = (String) s[2];
                String value = (String) s[3];
                int sy = renderY;
                renderY += itemH + gap;
                if (sy + itemH < clipTop || sy > clipBottom) continue;

                boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= sy && mouseY <= sy + itemH;
                graphics.fill(x, sy, x + w, sy + itemH, hovered ? th.listHover() : th.listBg());
                graphics.text(this.font, label, x + 4, sy + 6, th.text(), shadow());

                if (type.equals("slider")) {
                    int barX = x + w - 70;
                    int barW = 64;
                    int barY = sy + 8;
                    int barH = 6;
                    String displayVal = value;
                    String fillStr = value;
                    if (value.contains("|")) {
                        String[] parts = value.split("\\|", 2);
                        fillStr = parts[0];
                        displayVal = parts[1];
                    }
                    int fillW = 0;
                    try {
                        fillW = Math.round(Integer.parseInt(fillStr.replace("%", "")) * barW / 100f);
                    } catch (Exception ignored) {}
                    graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF444444);
                    if (fillW > 0) {
                        graphics.fill(barX, barY, barX + fillW, barY + barH, th.accent());
                    }
                    int pctX = barX - this.font.width(displayVal) - 6;
                    graphics.text(this.font, displayVal, pctX, sy + 6, th.text(), shadow());
                    int knobX = barX + fillW;
                    int knobW = 8;
                    graphics.fill(knobX - knobW / 2, barY - 2, knobX + knobW / 2 + 1, barY + barH + 2, th.accent());
                    graphics.fill(knobX - knobW / 2 + 1, barY - 1, knobX + knobW / 2, barY + barH + 1, th.panelBg());
                } else if (type.equals("folder")) {
                    String display = value;
                    int maxValW = Math.min(w - this.font.width(label) - 20, 100);
                    if (this.font.width(display) > maxValW) {
                        display = this.font.plainSubstrByWidth(display, maxValW - 4) + "...";
                    }
                    graphics.text(this.font, display, x + w - this.font.width(display) - 6, sy + 6, th.dimText(), shadow());
                } else {
                    int valColor = type.equals("toggle") ? (value.equals("ON") ? th.accent() : th.dimText()) : th.text();
                    String display = value;
                    int maxValW = Math.min(w - this.font.width(label) - 20, 120);
                    if (this.font.width(display) > maxValW) {
                        display = this.font.plainSubstrByWidth(display, maxValW - 4) + "...";
                    }
                    graphics.text(this.font, display, x + w - this.font.width(display) - 6, sy + 6, valColor, shadow());
                }
            }
        }

        Map<String, String> desc = settingDescriptions();
        String currentHover = null;
        int cursorRy = y + settingsScrollY;
        for (Object[] s : settings) {
            if (s.length == 1) { cursorRy += catH + gap; continue; }
            String id = (String) s[0];
            int sy = cursorRy;
            cursorRy += itemH + gap;
            if (sy + itemH < clipTop || sy > clipBottom) continue;
            if (mouseX >= x && mouseX <= x + w && mouseY >= sy && mouseY <= sy + itemH) {
                currentHover = id;
                break;
            }
        }
        if (currentHover == null) {
            hoveredSettingId = null;
            hoverStartTime = 0;
        } else if (!currentHover.equals(hoveredSettingId)) {
            hoveredSettingId = currentHover;
            hoverStartTime = System.currentTimeMillis();
        }
        if (hoveredSettingId != null && System.currentTimeMillis() - hoverStartTime > 1000) {
            String tip = desc.get(hoveredSettingId);
            if (tip != null) {
                int tipW = this.font.width(tip);
                int tipH = 10;
                int tipX = Math.min(mouseX + 12, x + w - tipW - 6);
                int tipY = mouseY + 12;
                if (tipX < x + 4) tipX = x + 4;
                if (tipY + tipH + 4 > clipBottom) tipY = mouseY - tipH - 8;
                graphics.fill(tipX - 2, tipY - 2, tipX + tipW + 4, tipY + tipH + 4, 0xCC000000);
                graphics.text(this.font, tip, tipX + 1, tipY + 1, 0xFFFFFFFF, true);
            }
        }
    }

    private static Map<String, String> settingDescriptions() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("volume", "Overall playback volume level");
        m.put("masterVolume", "Master volume (affects all sounds including Minecraft)");
        m.put("playbackMode", "Playback order: sequential, shuffle, repeat all, repeat one");
        m.put("playbackSpeed", "Playback speed multiplier (0.5\u00D7 to 2\u00D7)");
        m.put("stopWhenUnfocused", "Pause playback when Minecraft isn't the active window");
        m.put("pauseOnOpen", "Pause the game in single-player when the music player is open");
        m.put("pauseOnPauseScreen", "Pause music when the game pause screen is open");
        m.put("newUiEnabled", "Use the redesigned UI layout");
        m.put("nowPlayingStyle", "How the now-playing info is displayed");
        m.put("showNowPlayingToast", "Show a toast notification when a new song starts");
        m.put("miniPlayerEnabled", "Show the mini player overlay in-game");
        m.put("songEntryHeight", "Height of each song row in the list (0 = auto)");
        m.put("themeSelector", "Change the color theme");
        m.put("fadeOnPause", "Gradually fade out when pausing");
        m.put("eqEnabled", "Enable 5-band equalizer with preamp");
        m.put("preamp", "Pre-amplification gain for the equalizer");
        m.put("eqBand0", "60 Hz bass shelf equalizer band");
        m.put("eqBand1", "230 Hz low-mid equalizer band");
        m.put("eqBand2", "910 Hz mid equalizer band");
        m.put("eqBand3", "3.6 kHz high-mid equalizer band");
        m.put("eqBand4", "14 kHz treble shelf equalizer band");
        m.put("reverbEnabled", "Add reverb effect to audio");
        m.put("visualizerEnabled", "Show audio visualizer bars");
        m.put("miniVisualizerEnabled", "Show small EQ bars on the mini player");
        m.put("miniPlayerStyle", "Mini player size: Big (album art + controls) or Compact (controls only)");
        m.put("duckOnDamage", "Temporarily lower volume when taking damage");
        m.put("duckVolume", "Volume level during damage ducking");
        m.put("duckDuration", "How long the volume stays lowered after damage");
        m.put("customVolumeLevel", "Volume level set by the custom volume keybind");
        m.put("fadeDuration", "Duration of the fade-on-pause effect");
        m.put("reverbMix", "Wet/dry mix for the reverb effect");
        m.put("musicFolder", "Set the folder containing your MP3/WAV files");
        m.put("queuePersistence", "Save the current queue (song, position) across game restarts");
        m.put("debugLogs", "Enable verbose debug logging");
        m.put("listeningTime", "View your listening statistics");
        m.put("rememberResizing", "Save panel layout between sessions");
        m.put("resetLayout", "Reset panel widths to defaults");
        return m;
    }

    private List<Object[]> buildSettingsList() {
        List<Object[]> list = new ArrayList<>();
        MusicPlayerConfig c = manager.getConfig();
        boolean isMc = manager.getCurrentSource() == MusicPlayerManager.SongSource.MINECRAFT;
        String filter = settingsFilter.toLowerCase().trim();
        boolean hasFilter = !filter.isEmpty();

        java.util.function.Predicate<Object[]> match = item -> !hasFilter
                || ((String) item[1]).toLowerCase().contains(filter)
                || ((String) item[3]).toLowerCase().contains(filter);

        List<Object[]> pb = new ArrayList<>();
        pb.add(new Object[]{"volume", "Volume", "slider", Math.round(c.getVolume() * 100) + "%"});
        pb.add(new Object[]{"masterVolume", "Master Vol.", "slider", Math.round(c.getMasterVolume() * 100) + "%"});
        pb.add(new Object[]{"playbackMode", "Mode", "cycle", c.getPlaybackMode().name()});
        if (!isMc) pb.add(new Object[]{"playbackSpeed", "Speed", "cycle", formatSpeed(c.getPlaybackSpeed())});
        pb.add(new Object[]{"stopWhenUnfocused", "Unfocused", "toggle", c.isStopWhenUnfocused() ? "ON" : "OFF"});
        pb.add(new Object[]{"pauseOnOpen", "Pause SP", "toggle", c.isPauseOnOpen() ? "ON" : "OFF"});
        pb.add(new Object[]{"pauseOnPauseScreen", "Pause Screen", "toggle", c.isPauseOnPauseScreen() ? "ON" : "OFF"});
        pb.add(new Object[]{"newUiEnabled", "New UI", "toggle", c.isNewUiEnabled() ? "ON" : "OFF"});
        if (!hasFilter || pb.stream().anyMatch(match)) {
            list.add(new Object[]{"Playback"});
            for (Object[] item : pb) { if (!hasFilter || match.test(item)) list.add(item); }
        }

        List<Object[]> vc = new ArrayList<>();
        vc.add(new Object[]{"duckOnDamage", "Duck on Hit", "toggle", c.isDuckOnDamage() ? "ON" : "OFF"});
        vc.add(new Object[]{"duckVolume", "Duck Level", "slider", Math.round(c.getDuckVolume() * 100) + "%"});
        {
            int dn = Math.round((c.getDuckDurationMs() - 500f) / 4500f * 100);
            vc.add(new Object[]{"duckDuration", "Duck Time", "slider", dn + "|" + c.getDuckDurationMs() + "ms"});
        }
        vc.add(new Object[]{"customVolumeLevel", "Keybind Vol.", "slider", Math.round(c.getCustomVolumeKeybindLevel() * 100) + "%"});
        if (!hasFilter || vc.stream().anyMatch(match)) {
            list.add(new Object[]{"Volume Control"});
            for (Object[] item : vc) { if (!hasFilter || match.test(item)) list.add(item); }
        }

        List<Object[]> dp = new ArrayList<>();
        dp.add(new Object[]{"nowPlayingStyle", "Now Playing", "cycle", c.getNowPlayingStyle().name()});
        dp.add(new Object[]{"showNowPlayingToast", "Toast", "toggle", c.isShowNowPlayingToast() ? "ON" : "OFF"});
        int eh = c.getSongEntryHeight();
        if (eh > 0) {
            dp.add(new Object[]{"songEntryHeight", "Row Height", "slider", Math.round(eh / 36f * 100f) + "|" + eh + "px"});
        } else {
            dp.add(new Object[]{"songEntryHeight", "Row Height", "slider", "0|Auto"});
        }
        dp.add(new Object[]{"miniPlayerEnabled", "Mini Player", "toggle", c.isMiniPlayerEnabled() ? "ON" : "OFF"});
        dp.add(new Object[]{"miniPlayerStyle", "MP Style", "cycle", c.getMiniPlayerStyle() == MusicPlayerConfig.MiniPlayerStyle.BIG ? "Big" : "Compact"});
        dp.add(new Object[]{"themeSelector", "Theme", "cycle", ColorTheme.get(c.getThemeId()).name()});
        if (!hasFilter || dp.stream().anyMatch(match)) {
            list.add(new Object[]{"Display"});
            for (Object[] item : dp) { if (!hasFilter || match.test(item)) list.add(item); }
        }

        List<Object[]> ad = new ArrayList<>();
        ad.add(new Object[]{"fadeOnPause", "Fade on Pause", "toggle", c.isFadeOnPause() ? "ON" : "OFF"});
        {
            int fn = Math.round((c.getFadeDurationMs() - 200f) / 1800f * 100);
            ad.add(new Object[]{"fadeDuration", "Fade Time", "slider", fn + "|" + c.getFadeDurationMs() + "ms"});
        }
        ad.add(new Object[]{"eqEnabled", "Equalizer", "toggle", c.isEqEnabled() ? "ON" : "OFF"});
        if (c.isEqEnabled()) {
            float[] gains = c.getEqGains();
            ad.add(new Object[]{"preamp", "Preamp", "slider", Math.round(c.getPreamp() * 50) + "%"});
            ad.add(new Object[]{"eqBand0", "Bass (60Hz)", "slider", Math.round((gains[0] + 12f) / 24f * 100) + "%"});
            ad.add(new Object[]{"eqBand1", "Low Mid (230Hz)", "slider", Math.round((gains[1] + 12f) / 24f * 100) + "%"});
            ad.add(new Object[]{"eqBand2", "Mid (910Hz)", "slider", Math.round((gains[2] + 12f) / 24f * 100) + "%"});
            ad.add(new Object[]{"eqBand3", "High Mid (3.6kHz)", "slider", Math.round((gains[3] + 12f) / 24f * 100) + "%"});
            ad.add(new Object[]{"eqBand4", "Treble (14kHz)", "slider", Math.round((gains[4] + 12f) / 24f * 100) + "%"});
        }
        ad.add(new Object[]{"reverbEnabled", "Reverb", "toggle", c.isReverbEnabled() ? "ON" : "OFF"});
        ad.add(new Object[]{"reverbMix", "Reverb Mix", "slider", Math.round(c.getReverbWet() * 100) + "%"});
        if (!hasFilter || ad.stream().anyMatch(match)) {
            list.add(new Object[]{"Audio"});
            for (Object[] item : ad) { if (!hasFilter || match.test(item)) list.add(item); }
        }

        List<Object[]> vs = new ArrayList<>();
        vs.add(new Object[]{"visualizerEnabled", "Visualizer", "toggle", c.isVisualizerEnabled() ? "ON" : "OFF"});
        vs.add(new Object[]{"miniVisualizerEnabled", "Track EQ", "toggle", c.isMiniVisualizerEnabled() ? "ON" : "OFF"});
        if (!hasFilter || vs.stream().anyMatch(match)) {
            list.add(new Object[]{"Visualizer"});
            for (Object[] item : vs) { if (!hasFilter || match.test(item)) list.add(item); }
        }

        if (c.isNewUiEnabled()) {
            List<Object[]> ly = new ArrayList<>();
            ly.add(new Object[]{"rememberResizing", "Remember Resizing", "toggle", c.isRememberResizing() ? "ON" : "OFF"});
            ly.add(new Object[]{"resetLayout", "Reset Layout", "action", "Reset \u2192"});
            if (!hasFilter || ly.stream().anyMatch(match)) {
                list.add(new Object[]{"Layout"});
                for (Object[] item : ly) { if (!hasFilter || match.test(item)) list.add(item); }
            }
        }

        List<Object[]> ot = new ArrayList<>();
        ot.add(new Object[]{"musicFolder", "Music Folder", "folder", c.getMusicFolderPath().isEmpty() ? "(not set)" : c.getMusicFolderPath()});
        ot.add(new Object[]{"queuePersistence", "Save Queue", "toggle", c.isQueuePersistence() ? "ON" : "OFF"});
        ot.add(new Object[]{"debugLogs", "Debug Logs", "toggle", c.isDebugLogsEnabled() ? "ON" : "OFF"});
        ot.add(new Object[]{"listeningTime", "Listening Stats", "action", "Open \u2192"});
        if (!hasFilter || ot.stream().anyMatch(match)) {
            list.add(new Object[]{"Other"});
            for (Object[] item : ot) { if (!hasFilter || match.test(item)) list.add(item); }
        }

        return list;
    }

    private void handleSettingClick(String id) {
        MusicPlayerConfig c = manager.getConfig();
        switch (id) {
            case "volume" -> c.setVolume(Math.min(1f, c.getVolume() + 0.1f));
            case "masterVolume" -> c.setMasterVolume(Math.min(1f, c.getMasterVolume() + 0.1f));
            case "playbackMode" -> {
                var vals = MusicPlayerConfig.PlaybackMode.values();
                c.setPlaybackMode(vals[(c.getPlaybackMode().ordinal() + 1) % vals.length]);
            }
            case "playbackSpeed" -> {
                float cur = c.getPlaybackSpeed();
                int idx = 2;
                for (int pi = 0; pi < SPEED_PRESETS.length; pi++) {
                    if (Math.abs(cur - SPEED_PRESETS[pi]) < 0.01f) { idx = pi; break; }
                }
                c.setPlaybackSpeed(SPEED_PRESETS[(idx + 1) % SPEED_PRESETS.length]);
                manager.getEngine().setPlaybackSpeed(c.getPlaybackSpeed());
            }
            case "stopWhenUnfocused" -> c.setStopWhenUnfocused(!c.isStopWhenUnfocused());
            case "pauseOnOpen" -> c.setPauseOnOpen(!c.isPauseOnOpen());
            case "pauseOnPauseScreen" -> c.setPauseOnPauseScreen(!c.isPauseOnPauseScreen());
            case "newUiEnabled" -> c.setNewUiEnabled(!c.isNewUiEnabled());
            case "showNowPlayingToast" -> c.setShowNowPlayingToast(!c.isShowNowPlayingToast());
            case "fadeOnPause" -> c.setFadeOnPause(!c.isFadeOnPause());
            case "eqEnabled" -> c.setEqEnabled(!c.isEqEnabled());
            case "reverbEnabled" -> c.setReverbEnabled(!c.isReverbEnabled());
            case "visualizerEnabled" -> c.setVisualizerEnabled(!c.isVisualizerEnabled());
            case "miniVisualizerEnabled" -> c.setMiniVisualizerEnabled(!c.isMiniVisualizerEnabled());
            case "miniPlayerEnabled" -> c.setMiniPlayerEnabled(!c.isMiniPlayerEnabled());
            case "duckOnDamage" -> c.setDuckOnDamage(!c.isDuckOnDamage());
            case "debugLogs" -> c.setDebugLogsEnabled(!c.isDebugLogsEnabled());
            case "queuePersistence" -> c.setQueuePersistence(!c.isQueuePersistence());
            case "miniPlayerStyle" -> {
                c.setMiniPlayerStyle(c.getMiniPlayerStyle() == MusicPlayerConfig.MiniPlayerStyle.BIG
                    ? MusicPlayerConfig.MiniPlayerStyle.COMPACT : MusicPlayerConfig.MiniPlayerStyle.BIG);
            }
            case "nowPlayingStyle" -> {
                var vals = MusicPlayerConfig.NowPlayingStyle.values();
                c.setNowPlayingStyle(vals[(c.getNowPlayingStyle().ordinal() + 1) % vals.length]);
            }
            case "themeSelector" -> {
                String[] themes = ColorTheme.all().keySet().toArray(new String[0]);
                String cur = c.getThemeId();
                int idx = 0;
                for (int ti = 0; ti < themes.length; ti++) {
                    if (themes[ti].equals(cur)) { idx = ti; break; }
                }
                c.setThemeId(themes[(idx + 1) % themes.length]);
            }
            case "musicFolder" -> {
                String initial = c.getMusicFolderPath();
                if (initial.isEmpty()) initial = System.getProperty("user.home");
                String result = TinyFileDialogs.tinyfd_selectFolderDialog("Select music folder", initial);
                if (result != null && !result.isEmpty()) {
                    c.setMusicFolderPath(result);
                    manager.reloadLibraryFromDisk();
                    refreshSongList();
                }
            }
            case "listeningTime" -> {
                setPanelMode(PanelMode.NONE);
                minecraft.gui.setScreen(new ListeningStatsScreen(this));
            }
            case "rememberResizing" -> c.setRememberResizing(!c.isRememberResizing());
            case "resetLayout" -> {
                c.resetLayout();
                if (c.isRememberResizing()) {
                    sidebarW = Math.max(140, Math.min(190, width * 14 / 100));
                    heroW = Math.max(200, Math.min(280, width * 26 / 100));
                    updateLayout();
                }
            }
        }
    }

    private void handleSettingSliderClick(String id, double mouseX, int settingX, int settingWidth) {
        MusicPlayerConfig c = manager.getConfig();
        int barX = settingX + settingWidth - 70;
        int barW = 64;
        float pct = (float) (mouseX - barX) / barW;
        pct = Math.max(0f, Math.min(1f, pct));
        switch (id) {
            case "volume" -> {
                c.setVolume(pct);
                manager.getEngine().setVolume(pct);
                MiniPlayerHud.showVolumeIndicator(pct);
            }
            case "masterVolume" -> {
                c.setMasterVolume(pct);
                MiniPlayerHud.showVolumeIndicator(pct);
            }
            case "preamp" -> {
                c.setPreamp(pct * 2f);
                AudioDSP.updateFromConfig();
            }
            case "eqBand0" -> {
                float gain = pct * 24f - 12f;
                c.setEqBand(0, gain);
                AudioDSP.updateFromConfig();
            }
            case "eqBand1" -> {
                float gain = pct * 24f - 12f;
                c.setEqBand(1, gain);
                AudioDSP.updateFromConfig();
            }
            case "eqBand2" -> {
                float gain = pct * 24f - 12f;
                c.setEqBand(2, gain);
                AudioDSP.updateFromConfig();
            }
            case "eqBand3" -> {
                float gain = pct * 24f - 12f;
                c.setEqBand(3, gain);
                AudioDSP.updateFromConfig();
            }
            case "eqBand4" -> {
                float gain = pct * 24f - 12f;
                c.setEqBand(4, gain);
                AudioDSP.updateFromConfig();
            }
            case "duckVolume" -> c.setDuckVolume(pct);
            case "duckDuration" -> c.setDuckDurationMs(500 + Math.round(pct * 4500f));
            case "customVolumeLevel" -> c.setCustomVolumeKeybindLevel(pct);
            case "songEntryHeight" -> c.setSongEntryHeight(Math.round(pct * 36f));
            case "fadeDuration" -> c.setFadeDurationMs(200 + Math.round(pct * 1800f));
            case "reverbMix" -> c.setReverbWet(pct);
        }
    }

    private void renderUpNextPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, ColorTheme th) {
        int x = middleLeft + 10;
        int y = contentTop + 26;
        int w = middleWidth - 20;

        List<SongInfo> queue = manager.getUpNextQueue(20);
        if (queue.isEmpty()) {
            graphics.centeredText(this.font, "End of playlist", x + w / 2, y + 20, th.dimText());
            return;
        }

        int itemH = 28;
        int gap = 2;
        int clipTop = y;
        int clipBottom = contentBottom - 10;

        for (int i = 0; i < queue.size(); i++) {
            int sy = y + i * (itemH + gap);
            if (sy + itemH < clipTop || sy > clipBottom) continue;

            SongInfo song = queue.get(i);
            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= sy && mouseY <= sy + itemH;

            graphics.fill(x, sy, x + w, sy + itemH, hovered ? th.listHover() : th.listBg());

            String posStr = "+" + (i + 1);
            graphics.text(this.font, posStr, x + 4, sy + 6, th.dimText(), shadow());
            int posW = this.font.width(posStr);

            String title = song.getTitle();
            int maxTW = w - posW - 16;
            if (this.font.width(title) > maxTW) {
                title = this.font.plainSubstrByWidth(title, maxTW - 4) + "...";
            }
            graphics.text(this.font, title, x + posW + 12, sy + 3, th.text(), shadow());

            String artist = song.getArtist();
            if (artist != null) {
                graphics.text(this.font, artist, x + posW + 12, sy + 15, th.dimText(), shadow());
            }
        }
    }

    private void renderHero(GuiGraphicsExtractor graphics, ColorTheme th, boolean isMc) {
        int pad = 12;
        int artSize = Math.min(heroW - pad * 2, 160);
        artSize = Math.max(artSize, 64);
        int artX = heroLeft + (heroW - artSize) / 2;
        int artY = contentTop + pad;

        boolean songActive = isMc
                ? VanillaMusicPlayer.getInstance().getCurrentTrack() != null
                    && (VanillaMusicPlayer.getInstance().isPlaying() || VanillaMusicPlayer.getInstance().isPaused())
                : manager.getCurrentSong() != null
                    && (manager.getEngine().isActive() || manager.getEngine().isPaused());

        if (!songActive) {
            assignArtButton.visible = false;
            graphics.centeredText(this.font, "No song playing",
                    heroLeft + heroW / 2, artY + artSize / 2, th.dimText());
            return;
        }

        boolean hasCustomArt = AlbumArtManager.getInstance().hasArt();
        Identifier artId = null;
        if (isMc) {
            VanillaTrack vt = VanillaMusicPlayer.getInstance().getCurrentTrack();
            if (vt != null) {
                artId = AlbumArtManager.getBuiltInArt(vt.coverKey());
            }
        }
        if (artId == null && hasCustomArt) {
            artId = AlbumArtManager.ALBUM_ART_ID;
        }
        if (artId == null) {
            artId = AlbumArtManager.DEFAULT_ALBUM_ART;
        }

        graphics.fill(artX - 1, artY - 1, artX + artSize + 1, artY + artSize + 1, th.panelBorder());
        graphics.blit(artId, artX, artY, artX + artSize, artY + artSize, 0.0f, 1.0f, 0.0f, 1.0f);

        assignArtButton.setX(artX);
        assignArtButton.setY(artY);
        assignArtButton.setWidth(artSize);
        assignArtButton.setHeight(artSize);
        assignArtButton.visible = !hasCustomArt && !isMc;

        int textX = heroLeft + pad;
        int maxTextWidth = heroW - pad * 2;
        int textY = artY + artSize + 8;

        if (isMc) {
            VanillaTrack vt = VanillaMusicPlayer.getInstance().getCurrentTrack();
            if (vt != null) {
                String title = vt.title();
                while (this.font.width(title) > maxTextWidth && title.length() > 3) {
                    title = title.substring(0, title.length() - 4) + "...";
                }
                graphics.text(this.font, title, textX, textY, th.accent(), shadow());
                String info = vt.artist() + " \u2014 " + vt.album();
                while (this.font.width(info) > maxTextWidth && info.length() > 3) {
                    info = info.substring(0, info.length() - 4) + "...";
                }
                graphics.text(this.font, info, textX, textY + 11, th.dimText(), shadow());
            }
        } else {
            SongInfo song = manager.getCurrentSong();
            if (song != null) {
                String title = song.getTitle();
                while (this.font.width(title) > maxTextWidth && title.length() > 3) {
                    title = title.substring(0, title.length() - 4) + "...";
                }
                graphics.text(this.font, title, textX, textY, th.accent(), shadow());
                String info = song.getArtist();
                while (this.font.width(info) > maxTextWidth && info.length() > 3) {
                    info = info.substring(0, info.length() - 4) + "...";
                }
                graphics.text(this.font, info, textX, textY + 11, th.dimText(), shadow());
            }
        }
    }

    public void refreshSongList() {
        if (songList != null) {
            searchBox.setValue("");
            populateSongList();
            songList.setFilter("");
        }
    }

    private void assignAlbumArt() {
        String initialDir = manager.getConfig().getMusicFolderPath();
        if (manager.getCurrentSource() == MusicPlayerManager.SongSource.MINECRAFT) {
            return;
        }
        SongInfo song = manager.getCurrentSong();
        if (song == null) return;
        String result = TinyFileDialogs.tinyfd_openFileDialog(
            "Select album art PNG",
            initialDir,
            (org.lwjgl.PointerBuffer) null,
            (CharSequence) null,
            false
        );
        if (result != null && !result.isEmpty()) {
            AlbumArtManager.getInstance().loadManualArt(new File(result));
        }
    }

    private void restoreSongListScroll() {
        if (manager.getCurrentSource() == MusicPlayerManager.SongSource.MINECRAFT) {
            int vidx = manager.getVanillaIndex();
            if (vidx >= 0) {
                songList.scrollToIndex(vidx);
            }
        } else {
            int idx = manager.getCurrentIndex();
            if (idx >= 0 && searchBox.getValue().isEmpty()) {
                songList.scrollToIndex(idx);
            }
        }
    }

    private void populateSongList() {
        if (manager.getCurrentSource() == MusicPlayerManager.SongSource.MINECRAFT) {
            List<VanillaTrack> tracks = manager.getVanillaTracks();
            Map<String, List<SongInfo>> categories = new LinkedHashMap<>();

            List<SongInfo> discs = new ArrayList<>();
            List<SongInfo> soundtracks = new ArrayList<>();

            for (VanillaTrack vt : tracks) {
                SongInfo si = new SongInfo(vt.title(), vt.artist(), vt.id(), SongInfo.SourceType.LOCAL);
                if (vt.trackSource() == PlayableTrack.TrackSource.VANILLA_DISC) {
                    discs.add(si);
                } else {
                    soundtracks.add(si);
                }
            }

            if (!discs.isEmpty()) {
                categories.put("Music Discs", discs);
            }
            if (!soundtracks.isEmpty()) {
                categories.put("Soundtracks", soundtracks);
            }

            songList.setSongsGrouped(categories);
        } else {
            songList.setSongs(manager.getPlaylist());
        }
    }

    private int speedDropdownTop() {
        int below = speedButton.getY() + speedButton.getHeight() + 2;
        int dropdownH = SPEED_PRESETS.length * 14;
        if (below + dropdownH <= this.height) {
            return below;
        }
        return speedButton.getY() - dropdownH - 2;
    }

    private void renderSpeedDropdown(GuiGraphicsExtractor graphics) {
        int dropdownLeft = speedButton.getX();
        int dropdownTop = speedDropdownTop();
        int dropdownWidth = speedButton.getWidth();
        int optionH = 14;
        int dropdownH = SPEED_PRESETS.length * optionH;

        ColorTheme th = theme();
        graphics.fill(dropdownLeft - 1, dropdownTop - 1, dropdownLeft + dropdownWidth + 1, dropdownTop + dropdownH + 1, th.titleBarBorder());
        graphics.fill(dropdownLeft, dropdownTop, dropdownLeft + dropdownWidth, dropdownTop + dropdownH, th.panelBg());

        for (int i = 0; i < SPEED_PRESETS.length; i++) {
            int optY = dropdownTop + i * optionH;
            int bg = SPEED_PRESETS[i] == manager.getConfig().getPlaybackSpeed() ? 0x6000AA00 : 0x30000000;
            graphics.fill(dropdownLeft, optY, dropdownLeft + dropdownWidth, optY + optionH, bg);
            graphics.text(this.font, formatSpeed(SPEED_PRESETS[i]),
                    dropdownLeft + 2, optY + 2,
                    th.text(), shadow());
        }
    }

    private static String formatSpeed(float speed) {
        if (speed == 0.5f) return "0.5\u00D7";
        if (speed == 0.75f) return "0.75\u00D7";
        if (speed == 1.0f) return "1.0\u00D7";
        if (speed == 1.25f) return "1.25\u00D7";
        if (speed == 1.5f) return "1.5\u00D7";
        if (speed == 2.0f) return "2.0\u00D7";
        return String.format("%.2f\u00D7", speed);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
        if (ctxMenuActive) {
            double cmx = event.x();
            double cmy = event.y();
            if (event.button() == 0 || event.button() == 1) {
                if (!handleContextMenuClick((int)cmx, (int)cmy)) {
                    ctxMenuActive = false;
                    ctxShowPlaylistSub = false;
                }
                return true;
            }
        }

        if (speedDropdownOpen && event.button() == 0) {
            int dropdownLeft = speedButton.getX();
            int dropdownTop = speedDropdownTop();
            int dropdownWidth = speedButton.getWidth();
            int optionH = 14;
            double mouseX = event.x();
            double mouseY = event.y();
            for (int i = 0; i < SPEED_PRESETS.length; i++) {
                int optY = dropdownTop + i * optionH;
                if (mouseX >= dropdownLeft && mouseX <= dropdownLeft + dropdownWidth &&
                    mouseY >= optY && mouseY <= optY + optionH) {
                    float speed = SPEED_PRESETS[i];
                    manager.getConfig().setPlaybackSpeed(speed);
                    manager.getEngine().setPlaybackSpeed(speed);
                    speedDropdownOpen = false;
                    return true;
                }
            }
            speedDropdownOpen = false;
        }

        double mx = event.x();
        double my = event.y();

        if (event.button() == 0 && panelMode == PanelMode.NONE) {
            if (mx >= sidebarW && mx <= sidebarW + DRAG_BORDER && my >= contentTop && my <= contentBottom) {
                isDraggingSidebar = true;
                dragStartX = (int) mx;
                dragStartSidebarW = sidebarW;
                return true;
            }
            if (mx >= heroLeft && mx <= heroLeft + DRAG_BORDER && my >= contentTop && my <= contentBottom) {
                isDraggingHero = true;
                dragStartX = (int) mx;
                dragStartHeroLeft = heroLeft;
                return true;
            }
        }

        if (panelMode != PanelMode.NONE && event.button() == 0) {

            String backText = "\u2190 Back";
            int backX = middleLeft + middleWidth - this.font.width(backText) - 10;
            if (mx >= backX && mx <= backX + this.font.width(backText) && my >= contentTop + 2 && my <= contentTop + 18) {
                setPanelMode(PanelMode.NONE);
                return true;
            }

            if (panelMode == PanelMode.SETTINGS) {
                int x = middleLeft + 10;
                int y = contentTop + 26;
                int w = middleWidth - 20;
                int itemH = 22;
                int catH = 18;
                int gap = 1;
                int clipTop = y;
                int clipBottom = contentBottom - 10;
                int sbX = heroLeft - 8;

                List<Object[]> settings = buildSettingsList();
                int totalH = 0;
                for (Object[] s : settings) {
                    totalH += (s.length == 1 ? catH : itemH) + gap;
                }
                int visH = clipBottom - clipTop;
                if (totalH > visH) {
                    if (mx >= sbX && mx <= sbX + 4 && my >= clipTop && my <= clipBottom) {
                        float ratio = (float) (my - clipTop) / visH;
                        settingsScrollY = -(int) (ratio * (totalH - visH));
                        settingsScrollY = Math.max(-(totalH - visH), Math.min(0, settingsScrollY));
                        return true;
                    }
                }

                int ry = y + settingsScrollY;
                for (Object[] s : settings) {
                    if (s.length == 1) { ry += catH + gap; continue; }
                    String id = (String) s[0];
                    String type = (String) s[2];
                    int sy = ry;
                    ry += itemH + gap;
                    if (sy + itemH < clipTop || sy > clipBottom) continue;
                    if (mx >= x && mx <= x + w && my >= sy && my <= sy + itemH) {
                        if (type.equals("slider")) {
                            handleSettingSliderClick(id, mx, x, w);
                            draggingSliderId = id;
                            return true;
                        }
                        handleSettingClick(id);
                        return true;
                    }
                }
            }

            if (panelMode == PanelMode.UP_NEXT) {
                int x = middleLeft + 10;
                int y = contentTop + 26;
                int w = middleWidth - 20;
                int itemH = 28;
                int gap = 2;

                List<SongInfo> queue = manager.getUpNextQueue(20);
                for (int i = 0; i < queue.size(); i++) {
                    int sy = y + i * (itemH + gap);
                    if (mx >= x && mx <= x + w && my >= sy && my <= sy + itemH) {
                        SongInfo song = queue.get(i);
                        if (song != null) {
                            List<SongInfo> playlist = manager.getPlaylist();
                            for (int j = 0; j < playlist.size(); j++) {
                                if (playlist.get(j).getPath().equals(song.getPath())) {
                                    manager.playSong(j);
                                    setPanelMode(PanelMode.NONE);
                                    return true;
                                }
                            }
                        }
                        return true;
                    }
                }
            }
        }

        var ch = children();
        for (int i = ch.size() - 1; i >= 0; i--) {
            Object child = ch.get(i);
            if (child instanceof AbstractWidget aw) {
                if (panelMode != PanelMode.NONE && (aw == songList || aw == sidebar)) continue;
                if (aw == songList) {
                    boolean mcSource = manager.getCurrentSource() == MusicPlayerManager.SongSource.MINECRAFT;
                    boolean showCover = !mcSource && manager.isUsingCustomPlaylist()
                            && manager.getCurrentCustomPlaylist() != null
                            && !"Liked Songs".equals(manager.getCurrentCustomPlaylist().getName());
                    int listTop = contentTop + (showCover ? 54 : 0);
                    if (event.y() < listTop || event.y() >= contentBottom) continue;
                }
                if (aw.mouseClicked(event, isFocused)) {
                    this.setFocused(aw);
                    return true;
                }
            }
        }

        if (mx >= 0 && mx < this.width && my >= 0 && my < this.height) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        int button = event.button();
        double mx = event.x();
        double my = event.y();
        if (button == 0) {
            if (draggingSliderId != null && panelMode == PanelMode.SETTINGS) {
                int x = middleLeft + 10;
                int w = middleWidth - 20;
                handleSettingSliderClick(draggingSliderId, mx, x, w);
                return true;
            }
            if (isDraggingSidebar) {
                int newW = dragStartSidebarW + (int) (mx - dragStartX);
                sidebarW = Math.max(MIN_SIDEBAR, Math.min(width - heroW - MIN_HERO - 3, newW));
                updateLayout();
                return true;
            }
            if (isDraggingHero) {
                int newHL = dragStartHeroLeft + (int) (mx - dragStartX);
                heroLeft = Math.max(sidebarW + 5, Math.min(width - MIN_HERO, newHL));
                heroW = width - heroLeft;
                updateLayout();
                return true;
            }
        }
        if (getFocused() != null && getFocused() instanceof AbstractWidget aw) {
            if (aw.mouseDragged(event, dragX, dragY)) return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            if (isDraggingSidebar || isDraggingHero) {
                MusicPlayerConfig cfg = manager.getConfig();
                cfg.setSidebarWidth(sidebarW);
                cfg.setHeroWidth(heroW);
            }
            draggingSliderId = null;
            isDraggingSidebar = false;
            isDraggingHero = false;
        }
        if (getFocused() != null && getFocused() instanceof AbstractWidget aw) {
            if (aw.mouseReleased(event)) return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (getFocused() instanceof EditBox) {
            return getFocused().keyPressed(event);
        }
        if (MusicPlayerMod.getOpenGuiKey().matches(event)) {
            this.minecraft.gui.setScreen(null);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollYAmt) {
        if (panelMode == PanelMode.SETTINGS) {
            settingsScrollY += scrollYAmt * 10;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollYAmt);
    }

    @Override
    public boolean isPauseScreen() {
        return manager.getConfig().isPauseOnOpen() && minecraft != null && minecraft.level != null;
    }

    @Override
    public void onClose() {
        updateResizeCursor(false);
        this.minecraft.gui.setScreen(null);
    }

    private void renderContextMenu(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (ctxSong == null) return;
        int menuW = 160;
        int itemH = 18;
        List<String> items = buildCtxItems();
        int subH = ctxShowPlaylistSub ? PlaylistManager.getInstance().getPlaylists().size() * itemH : 0;
        int menuH = items.size() * itemH + 4 + subH;
        int mx = Math.min(ctxX, this.width - menuW - 4);
        int my = Math.min(ctxY, this.height - menuH - 4);
        if (mx < 4) mx = 4;
        if (my < 4) my = 4;

        graphics.fill(mx - 1, my - 1, mx + menuW + 1, my + menuH + 1, 0xFF444444);
        graphics.fill(mx, my, mx + menuW, my + menuH, 0xFF1A1A1A);

        ctxHoveredIdx = -1;
        int iy = my + 2;
        for (int i = 0; i < items.size(); i++) {
            boolean hovered = mouseX >= mx && mouseX <= mx + menuW && mouseY >= iy && mouseY <= iy + itemH;
            if (hovered) ctxHoveredIdx = i;
            if (hovered) graphics.fill(mx, iy, mx + menuW, iy + itemH, 0x60FFFFFF);
            graphics.text(this.font, items.get(i), mx + 6, iy + 4, hovered ? 0xFFFFFFFF : 0xFFCCCCCC, true);
            iy += itemH;
        }

        if (ctxShowPlaylistSub) {
            List<Playlist> playlists = PlaylistManager.getInstance().getPlaylists();
            int subX = mx + menuW;
            int subW = 150;
            int subH2 = playlists.size() * itemH + 4;
            graphics.fill(subX - 1, my - 1, subX + subW + 1, my + subH2 + 1, 0xFF444444);
            graphics.fill(subX, my, subX + subW, my + subH2, 0xFF1A1A1A);
            ctxHoveredPlaylist = -1;
            int siy = my + 2;
            for (int i = 0; i < playlists.size(); i++) {
                boolean hovered = mouseX >= subX && mouseX <= subX + subW && mouseY >= siy && mouseY <= siy + itemH;
                if (hovered) ctxHoveredPlaylist = i;
                if (hovered) graphics.fill(subX, siy, subX + subW, siy + itemH, 0x60FFFFFF);
                graphics.text(this.font, playlists.get(i).getName(), subX + 6, siy + 4, hovered ? 0xFFFFFFFF : 0xFFCCCCCC, true);
                siy += itemH;
            }
        }
    }

    private List<String> buildCtxItems() {
        List<String> items = new ArrayList<>();
        if (panelMode == PanelMode.PLAYLIST_EDIT && editingPlaylist != null) {
            items.add("Remove from Playlist");
            if (ctxSong.getSource() == SongInfo.SourceType.LOCAL) {
                items.add("Reveal in File Manager");
            }
        } else if (manager.isUsingCustomPlaylist() && manager.getCurrentSource() == MusicPlayerManager.SongSource.USER) {
            items.add("Remove from Playlist");
            items.add("Add to Playlist" + (ctxShowPlaylistSub ? " \u25B6" : ""));
            if (ctxSong.getSource() == SongInfo.SourceType.LOCAL) {
                items.add("Reveal in File Manager");
            }
        } else {
            items.add("Add to Playlist" + (ctxShowPlaylistSub ? " \u25B6" : ""));
            if (ctxSong.getSource() == SongInfo.SourceType.LOCAL) {
                items.add("Reveal in File Manager");
            }
        }
        return items;
    }

    private boolean handleContextMenuClick(int mx, int my) {
        if (ctxSong == null) return false;
        int menuW = 160;
        int itemH = 18;
        List<String> items = buildCtxItems();
        int subH = ctxShowPlaylistSub ? PlaylistManager.getInstance().getPlaylists().size() * itemH : 0;
        int menuH = items.size() * itemH + 4 + subH;
        int cmx = Math.min(ctxX, this.width - menuW - 4);
        int cmy = Math.min(ctxY, this.height - menuH - 4);
        if (cmx < 4) cmx = 4;
        if (cmy < 4) cmy = 4;

        int iy = cmy + 2;

        if (ctxShowPlaylistSub) {
            int subX = cmx + menuW;
            int subW = 150;
            if (mx >= cmx && mx <= cmx + menuW && my >= iy && my <= iy + itemH) {
                String label = items.isEmpty() ? "" : items.get(0);
                if (label.startsWith("Add to Playlist")) {
                    ctxShowPlaylistSub = false;
                    return true;
                }
            }
            List<Playlist> playlists = PlaylistManager.getInstance().getPlaylists();
            int siy = cmy + 2;
            for (int i = 0; i < playlists.size(); i++) {
                if (mx >= subX && mx <= subX + subW && my >= siy && my <= siy + itemH) {
                    Playlist pl = playlists.get(i);
                    SongInfo copy = new SongInfo(ctxSong.getTitle(), ctxSong.getArtist(), ctxSong.getPath(), ctxSong.getSource());
                    pl.addSong(copy);
                    PlaylistManager.getInstance().savePlaylist(pl);
                    if (manager.isUsingCustomPlaylist() && manager.getCurrentCustomPlaylist() == pl) {
                        manager.loadCustomPlaylist(pl);
                        populateSongList();
                    }
                    com.musicplayer.hud.NotificationManager.showSystem("Added to " + pl.getName());
                    ctxMenuActive = false;
                    ctxShowPlaylistSub = false;
                    return true;
                }
                siy += itemH;
            }
            return false;
        }

        for (int i = 0; i < items.size(); i++) {
            if (mx >= cmx && mx <= cmx + menuW && my >= iy && my <= iy + itemH) {
                String label = items.get(i);
                if (label.startsWith("Add to Playlist")) {
                    ctxShowPlaylistSub = true;
                    return true;
                } else if (label.equals("Reveal in File Manager")) {
                    String path = ctxSong.getPath();
                    if (path != null) {
                        try {
                            File f = new File(path);
                            if (f.exists()) {
                                String abs = f.getAbsolutePath();
                                String os = System.getProperty("os.name").toLowerCase();
                                if (os.contains("mac")) {
                                    Runtime.getRuntime().exec(new String[]{"open", "-R", abs});
                                } else if (os.contains("win")) {
                                    Runtime.getRuntime().exec(new String[]{"explorer", "/select,", abs});
                                } else {
                                    Desktop.getDesktop().open(f.getParentFile());
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                    ctxMenuActive = false;
                    return true;
                } else if (label.equals("Remove from Playlist")) {
                    Playlist target = (panelMode == PanelMode.PLAYLIST_EDIT && editingPlaylist != null) ? editingPlaylist : manager.getCurrentCustomPlaylist();
                    if (target != null) {
                        String path = ctxSong.getPath();
                        target.getSongs().removeIf(s -> s.getPath().equals(path));
                        PlaylistManager.getInstance().savePlaylist(target);
                        if (panelMode == PanelMode.PLAYLIST_EDIT) {
                            if (!manager.isUsingCustomPlaylist()) manager.loadCustomPlaylist(target);
                            rebuildEditSongList();
                        } else {
                            manager.loadCustomPlaylist(target);
                            populateSongList();
                        }
                        com.musicplayer.hud.NotificationManager.showSystem("Removed from playlist");
                    }
                    ctxMenuActive = false;
                    return true;
                }
                ctxMenuActive = false;
                return true;
            }
            iy += itemH;
        }
        return false;
    }
}
