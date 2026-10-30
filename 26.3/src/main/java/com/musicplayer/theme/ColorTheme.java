package com.musicplayer.theme;

import java.util.LinkedHashMap;
import java.util.Map;

public record ColorTheme(String id, String name, int panelBg, int panelBorder, int panelInner,
                         int titleBarBg, int titleBarBorder, int titleText, int accent,
                         int text, int dimText, int listBg, int listHover, int listSelected) {

    public static final ColorTheme DARK = new ColorTheme("dark", "Dark Minecraft",
        0xFF2D2D2D, 0xFF3C3C3C, 0xFF1E1E1E,
        0xFF383838, 0xFF555555, 0xFFFFFFFF, 0xFF55FF55,
        0xFFFFFFFF, 0xFFAAAAAA, 0xFF252525, 0xFF353535, 0xFF404040);

    public static final ColorTheme LIGHT = new ColorTheme("light", "Light",
        0xFFF0F0F0, 0xFFCCCCCC, 0xFF999999,
        0xFFE0E0E0, 0xFFBBBBBB, 0xFF222222, 0xFF228822,
        0xFF222222, 0xFF666666, 0xFFFAFAFA, 0xFFE8E8E8, 0xFFDDDDDD);

    public static final ColorTheme OCEAN = new ColorTheme("ocean", "Ocean",
        0xFF1A2B4A, 0xFF2A4A6A, 0xFF0F1F3A,
        0xFF1F3A5A, 0xFF3A6A8A, 0xFFCCEEFF, 0xFF44DDFF,
        0xFFCCEEFF, 0xFF88AACC, 0xFF152540, 0xFF1F3550, 0xFF2A4A6A);

    public static final ColorTheme NETHER = new ColorTheme("nether", "Nether",
        0xFF2A1010, 0xFF4A1A1A, 0xFF1A0808,
        0xFF3A1515, 0xFF6A2020, 0xFFFF6666, 0xFFFF4400,
        0xFFFF6666, 0xFFAA5555, 0xFF220A0A, 0xFF351010, 0xFF4A1515);

    public static final ColorTheme END = new ColorTheme("end", "The End",
        0xFF1A1A2A, 0xFF2A2A40, 0xFF0F0F1A,
        0xFF222238, 0xFF3A3A55, 0xFFCCAAFF, 0xFFAA66FF,
        0xFFCCAAFF, 0xFF8866AA, 0xFF151525, 0xFF202038, 0xFF2A2A45);

    public static final ColorTheme GRASS = new ColorTheme("grass", "Grass",
        0xFF1A3A1A, 0xFF2A5A2A, 0xFF0F2A0F,
        0xFF1F4A1F, 0xFF3A7A3A, 0xFFCCFFCC, 0xFF55FF55,
        0xFFCCFFCC, 0xFF88CC88, 0xFF152515, 0xFF1F3A1F, 0xFF2A4A2A);

    private static final Map<String, ColorTheme> PRESETS = new LinkedHashMap<>();
    static {
        register(DARK);
        register(LIGHT);
        register(OCEAN);
        register(NETHER);
        register(END);
        register(GRASS);
    }

    private static void register(ColorTheme theme) {
        PRESETS.put(theme.id(), theme);
    }

    public static ColorTheme get(String id) {
        return PRESETS.getOrDefault(id, DARK);
    }

    public static Map<String, ColorTheme> all() {
        return Map.copyOf(PRESETS);
    }

    public boolean textHasShadow() {
        return !"light".equals(this.id());
    }
}
