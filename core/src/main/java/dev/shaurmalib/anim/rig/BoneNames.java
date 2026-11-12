package dev.shaurmalib.anim.rig;

import java.util.Locale;
import java.util.Map;

/**
 * Канонічні імена кісток: {@code rightArm}, {@code RightArm}, {@code right_arm}, {@code right-arm}
 * → {@code right_arm}. Аніматори пишуть як звикли (PlayerAnimator — camelCase, GeckoLib — як завгодно),
 * а рушій порівнює одне ім'я. Одрук в імені — найчастіша причина «рука не рухається», тому
 * нормалізація тут, а не в кожному парсері.
 */
public final class BoneNames {

    private BoneNames() {}

    /** Синоніми для ванільного скелета гравця. */
    private static final Map<String, String> PLAYER_ALIASES = Map.ofEntries(
        Map.entry("arm_right", "right_arm"), Map.entry("arm_left", "left_arm"),
        Map.entry("leg_right", "right_leg"), Map.entry("leg_left", "left_leg"),
        Map.entry("item_right", "right_item"), Map.entry("item_left", "left_item"),
        Map.entry("right_hand", "right_item"), Map.entry("left_hand", "left_item"),
        Map.entry("biped_right_arm", "right_arm"), Map.entry("biped_left_arm", "left_arm"),
        Map.entry("biped_right_leg", "right_leg"), Map.entry("biped_left_leg", "left_leg"),
        Map.entry("biped_head", "head"), Map.entry("biped_body", "torso"),
        Map.entry("chest", "torso"), Map.entry("root", "body"));

    /** camelCase/PascalCase/дефіси → snake_case у нижньому регістрі. */
    public static String snake(String name) {
        if (name == null) return "";
        String n = name.trim();
        StringBuilder b = new StringBuilder(n.length() + 4);
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0 && b.length() > 0 && b.charAt(b.length() - 1) != '_'
                    && (Character.isLowerCase(n.charAt(i - 1)) || Character.isDigit(n.charAt(i - 1)))) b.append('_');
                b.append(Character.toLowerCase(c));
            } else if (c == '-' || c == ' ' || c == '.') {
                b.append('_');
            } else {
                b.append(c);
            }
        }
        return b.toString().toLowerCase(Locale.ROOT);
    }

    /** Канонічне ім'я для ванільного скелета гравця (з урахуванням синонімів). */
    public static String player(String name) {
        String s = snake(name);
        return PLAYER_ALIASES.getOrDefault(s, s);
    }
}
