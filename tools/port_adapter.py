#!/usr/bin/env python3
"""
Regenerates the version-specific adapter of a backport from the 26.3 adapter.

    python3 tools/port_adapter.py 1.20.1      (also: 1.21.1, 1.21.11)

core/ and ui/ are shared as-is. What this tool copies is the code that talks to the Minecraft sound system,
textures and mixins (McBootstrap, MusicPlayerMod, AlbumArtManager, VanillaMusicPlayer, player/*, mixin/*) and
then applies the small API differences of the target version. The four classes in host/ (drawing, input,
text fields, UiHost) are written per Minecraft generation; for the pre-1.21.9 versions they live in this file
as templates, for 1.21.11 they are derived from the 26.3 ones.

Change the sound glue in 26.3, run this tool for each backport, build. Only an API difference that is new
needs a new patch here.
"""
import os
import re
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "26.3", "src", "main", "java", "com", "musicplayer")

COPY_FILES = ["McBootstrap.java", "Ids.java", "AlbumArtManager.java", "MusicPlayerMod.java", "VanillaMusicPlayer.java"]
COPY_DIRS = ["mixin", "player"]

VERSIONS = {
    # era: "id" = Identifier era (1.21.11+), "rl" = ResourceLocation era
    "1.21.11": {"era": "id"},
    "1.21.1": {"era": "rl", "music_getevent": True, "cursor_arg": True, "hud": "delta", "renderbg4": True, "scroll4": True, "keybinds": "options.controls", "rlfactory": True},
    "1.20.1": {"era": "rl", "music_getevent": True, "hud": "float", "renderbg4": False, "scroll4": False, "keybinds": "controls", "rlfactory": False},
}


def rd(p):
    with open(p, encoding="utf-8") as f:
        return f.read()


def wr(p, s):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        f.write(s)


def rx(s, pat, new, label, required=True):
    if not re.search(pat, s, flags=re.S):
        if required:
            print("  MISS %s" % label)
        return s
    return re.sub(pat, lambda m: new, s, flags=re.S)


# --------------------------------------------------------------------------------------------- host templates

HOST_EDITBOX = '''package com.musicplayer.host;

import com.musicplayer.ui.UiTextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** A borderless EditBox behind the shared UiTextField interface. */
final class EditBoxField implements UiTextField {
    final EditBox box;

    EditBoxField(String hint, int maxLen) {
        var font = Minecraft.getInstance().font;
        box = new EditBox(font, 0, 0, 10, font.lineHeight, Component.literal(hint));
        box.setBordered(false);
        box.setHint(Component.literal(hint));
        box.setMaxLength(maxLen);
    }

    @Override public String getValue() { return box.getValue(); }
    @Override public void setValue(String v) { box.setValue(v); }
    @Override public void setHint(String hint) { box.setHint(Component.literal(hint)); }
    @Override public void setTextColor(int argb) { box.setTextColor(argb); }
    @Override public void setMaxLength(int n) { box.setMaxLength(n); }
    @Override public void setResponder(Consumer<String> r) { box.setResponder(r); }
    @Override public void setBounds(int x, int y, int w) { box.setX(x); box.setY(y); box.setWidth(w); }
    @Override public void setVisible(boolean v) { box.visible = v; }
    @Override public boolean isVisible() { return box.visible; }
    @Override public boolean isFocused() { return box.isFocused(); }
    @Override public void setFocused(boolean focused) { box.setFocused(focused); }
    @Override public void moveCursorToStart() { box.moveCursorToStart(); }
}
'''

