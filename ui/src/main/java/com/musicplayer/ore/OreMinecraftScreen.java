package com.musicplayer.ore;

import com.musicplayer.port.*;
import com.musicplayer.ui.*;

import com.musicplayer.MusicPlayerManager;
import com.musicplayer.PlayableTrack;
import com.musicplayer.VanillaTrack;
import com.musicplayer.config.MusicPlayerConfig;
import com.musicplayer.config.MusicPlayerConfig.PlaybackMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Minecraft music screen, from the design's Minecraft.dc.html. */
public class OreMinecraftScreen extends OrePlayerScreen {

    private static final String[] CATS = {"All", "Overworld", "Creative", "Nether", "End", "Menu", "Discs"};
    private static final int[] CAT_COLORS = {0, 0xFF5B8F3A, 0xFF4A7FB5, 0xFFA63C3C, 0xFF8A5FB0, 0xFFC9B13A, 0xFFC97A3A};
    private static final float[][] DECO = {
            {0, 0, .28f}, {0, 64, .10f}, {0, 96, .18f}, {32, 32, .18f}, {32, 128, .10f}, {64, 0, .10f}, {64, 64, .20f}};

    private String cat = "All";

    public OreMinecraftScreen() {
        super("Minecraft music");
    }

    @Override
    protected int activeTab() { return 1; }

    static String categoryOf(VanillaTrack t) {
        if (t.trackSource() == PlayableTrack.TrackSource.VANILLA_DISC) return "Discs";
        String key = ((t.id() == null ? "" : t.id()) + " " + (t.album() == null ? "" : t.album())).toLowerCase(Locale.ROOT);
        if (key.contains("nether") || key.contains("rubedo") || key.contains("so_below") || key.contains("chrysopoeia")
                || key.contains("concrete_halls") || key.contains("warmth") || key.contains("ballad_of_the_cats")) return "Nether";
        if (key.contains("/end") || key.contains("the_end") || key.contains("alpha") || key.contains("boss")) return "End";
        if (key.contains("creative") || key.contains("biome_fest") || key.contains("blind_spots") || key.contains("haunt_muskie")) return "Creative";
        if (key.contains("menu") || key.contains("title") || key.contains("mutation") || key.contains("moog_city")
                || key.contains("beginning") || key.contains("floating_trees")) return "Menu";
        return "Overworld";
    }

    private List<VanillaTrack> allTracks() {
        TrackRegistryPort reg = Ports.registry();
        if (reg.getAllTracks().isEmpty()) reg.discoverTracks();
        return reg.getAllTracks();
    }

    private boolean helpOpen;

    @Override
    protected boolean modal() { return helpOpen; }

    @Override
    public boolean onKeyPressed(UiKey event) {
        if (helpOpen && event.key() == UiKeys.ESCAPE) {
            helpOpen = false;
            return true;
        }
        return super.onKeyPressed(event);
    }

    private static final String[][] HELP = {
            {"Mute Minecraft music", "Silences the game's own background music while Melodix plays your songs, so the two never overlap. "
                    + "Music you start from this tab is never muted, and the game's music plays as usual whenever Melodix is idle."},
            {"Songs that start on their own", "When the game starts a track by itself it shows up in the player. "
                    + "You can pause it, skip it and change its volume like any other song."},
            {"Play all and Shuffle", "Play all starts the list from the top. Shuffle turns shuffle mode on. "
                    + "Next, previous and shuffle stay inside the category you picked."},
            {"Categories", "The chips filter the soundtrack by where a track plays: Overworld, Creative, Nether, End, Menu and music discs."}};

