package dev.shaurmalib.anim.clip;

import java.util.List;

/**
 * Пропс — додаткова модель (аптечка, ножиці, шприц…), прив'язана до кістки скелета на час кліпа.
 *
 * Звідки береться геометрія — одне з двох:
 * <ul>
 *   <li>{@code model} — Bedrock geo ({@code ns:path} до {@code .geo.json}) з власною {@code texture};
 *       кістки пропса (кришка, бинт) анімуються треками кліпа з тими ж іменами.
 *       Координати — абсолютні з Blockbench-сцени, рушій сам віднімає півот батьківської кістки.</li>
 *   <li>{@code item} — звичайний предмет гри ({@code ns:item}), намальований у кістці.</li>
 * </ul>
 *
 * @param id          ім'я пропса в кліпі (зазвичай ім'я групи в Blockbench)
 * @param parentBone  кістка скелета ({@code right_arm}, {@code head}...)
 * @param visibility  ступінчасті ключі видимості; порожньо — видимий увесь кліп
 * @param hideHeld    {@code right}/{@code left}/{@code both}/null — сховати ванільний предмет у руці,
 *                    поки пропс видимий (пропс і є той предмет, але анімований)
 */
public record PropSpec(String id, String parentBone, String model, String texture, String item,
                       List<VisKey> visibility, String hideHeld, boolean emissive) {

    public record VisKey(float time, boolean visible) {}

    public boolean visibleAt(float t) {
        if (visibility == null || visibility.isEmpty()) return true;
        boolean v = visibility.get(0).visible();
        if (t < visibility.get(0).time()) return v;
        for (VisKey k : visibility) {
            if (k.time() <= t) v = k.visible(); else break;
        }
        return v;
    }

    public boolean hidesRight() { return "right".equals(hideHeld) || "both".equals(hideHeld); }

    public boolean hidesLeft() { return "left".equals(hideHeld) || "both".equals(hideHeld); }
}
