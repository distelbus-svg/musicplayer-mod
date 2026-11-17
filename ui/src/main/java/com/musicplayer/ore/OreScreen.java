package com.musicplayer.ore;

import com.musicplayer.port.*;
import com.musicplayer.ui.*;

import com.musicplayer.MelodixApi;
import com.musicplayer.MusicPlayerManager;
import com.musicplayer.SongInfo;
import com.musicplayer.VanillaTrack;
import com.musicplayer.config.MusicPlayerConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleConsumer;

/**
 * Base for the OreUI redesign screens. Layout is expressed in the design's 1280x720 CSS pixels
 * and scaled with {@link #px(float)}. Text uses Minecraft's own font at whole-number scales so
 * it stays crisp, and icons are drawn as pixel rects at whole-number unit sizes.
 * Rendering is immediate-mode: every frame the screen re-registers its click regions.
 */
public abstract class OreScreen implements UiScreen {

    /** What the version-specific screen wrapper provides to a shared screen. */
    public interface Host {
        void addField(UiTextField field);
        /** Focuses the field; null clears the focus. */
        void focusField(UiTextField field);
        boolean anyFieldFocused();
        /** Draws the text fields (real game widgets) at this point of the frame. */
        void drawFields();
    }

    private Host host;
    /** Set by the wrapper before init(). */
    public int width, height;
    private final String title;

    protected static final int NORMAL = 0, GREEN = 1, LIGHT = 2;

    private static final class Hit {
        int x, y, w, h;
        Runnable click;
        DoubleConsumer slider;
        String tip;
        boolean noHand;
        boolean vertical;
        int key() { return ((x * 31 + y) * 31 + w) * 31 + h; }
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    protected final MusicPlayerManager manager = MusicPlayerManager.getInstance();
    protected OreTheme th;
    protected float s = 0.5f;
    protected UiTextField search;

    private final List<Hit> hits = new ArrayList<>();
    private Hit activeSlider;
    private Hit pressedHit;
    private int pressedKey;
    private String nextTip;
    private boolean nextPlain;
    private int hoverKey;
    private long hoverSince;
    private long lastNs;
    private final Map<Object, Float> anims = new HashMap<>();

    /** Seconds since the previous frame (capped), for animations. */
    protected float dt;
    /** Vertical content shift (screen px) of the button drawn last, while it is pressed. */
    protected int pressDy;
    /** Shared vertical scroll state for screens with one scrolling body. */
    protected int scrollY, scrollMax;

    protected OreScreen(String title) {
        this.title = title;
    }

    public String title() { return title; }

    // ---------------------------------------------------------------- host plumbing

    public final void attach(Host h) { this.host = h; }

    public final void onInit(int w, int h) {
        this.width = w;
        this.height = h;
        init();
    }

    public void onClose() { UiHost.get().open(null); }

    protected void focusField(UiTextField f) { if (host != null) host.focusField(f); }

    protected UiTextField newField(String hint, int maxLen) {
        UiTextField e = UiHost.get().newTextField(hint, maxLen);
        e.setTextColor(th.text());
        if (host != null) host.addField(e);
        return e;
    }

    // ---------------------------------------------------------------- layout helpers

    protected int px(float designPx) {
        return Math.max(1, Math.round(designPx * s));
    }

    protected final void computeScale() {
        s = Math.max(0.3f, Math.min(this.width / 1280f, this.height / 720f));
        th = OreTheme.of(manager.getConfig().getThemeId());
    }

    protected abstract int activeTab(); // 0 home, 1 minecraft, 2 playlists, -1 none (or an addon tab)

    /** Id of the addon tab (see {@link MelodixApi#registerTab}) this screen belongs to, or null. */
    protected String activeCustomTab() { return null; }

    protected abstract void renderBody(Gfx g, int mx, int my);

    protected void init() {
        computeScale();
        int hh = px(64);
        int sw = px(320), sh = px(44);
        int sx = this.width - px(24) - px(44) - px(8) - sw;
        String prev = search != null ? search.getValue() : "";
        int sy = (hh - px(2) - sh) / 2 + px(2);
        // borderless UiTextField draws its text at its top edge, so size it to the font and centre it by hand
        search = newField("Search", 64);
        search.setBounds(sx + px(46), sy + (sh - 7) / 2, sw - px(46) - px(48));
        search.setTextColor(th.text());
        boolean roomy = UiHost.get().textWidth("Search songs, artists", false) <= sw - px(46) - px(48);
        search.setHint(roomy ? "Search songs, artists" : "Search");
        search.setValue(prev);
        search.setResponder(v -> {
            if (liveSearch() && !v.isBlank()) UiHost.get().open(new OreSearchScreen(v).from(this));
        });
    }

    public void onRender(Gfx g, int mx, int my, float pt) {
        computeScale();
        if (pendingDialog != null && --pendingFrames <= 0) {
            Runnable r = pendingDialog;
            pendingDialog = null;
            r.run();
        }
        long nowNs = System.nanoTime();
        dt = lastNs == 0 ? 0f : Math.min(0.05f, (nowNs - lastNs) / 1e9f);
        lastNs = nowNs;
        hits.clear();
        nextTip = null;
        nextPlain = false;
        g.fill(0, 0, this.width, this.height, th.bg());
        renderBody(g, mx, my);
        renderHeader(g, mx, my);
        search.setVisible(!modal());
        if (modal()) hits.clear();
        renderOverlay(g, mx, my);
        Hit top = topHit(mx, my);
        if (top != null && !top.noHand) g.handCursor();
        if (host != null) host.drawFields();
        renderTooltip(g, mx, my, top, nowNs);
    }

    private Runnable pendingDialog;
    private int pendingFrames;