HOST_GFX_RL = '''package com.musicplayer.host;

import com.mojang.blaze3d.vertex.PoseStack;
import com.musicplayer.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Draws the shared UI with GuiGraphics (1.20 - 1.21.1). */
final class GfxImpl implements Gfx {
    private static final Style BOLD = Style.EMPTY.withBold(true);
    private static final Map<String, ResourceLocation> IDS = new ConcurrentHashMap<>();

    /** Set while a frame asks for the hand cursor; HostScreen applies it after the frame. */
    static boolean handWanted;

    private final GuiGraphics g;
    private final Minecraft mc = Minecraft.getInstance();

    GfxImpl(GuiGraphics g) { this.g = g; }

    private Font font() { return mc.font; }

    private static Component comp(String s, boolean bold) {
        return bold ? Component.literal(s).withStyle(BOLD) : Component.literal(s);
    }

    @Override public void fill(int x0, int y0, int x1, int y1, int argb) { g.fill(x0, y0, x1, y1, argb); }

    @Override public void text(String s, boolean bold, int x, int y, int color, boolean shadow) {
        g.drawString(font(), comp(s, bold), x, y, color, shadow);
    }

    @Override public void scaledText(String s, boolean bold, int x, int y, int scale, int color, boolean shadow) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 0);
        ps.scale(scale, scale, 1f);
        g.drawString(font(), comp(s, bold), 0, 0, color, shadow);
        ps.popPose();
    }

    @Override public void centeredText(String s, int cx, int y, int color) { g.drawCenteredString(font(), s, cx, y, color); }

    @Override public void blit(String texture, int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1) {
        int w = x1 - x0, h = y1 - y0;
        g.blit(IDS.computeIfAbsent(texture, com.musicplayer.Ids::parse), x0, y0, 0f, 0f, w, h, w, h);
    }

    @Override public void enableScissor(int x0, int y0, int x1, int y1) { g.enableScissor(x0, y0, x1, y1); }
    @Override public void disableScissor() { g.disableScissor(); }
    @Override public void nextStratum() { /* immediate-mode drawing: later draws already land on top */ }
    @Override public void handCursor() { handWanted = true; }

    @Override public int textWidth(String s, boolean bold) { return font().width(comp(s, bold)); }
    @Override public int lineHeight() { return font().lineHeight; }
    @Override public String trimToWidth(String s, int w) { return font().plainSubstrByWidth(s, w); }

    @Override public int guiWidth() { return mc.getWindow().getGuiScaledWidth(); }
    @Override public int guiHeight() { return mc.getWindow().getGuiScaledHeight(); }
    @Override public boolean screenOpen() { return mc.screen != null; }
}
'''

HOST_SCREEN_RL = '''package com.musicplayer.host;

import com.musicplayer.ore.OreScreen;
import com.musicplayer.ui.UiChar;
import com.musicplayer.ui.UiKey;
import com.musicplayer.ui.UiKeys;
import com.musicplayer.ui.UiMouse;
import com.musicplayer.ui.UiTextField;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** The game Screen that hosts a shared Ore screen. */
public final class HostScreen extends Screen implements OreScreen.Host {
    private static long handCursor;
    private static boolean handShown;

    private final OreScreen ui;
    private GuiGraphics curG;
    private int curMx, curMy;
    private float curPt;

    public HostScreen(OreScreen ui) {
        super(Component.literal(ui.title()));
        this.ui = ui;
        ui.attach(this);
    }

    public OreScreen ui() { return ui; }

    @Override protected void init() {
        ui.attach(this);
        ui.onInit(this.width, this.height);
    }

@@RENDER_BACKGROUND@@

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        curG = g; curMx = mx; curMy = my; curPt = pt;
        GfxImpl.handWanted = false;
        ui.onRender(new GfxImpl(g), mx, my, pt);
        applyCursor(GfxImpl.handWanted);
    }

    private static void applyCursor(boolean hand) {
        if (hand == handShown) return;
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (hand) {
            if (handCursor == 0) handCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR);
            GLFW.glfwSetCursor(window, handCursor);
        } else {
            GLFW.glfwSetCursor(window, 0L);
        }
        handShown = hand;
    }

    @Override public void removed() {
        applyCursor(false);
        super.removed();
    }

    // ---- OreScreen.Host
    @Override public void addField(UiTextField f) { addRenderableWidget(((EditBoxField) f).box); }
    @Override public void focusField(UiTextField f) { setFocused(f == null ? null : ((EditBoxField) f).box); }
    @Override public boolean anyFieldFocused() { return getFocused() != null; }
    @Override public void drawFields() { super.render(curG, curMx, curMy, curPt); }

    // ---- input
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        return ui.onMouseClicked(new UiMouse(x, y, button));
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (ui.onMouseDragged(new UiMouse(x, y, button), dx, dy)) return true;
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (ui.onMouseReleased(new UiMouse(x, y, button))) return true;
        return super.mouseReleased(x, y, button);
    }

@@MOUSE_SCROLLED@@

    @Override public boolean keyPressed(int key, int scancode, int mods) {
        int mapped = key == InputConstants.KEY_ESCAPE ? UiKeys.ESCAPE
                : key == InputConstants.KEY_RETURN ? UiKeys.ENTER
                : key == InputConstants.KEY_NUMPADENTER ? UiKeys.NUMPAD_ENTER
                : key == InputConstants.KEY_SLASH ? UiKeys.SLASH : UiKeys.UNKNOWN;
        if (ui.onKeyPressed(new UiKey(mapped))) return true;
        return super.keyPressed(key, scancode, mods);
    }

    @Override public boolean charTyped(char c, int mods) {
        if (ui.onCharTyped(new UiChar(c))) return true;
        return super.charTyped(c, mods);
    }

    @Override public boolean isPauseScreen() { return ui.isPauseScreen(); }

    @Override public void onClose() { ui.onClose(); }
}
'''

