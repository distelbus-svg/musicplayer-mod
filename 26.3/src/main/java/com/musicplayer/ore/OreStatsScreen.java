package com.musicplayer.ore;

import com.musicplayer.SongInfo;
import com.musicplayer.VanillaTrack;
import com.musicplayer.player.SoundtrackRegistry;
import com.musicplayer.stats.ListeningStatisticsManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Listening stats, from the design's Stats.dc.html. */
public class OreStatsScreen extends OreScreen {

    private record Stat(String path, String title, long time, int plays) {}

    private final Screen parent;
    private boolean session = false;
    private boolean hours = true;
    private int sort = 0; // 0 time, 1 plays, 2 name
    private final Map<String, String> titles = new HashMap<>();

    public OreStatsScreen(Screen parent) {
        super("Listening stats");
        this.parent = parent;
    }

    @Override
    protected int activeTab() { return -1; }

    private String titleOf(String path) {
        return titles.computeIfAbsent(path, p -> {
            for (VanillaTrack vt : SoundtrackRegistry.getInstance().getAllTracks()) {
                if (vt.id().equals(p)) return vt.title();
            }
            int idx = Math.max(p.lastIndexOf('/'), p.lastIndexOf('\\'));
            String name = idx >= 0 ? p.substring(idx + 1) : p;
            int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(0, dot) : name;
        });
    }

    private String fmt(long sec) {
        return hours ? String.format("%d:%02d h", sec / 3600, (sec % 3600) / 60) : (sec / 60) + " min";
    }

    private List<Stat> stats() {
        ListeningStatisticsManager st = ListeningStatisticsManager.getInstance();
        Map<String, Integer> counts = session ? st.getSessionPlayCounts() : st.getPlayCounts();
        Map<String, Long> times = session ? st.getSessionListeningTimes() : st.getListeningTimes();
        List<Stat> out = new ArrayList<>();
        java.util.Set<String> keys = new java.util.LinkedHashSet<>(counts.keySet());
        keys.addAll(times.keySet());
        for (String k : keys) {
            int plays = counts.getOrDefault(k, 0);
            long time = times.getOrDefault(k, 0L);
            if (plays > 0 || time > 0) out.add(new Stat(k, titleOf(k), time, plays));
        }
        switch (sort) {
            case 1 -> out.sort((a, b) -> Integer.compare(b.plays(), a.plays()));
            case 2 -> out.sort((a, b) -> a.title().compareToIgnoreCase(b.title()));
            default -> out.sort((a, b) -> Long.compare(b.time(), a.time()));
        }
        return out;
    }

