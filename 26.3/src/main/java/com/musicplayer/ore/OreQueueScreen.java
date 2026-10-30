package com.musicplayer.ore;

import com.musicplayer.SongInfo;
import com.musicplayer.VanillaTrack;
import com.musicplayer.player.SoundtrackRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.Locale;

/** The play queue, from the design's Queue.dc.html. */
public class OreQueueScreen extends OreScreen {

    private final Screen parent;

    public OreQueueScreen(Screen parent) {
        super("Up next");
        this.parent = parent;
    }

    @Override
    protected int activeTab() { return -1; }

    private void play(SongInfo si) {
        List<SongInfo> pl = manager.getPlaylist();
        for (int i = 0; i < pl.size(); i++) {
            if (pl.get(i).getPath().equals(si.getPath())) {
                manager.playSong(i);
                return;
            }
        }
        if (manager.getCurrentSource() == com.musicplayer.MusicPlayerManager.SongSource.MINECRAFT) {
            manager.playVanillaTrackById(si.getPath());
        }
    }

    private static String modeLabel(String name) {
        String s = name.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String durationOf(SongInfo si) {
        for (VanillaTrack vt : SoundtrackRegistry.getInstance().getAllTracks()) {
            if (vt.id().equals(si.getPath())) return vt.durationText();
        }
        return "";
    }

    @Override
    protected void renderBody(GuiGraphicsExtractor g, int mx, int my) {
        Now now = now();
        List<SongInfo> queue = manager.getUpNextQueue(20);
        int top = px(64);
        int left = px(32), right = this.width - px(32);
        int b2 = px(2);
        int y = top + px(24) - scrollY;

        g.enableScissor(0, top, this.width, this.height);

        // ---- title row
        tip("Back");
        button(g, mx, my, left, y + px(5), px(48), px(48), NORMAL, () -> this.minecraft.gui.setScreen(parent));
        iconBtn(g, ICON_BACK, 10, 10, left, y + px(5), px(48), px(48), px(22), th.text());
        String mode = modeLabel(manager.getConfig().getPlaybackMode().name());
        int mw = tw(mode, false, 14) + px(20);
        int mh = capH(14) + px(16);
        int mxx = right - mw;
        g.fill(mxx, y + px(14), mxx + mw, y + px(14) + mh, th.edge());
        g.fill(mxx + b2, y + px(14) + b2, mxx + mw - b2, y + px(14) + mh - b2, th.inset());
        text(g, mode, mxx + px(10), y + px(14) + (mh - capH(14)) / 2, th.text(), false, 14);
        int tx = left + px(48) + px(16);
        text(g, "Up next", tx, y, th.text(), true, 32);
        String sub = queue.isEmpty() ? "Nothing queued" : "Next " + queue.size() + (queue.size() == 1 ? " song" : " songs");
        text(g, sub, tx, y + px(42), th.dim(), false, 16);
        y += px(58) + px(20);

        // ---- now playing card
        int nw = px(340);
        int art = px(180);
        int nh = px(20) + capH(14) + px(14) + art + px(14) + capH(24) + px(4) + capH(16) + px(20) + b2 + px(4);
        card(g, left, y, nw, nh);
        vGradient(g, left + b2, y + b2, nw - 2 * b2, nh - 2 * b2 - px(4), hashColor(now.path) & 0xFFFFFF, 0.4f, 0.7f);
        int cy = y + px(20);
        text(g, "Now playing", left + px(20), cy, th.dim(), false, 14);
        cy += capH(14) + px(14);
        int ax = left + (nw - art) / 2;
        g.fill(ax - b2, cy - b2, ax + art + b2, cy + art + b2, th.hi());
        g.fill(ax, cy, ax + art, cy + art, th.edge());
        int bd = px(4);
        g.blit(now.art, ax + bd, cy + bd, ax + art - bd, cy + art - bd, 0f, 1f, 0f, 1f);
        cy += art + px(14);
        String nt = fit(now.active || !now.title.isEmpty() ? now.title : "Nothing playing", nw - px(40), true, 24);
        text(g, nt, left + (nw - tw(nt, true, 24)) / 2, cy, th.text(), true, 24);
        cy += capH(24) + px(4);
        String na = fit(now.artist, nw - px(40), false, 16);
        text(g, na, left + (nw - tw(na, false, 16)) / 2, cy, th.dim(), false, 16);

        // ---- queue card
        int qx = left + nw + px(20), qw = right - qx;
        int rowH = px(56);
        int listH = queue.isEmpty() ? px(8) + px(80) * 2 + px(60) + px(8) : px(8) + rowH * queue.size() + px(14) + capH(14) + px(14) + px(8);
        int qh = Math.max(listH, nh);
        card(g, qx, y, qw, qh);
        int innerL = qx + b2, innerR = qx + qw - b2;
        if (queue.isEmpty()) {
            String t1 = "End of playlist", t2 = "Nothing is queued after this song.";
            int ey = y + qh / 2 - px(30);
            text(g, t1, qx + (qw - tw(t1, true, 20)) / 2, ey, th.text(), true, 20);
            text(g, t2, qx + (qw - tw(t2, false, 16)) / 2, ey + capH(20) + px(12), th.dim(), false, 16);
        } else {
            int ry = y + px(8);
            int posW = px(44);
            for (int i = 0; i < queue.size(); i++) {
                SongInfo si = queue.get(i);
                int rowY = ry + i * rowH;
                if (rowY + rowH < top || rowY > this.height) continue;
                boolean hov = hit(mx, my, innerL, rowY, innerR - innerL, rowH, () -> play(si));
                if (hov) g.fill(innerL, rowY, innerR, rowY + rowH, th.rowHover());
                if (i > 0) g.fill(innerL, rowY, innerR, rowY + b2, th.sep());
                int mid = rowY + rowH / 2;
                text(g, "+" + (i + 1), innerL + px(16), mid - capH(16) / 2, th.accent(), true, 16);
                int thumbX = innerL + px(16) + posW;
                g.fill(thumbX, mid - px(18), thumbX + px(36), mid + px(18), th.edge());
                g.blit(coverFor(si), thumbX + b2, mid - px(18) + b2, thumbX + px(36) - b2, mid + px(18) - b2, 0f, 1f, 0f, 1f);
                int textX = thumbX + px(36) + px(12);
                String dur = durationOf(si);
                int durW = dur.isEmpty() ? 0 : tw(dur, false, 14) + px(16);
                int maxW = innerR - px(16) - durW - textX;
                text(g, fit(si.getTitle(), maxW, false, 16), textX, mid - capH(16) - px(1), th.text(), false, 16);
                text(g, fit(si.getArtist() == null ? "" : si.getArtist(), maxW, false, 14), textX, mid + px(3), th.dim(), false, 14);
                if (!dur.isEmpty()) textRight(g, dur, innerR - px(16), mid - capH(14) / 2, th.dim(), false, 14);
            }
            int ny = ry + rowH * queue.size();
            g.fill(innerL, ny, innerR, ny + b2, th.sep());
            text(g, "Click a song to play it now.", innerL + px(20), ny + b2 + px(14), th.dim(), false, 14);
        }
        y += qh + px(32);
        g.disableScissor();

        scrollMax = Math.max(0, (y + scrollY) - top - (this.height - top));
        scrollY = Math.max(0, Math.min(scrollMax, scrollY));
        scrollbar(g, top, this.height);
    }
}