HOST_UIHOST_RL = '''package com.musicplayer.host;

import com.musicplayer.MusicPlayerMod;
import com.musicplayer.port.UiScreen;
import com.musicplayer.ore.OreScreen;
import com.musicplayer.ui.Gfx;
import com.musicplayer.ui.KeyBindingView;
import com.musicplayer.ui.UiHost;
import com.musicplayer.ui.UiTextField;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.@@KEYBINDS_PKG@@.KeyBindsScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Implementation of what the shared UI needs from the game (1.20 - 1.21.1). */
public final class McUiHost implements UiHost {
    private Minecraft mc() { return Minecraft.getInstance(); }

    @Override public void open(UiScreen screen) {
        mc().setScreen(screen instanceof OreScreen o ? new HostScreen(o) : null);
    }

    @Override public boolean inWorld() { return mc().level != null; }
    @Override public boolean fullscreen() { return mc().options.fullscreen().get(); }

    @Override public void setFullscreen(boolean fullscreen) {
        mc().options.fullscreen().set(fullscreen); // the option own callback switches the window
        mc().options.save();
    }

    @Override public double mouseX() {
        var win = mc().getWindow();
        return mc().mouseHandler.xpos() * win.getGuiScaledWidth() / win.getScreenWidth();
    }

    @Override public double mouseY() {
        var win = mc().getWindow();
        return mc().mouseHandler.ypos() * win.getGuiScaledHeight() / win.getScreenHeight();
    }

    @Override public boolean mouseLeftDown() {
        return GLFW.glfwGetMouseButton(mc().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    @Override public void playToastSound() {
        var sm = mc().getSoundManager();
        if (sm != null) sm.play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1.0f));
    }

    @Override public boolean classicScreenOpen() { return false; }

    @Override public String playerName() { return mc().getUser().getName(); }
    @Override public void runOnMain(Runnable r) { mc().execute(r); }

    @Override public int textWidth(String s, boolean bold) {
        return mc().font.width(bold ? net.minecraft.network.chat.Component.literal(s).withStyle(net.minecraft.network.chat.Style.EMPTY.withBold(true))
                : net.minecraft.network.chat.Component.literal(s));
    }

    @Override public int lineHeight() { return mc().font.lineHeight; }

    @Override public UiTextField newTextField(String hint, int maxLen) { return new EditBoxField(hint, maxLen); }

    @Override public List<KeyBindingView> keyBindings() {
        List<KeyBindingView> out = new ArrayList<>();
        for (KeyMapping km : MusicPlayerMod.getAllKeys()) {
            if (km == null) out.add(new KeyBindingView("", "Unbound", true));
            else out.add(new KeyBindingView(km.getName(), km.getTranslatedKeyMessage().getString(), km.isUnbound()));
        }
        return out;
    }

    @Override public void openKeybindsScreen(UiScreen parent) {
        mc().setScreen(new KeyBindsScreen(mc().screen, mc().options));
    }

    @Override public void openFile(String title, String defaultLocation, Consumer<String> onResult) {
        onResult.accept(TinyFileDialogs.tinyfd_openFileDialog(title, defaultLocation, (org.lwjgl.PointerBuffer) null, (CharSequence) null, false));
    }

    @Override public void saveFile(String title, String defaultLocation, Consumer<String> onResult) {
        onResult.accept(TinyFileDialogs.tinyfd_saveFileDialog(title, defaultLocation, (org.lwjgl.PointerBuffer) null, (CharSequence) null));
    }

    @Override public void selectFolder(String title, String defaultLocation, Consumer<String> onResult) {
        onResult.accept(TinyFileDialogs.tinyfd_selectFolderDialog(title, defaultLocation));
    }

    @Override public void registerHud(String id, BiConsumer<Gfx, Float> renderer) {
        HudRenderCallback.EVENT.register((net.minecraft.client.gui.GuiGraphics graphics, @@HUD_DELTA@@ delta) -> renderer.accept(new GfxImpl(graphics), 0f));
    }

    @Override public boolean classicUiAvailable() { return false; }

    @Override public void openClassicUi() { }
}
'''