    /** A row of joined segment buttons (no gap), as in the design. Returns the total width. */
    private int segment(GuiGraphicsExtractor g, int mx, int my, int x, int y, int h, String[] labels, int cur, float size,
                        int padX, java.util.function.IntConsumer pick) {
        int cx = x;
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            int w = tw(labels[i], false, size) + 2 * padX;
            button(g, mx, my, cx, y, w, h, i == cur ? GREEN : NORMAL, () -> pick.accept(idx));
            textBtn(g, labels[i], false, size, cx, y, w, h, i == cur ? 0xFFFFFFFF : th.text());
            cx += w;
        }
        return cx - x;
    }

    @Override
    protected void renderBody(GuiGraphicsExtractor g, int mx, int my) {
        List<Stat> list = stats();
        int top = px(64);
        int left = px(32), right = this.width - px(32);
        int b2 = px(2);
        int y = top + px(24) - scrollY;

        g.enableScissor(0, top, this.width, this.height);

        // ---- title row
        int by = y + px(5);
        tip("Back");
        button(g, mx, my, left, by, px(48), px(48), NORMAL, () -> this.minecraft.gui.setScreen(parent));
        iconBtn(g, ICON_BACK, 10, 10, left, by, px(48), px(48), px(22), th.text());
        // right controls: scope + unit
        String[] unitL = {"h", "min"};
        String[] scopeL = {"Session", "All-time"};
        int unitW = 0, scopeW = 0;
        for (String s : unitL) unitW += tw(s, false, 14) + px(32);
        for (String s : scopeL) scopeW += tw(s, false, 14) + px(36);
        int ux = right - unitW;
        segment(g, mx, my, ux, y + px(7), px(44), unitL, hours ? 0 : 1, 14, px(16), i -> hours = i == 0);
        int sx = ux - px(20) - scopeW;
        segment(g, mx, my, sx, y + px(7), px(44), scopeL, session ? 0 : 1, 14, px(18), i -> session = i == 0);
        int tx = left + px(48) + px(16);
        text(g, "Listening stats", tx, y, th.text(), true, 32);
        text(g, fit(session ? "This session only" : "Everything Melodix has tracked", sx - px(16) - tx, false, 16),
                tx, y + px(42), th.dim(), false, 16);
        y += px(58) + px(20);

        // ---- summary cards
        long totalSec = 0;
        int totalPlays = 0;
        for (Stat s : list) { totalSec += s.time(); totalPlays += s.plays(); }
        Stat topStat = null;
        for (Stat s : list) if (topStat == null || s.plays() > topStat.plays()) topStat = s;
        int gap = px(16);
        float[] fr = {1.4f, 1f, 1f, 1.4f};
        float frSum = 4.8f;
        int avail = right - left - gap * 3;
        int cardH = px(18) + capH(14) + px(8) + px(36) + px(18) + b2 + px(4);
        int cx = left;
        for (int i = 0; i < 4; i++) {
            int cw = Math.round(avail * fr[i] / frSum);
            card(g, cx, y, cw, cardH);
            if (i == 0) {
                hGradient(g, cx + b2, y + b2, (int) ((cw - 2 * b2) * 0.9f), cardH - 2 * b2 - px(4), 0x3C8527, 0.40f);
            }
            int ix = cx + px(20);
            text(g, new String[]{"Total listening time", "Songs played", "Total plays", "Most played"}[i], ix, y + px(18), th.dim(), false, 14);
            int vy = y + px(18) + capH(14) + px(8);
            switch (i) {
                case 0 -> text(g, fit(fmt(totalSec), cw - px(40), true, 32), ix, vy, th.text(), true, 32);
                case 1 -> text(g, String.valueOf(list.size()), ix, vy, th.text(), true, 32);
                case 2 -> text(g, String.valueOf(totalPlays), ix, vy, th.text(), true, 32);
                default -> {
                    text(g, fit(topStat == null ? "-" : topStat.title(), cw - px(40), true, 24), ix, vy, th.text(), true, 24);
                    text(g, (topStat == null ? 0 : topStat.plays()) + " plays", ix, vy + px(32), th.dim(), false, 14);
                }
            }
            cx += cw + gap;
        }
        y += cardH + px(20);

        // ---- list card
        int rowH = px(56);
        int emptyH = list.isEmpty() ? px(32) * 2 + capH(16) + b2 : 0;
        int listH = px(16) + px(40) + px(12) + px(40) + rowH * list.size() + emptyH + b2 + px(4);
        card(g, left, y, right - left, listH);
        int innerL = left + b2, innerR = right - b2;
        int hy = y + px(16);
        text(g, "Songs", innerL + px(20), hy + (px(40) - capH(16)) / 2, th.text(), true, 16);
        String[] sortL = {"Time", "Plays", "A-Z"};
        int sortW = 0;
        for (String s : sortL) sortW += tw(s, false, 14) + px(32);
        int sortX = innerR - px(20) - sortW;
        segment(g, mx, my, sortX, hy, px(40), sortL, sort, 14, px(16), i -> sort = i);
        String sb = "Sort by";
        text(g, sb, sortX - px(12) - tw(sb, false, 14), hy + (px(40) - capH(14)) / 2, th.dim(), false, 14);

        int headY = hy + px(40) + px(12);
        int timeR = innerR - px(24) - px(70) - px(14);
        int timeX = timeR - px(110);
        int barX = timeX - px(14) - px(220);
        int numX = innerL + px(16), numW = px(32);
        int thumbX = numX + numW + px(14);
        int titleX = thumbX + px(36) + px(14);
        int hdrTextY = headY + (px(40) - capH(14)) / 2;
        text(g, "#", numX + (numW - tw("#", false, 14)) / 2, hdrTextY, th.dim(), false, 14);
        text(g, "Song title", titleX, hdrTextY, th.dim(), false, 14);
        text(g, "Share of time", barX, hdrTextY, th.dim(), false, 14);
        textRight(g, "Time", timeR, hdrTextY, th.dim(), false, 14);
        textRight(g, "Plays", innerR - px(24), hdrTextY, th.dim(), false, 14);

        long maxT = 1;
        for (Stat s : list) maxT = Math.max(maxT, s.time());
        int ry = headY + px(40);
        for (int i = 0; i < list.size(); i++) {
            Stat s = list.get(i);
            int rowY = ry + i * rowH;
            if (rowY + rowH < top || rowY > this.height) continue;
            boolean hov = mx >= innerL && mx < innerR && my >= Math.max(rowY, top) && my < rowY + rowH;
            if (hov) g.fill(innerL, rowY, innerR, rowY + rowH, th.rowHover());
            g.fill(innerL, rowY, innerR, rowY + b2, th.sep());
            int mid = rowY + b2 + (rowH - b2) / 2;
            String num = String.valueOf(i + 1);
            text(g, num, numX + (numW - tw(num, false, 16)) / 2, mid - capH(16) / 2, th.dim(), false, 16);
            g.fill(thumbX, mid - px(18), thumbX + px(36), mid + px(18), th.edge());
            g.blit(coverFor(new SongInfo(s.title(), "", s.path(), SongInfo.SourceType.LOCAL)),
                    thumbX + b2, mid - px(18) + b2, thumbX + px(36) - b2, mid + px(18) - b2, 0f, 1f, 0f, 1f);
            text(g, fit(s.title(), barX - px(14) - titleX, false, 16), titleX, mid - capH(16) / 2, th.text(), false, 16);
            int tw0 = px(220), th0 = px(12);
            inset(g, barX, mid - th0 / 2, tw0, th0);
            int fillW = Math.round((tw0 - 2 * b2) * (s.time() / (float) maxT));
            if (fillW > 0) {
                g.fill(barX + b2, mid - th0 / 2 + b2, barX + b2 + fillW, mid + th0 / 2 - b2, OreTheme.GREEN);
                g.fill(barX + b2, mid - th0 / 2 + b2, barX + b2 + fillW, mid - th0 / 2 + b2 + b2, OreTheme.GREEN_HI);
            }
            textRight(g, fmt(s.time()), timeR, mid - capH(16) / 2, th.accent(), false, 16);
            textRight(g, String.valueOf(s.plays()), innerR - px(24), mid - capH(16) / 2, th.dim(), false, 16);
        }
        if (list.isEmpty()) {
            int ey = ry;
            g.fill(innerL, ey, innerR, ey + b2, th.sep());
            String e = session ? "Nothing played yet this session." : "Nothing played yet.";
            text(g, e, innerL + ((innerR - innerL) - tw(e, false, 16)) / 2, ey + px(32), th.dim(), false, 16);
        }
        y += listH + px(32);
        g.disableScissor();

        scrollMax = Math.max(0, (y + scrollY) - top - (this.height - top));
        scrollY = Math.max(0, Math.min(scrollMax, scrollY));
        scrollbar(g, top, this.height);
    }
}