    /**
     * Native file dialogs don't work over a fullscreen window. When fullscreen, leaves it and re-runs
     * {@code retry} a moment later; returns true if the caller should stop now.
     */
    protected boolean blockedByFullscreen(Runnable retry) {
        if (!UiHost.get().fullscreen()) return false;
        com.musicplayer.hud.NotificationManager.showSystem("Leaving fullscreen to open the file browser");
        UiHost.get().setFullscreen(false);
        pendingDialog = retry;
        pendingFrames = 45;
        return true;
    }

    /** True while a dialog is open: everything registered before the overlay stops receiving clicks. */
    protected boolean modal() { return false; }

    /** Drawn after the header and body (dialogs, popups). */
    protected void renderOverlay(Gfx g, int mx, int my) { }

    private Hit topHit(int mx, int my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).contains(mx, my)) return hits.get(i);
        }
        return null;
    }

    private void renderTooltip(Gfx g, int mx, int my, Hit top, long nowNs) {
        if (top == null || top.tip == null) {
            hoverKey = 0;
            hoverSince = 0;
            return;
        }
        int key = top.key();
        if (hoverSince == 0 || key != hoverKey) {
            hoverKey = key;
            hoverSince = nowNs;
            return;
        }
        if (nowNs - hoverSince < 500_000_000L) return;
        int w = tw(top.tip, false, 14) + px(16), h = capH(14) + px(12);
        int x = Math.max(0, Math.min(this.width - w, mx + px(12)));
        int y = my + px(22);
        if (y + h > this.height) y = my - h - px(6);
        g.nextStratum();
        g.fill(x, y, x + w, y + h, th.edge());
        g.fill(x + px(2), y + px(2), x + w - px(2), y + h - px(2), th.panel());
        g.fill(x + px(2), y + px(2), x + w - px(2), y + px(4), th.hi());
        text(g, top.tip, x + px(8), y + (h - capH(14)) / 2, th.text(), false, 14);
    }

    // ---------------------------------------------------------------- header

    protected void renderHeader(Gfx g, int mx, int my) {
        int hh = px(64);
        g.fill(0, 0, this.width, hh, th.edge());
        g.fill(0, 0, this.width, hh - px(2), th.panel());
        g.fill(0, 0, this.width, px(2), th.hi());

        int x = px(24);
        button(g, mx, my, x, px(14), px(36), px(36), GREEN, null);
        iconBtn(g, ICON_NOTE, 12, 12, x, px(14), px(36), px(36), px(20), 0xFFFFFFFF);
        x += px(36) + px(12);
        textMid(g, "Melodix", x, px(2), hh - px(2), th.text(), true, 20);
        x += tw("Melodix", true, 20) + px(32);

        List<MelodixApi.Tab> custom = MelodixApi.tabs();
        String[] tabs = new String[3 + custom.size()];
        tabs[0] = "Home";
        tabs[1] = "Minecraft";
        tabs[2] = "Playlists";
        for (int c = 0; c < custom.size(); c++) tabs[3 + c] = custom.get(c).label();
        for (int i = 0; i < tabs.length; i++) {
            int w = tw(tabs[i], false, 16) + px(40);
            final int idx = i;
            boolean current = i < 3 ? i == activeTab() : custom.get(i - 3).id().equals(activeCustomTab());
            if (current) {
                button(g, mx, my, x, px(10), w, px(44), GREEN, null);
                textBtn(g, tabs[i], false, 16, x, px(10), w, px(44), 0xFFFFFFFF);
            } else {
                boolean hov = hit(mx, my, x, px(10), w, px(44), () -> openTab(idx));
                if (hov) g.fill(x, px(10), x + w, px(54), th.rowHover());
                textMid(g, tabs[i], x + px(20), px(10), px(44), hov ? th.text() : th.dim(), false, 16);
            }
            x += w + px(4);
        }

        // search field (the UiTextField is drawn on top, borderless)
        int sw = px(320), sh = px(44);
        int sx = this.width - px(24) - px(44) - px(8) - sw;
        int sy = (hh - px(2) - sh) / 2 + px(2);
        inset(g, sx, sy, sw, sh);
        nextPlain = true; // the text field shows its own I-beam cursor
        hit(mx, my, sx, sy, sw, sh, () -> focusField(search));
        icon(g, ICON_SEARCH, 11, 11, sx + px(14), sy + (sh - px(20)) / 2, px(20), th.dim());
        int bd = px(24);
        int bx0 = sx + sw - px(10) - bd, by0 = sy + (sh - bd) / 2;
        g.fill(bx0, by0, bx0 + bd, by0 + bd, th.sep());
        g.fill(bx0 + px(2), by0 + px(2), bx0 + bd - px(2), by0 + bd - px(2), th.inset());
        textBtnFlat(g, "/", 14, bx0, by0, bd, bd, th.dim());

        int gx = this.width - px(24) - px(44);
        nextTip = "Settings";
        button(g, mx, my, gx, px(10), px(44), px(44), NORMAL,
                () -> UiHost.get().open(new OreSettingsScreen(this)));
        iconBtn(g, ICON_GEAR, 12, 12, gx, px(10), px(44), px(44), px(22), th.text());
    }

    private void openTab(int idx) {
        if (idx == activeTab()) return;
        if (idx >= 3) {
            List<MelodixApi.Tab> custom = MelodixApi.tabs();
            if (idx - 3 < custom.size()) {
                UiScreen s = custom.get(idx - 3).opener().get();
                if (s != null) UiHost.get().open(s);
            }
            return;
        }
        switch (idx) {
            case 0 -> UiHost.get().open(new OreHomeScreen());
            case 1 -> UiHost.get().open(new OreMinecraftScreen());
            default -> UiHost.get().open(new OrePlaylistsScreen());
        }
    }

    // ---------------------------------------------------------------- hit regions

    /** Registers a click region; returns whether the mouse is over it. */
    protected boolean hit(int mx, int my, int x, int y, int w, int h, Runnable click) {
        Hit hit = new Hit();
        hit.x = x; hit.y = y; hit.w = w; hit.h = h; hit.click = click;
        hit.tip = nextTip;
        hit.noHand = nextPlain;
        nextTip = null;
        nextPlain = false;
        hits.add(hit);
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Registers a vertical slider region; the consumer receives 0..1 from the bottom (0) to the top (1). */
    protected void vslider(int x, int y, int w, int h, DoubleConsumer onFraction) {
        Hit hit = new Hit();
        hit.x = x; hit.y = y; hit.w = w; hit.h = h; hit.slider = onFraction; hit.vertical = true;
        hits.add(hit);
    }

    /** Registers a draggable slider region; the consumer receives 0..1 along the region's width. */
    protected void slider(int x, int y, int w, int h, DoubleConsumer onFraction) {
        Hit hit = new Hit();
        hit.x = x; hit.y = y; hit.w = w; hit.h = h; hit.slider = onFraction;
        hits.add(hit);
    }

    public boolean onMouseClicked(UiMouse event) {
        if (event.button() != UiKeys.MOUSE_LEFT) return false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.contains(event.x(), event.y())) {
                if (h.slider != null) {
                    activeSlider = h;
                    h.slider.accept(frac(h, event.x(), event.y()));
                } else {
                    pressedHit = h; // fires on release, like OreUI buttons
                    pressedKey = h.key();
                }
                return true;
            }
        }
        return false;
    }

    public boolean onMouseDragged(UiMouse event, double dx, double dy) {
        if (activeSlider != null) {
            activeSlider.slider.accept(frac(activeSlider, event.x(), event.y()));
            return true;
        }
        return false;
    }

    public boolean onMouseReleased(UiMouse event) {
        if (pressedHit != null) {
            int key = pressedKey;
            pressedHit = null;
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (h.slider == null && h.key() == key && h.contains(event.x(), event.y())) {
                    if (h.click != null) h.click.run();
                    break;
                }
            }
        }
        activeSlider = null;
        return false;
    }

    private static double frac(Hit h, double mx, double my) {
        if (h.vertical) return Math.max(0, Math.min(1, 1 - (my - h.y) / Math.max(1, h.h)));
        return Math.max(0, Math.min(1, (mx - h.x) / Math.max(1, h.w)));
    }

    public boolean onMouseScrolled(double mx, double my, double sx, double sy) {
        if (modal()) return false;
        scrollY = Math.max(0, Math.min(scrollMax, scrollY - (int) Math.round(sy * px(56))));
        return true;
    }

    /** Whether typing in the header field opens the search screen right away. */
    protected boolean liveSearch() { return true; }

    /** Lets the user pick an image as the cover of one local song. */
    protected void changeSongCover(SongInfo si) {
        if (si == null || si.getSource() != SongInfo.SourceType.LOCAL) return;
        if (blockedByFullscreen(() -> changeSongCover(si))) return;
        String initial = manager.getConfig().getMusicFolderPath();
        if (initial.isEmpty()) initial = System.getProperty("user.home");
        UiHost.get().openFile("Choose an album cover", initial, result -> {
            if (result == null || result.isEmpty()) return;
            String lower = result.toLowerCase(java.util.Locale.ROOT);
            if (!(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg"))) return;
            Ports.art().setCustomArt(si.getPath(), new java.io.File(result));
            com.musicplayer.hud.NotificationManager.showSystem("Album cover updated");
        });
    }

    /** Enter in the header search field. */
    protected void onSearchSubmit() {
        String q = search.getValue().trim();
        if (!q.isEmpty()) UiHost.get().open(new OreSearchScreen(q).from(this));
    }

    private boolean swallowSlash;

    public boolean onKeyPressed(UiKey event) {
        int k = event.key();
        if (search != null && search.isFocused() && (k == UiKeys.ENTER || k == UiKeys.NUMPAD_ENTER)) {
            onSearchSubmit();
            return true;
        }
        if (search != null && search.isVisible() && !search.isFocused() && !(host != null && host.anyFieldFocused()) && k == UiKeys.SLASH && !modal()) {
            focusField(search);
            swallowSlash = true;
            return true;
        }
        return false;
    }

    public boolean onCharTyped(UiChar event) {
        if (swallowSlash) {
            swallowSlash = false;
            if (event.codepoint() == '/') return true;
        }
        return false;
    }

    public boolean isPauseScreen() {
        return manager.getConfig().isPauseOnOpen() && UiHost.get().inWorld();
    }

    // ---------------------------------------------------------------- drawing: bevels

    /** Minecraft-style bevelled button; registers its click region if {@code onClick} is non-null. */
    protected boolean button(Gfx g, int mx, int my, int x, int y, int w, int h, int kind, Runnable onClick) {
        if (onClick == null) nextTip = null;
        boolean hov = onClick != null ? hit(mx, my, x, y, w, h, onClick)
                : (mx >= x && mx < x + w && my >= y && my < y + h);
        int face, hi, lip;
        switch (kind) {
            case GREEN -> { face = OreTheme.GREEN; hi = OreTheme.GREEN_HI; lip = OreTheme.GREEN_LIP; }
            case LIGHT -> { face = OreTheme.LIGHT_FACE; hi = OreTheme.LIGHT_HI; lip = OreTheme.LIGHT_LIP; }
            default -> { face = th.face(); hi = th.bhi(); lip = th.lip(); }
        }
        if (hov && onClick != null) { face = brighten(face); hi = brighten(hi); }
        int b = px(2), l = px(4);
        int off = 0;
        if (onClick != null) {
            boolean down = pressedHit != null && pressedKey == ((x * 31 + y) * 31 + w) * 31 + h && hov;
            off = Math.min(l - 1, Math.round(anim(pressedKey == 0 ? "b" + x + "," + y : "b" + x + "," + y, down ? 1f : 0f, 18f) * b));
        }
        pressDy = off;
        g.fill(x, y, x + w, y + h, th.edge());
        g.fill(x + b, y + b, x + w - b, y + h - b, face);
        if (off > 0) g.fill(x + b, y + b, x + w - b, y + b + off, lip); // pressed: face sinks, top darkens
        g.fill(x + b, y + b + off, x + w - b, y + b + b + off, hi);
        g.fill(x + b, y + b + off, x + b + b, y + h - b, hi);
        g.fill(x + w - b - b, y + b + off, x + w - b, y + h - b, hi);
        g.fill(x + b, y + h - b - (l - off), x + w - b, y + h - b, lip);
        return hov;
    }

    protected void card(Gfx g, int x, int y, int w, int h) {
        int b = px(2);
        g.fill(x, y, x + w, y + h, th.edge());
        g.fill(x + b, y + b, x + w - b, y + h - b, th.card());
        g.fill(x + b, y + b, x + w - b, y + b + b, th.hi());
        g.fill(x + b, y + h - b - px(4), x + w - b, y + h - b, th.cardLip());
    }

    protected void inset(Gfx g, int x, int y, int w, int h) {
        int b = px(2);
        g.fill(x, y, x + w, y + h, th.edge());
        g.fill(x + b, y + b, x + w - b, y + h - b, th.inset());
    }

    /** Inset track with green fill and a light handle. Registers a slider region over the track. */
    protected void sliderTrack(Gfx g, int x, int y, int w, int h, float value, DoubleConsumer onFraction) {
        inset(g, x, y, w, h);
        int b = px(2);
        int fw = Math.round((w - 2 * b) * Math.max(0f, Math.min(1f, value)));
        if (fw > 0) {
            g.fill(x + b, y + b, x + b + fw, y + h - b, OreTheme.GREEN);
            g.fill(x + b, y + b, x + b + fw, y + b + b, OreTheme.GREEN_HI);
            g.fill(x + b, y + h - b - b, x + b + fw, y + h - b, OreTheme.GREEN_LIP);
        }
        int tw = px(14), th2 = px(28);
        int tx = x + b + fw - tw / 2;
        tx = Math.max(x, Math.min(x + w - tw, tx));
        int ty = y + h / 2 - th2 / 2;
        button(g, -1, -1, tx, ty, tw, th2, LIGHT, null);
        slider(x, y - px(8), w, h + px(16), onFraction);
    }

    /** Horizontal fade from {@code alpha} at the left edge to transparent at the right edge. */
    protected void hGradient(Gfx g, int x, int y, int w, int h, int rgb, float alpha) {
        int n = Math.max(1, Math.min(w, 64));
        for (int i = 0; i < n; i++) {
            int x0 = x + w * i / n, x1 = x + w * (i + 1) / n;
            float a = alpha * (1f - (i + 0.5f) / n);
            g.fill(x0, y, x1, y + h, (Math.round(a * 255) << 24) | (rgb & 0xFFFFFF));
        }
    }

    /** The design's decorative translucent squares, anchored to a card's top-right inner corner. */
    protected void deco(Gfx g, int rightX, int topY, int rgb, float[][] spec) {
        int sq = px(32);
        for (float[] e : spec) {
            int x1 = rightX - px(e[1]);
            int y0 = topY + px(e[0]);
            g.fill(x1 - sq, y0, x1, y0 + sq, (Math.round(e[2] * 255) << 24) | (rgb & 0xFFFFFF));
        }
    }

    private static int brighten(int c) {
        int r = Math.min(255, ((c >> 16) & 0xFF) * 112 / 100);
        int gr = Math.min(255, ((c >> 8) & 0xFF) * 112 / 100);
        int b = Math.min(255, (c & 0xFF) * 112 / 100);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    // ---------------------------------------------------------------- drawing: text (Minecraft font)

    /** Whole-number text scale for a design font size; 16px body text is scale 1 at a 640x360 GUI. */
    protected int ts(float designPx) {
        return Math.max(1, (int) Math.floor(designPx * s / 8f + 0.3f));
    }

    /** Height of capital letters in screen pixels at a design size. */
    protected int capH(float designPx) {
        return 7 * ts(designPx);
    }

    /** Screen-pixel width of {@code str} at a design font size. */
    protected int tw(String str, boolean bold, float designPx) {
        return UiHost.get().textWidth(str, bold) * ts(designPx);
    }

    /** Draws text with its top at {@code y}. */
    protected void text(Gfx g, String str, int x, int y, int color, boolean bold, float designPx) {
        int sc = ts(designPx);
        boolean shadow = !th.light();
        if (sc == 1) {
            g.text(str, bold, x, y, color, shadow);
            return;
        }
        g.scaledText(str, bold, x, y, sc, color, shadow);
    }

    protected void textRight(Gfx g, String str, int right, int y, int color, boolean bold, float designPx) {
        text(g, str, right - tw(str, bold, designPx), y, color, bold, designPx);
    }

    /** Ellipsizes {@code str} to fit {@code maxW} screen pixels. */
    protected String fit(String str, int maxW, boolean bold, float designPx) {
        if (tw(str, bold, designPx) <= maxW) return str;
        String out = str;
        while (out.length() > 1 && tw(out + "...", bold, designPx) > maxW) out = out.substring(0, out.length() - 1);
        return out + "...";
    }

    /** Text vertically centred in a box starting at {@code y} with height {@code boxH}. */
    protected void textMid(Gfx g, String str, int x, int y, int boxH, int color, boolean bold, float designPx) {
        text(g, str, x, y + (boxH - capH(designPx)) / 2, color, bold, designPx);
    }

    /** Text centred on the face of a bevelled button (the 4px lip sits below the face). */
    protected void textBtn(Gfx g, String str, boolean bold, float designPx, int bx, int by, int bw, int bh, int color) {
        int x = bx + (bw - tw(str, bold, designPx)) / 2;
        int faceCentre = by + bh / 2 - px(2) + pressDy;
        text(g, str, x, faceCentre - capH(designPx) / 2, color, bold, designPx);
    }

    /** Text centred in a flat (lip-less) box. */
    protected void textBtnFlat(Gfx g, String str, float designPx, int bx, int by, int bw, int bh, int color) {
        int x = bx + (bw - tw(str, false, designPx)) / 2;
        text(g, str, x, by + (bh - capH(designPx)) / 2, color, false, designPx);
    }

    // ---------------------------------------------------------------- drawing: pixel icons

    // Each icon is a list of {x,y,w,h} unit rects, copied from the design's SVG paths.
    protected static final int[][] ICON_NOTE = {{5, 1, 6, 2}, {5, 1, 2, 8}, {2, 7, 5, 3}};
    protected static final int[][] ICON_GEAR = {{5, 1, 2, 2}, {5, 9, 2, 2}, {1, 5, 2, 2}, {9, 5, 2, 2}, {2, 2, 2, 2}, {8, 2, 2, 2},
            {2, 8, 2, 2}, {8, 8, 2, 2}, {3, 3, 6, 2}, {3, 5, 2, 2}, {7, 5, 2, 2}, {3, 7, 6, 2}};
    protected static final int[][] ICON_PLAY = {{3, 1, 1, 8}, {4, 2, 1, 6}, {5, 3, 1, 4}, {6, 4, 1, 2}};
    protected static final int[][] ICON_PAUSE = {{2, 1, 2, 8}, {6, 1, 2, 8}};
    protected static final int[][] ICON_PREV = {{2, 1, 1, 8}, {4, 4, 1, 2}, {5, 3, 1, 4}, {6, 2, 1, 6}, {7, 1, 1, 8}};
    protected static final int[][] ICON_NEXT = {{7, 1, 1, 8}, {2, 1, 1, 8}, {3, 2, 1, 6}, {4, 3, 1, 4}, {5, 4, 1, 2}};
    protected static final int[][] ICON_HEART = {{1, 1, 2, 1}, {5, 1, 2, 1}, {0, 2, 8, 2}, {1, 4, 6, 1}, {2, 5, 4, 1}, {3, 6, 2, 1}};
    protected static final int[][] ICON_PLUS = {{5, 1, 2, 10}, {1, 5, 10, 2}};
    // 10x10 versions of the design's 16x16 shuffle / repeat strokes, one-pixel lines
    protected static final int[][] ICON_SHUFFLE = {{0, 2, 2, 1}, {2, 3, 1, 1}, {3, 4, 1, 1}, {4, 5, 1, 1}, {5, 6, 1, 1}, {6, 7, 2, 1},
            {0, 7, 2, 1}, {2, 6, 1, 1}, {3, 5, 1, 1}, {4, 4, 1, 1}, {5, 3, 1, 1}, {6, 2, 2, 1},
            {7, 0, 1, 5}, {8, 1, 1, 3}, {9, 2, 1, 1}, {7, 5, 1, 5}, {8, 6, 1, 3}, {9, 7, 1, 1}};
    protected static final int[][] ICON_REPEAT = {
            {6, 0, 1, 1}, {6, 1, 2, 1}, {1, 2, 8, 1}, {1, 3, 1, 2}, {6, 3, 2, 1}, {6, 4, 1, 1},
            {3, 5, 1, 1}, {8, 5, 1, 2}, {2, 6, 2, 1}, {1, 7, 8, 1}, {2, 8, 2, 1}, {3, 9, 1, 1}};
    protected static final int[][] ICON_SPEAKER = {{0, 3, 2, 2}, {2, 2, 1, 4}, {3, 1, 1, 6}, {5, 3, 1, 2}, {6, 2, 1, 4}};
    // frame with a small inner square: M1 1h10v9h-10z (hole M3 3h6v5h-6z) + M6 5h3v3h-3z
    protected static final int[][] ICON_MINI = {{1, 1, 10, 2}, {1, 8, 10, 2}, {1, 3, 2, 5}, {9, 3, 2, 5}, {6, 5, 3, 3}};
    protected static final int[][] ICON_ARROW = {{1, 4, 6, 1}, {5, 2, 1, 5}, {6, 3, 1, 3}, {7, 4, 1, 1}};
    protected static final int[][] ICON_BARS = {{0, 3, 2, 5}, {3, 0, 2, 8}, {6, 2, 2, 6}};
    protected static final int[][] ICON_LIST = {{1, 2, 10, 2}, {1, 5, 10, 2}, {1, 8, 10, 2}};
    // square with a hole: M1 1h10v10h-10z (hole M3 4h6v5h-6z)
    protected static final int[][] ICON_FRAME = {{1, 1, 10, 3}, {1, 9, 10, 2}, {1, 4, 2, 5}, {9, 4, 2, 5}};
    protected static final int[][] ICON_X = {{1, 1, 2, 2}, {3, 3, 2, 2}, {5, 5, 2, 2}, {7, 7, 2, 2}, {9, 9, 2, 2}, {9, 1, 2, 2}, {7, 3, 2, 2}, {3, 7, 2, 2}, {1, 9, 2, 2}};
    protected static final int[][] ICON_BACK = {{5, 1, 2, 1}, {4, 2, 2, 1}, {3, 3, 2, 1}, {2, 4, 6, 2}, {3, 6, 2, 1}, {4, 7, 2, 1}, {5, 8, 2, 1}};
    protected static final int[][] ICON_UP = {{4, 2, 2, 1}, {3, 3, 4, 1}, {2, 4, 6, 1}, {1, 5, 8, 1}};
    protected static final int[][] ICON_DOWN = {{1, 4, 8, 1}, {2, 5, 6, 1}, {3, 6, 4, 1}, {4, 7, 2, 1}};
    protected static final int[][] ICON_GRIP = {{1, 1, 2, 2}, {5, 1, 2, 2}, {1, 4, 2, 2}, {5, 4, 2, 2}, {1, 7, 2, 2}, {5, 7, 2, 2}};
    protected static final int[][] ICON_DOTS = {{1, 5, 2, 2}, {5, 5, 2, 2}, {9, 5, 2, 2}};
    protected static final int[][] ICON_FOLDER = {{1, 2, 4, 2}, {1, 4, 10, 7}};
    protected static final int[][] ICON_CHECK = {{0, 4, 1, 1}, {1, 5, 1, 1}, {2, 6, 1, 1}, {3, 5, 1, 1}, {4, 4, 1, 1}, {5, 3, 1, 1}, {6, 2, 1, 1}, {7, 1, 1, 1}};
    protected static final int[][] ICON_IMPORT = {{5, 1, 2, 5}, {2, 5, 8, 1}, {3, 6, 6, 1}, {4, 7, 4, 1}, {5, 8, 2, 1}, {1, 10, 10, 1}};
    protected static final int[][] ICON_EDIT = {{1, 2, 10, 2}, {1, 5, 10, 2}, {1, 8, 6, 2}};
    protected static final int[][] ICON_RENAME = {{8, 1, 2, 2}, {6, 3, 2, 2}, {4, 5, 2, 2}, {2, 7, 2, 2}, {1, 9, 2, 2}};
    protected static final int[][] ICON_COVER = {{1, 2, 10, 1}, {1, 9, 10, 1}, {1, 3, 1, 6}, {10, 3, 1, 6}, {3, 4, 2, 2}, {3, 7, 6, 1}};
    protected static final int[][] ICON_GRID = {{1, 1, 4, 4}, {7, 1, 4, 4}, {1, 7, 4, 4}, {7, 7, 4, 4}};
    protected static final int[][] ICON_EXPORT = {{5, 1, 2, 6}, {3, 5, 2, 2}, {7, 5, 2, 2}, {1, 9, 10, 2}};
    protected static final int[][] ICON_EXPAND = {{6, 1, 4, 1}, {9, 2, 1, 4}, {8, 3, 1, 1}, {7, 4, 1, 1}, {6, 5, 1, 1}, {1, 3, 4, 1}, {1, 4, 1, 6}, {2, 9, 6, 1}, {8, 7, 1, 3}};
    protected static final int[][] ICON_CHEV = {{3, 1, 1, 7}, {4, 2, 1, 5}, {5, 3, 1, 3}, {6, 4, 1, 1}};
    protected static final int[][] ICON_SEARCH = {{1, 1, 7, 2}, {1, 6, 7, 2}, {1, 3, 2, 3}, {6, 3, 2, 3}, {8, 8, 1, 1}, {9, 9, 1, 1}, {10, 10, 1, 1}};

    /**
     * Draws an icon centred in a {@code size} box using a whole-number pixel unit, so every unit of the
     * design's pixel art lands on exact screen pixels.
     */
    protected void icon(Gfx g, int[][] rects, int gw, int gh, int x, int y, int size, int color) {
        int k = Math.max(1, Math.round(size / (float) Math.max(gw, gh)));
        int ox = x + (size - gw * k) / 2, oy = y + (size - gh * k) / 2;
        for (int[] r : rects) {
            g.fill(ox + r[0] * k, oy + r[1] * k, ox + (r[0] + r[2]) * k, oy + (r[1] + r[3]) * k, color);
        }
    }

    /** Icon centred on the face of a bevelled button. */
    protected void iconBtn(Gfx g, int[][] rects, int gw, int gh, int bx, int by, int bw, int bh, int size, int color) {
        icon(g, rects, gw, gh, bx + (bw - size) / 2, by + bh / 2 - px(2) - size / 2 + pressDy, size, color);
    }

    // ---------------------------------------------------------------- shared widgets

    /** Borderless text field placed over a box; call every frame. */
    protected UiTextField makeField(String hint, int maxLen) {
        return newField(hint, maxLen);
    }

    /** Draws the inset box for a field, positions the UiTextField in it and makes the box click-to-focus. */
    protected void field(Gfx g, int mx, int my, UiTextField e, int x, int y, int w, int h, int padL, int padR, boolean visible) {
        e.setVisible(visible);
        if (!visible) return;
        int b = px(2);
        g.fill(x, y, x + w, y + h, e.isFocused() ? 0xFF6CC349 : th.edge());
        g.fill(x + b, y + b, x + w - b, y + h - b, th.inset());
        e.setBounds(x + padL, y + (h - 7) / 2, Math.max(10, w - padL - padR));
        // the box is created narrow, so its text starts scrolled to the end; show the start while unfocused
        if (!e.isFocused()) e.moveCursorToStart();
        nextPlain = true;
        hit(mx, my, x, y, w, h, () -> focusField(e));
    }

    /** The design's 88x36 switch with a sliding light knob. */
    protected void drawSwitch(Gfx g, int mx, int my, int x, int y, boolean on, Object key, boolean labels, Runnable click) {
        int w = px(88), h = px(36), b = px(2);
        float v = smooth(anim(key, on ? 1f : 0f, 9f));
        g.fill(x, y, x + w, y + h, th.edge());
        int track = lerpColor(th.inset(), OreTheme.GREEN, v);
        g.fill(x + b, y + b, x + w - b, y + h - b, track);
        if (v > 0.5f) {
            g.fill(x + b, y + b, x + w - b, y + b + b, OreTheme.GREEN_HI);
            g.fill(x + b, y + h - b - b, x + w - b, y + h - b, OreTheme.GREEN_LIP);
        }
        int knobW = px(46);
        int kx = Math.round((x - b) + v * ((x + w - knobW + b) - (x - b)));
        if (labels) {
            String l = on ? "I" : "O";
            int zoneX = on ? x + b : x + w - b - (w - b * 2 - knobW + b);
            int zoneW = w - knobW - b;
            text(g, l, (on ? x + b : x + knobW - b) + (zoneW - tw(l, false, 20)) / 2, y + (h - capH(20)) / 2 - px(1),
                    on ? 0xFFFFFFFF : th.dim(), false, 20);
        }
        button(g, -1, -1, kx, y - b, knobW, h + b * 2, LIGHT, null);
        hit(mx, my, x - b, y - b, w + 2 * b, h + 2 * b, click);
    }

    protected void checkbox(Gfx g, int mx, int my, int x, int y, boolean on, Runnable click) {
        int s28 = px(28), b = px(2);
        g.fill(x, y, x + s28, y + s28, th.edge());
        g.fill(x + b, y + b, x + s28 - b, y + s28 - b, on ? OreTheme.GREEN : th.inset());
        if (on) {
            g.fill(x + b, y + s28 - b - px(3), x + s28 - b, y + s28 - b, OreTheme.GREEN_LIP);
            g.fill(x + b, y + b, x + s28 - b, y + b + b, OreTheme.GREEN_HI);
            icon(g, ICON_CHECK, 8, 8, x + (s28 - px(16)) / 2, y + (s28 - px(16)) / 2, px(16), 0xFFFFFFFF);
        }
        hit(mx, my, x, y, s28, s28, click);
    }

    /** Scroll bar for the shared scroll state, drawn over the right edge from {@code top} to the bottom of the screen. */
    protected void scrollbar(Gfx g, int top, int bottom) {
        scrollbarAt(g, this.width - px(16), top, bottom);
    }

    protected void scrollbarAt(Gfx g, int trackX, int top, int bottom) {
        if (scrollMax <= 0) return;
        int b2 = px(2), trackH = bottom - top;
        g.fill(trackX, top, trackX + px(16), bottom, th.edge());
        g.fill(trackX + b2, top, trackX + px(16), bottom, th.inset());
        int thumbH = Math.max(px(40), trackH * trackH / (trackH + scrollMax));
        int thumbY = top + (trackH - thumbH) * scrollY / scrollMax;
        button(g, -1, -1, trackX, thumbY, px(16), thumbH, LIGHT, null);
        nextTip = null;
        vslider(trackX - px(4), top, px(20), trackH, f -> scrollY = Math.max(0, Math.min(scrollMax, (int) Math.round((1 - f) * scrollMax))));
    }

    /** Vertical fade from {@code alpha} at the top to transparent at {@code fraction} of the height. */
    protected void vGradient(Gfx g, int x, int y, int w, int h, int rgb, float alpha, float fraction) {
        int fh = Math.max(1, Math.round(h * fraction));
        int n = Math.max(1, Math.min(fh, 48));
        for (int i = 0; i < n; i++) {
            int y0 = y + fh * i / n, y1 = y + fh * (i + 1) / n;
            float a = alpha * (1f - (i + 0.5f) / n);
            g.fill(x, y0, x + w, y1, (Math.round(a * 255) << 24) | (rgb & 0xFFFFFF));
        }
    }

    /** Popup menu background (shadow, border, card). */
    protected void menuBox(Gfx g, int x, int y, int w, int h) {
        int b = px(2);
        g.fill(x, y + px(6), x + w, y + h + px(6), 0x59000000);
        g.fill(x, y, x + w, y + h, th.edge());
        g.fill(x + b, y + b, x + w - b, y + h - b, th.card());
        g.fill(x + b, y + b, x + w - b, y + b + b, th.hi());
        g.fill(x + b, y + h - b - px(4), x + w - b, y + h - b, th.cardLip());
    }

    protected void menuItem(Gfx g, int mx, int my, int x, int y, int w, int[][] ic, int gw, int gh,
                            String label, boolean danger, Runnable click) {
        int h = px(40);
        boolean hov = hit(mx, my, x, y, w, h, click);
        if (hov) g.fill(x, y, x + w, y + h, th.rowHover());
        int col = danger ? th.like() : th.text();
        if (ic != null) icon(g, ic, gw, gh, x + px(14), y + (h - px(18)) / 2, px(18), col);
        text(g, label, x + px(14) + px(18) + px(12), y + (h - capH(16)) / 2, col, false, 16);
    }

    protected int hitMark() { return hits.size(); }

    /** Drops every click region registered since {@code mark} (used to disable a dimmed row). */
    protected void dropHitsSince(int mark) {
        while (hits.size() > mark) hits.remove(hits.size() - 1);
    }

    /** A region that swallows clicks without a hand cursor (e.g. the body of a dialog). */
    protected void catcherArea(int mx, int my, int x, int y, int w, int h) {
        nextPlain = true;
        hit(mx, my, x, y, w, h, () -> { });
    }

    private final float[] miniBars = new float[3];
    private final float[] miniShown = new float[3];

    /**
     * The three-bar "now playing" indicator in song lists. Follows the real audio when the track EQ setting is on
     * (own songs and the Minecraft soundtrack); falls back to a gentle bounce until audio data arrives, and
     * rests low while paused. With the setting off it is the plain static icon.
     */
    protected void miniBars(Gfx g, Now n, int x, int y, int size, int color) {
        if (!manager.getConfig().isMiniVisualizerEnabled()) {
            icon(g, ICON_BARS, 8, 8, x, y, size, color);
            return;
        }
        boolean hasData = false;
        if (!n.paused) {
            if (n.mc) {
                hasData = Ports.soundtrack().getVisualizerBars(miniBars, 3);
            } else {
                manager.getEngine().getVisualizerBars(miniBars, 3);
                for (float b : miniBars) if (b > 0.01f) { hasData = true; break; }
            }
        }
        long t = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            float target;
            if (n.paused) target = 0.25f;
            else if (hasData) target = Math.max(0.15f, Math.min(1f, miniBars[i]));
            else target = 0.35f + 0.55f * (0.5f + 0.5f * (float) Math.sin(t / (170.0 + i * 60.0) + i * 2.1));
            miniShown[i] += (target - miniShown[i]) * Math.min(1f, dt * 16f);
        }
        int u = Math.max(1, size / 8);
        int left = x + (size - 8 * u) / 2;
        int bottom = y + size;
        for (int i = 0; i < 3; i++) {
            int h = Math.max(u, Math.round(8 * u * miniShown[i]));
            int bx = left + i * 3 * u;
            g.fill(bx, bottom - h, bx + 2 * u, bottom, color);
        }
    }

    /** Full-screen click catcher placed under a popup so a click outside closes it. */
    protected void catcher(int mx, int my, Runnable close) {
        nextPlain = true;
        hit(mx, my, 0, 0, this.width, this.height, close);
    }

    // ---------------------------------------------------------------- now-playing snapshot

    protected static final class Now {
        public SongInfo song;
        public String title = "", artist = "", sub = "";
        public boolean active, paused, mc;
        public long posMs, durMs;
        public String art = Ports.art().defaultArt();
        public String path = "";

        public float progress() { return durMs > 0 ? Math.min(1f, (float) posMs / durMs) : 0f; }
    }

    protected Now now() {
        Now n = new Now();
        n.mc = manager.getCurrentSource() == MusicPlayerManager.SongSource.MINECRAFT;
        n.song = manager.getCurrentSong();
        if (n.mc) {
            VanillaPort v = Ports.vanilla();
            VanillaTrack vt = v.getCurrentTrack();
            n.active = vt != null && (v.isPlaying() || v.isPaused());
            n.paused = v.isPaused() || Ports.soundtrack().isPausePending();
            if (vt != null) {
                n.title = vt.title();
                n.artist = vt.artist();
                n.sub = vt.artist() + " · " + vt.album();
                n.path = vt.id();
                n.posMs = v.getElapsedMs();
                n.durMs = vt.durationSeconds() * 1000L;
                String a = Ports.art().builtInArt(vt.coverKey());
                if (a != null) n.art = a;
            }
        } else {
            var e = manager.getEngine();
            n.active = n.song != null && (e.isActive() || e.isPaused());
            n.paused = e.isPaused() || e.isPausing();
            if (n.song != null) {
                n.title = n.song.getTitle();
                n.artist = n.song.getArtist();
                n.sub = n.song.getArtist();
                n.path = n.song.getPath();
            }
            n.posMs = e.getPositionMs();
            n.durMs = e.getDurationMs();
            if (Ports.art().hasArt()) n.art = Ports.art().albumArtId();
        }
        return n;
    }

    protected boolean isLiked(String path) {
        return path != null && !path.isEmpty() && manager.getConfig().isLiked(path);
    }

    protected void toggleLike(String path) {
        if (path == null || path.isEmpty()) return;
        MusicPlayerConfig c = manager.getConfig();
        if (c.isLiked(path)) c.unlikeSong(path); else c.likeSong(path);
    }

    protected static int hashColor(String key) {
        int[] palette = {0xFF5B8F3A, 0xFF4A7FB5, 0xFFA63C3C, 0xFF8A5FB0, 0xFFC9B13A, 0xFFC97A3A, 0xFF3FA6A0, 0xFF4A4F9E};
        return palette[Math.floorMod(key == null ? 0 : key.hashCode(), palette.length)];
    }

    protected static String time(long ms) {
        return MusicPlayerManager.formatTime(ms);
    }

    // ---------------------------------------------------------------- animation, tooltips, covers, wrapping

    /** Sets the tooltip for the next click region registered (shown after hovering for half a second). */
    protected void tip(String text) {
        nextTip = text;
    }

    /** Eases a value toward {@code target} at {@code speed} units per second. Starts at the target. */
    protected float anim(Object key, float target, float speed) {
        float v = anims.getOrDefault(key, target);
        float step = speed * dt;
        if (v < target) v = Math.min(target, v + step);
        else if (v > target) v = Math.max(target, v - step);
        anims.put(key, v);
        return v;
    }

    protected static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }

    protected static int lerpColor(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int gr = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (gr << 8) | bl;
    }

    private int coverKeysCount = -1;
    private final java.util.Map<String, String> coverKeys = new java.util.HashMap<>();

    /** Real album cover for a song: built-in art for Minecraft tracks, embedded art for local files. */
    protected String coverFor(SongInfo si) {
        String path = si.getPath();
        if (path == null) return Ports.art().artForSong(null, null);
        var reg = Ports.registry();
        if (coverKeysCount != reg.trackCount()) { // rebuilt only when the registry changed
            coverKeysCount = reg.trackCount();
            coverKeys.clear();
            for (VanillaTrack vt : reg.getAllTracks()) coverKeys.put(vt.id(), vt.coverKey());
        }
        return Ports.art().artForSong(path, coverKeys.get(path));
    }

    /** Greedy word wrap to {@code maxW} screen pixels. */
    protected List<String> wrap(String str, int maxW, boolean bold, float designPx) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : str.split(" ")) {
            String t = cur.length() == 0 ? word : cur + " " + word;
            if (cur.length() > 0 && tw(t, bold, designPx) > maxW) {
                out.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(t);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }
}