def host_files_rl(cfg):
    screen = HOST_SCREEN_RL
    if cfg["renderbg4"]:
        screen = screen.replace("@@RENDER_BACKGROUND@@",
                                "    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {\n        // the shared screen paints its own background\n    }")
    else:
        screen = screen.replace("@@RENDER_BACKGROUND@@",
                                "    @Override public void renderBackground(GuiGraphics g) {\n        // the shared screen paints its own background\n    }")
    if cfg["scroll4"]:
        screen = screen.replace("@@MOUSE_SCROLLED@@",
                                "    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {\n        if (ui.onMouseScrolled(mx, my, sx, sy)) return true;\n        return super.mouseScrolled(mx, my, sx, sy);\n    }")
    else:
        screen = screen.replace("@@MOUSE_SCROLLED@@",
                                "    @Override public boolean mouseScrolled(double mx, double my, double delta) {\n        if (ui.onMouseScrolled(mx, my, 0, delta)) return true;\n        return super.mouseScrolled(mx, my, delta);\n    }")
    uihost = HOST_UIHOST_RL.replace("@@KEYBINDS_PKG@@", cfg["keybinds"])
    uihost = uihost.replace("@@HUD_DELTA@@", "float" if cfg["hud"] == "float" else "net.minecraft.client.DeltaTracker")
    editbox = HOST_EDITBOX.replace("box.moveCursorToStart();", "box.moveCursorToStart(false);") if cfg.get("cursor_arg") else HOST_EDITBOX
    return {"EditBoxField.java": editbox, "GfxImpl.java": HOST_GFX_RL, "HostScreen.java": screen, "McUiHost.java": uihost}


# --------------------------------------------------------------------------------------------- patches

