package com.musicplayer.ore;

/** Colour tokens for the OreUI redesign (dark / light), taken from the design file. */
public record OreTheme(int bg, int panel, int card, int cardLip, int hi, int inset, int edge, int sep,
                       int text, int dim, int face, int bhi, int lip, int rowHover, int accent, int like,
                       boolean light) {

    public static final int GREEN = 0xFF3C8527;
    public static final int GREEN_HI = 0xFF52A535;
    public static final int GREEN_LIP = 0xFF2A641C;
    public static final int LIGHT_FACE = 0xFFD0D1D4;
    public static final int LIGHT_HI = 0xFFECEDEE;
    public static final int LIGHT_LIP = 0xFF58585A;

    public static final OreTheme DARK = new OreTheme(
            0xFF2B2C2D, 0xFF3B3C3D, 0xFF3B3C3D, 0x47000000, 0xFF5D5E5F, 0xFF242425, 0xFF1E1E1F, 0xFF4E4F50,
            0xFFFFFFFF, 0xFFC6C7CA, 0xFF48494A, 0xFF5D5E5F, 0xFF2B2B2C, 0xFF4A4B4C, 0xFF6CC349, 0xFFFF5555, false);

    public static final OreTheme LIGHT = new OreTheme(
            0xFFC6C7CA, 0xFFB1B2B5, 0xFFD8D9DC, 0x2E000000, 0xFFF2F3F4, 0xFFA6A7AA, 0xFF1E1E1F, 0xFF9C9DA0,
            0xFF1E1E1F, 0xFF3A3A3C, 0xFFE6E7E9, 0xFFFFFFFF, 0xFF6E6E70, 0xFFE4E5E7, 0xFF1F5A12, 0xFFC42020, true);

    /**
     * The design defines dark and light. The other mod themes (Ocean, Nether, The End, Grass) are the dark
     * theme with its surfaces tinted from the theme's own panel colours and a matching accent.
     */
    public static OreTheme of(String themeId) {
        if (themeId == null || themeId.equals("dark")) return DARK;
        if (themeId.equals("light")) return LIGHT;
        com.musicplayer.theme.ColorTheme t = com.musicplayer.theme.ColorTheme.get(themeId);
        if (t == com.musicplayer.theme.ColorTheme.DARK) return DARK;
        int deep = t.panelInner() | 0xFF000000, panel = t.panelBg() | 0xFF000000, border = t.panelBorder() | 0xFF000000;
        int accent = switch (themeId) {
            case "ocean" -> 0xFF44DDFF;
            case "nether" -> 0xFFFF7744;
            case "end" -> 0xFFBB88FF;
            case "grass" -> 0xFF55FF55;
            default -> DARK.accent();
        };
        return new OreTheme(
                mix(deep, panel, 0.35f), mix(panel, border, 0.35f), mix(panel, border, 0.25f), 0x47000000,
                mix(border, 0xFFFFFFFF, 0.25f), mix(deep, 0xFF000000, 0.35f), mix(deep, 0xFF000000, 0.6f), mix(panel, border, 0.7f),
                0xFFFFFFFF, mix(border, 0xFFFFFFFF, 0.7f), mix(panel, border, 0.6f), mix(border, 0xFFFFFFFF, 0.2f),
                mix(deep, 0xFF000000, 0.4f), mix(panel, border, 0.55f), accent, 0xFFFF5555, false);
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
