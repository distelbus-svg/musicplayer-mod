package com.musicplayer;

import com.musicplayer.config.MusicPlayerConfig;
import com.musicplayer.config.MusicPlayerConfig.PlaybackMode;
import com.musicplayer.theme.ColorTheme;
import com.musicplayer.widget.ProgressBarWidget;
import com.musicplayer.widget.SongListWidget;
import com.musicplayer.widget.VolumeSliderWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.lwjgl.util.tinyfd.TinyFileDialogs;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class MusicPlayerScreenLegacy extends Screen {

    private final MusicPlayerManager manager;
    private SongListWidget songList;
    private ProgressBarWidget progressBar;
    private VolumeSliderWidget volumeSlider;
    private EditBox searchBox;

    private Button playPauseButton;
    private Button shuffleButton;
    private Button repeatButton;
    private Button speedButton;
    private boolean speedDropdownOpen = false;
    private static final float[] SPEED_PRESETS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    private Button assignArtButton;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;

    private ColorTheme theme() {
        return ColorTheme.get(manager.getConfig().getThemeId());
    }

    private boolean shadow() {
        return theme().textHasShadow();
    }

    public MusicPlayerScreenLegacy() {
        super(Component.literal("\u266B Music Player"));
        this.manager = MusicPlayerManager.getInstance();
    }

    @Override
    protected void init() {
        if (!manager.isLoaded()) {
            manager.reloadLibraryFromDisk();
        }

        panelWidth = Math.min(340, this.width - 40);
        panelHeight = Math.min(276, this.height - 40);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;

        int contentLeft = panelLeft + 8;
        int contentWidth = panelWidth - 16;

        int titleBarY = panelTop + 4;

        addRenderableWidget(Button.builder(Component.literal("\u2699"), button -> {
            this.minecraft.gui.setScreen(new MusicSettingsScreen(this));
        }).bounds(panelLeft + panelWidth - 44, titleBarY, 18, 18).build());

        addRenderableWidget(Button.builder(Component.literal("\u22EE"), button -> {
            this.minecraft.gui.setScreen(new UpNextScreen(this));
        }).bounds(panelLeft + panelWidth - 88, titleBarY, 18, 18).build());

        addRenderableWidget(Button.builder(Component.literal("\u2630"), button -> {
            this.minecraft.gui.setScreen(new PlaylistListScreen(this));
        }).bounds(panelLeft + panelWidth - 66, titleBarY, 18, 18).build());

        addRenderableWidget(Button.builder(Component.literal("X"), button -> {
            this.onClose();
        }).bounds(panelLeft + panelWidth - 22, titleBarY, 18, 18).build());

        Button sourceButton;
        boolean isMc = manager.getCurrentSource() == MusicPlayerManager.SongSource.MINECRAFT;
        sourceButton = Button.builder(
            Component.literal(isMc ? "\u25B7\u266B" : "\u266B"),
            button -> toggleSource()
        ).bounds(panelLeft + 4, titleBarY, 22, 18).build();
        sourceButton.setTooltip(Tooltip.create(Component.literal("Switch between User Library and Minecraft Soundtrack")));
        addRenderableWidget(sourceButton);

        int searchBoxY = panelTop + 26;
        searchBox = new EditBox(
                this.font,
                contentLeft, searchBoxY,
                contentWidth, 14,
                Component.literal("Search")
        );
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.literal("Search..."));
        searchBox.setResponder(query -> {
            songList.setFilter(query);
            searchBox.setFocused(true);
        });
        addRenderableWidget(searchBox);

        int listTop = searchBoxY + 18;
        int separatorY = panelTop + panelHeight - 98;
        int listHeight = separatorY - listTop - 4;
        songList = new SongListWidget(this.minecraft, contentWidth, listHeight, listTop, 16);
        songList.setX(contentLeft);
        songList.setY(listTop);
        populateSongList();
        restoreSongListScroll();
        addRenderableWidget(songList);

        int controlsTop = panelTop + panelHeight - 96;

        int progressX = contentLeft + 54;
        int progressW = contentWidth - 54;
        progressBar = new ProgressBarWidget(progressX, controlsTop + 25, progressW, 8);
        addRenderableWidget(progressBar);

        int btnY = controlsTop + 72;
        int btnSize = 20;
        int speedBtnWidth = 34;
        int totalBtnWidth = btnSize * 5 + 4 * 4 + 4 + speedBtnWidth;
        int btnStartX = contentLeft + (contentWidth - totalBtnWidth - 80) / 2;

        addRenderableWidget(Button.builder(Component.literal("\u23EE"), button -> {
            manager.previousTrack();
        }).bounds(btnStartX, btnY, btnSize, btnSize).build());

        playPauseButton = Button.builder(Component.literal("\u25B6"), button -> {
            manager.togglePlayPause();
        }).bounds(btnStartX + btnSize + 4, btnY, btnSize, btnSize).build();
        addRenderableWidget(playPauseButton);

        addRenderableWidget(Button.builder(Component.literal("\u23ED"), button -> {
            manager.nextTrack();
        }).bounds(btnStartX + (btnSize + 4) * 2, btnY, btnSize, btnSize).build());

        shuffleButton = Button.builder(Component.literal("\u21C6"), button -> {
            PlaybackMode mode = manager.getConfig().getPlaybackMode();
            if (mode == PlaybackMode.SHUFFLE) {
                manager.setPlaybackMode(PlaybackMode.SEQUENTIAL);
            } else {
                manager.setPlaybackMode(PlaybackMode.SHUFFLE);
            }
        }).bounds(btnStartX + (btnSize + 4) * 3, btnY, btnSize, btnSize).build();
        addRenderableWidget(shuffleButton);

        repeatButton = Button.builder(Component.literal("\u21BB"), button -> {
            PlaybackMode mode = manager.getConfig().getPlaybackMode();
            if (mode == PlaybackMode.REPEAT_ALL) {
                manager.setPlaybackMode(PlaybackMode.REPEAT_ONE);
            } else if (mode == PlaybackMode.REPEAT_ONE) {
                manager.setPlaybackMode(PlaybackMode.SEQUENTIAL);
            } else {
                manager.setPlaybackMode(PlaybackMode.REPEAT_ALL);
            }
        }).bounds(btnStartX + (btnSize + 4) * 4, btnY, btnSize, btnSize).build();
        addRenderableWidget(repeatButton);

        int speedBtnX = btnStartX + (btnSize + 4) * 5 + 4;
        speedButton = Button.builder(Component.literal(formatSpeed(manager.getConfig().getPlaybackSpeed())), button -> {
            speedDropdownOpen = !speedDropdownOpen;
        }).bounds(speedBtnX, btnY, speedBtnWidth, btnSize).build();
        addRenderableWidget(speedButton);

        int volWidth = 70;
        int volX = contentLeft + contentWidth - volWidth;
        volumeSlider = new VolumeSliderWidget(volX, btnY + 4, volWidth, 12);
        addRenderableWidget(volumeSlider);

        assignArtButton = Button.builder(Component.literal("+"), button -> {
            assignAlbumArt();
        }).bounds(panelLeft + 8, controlsTop + 2, 48, 48).build();
        assignArtButton.setTooltip(Tooltip.create(Component.literal("Assign album art (PNG)")));
        addRenderableWidget(assignArtButton);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ColorTheme th = theme();

        graphics.fill(0, 0, this.width, this.height, 0x90000000);

        graphics.fill(panelLeft - 2, panelTop - 2, panelLeft + panelWidth + 2, panelTop + panelHeight + 2, th.panelBorder());
        graphics.fill(panelLeft - 1, panelTop - 1, panelLeft + panelWidth + 1, panelTop, th.panelInner());
        graphics.fill(panelLeft - 1, panelTop - 1, panelLeft, panelTop + panelHeight + 1, th.panelInner());
        graphics.fill(panelLeft + panelWidth, panelTop, panelLeft + panelWidth + 1, panelTop + panelHeight + 1, th.panelInner());
        graphics.fill(panelLeft, panelTop + panelHeight, panelLeft + panelWidth + 1, panelTop + panelHeight + 1, th.panelInner());
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, th.panelBg());

        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 24, th.titleBarBg());
        graphics.fill(panelLeft, panelTop + 23, panelLeft + panelWidth, panelTop + 24, th.titleBarBorder());

        boolean isMc = manager.getCurrentSource() == MusicPlayerManager.SongSource.MINECRAFT;
        String context;
        if (isMc) {
            context = "\u266B Minecraft Soundtrack";
        } else if (manager.isUsingCustomPlaylist()) {
            context = "\u266B " + manager.getCurrentCustomPlaylist().getName();
        } else {
            context = "\u266B Music Library";
        }
        String modeIcon = switch (manager.getConfig().getPlaybackMode()) {
            case SHUFFLE -> " \u21C6";
            case REPEAT_ALL -> " \u21BB";
            case REPEAT_ONE -> " \u21BB1";
            default -> "";
        };
        String fullTitle = context + modeIcon;
        int maxTitleWidth = panelWidth - 145;
        if (this.font.width(fullTitle) > maxTitleWidth) {
            while (this.font.width(fullTitle) > maxTitleWidth && fullTitle.length() > 4) {
                fullTitle = fullTitle.substring(0, fullTitle.length() - 4) + "...";
            }
        }
        graphics.text(this.font, fullTitle, panelLeft + 28, panelTop + 8, th.titleText(), shadow());

        int controlsTop = panelTop + panelHeight - 96;

        graphics.fill(panelLeft + 4, controlsTop - 2, panelLeft + panelWidth - 4, controlsTop - 1, th.titleBarBorder());

        boolean isPlaying = isMc
            ? VanillaMusicPlayer.getInstance().isPlaying()
            : manager.getEngine().isActive() && !manager.getEngine().isPaused();

        int artSize = 48;
        int artX = panelLeft + 8;
        int artY = controlsTop + 2;

        boolean songActive = isMc
            ? VanillaMusicPlayer.getInstance().getCurrentTrack() != null && isPlaying
            : manager.getCurrentSong() != null && (manager.getEngine().isActive() || manager.getEngine().isPaused());

        if (songActive) {
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
            graphics.blit(artId,
                artX, artY, artX + artSize, artY + artSize,
                0.0f, 1.0f, 0.0f, 1.0f);

            assignArtButton.setX(artX);
            assignArtButton.setY(artY);
            assignArtButton.setWidth(artSize);
            assignArtButton.setHeight(artSize);
            assignArtButton.visible = !hasCustomArt && !isMc;

            int textX = artX + artSize + 6;
            int maxTextWidth = panelLeft + panelWidth - 8 - textX;

            if (isMc) {
                VanillaTrack vt = VanillaMusicPlayer.getInstance().getCurrentTrack();
                if (vt != null) {
                    String npLine = vt.title();
                    while (this.font.width(npLine) > maxTextWidth && npLine.length() > 3) {
                        npLine = npLine.substring(0, npLine.length() - 4) + "...";
                    }
                    graphics.text(this.font, npLine, textX, artY, th.accent(), shadow());
                    String info = vt.artist() + " \u2014 " + vt.album();
                    while (this.font.width(info) > maxTextWidth && info.length() > 3) {
                        info = info.substring(0, info.length() - 4) + "...";
                    }
                    graphics.text(this.font, info, textX, artY + 11, th.dimText(), shadow());
                }
            } else {
                SongInfo song = manager.getCurrentSong();
                if (song != null) {
                    String nt = song.getTitle();
                    while (this.font.width(nt) > maxTextWidth && nt.length() > 3) {
                        nt = nt.substring(0, nt.length() - 4) + "...";
                    }
                    graphics.text(this.font, nt, textX, artY, th.accent(), shadow());
                    String info = song.getArtist();
                    while (this.font.width(info) > maxTextWidth && info.length() > 3) {
                        info = info.substring(0, info.length() - 4) + "...";
                    }
                    graphics.text(this.font, info, textX, artY + 11, th.dimText(), shadow());
                }
            }
        } else {
            assignArtButton.visible = false;
            graphics.text(this.font, "No song playing", panelLeft + 8, controlsTop + 8, th.accent(), shadow());
        }

        if (isMc) {
            boolean vp = VanillaMusicPlayer.getInstance().isPlaying();
            playPauseButton.setMessage(Component.literal(vp ? "\u23F8" : "\u25B6"));
        } else {
            if (manager.getEngine().isActive() && !manager.getEngine().isPaused()) {
                playPauseButton.setMessage(Component.literal("\u23F8"));
            } else {
                playPauseButton.setMessage(Component.literal("\u25B6"));
            }
        }

        PlaybackMode mode = manager.getConfig().getPlaybackMode();
        if (mode == PlaybackMode.REPEAT_ONE) {
            repeatButton.setMessage(Component.literal("\u21BB1"));
        } else {
            repeatButton.setMessage(Component.literal("\u21BB"));
        }

        speedButton.visible = !isMc;
        if (!isMc) {
            speedButton.setMessage(Component.literal(formatSpeed(manager.getConfig().getPlaybackSpeed())));
        } else {
            speedDropdownOpen = false;
        }

        PlaybackMode activeMode = manager.getConfig().getPlaybackMode();
        String modeText = switch (activeMode) {
            case SHUFFLE -> "Shuffle On";
            case REPEAT_ALL -> "Repeat All";
            case REPEAT_ONE -> "Repeat One";
            default -> null;
        };
        if (modeText != null) {
            int modeX = shuffleButton.getX();
            int modeY = shuffleButton.getY() + shuffleButton.getHeight() + 2;
            int modeW = shuffleButton.getWidth() + repeatButton.getWidth() + 4;
            graphics.fill(modeX, modeY, modeX + modeW, modeY + 10, 0x88000000);
            graphics.centeredText(this.font, Component.literal(modeText), modeX + modeW / 2, modeY + 1, th.accent());
        }

        int hintColor = th.dimText();
        if (isMc) {
            if (manager.getVanillaTracks().isEmpty()) {
                graphics.centeredText(this.font, "No Minecraft music tracks found",
                        panelLeft + panelWidth / 2,
                        panelTop + 26 + (panelHeight - 120) / 2,
                        hintColor);
            } else if (!searchBox.getValue().isEmpty() && songList.getEntryCount() == 0) {
                graphics.centeredText(this.font, "No results",
                        panelLeft + panelWidth / 2,
                        panelTop + 26 + (panelHeight - 120) / 2,
                        hintColor);
            }
        } else {
            if (manager.getPlaylist().isEmpty()) {
                String hint = manager.getConfig().getMusicFolderPath().isEmpty()
                        ? "Click \u2699 to set your music folder"
                        : "No MP3/WAV files found in folder";
                graphics.centeredText(this.font, hint,
                        panelLeft + panelWidth / 2,
                        panelTop + 26 + (panelHeight - 120) / 2,
                        hintColor);
            } else if (!searchBox.getValue().isEmpty() && songList.getEntryCount() == 0) {
                graphics.centeredText(this.font, "No results",
                        panelLeft + panelWidth / 2,
                        panelTop + 26 + (panelHeight - 120) / 2,
                        hintColor);
            }
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        if (speedDropdownOpen) {
            renderSpeedDropdown(graphics);
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
        String songId;
        String initialDir = manager.getConfig().getMusicFolderPath();
        if (manager.getCurrentSource() == MusicPlayerManager.SongSource.MINECRAFT) {
            VanillaTrack vt = VanillaMusicPlayer.getInstance().getCurrentTrack();
            if (vt == null) return;
            songId = vt.id();
        } else {
            SongInfo song = manager.getCurrentSong();
            if (song == null) return;
            songId = song.getPath();
        }
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

    private void toggleSource() {
        if (manager.getCurrentSource() == MusicPlayerManager.SongSource.MINECRAFT) {
            manager.setCurrentSource(MusicPlayerManager.SongSource.USER);
        } else {
            manager.setCurrentSource(MusicPlayerManager.SongSource.MINECRAFT);
        }
        searchBox.setValue("");
        populateSongList();
        restoreSongListScroll();
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
        return super.mouseClicked(event, isFocused);
    }

    @Override
    public boolean isPauseScreen() {
        return manager.getConfig().isPauseOnOpen() && minecraft != null && minecraft.level != null;
    }

    @Override
    public void onClose() {
        if (manager.getEngine().isActive() && !manager.getEngine().isPaused()) {
            manager.getEngine().togglePause();
        }
        this.minecraft.gui.setScreen(null);
    }
}