def patch_id_era(d):
    """1.21.11: same Identifier naming as 26.x, different Gui/input names."""
    def edit(path, fn):
        p = os.path.join(d, path)
        wr(p, fn(rd(p)))

    edit("McBootstrap.java", lambda s: s.replace("mc.gui.screen()", "mc.screen"))
    edit("mixin/MusicManagerMixin.java", lambda s: s.replace("minecraft.gui.screen()", "minecraft.screen"))
    edit("player/FixedSoundInstance.java", lambda s: s.replace("getOrResolve(", "resolve("))

    def mod(s):
        s = s.replace("import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;", "import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;")
        s = s.replace("KeyMappingHelper.registerKeyMapping(", "KeyBindingHelper.registerKeyBinding(")
        s = s.replace("InputConstants.Type.KEYBOARD", "InputConstants.Type.KEYSYM")
        s = s.replace("boolean focused = client.getWindow().isFocused();",
                      "boolean focused = org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(client.getWindow().handle(), org.lwjgl.glfw.GLFW.GLFW_FOCUSED) == org.lwjgl.glfw.GLFW.GLFW_TRUE;")
        s = s.replace("// Window focus is polled each tick (26.3 uses SDL and has no GLFW focus callback)", "// Window focus is polled each tick")
        s = s.replace("client.gui.screen()", "client.screen").replace("client.gui.setScreen(", "client.setScreen(")
        s = s.replace("client.gui.hud.setOverlayMessage", "client.gui.setOverlayMessage")
        return s
    edit("MusicPlayerMod.java", mod)

    for name in ["EditBoxField.java", "GfxImpl.java", "HostScreen.java", "McUiHost.java"]:
        shutil.copy(os.path.join(SRC, "host", name), os.path.join(d, "host", name))

    def gfx(s):
        s = s.replace("GuiGraphicsExtractor", "GuiGraphics")
        s = s.replace("g.text(font(), comp(s, bold), x, y, color, shadow);", "g.drawString(font(), comp(s, bold), x, y, color, shadow);")
        s = s.replace("g.text(font(), comp(s, bold), 0, 0, color, shadow);", "g.drawString(font(), comp(s, bold), 0, 0, color, shadow);")
        s = s.replace("g.centeredText(font(), s, cx, y, color);", "g.drawCenteredString(font(), s, cx, y, color);")
        s = s.replace("g.blit(IDS.computeIfAbsent(texture, Ids::parse), x0, y0, x1, y1, u0, u1, v0, v1);",
                      "int w = x1 - x0, h = y1 - y0;\n        g.blit(RenderPipelines.GUI_TEXTURED, IDS.computeIfAbsent(texture, Ids::parse), x0, y0, 0f, 0f, w, h, w, h);")
        s = s.replace("import net.minecraft.client.gui.GuiGraphics;", "import net.minecraft.client.gui.GuiGraphics;\nimport net.minecraft.client.renderer.RenderPipelines;")
        s = s.replace("mc.gui.screen()", "mc.screen")
        return s
    edit("host/GfxImpl.java", gfx)

    def hs(s):
        s = s.replace("GuiGraphicsExtractor", "GuiGraphics")
        s = s.replace("extractBackground(GuiGraphics g", "renderBackground(GuiGraphics g")
        s = s.replace("public void extractRenderState(GuiGraphics g", "public void render(GuiGraphics g")
        s = s.replace("super.extractRenderState(curG", "super.render(curG")
        return s
    edit("host/HostScreen.java", hs)

    def uh(s):
        s = s.replace("import com.musicplayer.MusicPlayerScreen;\nimport com.musicplayer.MusicPlayerScreenLegacy;\n", "")
        s = s.replace("import com.musicplayer.SdlFileDialogs;\n", "")
        s = s.replace("import com.musicplayer.config.MusicPlayerConfig;\n", "")
        s = s.replace("import org.lwjgl.sdl.SDLMouse;\n", "import org.lwjgl.glfw.GLFW;\nimport org.lwjgl.util.tinyfd.TinyFileDialogs;\n")
        s = s.replace("return (SDLMouse.SDL_GetMouseState(null, null) & SDLMouse.SDL_BUTTON_LMASK) != 0;",
                      "return GLFW.glfwGetMouseButton(mc().getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;")
        s = re.sub(r"    @Override public boolean classicScreenOpen\(\) \{.*?\n    \}\n", "    @Override public boolean classicScreenOpen() { return false; }\n", s, flags=re.S)
        s = s.replace("SdlFileDialogs.openFile(title, defaultLocation, onResult);", "onResult.accept(TinyFileDialogs.tinyfd_openFileDialog(title, defaultLocation, (org.lwjgl.PointerBuffer) null, (CharSequence) null, false));")
        s = s.replace("SdlFileDialogs.saveFile(title, defaultLocation, onResult);", "onResult.accept(TinyFileDialogs.tinyfd_saveFileDialog(title, defaultLocation, (org.lwjgl.PointerBuffer) null, (CharSequence) null));")
        s = s.replace("SdlFileDialogs.selectFolder(title, defaultLocation, onResult);", "onResult.accept(TinyFileDialogs.tinyfd_selectFolderDialog(title, defaultLocation));")
        s = re.sub(r"    @Override public boolean classicUiAvailable\(\) \{ return true; \}\n\n    @Override public void openClassicUi\(\) \{.*?\n    \}\n",
                   "    @Override public boolean classicUiAvailable() { return false; }\n\n    @Override public void openClassicUi() { }\n", s, flags=re.S)
        s = s.replace("mc().gui.screen()", "mc().screen").replace("mc().gui.setScreen(", "mc().setScreen(")
        return s
    edit("host/McUiHost.java", uh)


