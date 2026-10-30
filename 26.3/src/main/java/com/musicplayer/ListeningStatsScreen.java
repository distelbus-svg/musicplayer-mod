package com.musicplayer;

import com.musicplayer.stats.ListeningStatisticsManager;
import com.musicplayer.theme.ColorTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ListeningStatsScreen extends Screen {

    private final Screen parent;
    private StatsListWidget listWidget;

    private boolean isHoursUnit = true;
    private boolean isSessionScope = false;
    private int sortBy = 2;

    private int scopeBtnX, scopeBtnY, scopeBtnW = 70, scopeBtnH = 16;
    private int unitBtnX, unitBtnY, unitBtnW = 30, unitBtnH = 16;
    private int sortBtnX, sortBtnY, sortBtnW = 80, sortBtnH = 16;

    public ListeningStatsScreen(Screen parent) {
        super(Component.literal("Listening Statistics"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ColorTheme th = ColorTheme.get(MusicPlayerManager.getInstance().getConfig().getThemeId());
        sidebarW = Math.max(140, Math.min(190, this.width * 14 / 100));
        topBarH = 26;

        contentTop = topBarH + 2;
        contentBottom = this.height - 10;

        int btnY = contentTop + 6;
        scopeBtnX = this.width / 2 - scopeBtnW / 2 - 60;
        scopeBtnY = btnY;
        unitBtnX = this.width / 2 - unitBtnW / 2;
        unitBtnY = btnY;
        sortBtnX = this.width / 2 - sortBtnW / 2 + 60;
        sortBtnY = btnY;

        int listY = btnY + scopeBtnH + 42;
        int listH = contentBottom - listY - 10;
        listWidget = new StatsListWidget(this.minecraft, this.width - sidebarW - 30, listH, listY, 18);
        listWidget.setX(sidebarW + 10);
        listWidget.setY(listY);
        addRenderableWidget(listWidget);

        refreshStats();
    }

    private static final long monotonicMs() {
        return System.nanoTime() / 1_000_000L;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ColorTheme th = ColorTheme.get(MusicPlayerManager.getInstance().getConfig().getThemeId());

        graphics.fill(0, 0, this.width, this.height, th.panelBg());

        graphics.fill(0, 0, this.width, topBarH, th.titleBarBg());
        graphics.fill(0, topBarH, this.width, topBarH + 1, th.titleBarBorder());
        graphics.text(this.font, this.title, 10, 6, th.titleText(), true);

        String backText = "\u2190 Back";
        int backX = this.width - this.font.width(backText) - 10;
        boolean backHover = mouseX >= backX && mouseX <= backX + this.font.width(backText) && mouseY >= contentTop - 16 && mouseY <= contentTop;
        graphics.text(this.font, backText, backX, contentTop - 12, backHover ? th.accent() : th.dimText(), true);

        graphics.fill(sidebarW, contentTop, sidebarW + 1, this.height, th.titleBarBorder());
        graphics.fill(sidebarW + 10, scopeBtnY - 2, this.width - 10, scopeBtnY + scopeBtnH + 2, th.panelBg());

        String scopeText = isSessionScope ? "Session" : "All-Time";
        String unitText = isHoursUnit ? "h" : "min";
        String sortText = switch (sortBy) {
            case 0 -> "Sort: A-Z";
            case 1 -> "Sort: Plays";
            default -> "Sort: Time";
        };

        int btnBg = 0x40000000;
        int btnHover = 0x60000000;
        graphics.fill(scopeBtnX, scopeBtnY, scopeBtnX + scopeBtnW, scopeBtnY + scopeBtnH,
                (mouseX >= scopeBtnX && mouseX <= scopeBtnX + scopeBtnW && mouseY >= scopeBtnY && mouseY <= scopeBtnY + scopeBtnH) ? btnHover : btnBg);
        graphics.centeredText(this.font, scopeText, scopeBtnX + scopeBtnW / 2, scopeBtnY + 4, th.text());

        graphics.fill(unitBtnX, unitBtnY, unitBtnX + unitBtnW, unitBtnY + unitBtnH,
                (mouseX >= unitBtnX && mouseX <= unitBtnX + unitBtnW && mouseY >= unitBtnY && mouseY <= unitBtnY + unitBtnH) ? btnHover : btnBg);
        graphics.centeredText(this.font, unitText, unitBtnX + unitBtnW / 2, unitBtnY + 4, th.text());

        graphics.fill(sortBtnX, sortBtnY, sortBtnX + sortBtnW, sortBtnY + sortBtnH,
                (mouseX >= sortBtnX && mouseX <= sortBtnX + sortBtnW && mouseY >= sortBtnY && mouseY <= sortBtnY + sortBtnH) ? btnHover : btnBg);
        graphics.centeredText(this.font, sortText, sortBtnX + sortBtnW / 2, sortBtnY + 4, th.text());

        ListeningStatisticsManager stats = ListeningStatisticsManager.getInstance();
        long totalSecs = isSessionScope ? stats.getSessionListeningTimeSeconds() : stats.getTotalListeningTimeSeconds();
        String timeLabel = isHoursUnit
                ? String.format("Total: %d:%02d h", totalSecs / 3600, (totalSecs % 3600) / 60)
                : String.format("Total: %d min", totalSecs / 60);
        graphics.centeredText(this.font, timeLabel, this.width / 2, scopeBtnY + scopeBtnH + 14, th.accent());

        graphics.fill(sidebarW + 10, scopeBtnY + scopeBtnH + 24, this.width - 10, scopeBtnY + scopeBtnH + 25, th.titleBarBorder());

        graphics.text(this.font, "Song Title", sidebarW + 18, scopeBtnY + scopeBtnH + 28, th.dimText(), true);
        graphics.text(this.font, "Time", this.width - 140, scopeBtnY + scopeBtnH + 28, th.dimText(), true);
        graphics.text(this.font, "Plays", this.width - 60, scopeBtnY + scopeBtnH + 28, th.dimText(), true);

        graphics.fill(sidebarW + 10, scopeBtnY + scopeBtnH + 39, this.width - 10, scopeBtnY + scopeBtnH + 40, 0xFF3C3C3C);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
        if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            double mx = event.x();
            double my = event.y();
            String backText = "\u2190 Back";
            int backX = this.width - this.font.width(backText) - 10;
            if (mx >= backX && mx <= backX + this.font.width(backText) && my >= contentTop - 16 && my <= contentTop) {
                onClose();
                return true;
            }
            if (mx >= scopeBtnX && mx <= scopeBtnX + scopeBtnW && my >= scopeBtnY && my <= scopeBtnY + scopeBtnH) {
                toggleScope();
                return true;
            }
            if (mx >= unitBtnX && mx <= unitBtnX + unitBtnW && my >= unitBtnY && my <= unitBtnY + unitBtnH) {
                toggleUnit();
                return true;
            }
            if (mx >= sortBtnX && mx <= sortBtnX + sortBtnW && my >= sortBtnY && my <= sortBtnY + sortBtnH) {
                toggleSort();
                return true;
            }
        }
        return super.mouseClicked(event, isFocused);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private void toggleUnit() {
        isHoursUnit = !isHoursUnit;
        refreshStats();
    }

    private void toggleScope() {
        isSessionScope = !isSessionScope;
        refreshStats();
    }

    private void toggleSort() {
        sortBy = (sortBy + 1) % 3;
        refreshStats();
    }

    private void refreshStats() {
        if (listWidget == null) return;
        listWidget.clear();

        ListeningStatisticsManager stats = ListeningStatisticsManager.getInstance();
        Map<String, Integer> playCounts = isSessionScope ? stats.getSessionPlayCounts() : stats.getPlayCounts();
        Map<String, Long> listeningTimes = isSessionScope ? stats.getSessionListeningTimes() : stats.getListeningTimes();

        List<SongStat> songStats = new ArrayList<>();
        for (String path : playCounts.keySet()) {
            int plays = playCounts.getOrDefault(path, 0);
            long time = listeningTimes.getOrDefault(path, 0L);
            if (plays > 0 || time > 0) {
                songStats.add(new SongStat(path, plays, time));
            }
        }
        for (String path : listeningTimes.keySet()) {
            if (!playCounts.containsKey(path)) {
                int plays = playCounts.getOrDefault(path, 0);
                long time = listeningTimes.getOrDefault(path, 0L);
                if (plays > 0 || time > 0) {
                    songStats.add(new SongStat(path, plays, time));
                }
            }
        }

        if (sortBy == 0) {
            songStats.sort((a, b) -> songName(a.path).compareToIgnoreCase(songName(b.path)));
        } else if (sortBy == 1) {
            songStats.sort((a, b) -> Integer.compare(b.plays, a.plays));
        } else {
            songStats.sort((a, b) -> Long.compare(b.time, a.time));
        }

        for (SongStat stat : songStats) {
            String name = songName(stat.path);
            String timeStr = isHoursUnit
                    ? String.format("%d:%02d h", stat.time / 3600, (stat.time % 3600) / 60)
                    : String.format("%d min", stat.time / 60);
            listWidget.addEntry(new StatsEntry(name, timeStr, stat.plays));
        }
    }

    private static String songName(String path) {
        int idx = path.lastIndexOf('/');
        if (idx < 0) idx = path.lastIndexOf('\\');
        String name = idx >= 0 ? path.substring(idx + 1) : path;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int topBarH;
    private int sidebarW;
    private int contentTop;
    private int contentBottom;

    private static class SongStat {
        public final String path;
        public final int plays;
        public final long time;
        SongStat(String path, int plays, long time) {
            this.path = path;
            this.plays = plays;
            this.time = time;
        }
    }

    public class StatsListWidget extends ObjectSelectionList<StatsEntry> {
        public StatsListWidget(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }
        public void clear() { clearEntries(); }
        @Override
        public int addEntry(StatsEntry entry) { return super.addEntry(entry); }
        @Override
        public int getRowWidth() { return this.width - 10; }
        @Override
        protected int scrollBarX() { return this.getX() + this.width - 6; }
    }

    public class StatsEntry extends ObjectSelectionList.Entry<StatsEntry> {
        private final String title;
        private final String timeStr;
        private final int plays;
        public StatsEntry(String title, String timeStr, int plays) {
            this.title = title;
            this.timeStr = timeStr;
            this.plays = plays;
        }
        @Override
        public Component getNarration() { return Component.literal(title); }
        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int entryIndex, int top, boolean isSelected, float partialTick) {
            ColorTheme th = ColorTheme.get(MusicPlayerManager.getInstance().getConfig().getThemeId());
            int left = this.getX();
            int y = this.getY();
            int entryWidth = this.getWidth();
            int entryHeight = this.getHeight();
            int bgColor = (entryIndex % 2 == 0) ? 0x20FFFFFF : 0x10FFFFFF;
            graphics.fill(left, y, left + entryWidth, y + entryHeight, bgColor);
            String displayTitle = title;
            int maxTitleW = entryWidth - 130;
            if (font.width(displayTitle) > maxTitleW) {
                displayTitle = font.plainSubstrByWidth(displayTitle, maxTitleW - 4) + "...";
            }
            graphics.text(font, displayTitle, left + 6, y + 4, th.text(), true);
            graphics.text(font, timeStr, left + entryWidth - 80, y + 4, th.accent(), true);
            graphics.text(font, String.valueOf(plays), left + entryWidth - 30, y + 4, th.dimText(), true);
        }
    }
}
