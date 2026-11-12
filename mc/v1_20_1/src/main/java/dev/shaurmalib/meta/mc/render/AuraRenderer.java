package dev.shaurmalib.meta.mc.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Аури — косметика-ефекти навколо гравця (не моделі). Усе малюється кольоровими напівпрозорими
 * чотирикутниками (шар MetaRenderTypes.GLOW), тому ніяких текстур чи моделей не потрібно.
 *
 * Координати локальні: початок — під ногами гравця, y — угору, площина землі — XZ.
 * Кожен чотирикутник пишеться з обох боків, щоб ефект було видно і зверху, і знизу незалежно від відсікання граней.
 *
 * Зірка Сатани: світне коло на землі, два кільця з рунами, що обертаються проти ходу зірки, перевернута
 * пентаграма, що «вимальовується» при появі, полум'я на променях, хвиля, що розходиться, світловий стовп
 * навколо гравця й промені, що піднімаються. Частинки додає {@link #particles}.
 */
public final class AuraRenderer {

    private static final float TAU = (float) (Math.PI * 2);
    /** Підйом над землею, щоб не миготіло (z-fighting). */
    private static final float Y0 = .02f;
    private static final float PLAYER_H = 1.9f;

    private AuraRenderer() {}

    // ══════════════════════════════════════════════════════════════════════
    //  Зірка Сатани
    // ══════════════════════════════════════════════════════════════════════

    /**
     * @param t      час у тіках (з дробовою частиною)
     * @param rgb    колір аури (0xRRGGBB)
     * @param scale  розмір аури (1 = радіус ≈ 1.15 блока)
     * @param appear поява 0..1: від нуля вимальовується пентаграма й «вистрибує» коло
     * @param full   повна деталізація (поблизу) або спрощена (далеко)
     */
    /** flat = лише те, що лежить на землі (без полум'я, стовпа, променів): для власного виду від першої особи. */
    public static void satanStar(PoseStack m, VertexConsumer vc, float t, int rgb, float scale, float appear, boolean full, boolean flat) {
        if (appear <= .01f) return;
        Matrix4f mat = m.last().pose();
        float fade = clamp01(appear * 1.6f);
        float R = 1.15f * scale * easeOutBack(clamp01(appear));
        float pulse = .88f + .12f * (float) Math.sin(t * .13f);
        int hot = mix(rgb, 0xFFFFFF, .5f);
        int seg = full ? 64 : 32;

        // 1. Світне коло на землі
        disc(vc, mat, R * 1.04f, seg, Y0, col(rgb, .26f * fade * pulse), col(rgb, .03f * fade));

        // 2. Кільця й ореол між ними
        ring(vc, mat, R * .80f, R * 1.06f, seg, Y0 + .002f, col(rgb, .13f * fade * pulse));
        ring(vc, mat, R * .965f, R * 1.0f, seg, Y0 + .006f, col(hot, .95f * fade));
        ring(vc, mat, R * .86f, R * .885f, seg, Y0 + .006f, col(hot, .85f * fade));

        // 3. Руни між кільцями (крутяться проти ходу зірки, мерехтять)
        float rr = -t * .012f;
        int glyphs = 12;
        float rIn = R * .895f, rOut = R * .955f, rMid = (rIn + rOut) / 2f, gw = .028f * scale;
        for (int i = 0; i < glyphs; i++) {
            float a = rr + i * TAU / glyphs;
            float fl = .5f + .5f * (float) Math.sin(t * .21f + i * 2.1f);
            int c = col(hot, (.35f + .5f * fl) * fade);
            switch (i % 3) {
                case 0 -> {
                    line(vc, mat, cos(a) * rIn, sin(a) * rIn, cos(a) * rOut, sin(a) * rOut, gw, Y0 + .008f, c);
                    line(vc, mat, cos(a - .06f) * rMid, sin(a - .06f) * rMid, cos(a + .06f) * rMid, sin(a + .06f) * rMid, gw, Y0 + .008f, c);
                }
                case 1 -> {
                    line(vc, mat, cos(a - .05f) * rIn, sin(a - .05f) * rIn, cos(a + .05f) * rOut, sin(a + .05f) * rOut, gw, Y0 + .008f, c);
                    line(vc, mat, cos(a + .05f) * rIn, sin(a + .05f) * rIn, cos(a - .05f) * rOut, sin(a - .05f) * rOut, gw, Y0 + .008f, c);
                }
                default -> {
                    float d = .022f * scale;
                    float x = cos(a) * rMid, z = sin(a) * rMid;
                    quad(vc, mat, x - d, Y0 + .008f, z - d, c, x + d, Y0 + .008f, z - d, c, x + d, Y0 + .008f, z + d, c, x - d, Y0 + .008f, z + d, c);
                    line(vc, mat, cos(a) * rIn, sin(a) * rIn, cos(a) * (rMid - .03f * scale), sin(a) * (rMid - .03f * scale), gw, Y0 + .008f, c);
                }
            }
        }

        // 4. Пентаграма: п'ять вершин, кожна з'єднана з другою через одну; ліній «вимальовуються» по черзі
        float rot = t * .012f + .3f;
        float rs = R * .855f;
        float[] px = new float[5], pz = new float[5];
        for (int k = 0; k < 5; k++) {
            float a = rot + k * TAU / 5f;
            px[k] = cos(a) * rs;
            pz[k] = sin(a) * rs;
        }
        for (int k = 0; k < 5; k++) {
            int j = (k + 2) % 5;
            float lf = clamp01(appear * 6f - k * .9f);
            if (lf <= 0f) continue;
            float x2 = px[k] + (px[j] - px[k]) * lf, z2 = pz[k] + (pz[j] - pz[k]) * lf;
            line(vc, mat, px[k], pz[k], x2, z2, .17f * scale, Y0 + .010f, col(rgb, .14f * fade * pulse));
            line(vc, mat, px[k], pz[k], x2, z2, .09f * scale, Y0 + .012f, col(rgb, .38f * fade));
            line(vc, mat, px[k], pz[k], x2, z2, .055f * scale * pulse, Y0 + .014f, col(hot, .98f * fade));
        }

        // 5. Хвиля, що розходиться від центра
        float ph = (t % 70f) / 70f;
        float rw = R * (.15f + 1.3f * ph);
        ring(vc, mat, rw - .025f * scale, rw + .025f * scale, seg, Y0 + .016f, col(hot, (1f - ph) * (1f - ph) * .6f * fade));

        // 6. Полум'я на променях зірки
        float fa = clamp01(appear * 2.5f - 1.2f);
        if (fa > 0f && !flat) {
            for (int k = 0; k < 5; k++) {
                float h = scale * (.30f + .22f * (.5f + .5f * (float) Math.sin(t * .55f + k * 1.9f)) + .08f * (float) Math.sin(t * 1.3f + k)) * fa;
                flame(vc, mat, px[k], pz[k], h, .17f * scale, t * .02f + k, rgb, hot, fade, t + k * 3f);
            }
        }

        // 7. Світловий стовп навколо гравця + промені, що піднімаються
        if (appear > .3f && !flat) {
            float rc = R * .9f * (1f + .02f * (float) Math.sin(t * .2f));
            int cs = full ? 24 : 12;
            for (int i = 0; i < cs; i++) {
                float a0 = i * TAU / cs, a1 = (i + 1) * TAU / cs;
                float x0 = cos(a0) * rc, z0 = sin(a0) * rc, x1 = cos(a1) * rc, z1 = sin(a1) * rc;
                int cb = col(rgb, .17f * fade * pulse), ct = col(rgb, 0f);
                quad(vc, mat, x0, Y0, z0, cb, x1, Y0, z1, cb, x1, Y0 + PLAYER_H, z1, ct, x0, Y0 + PLAYER_H, z0, ct);
            }
            if (full) {
                for (int i = 0; i < 8; i++) {
                    float a = i * TAU / 8f + t * .01f;
                    float prog = (t * .018f + i * .137f) % 1f;
                    float yc = Y0 + prog * PLAYER_H, len = .55f;
                    float cx = cos(a) * rc * .97f, cz = sin(a) * rc * .97f;
                    float tx = -sin(a) * .035f * scale, tz = cos(a) * .035f * scale;
                    float peak = .6f * (1f - prog) * fade;
                    float yLow = Math.max(Y0, yc - len), yHigh = yc + len * .3f;
                    quad(vc, mat, cx - tx, yLow, cz - tz, col(hot, 0f), cx + tx, yLow, cz + tz, col(hot, 0f),
                            cx + tx, yc, cz + tz, col(hot, peak), cx - tx, yc, cz - tz, col(hot, peak));
                    quad(vc, mat, cx - tx, yc, cz - tz, col(hot, peak), cx + tx, yc, cz + tz, col(hot, peak),
                            cx + tx, yHigh, cz + tz, col(hot, 0f), cx - tx, yHigh, cz - tz, col(hot, 0f));
                }
            }
        }
    }

    /** Вибір виду аури: 0 — Зірка Сатани, 1 — Хеловін. */
    public static void draw(int type, PoseStack m, VertexConsumer vc, float t, int rgb, float scale, float appear, boolean full, boolean flat) {
        if (type == 1) halloween(m, vc, t, rgb, scale, appear, full, flat);
        else satanStar(m, vc, t, rgb, scale, appear, full, flat);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Хеловін: павутиння на землі, дві ліхтарні свічки-язики, кажани, що кружляють, і вогники, що здіймаються
    //  (шар адитивний, тому кольори світлі: чорного тут не видно)
    // ══════════════════════════════════════════════════════════════════════
    private static final int PURPLE = 0xB45CFF;

    public static void halloween(PoseStack m, VertexConsumer vc, float t, int rgb, float scale, float appear, boolean full, boolean flat) {
        if (appear <= .01f) return;
        Matrix4f mat = m.last().pose();
        float fade = clamp01(appear * 1.6f);
        float R = 1.15f * scale * easeOutBack(clamp01(appear));
        float pulse = .88f + .12f * (float) Math.sin(t * .11f);
        int hot = mix(rgb, 0xFFFFFF, .45f);
        int seg = full ? 48 : 24;

        // 1. Тепле коло й подвійне кільце
        disc(vc, mat, R * 1.02f, seg, Y0, col(rgb, .24f * fade * pulse), col(PURPLE, .04f * fade));
        ring(vc, mat, R * .97f, R * 1.02f, seg, Y0 + .006f, col(hot, .9f * fade));
        ring(vc, mat, R * .90f, R * .915f, seg, Y0 + .006f, col(PURPLE, .7f * fade));

        // 2. Павутиння: 8 променів і 4 кільця-ланцюжки між ними (повільно обертається)
        int spokes = 8;
        float rot = t * .004f;
        float rw = R * .88f;
        for (int i = 0; i < spokes; i++) {
            float a = rot + i * TAU / spokes;
            line(vc, mat, 0, 0, cos(a) * rw, sin(a) * rw, .035f * scale, Y0 + .010f, col(hot, .55f * fade));
        }
        for (int ring = 1; ring <= 4; ring++) {
            float r = rw * ring / 4f;
            for (int i = 0; i < spokes; i++) {
                float a0 = rot + i * TAU / spokes, a1 = rot + (i + 1) * TAU / spokes;
                // Дуга провисає до центра, як справжнє павутиння.
                float mid = (a0 + a1) / 2f, rm = r * .93f;
                float x0 = cos(a0) * r, z0 = sin(a0) * r, xm = cos(mid) * rm, zm = sin(mid) * rm, x1 = cos(a1) * r, z1 = sin(a1) * r;
                line(vc, mat, x0, z0, xm, zm, .022f * scale, Y0 + .012f, col(hot, .5f * fade));
                line(vc, mat, xm, zm, x1, z1, .022f * scale, Y0 + .012f, col(hot, .5f * fade));
            }
        }

        // 3. Язики полум'я вздовж зовнішнього кільця: помаранчеві й фіолетові по черзі
        float fa = clamp01(appear * 2.5f - 1.2f);
        if (fa > 0f && !flat) {
            int n = 8;
            for (int k = 0; k < n; k++) {
                float a = k * TAU / n + .2f;
                float h = scale * (.26f + .2f * (.5f + .5f * (float) Math.sin(t * .5f + k * 1.7f))) * fa;
                int c = (k % 2 == 0) ? rgb : PURPLE;
                flame(vc, mat, cos(a) * R * .94f, sin(a) * R * .94f, h, .14f * scale, t * .02f + k, c, mix(c, 0xFFFFFF, .5f), fade, t + k * 3f);
            }
        }

        // 4. Кажани кружляють довкола гравця, змахуючи крилами
        if (appear > .3f && !flat) {
            int bats = full ? 5 : 3;
            for (int i = 0; i < bats; i++) {
                float a = t * (.035f + .004f * i) + i * TAU / bats;
                float rb = R * (.72f + .12f * (float) Math.sin(t * .03f + i));
                float yb = Y0 + .9f + .6f * (.5f + .5f * (float) Math.sin(t * .045f + i * 2.3f));
                bat(vc, mat, cos(a) * rb, yb, sin(a) * rb, a, .3f * scale, (float) Math.sin(t * .7f + i * 1.3f), fade, i % 2 == 0 ? PURPLE : hot);
            }
        }

        // 5. Вогники, що здіймаються стовпом
        if (appear > .3f && full && !flat) {
            for (int i = 0; i < 10; i++) {
                float a = i * TAU / 10f + t * .02f;
                float prog = (t * .012f + i * .173f) % 1f;
                float rr = R * (.35f + .45f * ((i * 37) % 10) / 10f);
                float x = cos(a) * rr, z = sin(a) * rr, y = Y0 + prog * PLAYER_H * 1.1f, d = .035f * scale;
                int c = col(i % 3 == 0 ? PURPLE : hot, (1f - prog) * .9f * fade);
                quad(vc, mat, x - d, y, z, c, x, y + d * 1.6f, z, c, x + d, y, z, c, x, y - d * 1.6f, z, c);
            }
        }
    }

    /** Плоский кажан: тіло з вушками й два крила, що змахують. a — кут на орбіті (політ уздовж дотичної). */
    private static void bat(VertexConsumer vc, Matrix4f mat, float x, float y, float z, float a, float span, float flap, float fade, int rgb) {
        float rx = cos(a), rz = sin(a);        // радіально (крила)
        float tx = -sin(a), tz = cos(a);      // уздовж польоту
        int c = col(rgb, .95f * fade), ce = col(rgb, .25f * fade);
        float up = flap * span * .55f;
        for (int side = -1; side <= 1; side += 2) {
            float ex = x + rx * span * side, ez = z + rz * span * side;
            float mx = x + rx * span * .55f * side, mz = z + rz * span * .55f * side;
            // Крило: від тіла до кінчика з вигином і «пальцями»
            quad(vc, mat, x + tx * .07f, y + .02f, z + tz * .07f, c,
                    mx + tx * .10f, y + up * .5f + .03f, mz + tz * .10f, c,
                    ex, y + up, ez, ce,
                    mx - tx * .06f, y + up * .5f - .05f, mz - tz * .06f, c);
            quad(vc, mat, x - tx * .07f, y + .02f, z - tz * .07f, c,
                    mx - tx * .06f, y + up * .5f - .05f, mz - tz * .06f, c,
                    ex - tx * .03f, y + up - .03f, ez - tz * .03f, ce,
                    x - tx * .12f, y - .05f, z - tz * .12f, c);
        }
        // Тіло та вушка
        float b = span * .16f;
        quad(vc, mat, x + tx * b, y, z + tz * b, c, x, y + b * 1.3f, z, c, x - tx * b, y, z - tz * b, c, x, y - b * 1.3f, z, c);
        quad(vc, mat, x + tx * b, y + b * .6f, z + tz * b, c, x + tx * b * 1.2f, y + b * 2f, z + tz * b * 1.2f, c, x + tx * b * .4f, y + b * 1.1f, z + tz * b * .4f, c, x + tx * b * .4f, y + b * .6f, z + tz * b * .4f, c);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Частинки (викликається раз на тік для кожного гравця з аурою)
    // ══════════════════════════════════════════════════════════════════════
    public static void particles(ClientLevel w, double x, double y, double z, int rgb, float scale, Random rnd, int type) {
        particles(w, x, y, z, rgb, scale, rnd);
        if (type == 1 && rnd.nextInt(3) == 0) {
            float a = rnd.nextFloat() * TAU, r = 1.15f * scale * (.2f + .7f * rnd.nextFloat());
            w.addParticle(ParticleTypes.WITCH, x + cos(a) * r, y + .1 + rnd.nextDouble() * 1.4, z + sin(a) * r, 0, .02, 0);
        }
    }

    public static void particles(ClientLevel w, double x, double y, double z, int rgb, float scale, Random rnd) {
        float R = 1.15f * scale;
        Vector3f c = new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
        int n = rnd.nextInt(3) == 0 ? 2 : 1;
        for (int i = 0; i < n; i++) {
            float a = rnd.nextFloat() * TAU;
            float r = R * (.15f + .8f * (float) Math.sqrt(rnd.nextFloat()));
            w.addParticle(new DustParticleOptions(c, .7f + rnd.nextFloat() * .6f),
                    x + cos(a) * r, y + .05, z + sin(a) * r, 0, .03 + rnd.nextDouble() * .03, 0);
        }
        // Тепла аура (червона/помаранчева) додатково «іскрить» вогнем по зовнішньому кільцю.
        boolean warm = ((rgb >> 16) & 255) > 170 && ((rgb >> 8) & 255) < 130;
        if (warm && rnd.nextInt(3) == 0) {
            float a = rnd.nextFloat() * TAU;
            w.addParticle(ParticleTypes.FLAME, x + cos(a) * R * .86f, y + .05, z + sin(a) * R * .86f, 0, .015, 0);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Примітиви
    // ══════════════════════════════════════════════════════════════════════

    /** Вертикальне полум'я: два схрещені плоскі язики з прозорою вершиною та легким коливанням. */
    private static void flame(VertexConsumer vc, Matrix4f mat, float x, float z, float h, float w, float phi0,
                              int rgb, int hot, float fade, float sway) {
        if (h <= .01f) return;
        float sx = (float) Math.sin(sway * .35f) * .05f * h, sz = (float) Math.cos(sway * .29f) * .05f * h;
        int cb = col(hot, .95f * fade), ct = col(rgb, 0f);
        for (int q = 0; q < 2; q++) {
            float phi = phi0 + q * (TAU / 4f);
            float dx = cos(phi) * w, dz = sin(phi) * w;
            float tx = dx * .2f, tz = dz * .2f;
            quad(vc, mat, x - dx, Y0 + .02f, z - dz, cb, x + dx, Y0 + .02f, z + dz, cb,
                    x + tx + sx, Y0 + .02f + h, z + tz + sz, ct, x - tx + sx, Y0 + .02f + h, z - tz + sz, ct);
        }
    }

    /** Пласка лінія товщини w у площині y. */
    private static void line(VertexConsumer vc, Matrix4f mat, float x1, float z1, float x2, float z2, float w, float y, int c) {
        float dx = x2 - x1, dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4f) return;
        float nx = -dz / len * w * .5f, nz = dx / len * w * .5f;
        quad(vc, mat, x1 + nx, y, z1 + nz, c, x1 - nx, y, z1 - nz, c, x2 - nx, y, z2 - nz, c, x2 + nx, y, z2 + nz, c);
    }

    /** Плоске кільце між радіусами r0 і r1. */
    private static void ring(VertexConsumer vc, Matrix4f mat, float r0, float r1, int seg, float y, int c) {
        for (int i = 0; i < seg; i++) {
            float a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            quad(vc, mat, cos(a0) * r0, y, sin(a0) * r0, c, cos(a0) * r1, y, sin(a0) * r1, c,
                    cos(a1) * r1, y, sin(a1) * r1, c, cos(a1) * r0, y, sin(a1) * r0, c);
        }
    }

    /** Заповнений диск із градієнтом прозорості від центра (cc) до краю (ce). */
    private static void disc(VertexConsumer vc, Matrix4f mat, float r, int seg, float y, int cc, int ce) {
        for (int i = 0; i < seg; i++) {
            float a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            quad(vc, mat, 0, y, 0, cc, cos(a0) * r, y, sin(a0) * r, ce, cos(a1) * r, y, sin(a1) * r, ce, 0, y, 0, cc);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat, float x, float y, float z, int argb) {
        vc.vertex(mat, x, y, z).color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255).endVertex();
    }

    /** Чотирикутник із кольором у кожній вершині; пишеться з обох боків. */
    private static void quad(VertexConsumer vc, Matrix4f mat,
                             float x1, float y1, float z1, int c1, float x2, float y2, float z2, int c2,
                             float x3, float y3, float z3, int c3, float x4, float y4, float z4, int c4) {
        vertex(vc, mat, x1, y1, z1, c1);
        vertex(vc, mat, x2, y2, z2, c2);
        vertex(vc, mat, x3, y3, z3, c3);
        vertex(vc, mat, x4, y4, z4, c4);
        vertex(vc, mat, x4, y4, z4, c4);
        vertex(vc, mat, x3, y3, z3, c3);
        vertex(vc, mat, x2, y2, z2, c2);
        vertex(vc, mat, x1, y1, z1, c1);
    }

    // ── Дрібні утиліти ────────────────────────────────────────────────────
    private static float cos(float a) { return (float) Math.cos(a); }

    private static float sin(float a) { return (float) Math.sin(a); }

    private static float clamp01(float v) { return v < 0f ? 0f : Math.min(1f, v); }

    private static int col(int rgb, float alpha) {
        return ((int) (clamp01(alpha) * 255f + .5f) << 24) | (rgb & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1f - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1f - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1f - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1f, u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }
}