def patch_rl_era(d, ver, cfg):
    """1.21.1 / 1.20.1: ResourceLocation naming, GuiGraphics, pre-1.21.9 input."""
    def edit(path, fn):
        p = os.path.join(d, path)
        wr(p, fn(rd(p)))

    for root, _, files in os.walk(d):
        for f in files:
            if f.endswith(".java") and f != "Ids.java":
                p = os.path.join(root, f)
                s = rd(p)
                s = s.replace("import net.minecraft.resources.Identifier;", "import net.minecraft.resources.ResourceLocation;")
                s = re.sub(r"\bIdentifier\b", "ResourceLocation", s)
                s = s.replace("GuiGraphicsExtractor", "GuiGraphics")
                s = re.sub(r"new DynamicTexture\(\(\) -> [A-Za-z_.]+\.toString\(\), (\w+)\)", lambda m: "new DynamicTexture(%s)" % m.group(1), s)
                if cfg.get("music_getevent"):
                    s = s.replace("music.sound()", "music.getEvent()")
                    s = s.replace("soundEvent.location()", "soundEvent.getLocation()")
                wr(p, s)

    if cfg["rlfactory"]:
        of, rl, parse = "ResourceLocation.parse(s)", "ResourceLocation.fromNamespaceAndPath(namespace, path)", "ResourceLocation.parse(s)"
    else:
        of, rl, parse = "new ResourceLocation(s)", "new ResourceLocation(namespace, path)", "new ResourceLocation(s)"
    ids = '''package com.musicplayer;

import net.minecraft.resources.ResourceLocation;

/**
 * Converts between the string texture/sound ids used by the shared code and game ResourceLocations, and hides
 * the factory methods whose names differ between Minecraft versions.
 */
public final class Ids {
    private Ids() {}

    public static ResourceLocation of(String s) {
        return s == null ? null : %s;
    }

    public static String str(ResourceLocation id) {
        return id == null ? null : id.toString();
    }

    public static ResourceLocation rl(String namespace, String path) {
        return %s;
    }

    public static ResourceLocation parse(String s) {
        return %s;
    }
}
''' % (of, rl, parse)
    wr(os.path.join(d, "Ids.java"), ids)

    for name, content in host_files_rl(cfg).items():
        wr(os.path.join(d, "host", name), content)

    edit("McBootstrap.java", lambda s: patch_bootstrap_rl(s, cfg))
    edit("mixin/MusicManagerMixin.java", lambda s: s.replace("minecraft.gui.screen()", "minecraft.screen"))
    edit("player/FixedSoundInstance.java", lambda s: s.replace("getOrResolve(", "resolve("))
    edit("MusicPlayerMod.java", lambda s: patch_mod_rl(s, cfg))


