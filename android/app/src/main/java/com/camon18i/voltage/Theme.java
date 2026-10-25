package com.camon18i.voltage;

import android.graphics.Color;

/** Colours for the launcher's own screens (settings, drawer, folder window). Home labels stay white on the wallpaper. */
final class Theme {
    static final int DARK = 0, LIGHT = 1, AUTO = 2, CUSTOM = 3;
    static final int[] ACCENTS = {0xFFFF5A4F, 0xFF4F8CFF, 0xFF35C27A, 0xFFA060FF, 0xFFFFA23A, 0xFF22C5C5};
    static final int[] BACKGROUNDS = {0xF20A0B14, 0xF2111A2E, 0xF2231528, 0xF2112419, 0xF23A3F4B, 0xF2F4F5FA};

    static int bg = 0xF20A0B14, panel = 0xF01B1E36, text = 0xFFFFFFFF, sub = 0xFF8E93B0, accent = 0xFFFF5A4F;
    static int ov = 0x00FFFFFF;      // base colour of the translucent overlays (white on dark, black on light)
    static boolean light = false;

    private Theme() {}

    /** Overlay colour with the given alpha (0..255). */
    static int a(int alpha) { return (alpha << 24) | (ov & 0xFFFFFF); }

    static int accentA(int alpha) { return (alpha << 24) | (accent & 0xFFFFFF); }

    private static double lum(int c) {
        return (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0;
    }

    static void apply(int mode, boolean systemNight, int customAccent, int customBg) {
        int b;
        boolean lt;
        accent = ACCENTS[0];
        if (mode == LIGHT) { lt = true; b = BACKGROUNDS[5]; }
        else if (mode == AUTO) { lt = !systemNight; b = lt ? BACKGROUNDS[5] : BACKGROUNDS[0]; }
        else if (mode == CUSTOM) { b = customBg; accent = customAccent; lt = lum(b) > 0.6; }
        else { lt = false; b = BACKGROUNDS[0]; }
        light = lt;
        bg = b;
        if (lt) {
            panel = 0xF0FFFFFF; text = 0xFF14151F; sub = 0xFF5A5F7A; ov = 0x00000000;
        } else {
            panel = mode == CUSTOM ? ((b & 0x00FFFFFF) | 0xF0000000) : 0xF01B1E36;
            text = 0xFFFFFFFF; sub = 0xFF8E93B0; ov = 0x00FFFFFF;
        }
    }
}