    @Override
    protected void renderOverlay(Gfx g, int mx, int my) {
        if (!helpOpen) return;
        g.fill(0, 0, this.width, this.height, 0xB0000000);
        catcher(mx, my, () -> helpOpen = false);
        int pad = px(24);
        int w = Math.min(px(720), this.width - px(48)), iw = w - 2 * pad;
        int lineH = capH(16) + px(8);
        List<String> intro = wrap("These notes used to sit next to the switch. They moved here so the page fits smaller screens.", iw, false, 16);
        List<List<String>> wrapped = new java.util.ArrayList<>();
        int contentH = intro.size() * lineH + px(10);
        for (String[] sec : HELP) {
            List<String> l = wrap(sec[1], iw, false, 16);
            wrapped.add(l);
            contentH += capH(16) + px(6) + l.size() * lineH + px(10);
        }
        int btnH = px(48);
        int h = pad + capH(24) + px(16) + contentH + btnH + pad;
        int x = (this.width - w) / 2, y = Math.max(px(16), (this.height - h) / 2);
        menuBox(g, x, y, w, h);
        catcherArea(mx, my, x, y, w, h);
        int cy = y + pad;
        text(g, "Minecraft music", x + pad, cy, th.text(), true, 24);
        cy += capH(24) + px(16);
        for (String l : intro) {
            text(g, l, x + pad, cy, th.dim(), false, 16);
            cy += lineH;
        }
        cy += px(10);
        for (int i = 0; i < HELP.length; i++) {
            text(g, HELP[i][0], x + pad, cy, th.accent(), true, 16);
            cy += capH(16) + px(6);
            for (String l : wrapped.get(i)) {
                text(g, l, x + pad, cy, th.text(), false, 16);
                cy += lineH;
            }
            cy += px(10);
        }
        String ok = "Got it";
        int okW = tw(ok, true, 16) + px(48);
        int okX = x + w - pad - okW, okY = y + h - pad - btnH;
        button(g, mx, my, okX, okY, okW, btnH, GREEN, () -> helpOpen = false);
        textBtn(g, ok, true, 16, okX, okY, okW, btnH, 0xFFFFFFFF);
    }

    /** Plays a track; next, previous and shuffle then stay inside the list that is shown ({@code context}). */
    private void play(List<VanillaTrack> context, VanillaTrack t) {
        manager.playMinecraftTrack(context, t.id());
    }