def patch_bootstrap_rl(s, cfg):
    s = s.replace("mc.gui.screen()", "mc.screen")
    s = rx(s, r"ConfirmLinkScreen\.confirmLinkNow\(mc\.screen, URI\.create\(url\)\);",
           "mc.setScreen(new ConfirmLinkScreen(open -> {\n                if (open) net.minecraft.Util.getPlatform().openUri(url);\n                mc.setScreen(null);\n            }, url, true));", "confirm-link")
    s = s.replace("new DynamicTexture(() -> id.toString(), image)", "new DynamicTexture(image)")
    return s


def patch_mod_rl(s, cfg):
    s = s.replace("import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;", "import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;")
    s = s.replace("KeyMappingHelper.registerKeyMapping(", "KeyBindingHelper.registerKeyBinding(")
    s = s.replace("InputConstants.Type.KEYBOARD", "InputConstants.Type.KEYSYM")
    s = rx(s, r"    private static final KeyMapping\.Category CATEGORY = KeyMapping\.Category\.register\(\s*[^;]*\);",
           '    private static final String CATEGORY = "key.category.musicplayer.main";', "category")
    s = s.replace("boolean focused = client.getWindow().isFocused();",
                  "boolean focused = org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(client.getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_FOCUSED) == org.lwjgl.glfw.GLFW.GLFW_TRUE;")
    s = s.replace("// Window focus is polled each tick (26.3 uses SDL and has no GLFW focus callback)", "// Window focus is polled each tick")
    s = s.replace("client.gui.screen()", "client.screen").replace("client.gui.setScreen(", "client.setScreen(")
    s = s.replace("client.gui.hud.setOverlayMessage", "client.gui.setOverlayMessage")
    return s


# --------------------------------------------------------------------------------------------- common

def common_patches(d):
    """Backports have no classic UI: the open-GUI key only handles the Ore UI."""
    p = os.path.join(d, "MusicPlayerMod.java")
    s = rd(p)
    s = re.sub(r"                boolean newUi = [^\n]*\n", "", s)
    s = rx(s,
           r"                if \(cur == null\) \{\n.*?\n                \} else if \(cur instanceof com\.musicplayer\.host\.HostScreen[^\{]*\{\n                    client\.(?:gui\.)?setScreen\(null\);\n                \}\n",
           "                if (cur == null) {\n                    com.musicplayer.ui.UiHost.get().open(new com.musicplayer.ore.OreHomeScreen());\n                } else if (cur instanceof com.musicplayer.host.HostScreen) {\n                    client.gui.setScreen(null);\n                }\n",
           "open-key")
    wr(p, s)


def sync_project(ver):
    cfg = VERSIONS[ver]
    proj = os.path.join(ROOT, "Backports", ver)
    d = os.path.join(proj, "src", "main", "java", "com", "musicplayer")
    if os.path.isdir(d):
        shutil.rmtree(d)
    os.makedirs(os.path.join(d, "host"))
    for f in COPY_FILES:
        shutil.copy(os.path.join(SRC, f), os.path.join(d, f))
    for sub in COPY_DIRS:
        shutil.copytree(os.path.join(SRC, sub), os.path.join(d, sub))

    common_patches(d)
    if cfg["era"] == "id":
        patch_id_era(d)
    else:
        patch_rl_era(d, ver, cfg)

    res26 = os.path.join(ROOT, "26.3", "src", "main", "resources")
    resv = os.path.join(proj, "src", "main", "resources")
    for root, _, files in os.walk(os.path.join(res26, "assets")):
        for f in files:
            if f == ".DS_Store":
                continue
            src = os.path.join(root, f)
            dst = os.path.join(resv, os.path.relpath(src, res26))
            if not os.path.exists(dst):
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                shutil.copy(src, dst)
    print("synced", ver)


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in VERSIONS:
        print("usage: port_adapter.py <%s>" % "|".join(VERSIONS))
        sys.exit(2)
    sync_project(sys.argv[1])
