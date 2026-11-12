package dev.shaurmalib.render;

/** Операції з кольором 0xAARRGGBB. Чисті, без GL/MC. */
public final class Argb {
    private Argb() {}
    public static int of(int a, int r, int g, int b) { return (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255); }
    public static int alpha(int argb) { return argb >>> 24; }
    public static int withAlpha(int argb, float alpha01) {
        return (Math.round(Math.max(0f, Math.min(1f, alpha01)) * 255) << 24) | (argb & 0xFFFFFF);
    }
    public static int mulAlpha(int argb, float f) { return withAlpha(argb, alpha(argb) / 255f * f); }
    public static int lerp(int from, int to, float t) {
        int a = Math.round(alpha(from) + (alpha(to) - alpha(from)) * t);
        int r = Math.round(((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * t);
        int g = Math.round(((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * t);
        int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * t);
        return of(a, r, g, b);
    }
}