    @Override
    protected void renderBody(Gfx g, int mx, int my) {
        MusicPlayerConfig cfg = manager.getConfig();
        List<VanillaTrack> all = allTracks();
        List<VanillaTrack> shown = new ArrayList<>();
        for (VanillaTrack t : all) if (cat.equals("All") || categoryOf(t).equals(cat)) shown.add(t);
        Now now = now();

        int top = px(64);
        int footH = px(88);
        int bodyBottom = this.height - footH;
        int asideW = compact ? 0 : px(336);
        int mainRight = this.width - asideW;
        int left = px(32), w = mainRight - px(32) - left;
        int y = top + px(28) - scrollY;
        int b2 = px(2);

        g.enableScissor(0, top, mainRight, bodyBottom);

        // ---- hero
        int hh = px(168);
        card(g, left, y, w, hh);
        hGradient(g, left + b2, y + b2, (int) ((w - 2 * b2) * 0.72f), hh - 2 * b2 - px(4),
                0x3C8527, th.light() ? 0.22f : 0.40f);
        deco(g, left + w - b2, y + b2, 0x52A535, DECO);
        int cover = px(120), cx = left + px(24), cy = y + px(24);
        g.fill(cx - px(6), cy - px(6), cx + cover + px(6), cy + cover + px(6), th.hi());
        g.fill(cx - px(4), cy - px(4), cx + cover + px(4), cy + cover + px(4), th.edge());
        String mcArt = Ports.art().builtInArt("minecraft");
        g.blit(mcArt != null ? mcArt : Ports.art().defaultArt(), cx, cy, cx + cover, cy + cover, 0f, 1f, 0f, 1f);

        int tx = cx + cover + px(24);

        int by = y + px(96);
        boolean visibleBtn = by + px(52) > top;
        Runnable playAll = () -> { if (!shown.isEmpty()) play(shown, shown.get(0)); };
        boolean shufOn = cfg.getPlaybackMode() == PlaybackMode.SHUFFLE;
        Runnable shuffle = () -> {
            manager.toggleShuffle();
            boolean playingHere = now.mc && now.active;
            if (cfg.getPlaybackMode() == PlaybackMode.SHUFFLE && !shown.isEmpty() && !playingHere) {
                play(shown, shown.get(new Random().nextInt(shown.size())));
            }
        };
        int paW = px(148);
        int shW = px(20) + px(22) + px(10) + tw("Shuffle", false, 16) + px(20);
        int sx0 = tx + paW + px(10);
        int buttonsEnd = sx0 + shW;

        // mute switch: with its label when there is room next to the buttons, otherwise a short label in the corner
        int swW = px(88), swH = px(36);
        int swX = left + w - px(24) - px(12) - swW;
        int labelW = Math.max(px(198), tw("Mute Minecraft music", true, 16) + px(4));
        int lx = swX - px(14) - labelW;
        boolean fullMute = lx >= buttonsEnd + px(24);
        int swY = fullMute ? y + (hh - swH) / 2 : y + px(24);
        int shortW = tw("Mute", true, 16);
        int textMax = (fullMute ? lx : swX - px(10) - shortW) - px(16) - tx;

        text(g, fit("Minecraft music", textMax, true, 32), tx, y + px(24), th.text(), true, 32);
        text(g, fit("The in-game soundtrack \u00b7 " + all.size() + " songs", textMax, false, 16), tx, y + px(24) + px(42), th.dim(), false, 16);

        tip("Play all");
        button(g, mx, my, tx, by, paW, px(52), GREEN, visibleBtn ? playAll : null);
        {
            String label = "Play all";
            int iw = px(22), contentW = iw + px(12) + tw(label, true, 16);
            int ix = tx + (paW - contentW) / 2;
            icon(g, ICON_PLAY, 10, 10, ix, by + px(26) - px(2) - iw / 2 + pressDy, iw, 0xFFFFFFFF);
            text(g, label, ix + iw + px(12), by + px(26) - px(2) - capH(16) / 2 + pressDy, 0xFFFFFFFF, true, 16);
        }
        tip("Shuffle");
        button(g, mx, my, sx0, by, shW, px(52), shufOn ? GREEN : NORMAL, visibleBtn ? shuffle : null);
        icon(g, ICON_SHUFFLE, 10, 10, sx0 + px(20), by + px(26) - px(2) - px(11) + pressDy, px(22), shufOn ? 0xFFFFFFFF : th.text());
        text(g, "Shuffle", sx0 + px(20) + px(22) + px(10), by + px(26) - px(2) - capH(16) / 2 + pressDy, shufOn ? 0xFFFFFFFF : th.text(), false, 16);

        boolean muted = cfg.isSuppressVanillaMusic();
        String more = "more...";
        int moreW = tw(more, false, 14);
        int moreX, moreY;
        if (fullMute) {
            List<String> desc = wrap("Silences the game's own tracks while Melodix plays", labelW, false, 14);
            int lineH = capH(14) + px(6);
            int blockH = capH(16) + px(6) + desc.size() * lineH;
            int ly = y + (hh - (blockH + px(8) + capH(14))) / 2;
            text(g, "Mute Minecraft music", lx, ly, th.text(), true, 16);
            for (int i = 0; i < desc.size(); i++) {
                text(g, desc.get(i), lx, ly + capH(16) + px(6) + i * lineH, th.dim(), false, 14);
            }
            moreX = lx;
            moreY = ly + blockH + px(8);
        } else {
            text(g, "Mute", swX - px(10) - shortW, swY + (swH - capH(16)) / 2, th.text(), true, 16);
            moreX = swX + swW - moreW;
            moreY = swY + swH + px(8);
        }
        boolean moreHov = hit(mx, my, moreX - px(4), moreY - px(4), moreW + px(8), capH(14) + px(8), () -> helpOpen = true);
        text(g, more, moreX, moreY, th.accent(), false, 14);
        if (moreHov) g.fill(moreX, moreY + capH(14) + px(2), moreX + moreW, moreY + capH(14) + px(2) + Math.max(1, px(1)), th.accent());
        drawSwitch(g, mx, my, swX, swY, muted, "mute", true, () -> cfg.setSuppressVanillaMusic(!cfg.isSuppressVanillaMusic()));

        y += hh + px(20);

        // ---- category chips
        int cxp = left;
        for (int i = 0; i < CATS.length; i++) {
            String name = CATS[i];
            String label = name.equals("Discs") ? "Music discs" : name;
            int count = name.equals("All") ? all.size() : (int) all.stream().filter(t -> categoryOf(t).equals(name)).count();
            String num = String.valueOf(count);
            int cw = tw(label, false, 14) + px(8) + tw(num, false, 14) + px(32);
            if (cxp > left && cxp + cw > left + w) { cxp = left; y += px(48); }
            boolean sel = cat.equals(name);
            Runnable pick = () -> { cat = name; scrollY = 0; };
            button(g, mx, my, cxp, y, cw, px(40), sel ? GREEN : NORMAL, y + px(40) > top ? pick : null);
            int faceMid = y + px(40) / 2 - px(2);
            text(g, label, cxp + px(16), faceMid - capH(14) / 2, sel ? 0xFFFFFFFF : th.text(), false, 14);
            text(g, num, cxp + px(16) + tw(label, false, 14) + px(8), faceMid - capH(14) / 2,
                    sel ? 0xFFD4F0C8 : th.dim(), false, 14);
            cxp += cw + px(8);
        }
        y += px(40) + px(20);

        // ---- songs
        int rowH = px(56);
        int listH = px(40) + rowH * shown.size() + b2 + px(4);
        card(g, left, y, w, listH);
        int innerL = left + b2, innerR = left + w - b2;
        int hy = y + b2;
        int numX = innerL + px(16), numW = px(32);
        int thumbX = numX + numW + px(14);
        int colTitle = thumbX + px(36) + px(14);
        int colTime = innerR - px(100) - px(56);
        int colCat = colTime - px(14) - px(120);
        int colArtist = colCat - px(14) - px(200);
        // with the side player open there is no room for the category column
        boolean showCat = colArtist - colTitle >= px(260);
        if (!showCat) colArtist = colTime - px(14) - px(200);
        int artistEnd = showCat ? colCat : colTime;
        int hdrY = hy + (px(40) - capH(14)) / 2;
        text(g, "#", numX + (numW - tw("#", false, 14)) / 2, hdrY, th.dim(), false, 14);
        text(g, "Title", colTitle, hdrY, th.dim(), false, 14);
        text(g, "Artist", colArtist, hdrY, th.dim(), false, 14);
        if (showCat) text(g, "Category", colCat, hdrY, th.dim(), false, 14);
        textRight(g, "Time", colTime + px(56), hdrY, th.dim(), false, 14);

        for (int i = 0; i < shown.size(); i++) {
            VanillaTrack t = shown.get(i);
            int ry = y + b2 + px(40) + i * rowH;
            if (ry + rowH < top || ry > bodyBottom) continue;
            boolean playing = now.mc && now.active && t.id().equals(now.path);
            boolean liked = isLiked(t.id());
            int visTop = Math.max(ry, top);
            boolean hov = hit(mx, my, innerL, visTop, innerR - innerL - px(52), Math.min(ry + rowH, bodyBottom) - visTop, () -> play(shown, t));
            if (playing) g.fill(innerL, ry, innerR, ry + rowH, 0xFF2F6A21);
            else if (hov) g.fill(innerL, ry, innerR, ry + rowH, th.rowHover());
            g.fill(innerL, ry, innerR, ry + b2, th.sep());
            int textCol = playing ? 0xFFFFFFFF : th.text();
            int dimCol = playing ? 0xFFD4F0C8 : th.dim();
            int mid = ry + b2 + (rowH - b2) / 2;
            if (playing) {
                miniBars(g, now, numX + (numW - px(18)) / 2, mid - px(9), px(18), 0xFF8CE06A);
            } else {
                String num = String.valueOf(i + 1);
                text(g, num, numX + (numW - tw(num, false, 16)) / 2, mid - capH(16) / 2, th.dim(), false, 16);
            }
            int cc = CAT_COLORS[Math.max(0, java.util.Arrays.asList(CATS).indexOf(categoryOf(t)))];
            g.fill(thumbX, mid - px(18), thumbX + px(36), mid + px(18), th.edge());
            String rowCover = Ports.art().builtInArt(t.coverKey());
            if (rowCover != null) {
                g.blit(rowCover, thumbX + b2, mid - px(18) + b2, thumbX + px(36) - b2, mid + px(18) - b2, 0f, 1f, 0f, 1f);
            } else {
                g.fill(thumbX + b2, mid - px(18) + b2, thumbX + px(36) - b2, mid + px(18) - b2, cc);
            }
            text(g, fit(t.title(), colArtist - colTitle - px(12), false, 16), colTitle, mid - capH(16) / 2, textCol, false, 16);
            text(g, fit(t.artist(), artistEnd - colArtist - px(12), false, 16), colArtist, mid - capH(16) / 2, dimCol, false, 16);
            String cn = categoryOf(t);
            if (showCat) text(g, cn.equals("Discs") ? "Music discs" : cn, colCat, mid - capH(14) / 2, dimCol, false, 14);
            textRight(g, t.durationText(), colTime + px(56), mid - capH(14) / 2, dimCol, false, 14);
            int hx = innerR - px(12) - px(40);
            int lc = liked ? th.like() : dimCol;
            tip(liked ? "Remove from liked songs" : "Like");
            hit(mx, my, hx, Math.max(mid - px(20), top), px(40), px(40), () -> toggleLike(t.id()));
            icon(g, ICON_HEART, 8, 7, hx + px(10), mid - px(10), px(20), lc);
        }

        y += listH + px(32);
        g.disableScissor();

        int contentH = (y + scrollY) - top;
        scrollMax = Math.max(0, contentH - (bodyBottom - top));
        scrollY = Math.max(0, Math.min(scrollMax, scrollY));
        scrollbarAt(g, mainRight - px(16), top, bodyBottom);

        if (!compact) renderAside(g, mx, my, now, mainRight, top, bodyBottom, asideW);
        renderFooter(g, mx, my, now, bodyBottom, footH);
    }
}
