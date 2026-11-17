package com.musicplayer.ore;

import com.musicplayer.port.*;
import com.musicplayer.ui.*;

import com.musicplayer.SongInfo;

import java.util.List;

/**
 * Base for the screens that show the big player on the right and the now-playing bar at the bottom
 * (playlists and the Minecraft music tab). The compact view is shared and remembered between openings.
 */
public abstract class OrePlayerScreen extends OreScreen {

    protected static boolean compact = false;

    protected OrePlayerScreen(String title) {
        super(title);
    }

    protected void renderAside(Gfx g, int mx, int my, Now n, int ax, int top, int bottom, int asideW) {
        g.enableScissor(ax, top, this.width, bottom);
        int b2 = px(2);
        int x = ax, w = asideW - px(24);
        int y = top + px(24);
        int pad = px(20);
        int art = w - 2 * pad;
        int ch = pad + art + px(16) + px(30) + px(4) + px(20) + pad;
        card(g, x, y, w, ch);
        int glow = n.path == null || n.path.isEmpty() ? 0x4A7FB5 : hashColor(n.path) & 0xFFFFFF;
        vGradient(g, x + b2, y + b2, w - 2 * b2, ch - 2 * b2 - px(4), glow, th.light() ? 0.30f : 0.40f, 0.6f);
        int axx = x + pad, ayy = y + pad;
        g.fill(axx - px(2), ayy - px(2), axx + art + px(2), ayy + art + px(2), th.hi());
        g.fill(axx, ayy, axx + art, ayy + art, th.edge());
        g.blit(n.art, axx + px(4), ayy + px(4), axx + art - px(4), ayy + art - px(4), 0f, 1f, 0f, 1f);
        int ty = ayy + art + px(16);
        int likeW = px(40);
        String title = n.title.isEmpty() ? "Nothing playing" : n.title;
        text(g, fit(title, art - likeW - px(12), true, 24), axx, ty, th.text(), true, 24);
        text(g, fit(n.artist, art - likeW - px(12), false, 16), axx, ty + px(30) + px(4), th.dim(), false, 16);
        boolean lk = isLiked(n.path);
        tip(lk ? "Remove from liked songs" : "Like");
        boolean hl = hit(mx, my, axx + art - likeW, ty, likeW, likeW, () -> toggleLike(n.path));
        if (hl) g.fill(axx + art - likeW, ty, axx + art, ty + likeW, th.rowHover());
        icon(g, ICON_HEART, 8, 7, axx + art - likeW + px(8), ty + px(8), px(24), lk ? th.like() : th.dim());

        // next in queue
        int qy = y + ch + px(16);
        int qh = px(16) + capH(16) + px(12) + px(44) + px(16) + b2 + px(4);
        card(g, x, qy, w, qh);
        text(g, "Next in queue", x + pad, qy + px(16), th.text(), true, 16);
        String open = "Open queue";
        if (tw("Next in queue", true, 16) + px(12) + tw(open, false, 14) > w - 2 * pad) open = "Open";
        if (tw("Next in queue", true, 16) + px(12) + tw(open, false, 14) > w - 2 * pad) open = "";
        int ow = tw(open, false, 14);
        boolean ho = hit(mx, my, x + w - pad - ow, qy + px(12), ow, px(28), () -> UiHost.get().open(new OreQueueScreen(this)));
        text(g, open, x + w - pad - ow, qy + px(16) + (capH(16) - capH(14)) / 2, ho ? th.text() : th.dim(), false, 14);
        List<SongInfo> q = manager.getUpNextQueue(1);
        if (!q.isEmpty()) {
            SongInfo nx = q.get(0);
            int ry = qy + px(16) + capH(16) + px(12);
            g.fill(x + pad, ry, x + pad + px(44), ry + px(44), th.edge());
            g.blit(coverFor(nx), x + pad + b2, ry + b2, x + pad + px(44) - b2, ry + px(44) - b2, 0f, 1f, 0f, 1f);
            int tx = x + pad + px(44) + px(12);
            int mw = x + w - pad - tx;
            text(g, fit(nx.getTitle(), mw, false, 16), tx, ry + px(22) - capH(16) - px(1), th.text(), false, 16);
            text(g, fit(nx.getArtist(), mw, false, 14), tx, ry + px(22) + px(3), th.dim(), false, 14);
        }
        g.disableScissor();
    }

