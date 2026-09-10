package dev.shaurmalib.common.item;

/**
 * Яку РЕАЛЬНУ руку гравця рушій може заміняти анімованою кісткою предмета.
 * <p>
 * Джерело реального механізму в snipers_shaurma (перевірено по коду, план
 * помилково описував інший): {@code AnimatedGeoItemRenderer.renderRecursively}
 * ЗАВЖДИ ховає обидві кістки з іменами "LeftArm" і "RightArm" у моделі
 * предмета і підставляє замість них модель руки гравця (скін) — це
 * не залежить від {@link HandSlot}, який предмет реально тримає гравець.
 * Окремо, {@code ItemLeftArmHideHandler} (клієнтський, через
 * {@code RenderHandEvent}) ховає РЕАЛЬНУ ліву руку гравця (тільки OFF_HAND!)
 * якщо тримається предмет із жорсткого {@code Set<Class<?>>} whitelist і
 * зараз грає non-idle анімація на контролері "mainCtrl". Права рука (MAIN_HAND)
 * такого механізму в оригіналі НЕ має взагалі.
 * <p>
 * Бібліотека узагальнює це на обидва слоти явно.
 */
public enum HandSlot {
    MAIN_HAND,
    OFF_HAND
}
