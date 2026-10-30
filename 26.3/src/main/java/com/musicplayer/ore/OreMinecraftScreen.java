package com.musicplayer.ore;

import com.musicplayer.AlbumArtManager;
import com.musicplayer.MusicPlayerManager;
import com.musicplayer.PlayableTrack;
import com.musicplayer.VanillaTrack;
import com.musicplayer.config.MusicPlayerConfig;
import com.musicplayer.player.SoundtrackRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Minecraft music screen, from the design's Minecraft.dc.html. */
public class OreMinecraftScreen extends OreScreen {

    private static final String[] CATS = {"All", "Overworld", "Creative", "Nether", "End", "Menu", "Discs"};
    private static final int[] CAT_COLORS = {0, 0xFF5B8F3A, 0xFF4A7FB5, 0xFFA63C3C, 0xFF8A5FB0, 0xFFC9B13A, 0xFFC97A3A};
    private static final float[][] DECO = {
            {0, 0, .28f}, {0, 64, .10f}, {0, 96, .18f}, {32, 32, .18f}, {32, 128, .10f}, {64, 0, .10f}, {64, 64, .20f}};

    private String cat = "All";
    private int scroll = 0;
    private int maxScroll = 0;

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
        SoundtrackRegistry reg = SoundtrackRegistry.getInstance();
        if (reg.getAllTracks().isEmpty()) reg.discoverTracks();
        return reg.getAllTracks();
    }

    private void play(VanillaTrack t) {
        if (manager.getCurrentSource() != MusicPlayerManager.SongSource.MINECRAFT) {
            manager.setCurrentSource(MusicPlayerManager.SongSource.MINECRAFT);
        }
        manager.playVanillaTrackById(t.id());
    }

    @Override
    protected void renderBody(GuiGraphicsExtractor g, int mx, int my) {
        MusicPlayerConfig cfg = manager.getConfig();
        List<VanillaTrack> all = allTracks();
        List<VanillaTrack> shown = new ArrayList<>();
        for (VanillaTrack t : all) if (cat.equals("All") || categoryOf(t).equals(cat)) shown.add(t);
        Now now = now();

        int top = px(64);
        int left = px(32), w = this.width - px(64);
        int y = top + px(28) - scroll;
        int b2 = px(2);

        g.enableScissor(0, top, this.width, this.height);

        // ---- hero
        int hh = px(168);
        card(g, left, y, w, hh);
        hGradient(g, left + b2, y + b2, (int) ((w - 2 * b2) * 0.72f), hh - 2 * b2 - px(4),
                0x3C8527, th.light() ? 0.22f : 0.40f);
        deco(g, left + w - b2, y + b2, 0x52A535, DECO);
        int cover = px(120), cx = left + px(24), cy = y + px(24);
        g.fill(cx - px(6), cy - px(6), cx + cover + px(6), cy + cover + px(6), th.hi());
        g.fill(cx - px(4), cy - px(4), cx + cover + px(4), cy + cover + px(4), th.edge());
        Identifier mcArt = AlbumArtManager.getBuiltInArt("minecraft");
        g.blit(mcArt != null ? mcArt : AlbumArtManager.DEFAULT_ALBUM_ART, cx, cy, cx + cover, cy + cover, 0f, 1f, 0f, 1f);

        int tx = cx + cover + px(24);
        text(g, "Minecraft music", tx, y + px(24), th.text(), true, 32);
        text(g, "The in-game soundtrack · " + all.size() + " songs", tx, y + px(24) + px(42), th.dim(), false, 16);

        int by = y + px(96);
        boolean visibleBtn = by + px(52) > top;
        Runnable playAll = () -> { if (!shown.isEmpty()) play(shown.get(0)); };
        Runnable shuffle = () -> { if (!shown.isEmpty()) play(shown.get(new Random().nextInt(shown.size()))); };
        int paW = px(148);
        tip("Play all");
        button(g, mx, my, tx, by, paW, px(52), GREEN, visibleBtn ? playAll : null);
        {
            String label = "Play all";
            int iw = px(22), contentW = iw + px(12) + tw(label, true, 16);
            int ix = tx + (paW - contentW) / 2;
            icon(g, ICON_PLAY, 10, 10, ix, by + px(26) - px(2) - iw / 2 + pressDy, iw, 0xFFFFFFFF);
            text(g, label, ix + iw + px(12), by + px(26) - px(2) - capH(16) / 2 + pressDy, 0xFFFFFFFF, true, 16);
        }
        // 20px padding on both sides, 10px between icon and label (as in the design)
        int shW = px(20) + px(22) + px(10) + tw("Shuffle", false, 16) + px(20);
        int sx0 = tx + paW + px(10);
        tip("Play a random song");
        button(g, mx, my, sx0, by, shW, px(52), NORMAL, visibleBtn ? shuffle : null);
        icon(g, ICON_SHUFFLE, 10, 10, sx0 + px(20), by + px(26) - px(2) - px(11) + pressDy, px(22), th.text());
        text(g, "Shuffle", sx0 + px(20) + px(22) + px(10), by + px(26) - px(2) - capH(16) / 2 + pressDy, th.text(), false, 16);

        // mute switch (maps to "suppress vanilla music")
        int swW = px(88), swH = px(36);
        int swX = left + w - px(24) - px(12) - swW, swY = y + (hh - swH) / 2;
        // the label column is as wide as the title needs, and the description wraps inside it
        int labelW = Math.max(px(198), tw("Mute Minecraft music", true, 16) + px(4));
        int lx = swX - px(14) - labelW;
        List<String> desc = wrap("Silences the game's own tracks while Melodix plays", labelW, false, 14);
        int lineH = capH(14) + px(6);
        int blockH = capH(16) + px(6) + desc.size() * lineH;
        int ly = y + (hh - blockH) / 2;
        text(g, "Mute Minecraft music", lx, ly, th.text(), true, 16);
        for (int i = 0; i < desc.size(); i++) {
            text(g, desc.get(i), lx, ly + capH(16) + px(6) + i * lineH, th.dim(), false, 14);
        }
        boolean muted = cfg.isSuppressVanillaMusic();
        float mv = smooth(anim("mute", muted ? 1f : 0f, 9f));
        g.fill(swX, swY, swX + swW, swY + swH, th.edge());
        g.fill(swX + b2, swY + b2, swX + swW - b2, swY + swH - b2, lerpColor(th.inset(), OreTheme.GREEN, mv));
        int knobW = px(46);
        int kx = Math.round((swX - b2) + mv * ((swX + swW - knobW + b2) - (swX - b2)));
        button(g, -1, -1, kx, swY - b2, knobW, swH + b2 * 2, LIGHT, null);
        icon(g, new int[][]{{3, 1, 2, 6}}, 8, 8, kx + (knobW - px(16)) / 2, swY - b2 + (swH + 2 * b2 - px(4)) / 2 - px(8), px(16), 0xFF1E1E1F);
        tip(muted ? "Unmute Minecraft music" : "Mute Minecraft music");
        hit(mx, my, swX - b2, swY - b2, swW + 2 * b2, swH + 2 * b2, () -> cfg.setSuppressVanillaMusic(!cfg.isSuppressVanillaMusic()));

        y += hh + px(20);

        // ---- category chips
        int cxp = left;
        for (int i = 0; i < CATS.length; i++) {
            String name = CATS[i];
            String label = name.equals("Discs") ? "Music discs" : name;
            int count = name.equals("All") ? all.size() : (int) all.stream().filter(t -> categoryOf(t).equals(name)).count();
            String num = String.valueOf(count);
            int cw = tw(label, false, 14) + px(8) + tw(num, false, 14) + px(32);
            boolean sel = cat.equals(name);
            Runnable pick = () -> { cat = name; scroll = 0; };
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
        int hdrY = hy + (px(40) - capH(14)) / 2;
        text(g, "#", numX + (numW - tw("#", false, 14)) / 2, hdrY, th.dim(), false, 14);
        text(g, "Title", colTitle, hdrY, th.dim(), false, 14);
        text(g, "Artist", colArtist, hdrY, th.dim(), false, 14);
        text(g, "Category", colCat, hdrY, th.dim(), false, 14);
        textRight(g, "Time", colTime + px(56), hdrY, th.dim(), false, 14);

        for (int i = 0; i < shown.size(); i++) {
            VanillaTrack t = shown.get(i);
            int ry = y + b2 + px(40) + i * rowH;
            if (ry + rowH < top || ry > this.height) continue;
            boolean playing = now.mc && now.active && t.id().equals(now.path);
            boolean liked = isLiked(t.id());
            int visTop = Math.max(ry, top);
            boolean hov = hit(mx, my, innerL, visTop, innerR - innerL - px(52), rowH - (visTop - ry), () -> play(t));
            if (playing) g.fill(innerL, ry, innerR, ry + rowH, 0xFF2F6A21);
            else if (hov) g.fill(innerL, ry, innerR, ry + rowH, th.rowHover());
            g.fill(innerL, ry, innerR, ry + b2, th.sep());
            int textCol = playing ? 0xFFFFFFFF : th.text();
            int dimCol = playing ? 0xFFD4F0C8 : th.dim();
            int mid = ry + b2 + (rowH - b2) / 2;
            if (playing) {
                icon(g, ICON_BARS, 8, 8, numX + (numW - px(18)) / 2, mid - px(9), px(18), 0xFF8CE06A);
            } else {
                String num = String.valueOf(i + 1);
                text(g, num, numX + (numW - tw(num, false, 16)) / 2, mid - capH(16) / 2, th.dim(), false, 16);
            }
            int cc = CAT_COLORS[Math.max(0, java.util.Arrays.asList(CATS).indexOf(categoryOf(t)))];
            g.fill(thumbX, mid - px(18), thumbX + px(36), mid + px(18), th.edge());
            Identifier rowCover = AlbumArtManager.getBuiltInArt(t.coverKey());
            if (rowCover != null) {
                g.blit(rowCover, thumbX + b2, mid - px(18) + b2, thumbX + px(36) - b2, mid + px(18) - b2, 0f, 1f, 0f, 1f);
            } else {
                g.fill(thumbX + b2, mid - px(18) + b2, thumbX + px(36) - b2, mid + px(18) - b2, cc);
            }
            text(g, fit(t.title(), colArtist - colTitle - px(12), false, 16), colTitle, mid - capH(16) / 2, textCol, false, 16);
            text(g, fit(t.artist(), colCat - colArtist - px(12), false, 16), colArtist, mid - capH(16) / 2, dimCol, false, 16);
            String cn = categoryOf(t);
            text(g, cn.equals("Discs") ? "Music discs" : cn, colCat, mid - capH(14) / 2, dimCol, false, 14);
            textRight(g, t.durationText(), colTime + px(56), mid - capH(14) / 2, dimCol, false, 14);
            int hx = innerR - px(12) - px(40);
            int lc = liked ? th.like() : dimCol;
            tip(liked ? "Remove from liked songs" : "Like");
            hit(mx, my, hx, Math.max(mid - px(20), top), px(40), px(40), () -> toggleLike(t.id()));
            icon(g, ICON_HEART, 8, 7, hx + px(10), mid - px(10), px(20), lc);
        }

        y += listH + px(32);
        g.disableScissor();

        int contentH = (y + scroll) - top;
        maxScroll = Math.max(0, contentH - (this.height - top));
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        if (maxScroll > 0) { // scrollbar
            int trackX = this.width - px(16), trackH = this.height - top;
            g.fill(trackX, top, this.width, this.height, th.edge());
            g.fill(trackX + b2, top, this.width, this.height, th.inset());
            int thumbH = Math.max(px(40), trackH * trackH / (trackH + maxScroll));
            int thumbY = top + (trackH - thumbH) * scroll / maxScroll;
            button(g, -1, -1, trackX, thumbY, px(16), thumbH, LIGHT, null);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.round(sy * px(56))));
        return true;
    }
}