    protected void renderFooter(Gfx g, int mx, int my, Now n, int fy, int fh) {
        int b2 = px(2);
        g.fill(0, fy, this.width, this.height, th.edge());
        g.fill(0, fy + b2, this.width, this.height, th.panel());
        g.fill(0, fy + b2, this.width, fy + b2 + b2, th.hi());
        int pad = px(24);
        int sideW = px(300);

        // left: art + title
        int ts = px(52);
        int ty0 = fy + (fh - ts) / 2;
        g.fill(pad, ty0, pad + ts, ty0 + ts, th.edge());
        g.blit(n.art, pad + b2, ty0 + b2, pad + ts - b2, ty0 + ts - b2, 0f, 1f, 0f, 1f);
        int tx = pad + ts + px(14);
        int tmax = pad + sideW - tx;
        String title = n.title.isEmpty() ? "Nothing playing" : n.title;
        text(g, fit(title, tmax, true, 16), tx, ty0 + px(8), th.text(), true, 16);
        text(g, fit(n.artist, tmax, false, 14), tx, ty0 + px(8) + px(26), th.dim(), false, 14);

        // centre: transport + progress
        int cl = pad + sideW + px(24), cr = this.width - pad - sideW - px(24);
        int cw = cr - cl;
        int rowTop = fy + (fh - px(62)) / 2;
        int b44 = px(44), bh = px(40), pw = px(64), gap = px(8);
        int tw3 = b44 * 2 + pw + gap * 2;
        int bx = cl + (cw - tw3) / 2;
        tip("Previous");
        button(g, mx, my, bx, rowTop, b44, bh, NORMAL, manager::previousTrack);
        iconBtn(g, ICON_PREV, 10, 10, bx, rowTop, b44, bh, px(20), th.text());
        bx += b44 + gap;
        boolean playing = n.active && !n.paused;
        tip(playing ? "Pause" : "Play");
        button(g, mx, my, bx, rowTop, pw, bh, LIGHT, () -> {
            if (manager.getCurrentSong() != null || n.mc) manager.togglePlayPause();
        });
        iconBtn(g, playing ? ICON_PAUSE : ICON_PLAY, 10, 10, bx, rowTop, pw, bh, px(20), 0xFF1E1E1F);
        bx += pw + gap;
        tip("Next");
        button(g, mx, my, bx, rowTop, b44, bh, NORMAL, manager::nextTrack);
        iconBtn(g, ICON_NEXT, 10, 10, bx, rowTop, b44, bh, px(20), th.text());

        int py = rowTop + bh + px(6);
        int tcol = px(40);
        String cur = time(n.posMs), dur = time(n.durMs);
        text(g, cur, cl + tcol - tw(cur, false, 14), py + (px(12) - capH(14)) / 2, th.dim(), false, 14);
        text(g, dur, cr - tcol + px(12), py + (px(12) - capH(14)) / 2, th.dim(), false, 14);
        sliderTrack(g, cl + tcol + px(12), py, cw - 2 * (tcol + px(12)), px(12), n.progress(), f -> {
            if (n.active) manager.seek((float) f);
        });

        // right: volume + view toggle
        int rx = this.width - pad;
        int vbtn = px(44);
        tip(compact ? "Open the full player" : "Switch to the compact view");
        rx -= vbtn;
        button(g, mx, my, rx, fy + (fh - bh) / 2, vbtn, bh, NORMAL, () -> { compact = !compact; scrollY = 0; });
        iconBtn(g, compact ? ICON_EXPAND : ICON_MINI, compact ? 11 : 12, compact ? 11 : 11, rx, fy + (fh - bh) / 2, vbtn, bh, px(20), th.text());
        rx -= px(12);
        int vw = px(110);
        rx -= vw;
        sliderTrack(g, rx, fy + (fh - px(12)) / 2, vw, px(12), manager.getVolume(), f -> manager.setVolume((float) f));
        icon(g, ICON_SPEAKER, 8, 8, rx - px(12) - px(20), fy + (fh - px(20)) / 2, px(20), th.dim());
    }
}
