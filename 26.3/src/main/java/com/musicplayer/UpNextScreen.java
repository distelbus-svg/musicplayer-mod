package com.musicplayer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

public class UpNextScreen extends Screen {

    private final Screen parent;
    private final MusicPlayerManager manager;

    private UpNextListWidget listWidget;

    public UpNextScreen(Screen parent) {
        super(Component.literal("Up Next"));
        this.parent = parent;
        this.manager = MusicPlayerManager.getInstance();
    }

    @Override
    protected void init() {
        int listWidth = Math.min(320, this.width - 40);
        int listLeft = (this.width - listWidth) / 2;
        int listTop = 40;
        int listHeight = this.height - listTop - 50;

        listWidget = new UpNextListWidget(this.minecraft, listWidth, listHeight, listTop, 30);
        listWidget.setX(listLeft);
        rebuildList();

        addRenderableWidget(listWidget);

        Button backBtn = Button.builder(Component.literal("\u2190 Back"), b ->
            this.minecraft.gui.setScreen(parent)
        ).bounds(listLeft, this.height - 36, 60, 20).build();
        addRenderableWidget(backBtn);
    }

    private void rebuildList() {
        listWidget.clearAll();
        List<SongInfo> queue = manager.getUpNextQueue(20);
        if (queue.isEmpty()) {
            listWidget.addQueueEntry(listWidget.new QueueEntry(null, -1, true));
        } else {
            for (int i = 0; i < queue.size(); i++) {
                listWidget.addQueueEntry(listWidget.new QueueEntry(queue.get(i), i + 1, false));
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0000000);

        int listWidth = Math.min(320, this.width - 40);
        int listLeft = (this.width - listWidth) / 2;

        graphics.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);

        String info = "Next " + Math.min(20, manager.getUpNextQueue(20).size()) + " songs";
        String mode = "(" + manager.getConfig().getPlaybackMode().name() + ")";
        graphics.text(this.font, info, listLeft, 28, 0xFFAAAAAA, true);
        graphics.text(this.font, mode, listLeft + this.font.width(info) + 8, 28, 0xFF888888, true);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ─── List Widget ───

    private class UpNextListWidget extends ObjectSelectionList<UpNextListWidget.QueueEntry> {

        public UpNextListWidget(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        public void clearAll() {
            clearEntries();
        }

        public void addQueueEntry(QueueEntry entry) {
            addEntry(entry);
        }

        @Override
        public int getRowWidth() { return this.width - 10; }

        @Override
        protected int scrollBarX() { return this.getX() + this.width - 6; }

        @Override
        public void setSelected(QueueEntry entry) {
            // Disable selection highlight — the list shows a static queue, not a picker
        }

        public class QueueEntry extends ObjectSelectionList.Entry<QueueEntry> {
            private final SongInfo song;
            private final int queuePos;
            private final boolean isEmpty;

            QueueEntry(SongInfo song, int queuePos, boolean isEmpty) {
                this.song = song;
                this.queuePos = queuePos;
                this.isEmpty = isEmpty;
            }

            @Override
            public Component getNarration() {
                return song != null ? Component.literal(song.getTitle()) : Component.literal("Empty queue");
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int entryIndex, int top, boolean isSelected, float partialTick) {
                int y = this.getY();
                int left = UpNextListWidget.this.getRowLeft();
                int width = UpNextListWidget.this.getRowWidth();

                graphics.fill(left, y, left + width, y + this.getHeight(), 0x30000000);

                if (isEmpty) {
                    graphics.centeredText(Minecraft.getInstance().font, "End of playlist",
                            left + width / 2, y + 8, 0xFF666666);
                    return;
                }

                var font = Minecraft.getInstance().font;

                String posStr = "+" + queuePos;
                int posWidth = font.width(posStr);
                graphics.text(font, posStr, left + 4, y + 6, 0xFFAAAAAA, true);

                String title = song.getTitle();
                int maxTitleWidth = width - posWidth - 30;
                if (font.width(title) > maxTitleWidth) {
                    title = font.plainSubstrByWidth(title, maxTitleWidth - 4) + "...";
                }
                graphics.text(font, title, left + posWidth + 12, y + 6, 0xFFFFFFFF, true);

                String artist = song.getArtist();
                if (artist != null) {
                    graphics.text(font, artist, left + posWidth + 12, y + 17, 0xFF888888, true);
                }
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
                if (event.buttonInfo().button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && !isEmpty && song != null) {
                    List<SongInfo> playlist = manager.getPlaylist();
                    for (int i = 0; i < playlist.size(); i++) {
                        if (playlist.get(i).getPath().equals(song.getPath())) {
                            manager.playSong(i);
                            Minecraft.getInstance().gui.setScreen(null);
                            return true;
                        }
                    }
                }
                return false;
            }
        }
    }
}
