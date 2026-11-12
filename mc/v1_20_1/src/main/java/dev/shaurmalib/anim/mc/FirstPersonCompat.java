package dev.shaurmalib.anim.mc;

import com.mojang.logging.LogUtils;
import dev.shaurmalib.anim.ShaurmaAnim;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Автодетект модів першої особи (клей лоадера дає {@code ModList::isLoaded} / {@code FabricLoader::isModLoaded}).
 *
 * <ul>
 *   <li><b>First-person Model</b> ({@code firstperson}) малює справжнє тіло з камери — анімовані руки рушія
 *       були б другою парою рук. Вимикаємо руки, камеру за головою лишаємо: тіло, яке видно, анімує той самий
 *       рушій, і камера в оці цього тіла — саме те, що треба (впав — бачиш з підлоги).</li>
 *   <li><b>Real Camera</b> ({@code realcamera}) і малює тіло, і САМ ставить камеру в голову моделі —
 *       вимикаємо і руки, і камеру рушія, інакше два міксини {@code Camera.setup} смикають камеру.</li>
 * </ul>
 * Мод може перекрити будь-що явно: {@link ShaurmaAnim#setFirstPersonArms}, {@link ShaurmaAnim#setHeadCamera}.
 */
public final class FirstPersonCompat {

    private FirstPersonCompat() {}

    /** Малюють тіло від першої особи. */
    public static final List<String> BODY_MODS = List.of("firstperson", "firstpersonmod", "firstpersonmodel", "realcamera");
    /** Самі ведуть камеру за головою моделі. */
    public static final List<String> CAMERA_MODS = List.of("realcamera");

    public static void detect(Predicate<String> isLoaded) {
        List<String> body = new ArrayList<>(), cam = new ArrayList<>();
        for (String id : BODY_MODS) if (safe(isLoaded, id)) body.add(id);
        for (String id : CAMERA_MODS) if (safe(isLoaded, id)) cam.add(id);
        if (body.isEmpty() && cam.isEmpty()) return;
        String note = String.join(",", body);
        ShaurmaAnim.applyFirstPersonCompat(!body.isEmpty(), !cam.isEmpty(), note);
        LogUtils.getLogger().info("[ShaurmaAnim] first-person мод: {} → руки рушія {}, камера за головою {}",
            note, body.isEmpty() ? "лишаються" : "вимкнено", cam.isEmpty() ? "лишається" : "вимкнено");
    }

    private static boolean safe(Predicate<String> p, String id) {
        try {
            return p.test(id);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
