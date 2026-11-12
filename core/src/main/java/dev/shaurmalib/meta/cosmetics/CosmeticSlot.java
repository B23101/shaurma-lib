package dev.shaurmalib.meta.cosmetics;

import java.util.List;

/**
 * Місце на гравці. Межі параметрів — жорсткі (діють і на сервері), щоб косметика не заважала іншим:
 * улюбленець не закриває огляд, капелюх не стирчить на пів блока, аура не заливає всю кімнату.
 * Параметри в блоках; модель посадки — та сама, що була в гардеробі мафії.
 */
public enum CosmeticSlot {
    HEAD("Голова", 0xA77BFF, List.of(
            new ParamSpec("y", "Посадка", -.10f, .35f, 0f),
            new ParamSpec("scale", "Розмір", .70f, 1.30f, 1f))),
    BODY("Тіло", 0x4DA3FF, List.of(
            new ParamSpec("scale", "Розмір", .80f, 1.20f, 1f))),
    LEGS("Ноги", 0x3DDC84, List.of(
            new ParamSpec("scale", "Розмір", .80f, 1.20f, 1f))),
    BACK("Спина", 0xFF5FD2, List.of(
            new ParamSpec("y", "Висота", -.25f, .35f, 0f),
            new ParamSpec("z", "Відстань", -.25f, .40f, 0f),
            new ParamSpec("scale", "Розмір", .35f, .90f, .75f))),
    PET("Улюбленці", 0x55E5FF, List.of(
            new ParamSpec("x", "Вбік", -1.1f, 1.1f, .75f),
            new ParamSpec("y", "Висота", 1.4f, 2.3f, 1.8f),
            new ParamSpec("z", "Вперед", -1.0f, 1.0f, 0f),
            new ParamSpec("scale", "Розмір", .35f, .80f, .55f),
            new ParamSpec("bob", "Розмах", 0f, .20f, .08f),
            new ParamSpec("speed", "Швидкість", .5f, 2f, 1f))),
    AURA("Аури", 0xE5383B, List.of(
            new ParamSpec("scale", "Розмір", .60f, 1.40f, 1f)));

    /** Найбільша горизонтальна відстань улюбленця від гравця. */
    public static final float PET_MAX_RADIUS = 1.2f;

    public final String title;
    public final int accent;
    public final List<ParamSpec> params;

    CosmeticSlot(String title, int accent, List<ParamSpec> params) {
        this.title = title;
        this.accent = accent;
        this.params = params;
    }

    public ParamSpec param(String key) {
        for (ParamSpec p : params) if (p.key().equals(key)) return p;
        return null;
    }

    public static CosmeticSlot byName(String s) {
        for (CosmeticSlot c : values()) if (c.name().equalsIgnoreCase(s)) return c;
        return null;
    }
}
