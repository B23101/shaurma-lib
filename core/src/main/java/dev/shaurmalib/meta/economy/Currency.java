package dev.shaurmalib.meta.economy;

/**
 * Валюта режиму. У кожного режиму своя (маніяк — DarkCoin, мафія — власна), бібліотека не знає назв.
 *
 * @param id    ключ у гаманці, напр. {@code "darkcoin"}
 * @param name  назва для екранів («DarkCoins»)
 * @param color колір монети в UI (RGB)
 * @param icon  id іконки (coin, blood, gem, skull)
 */
public record Currency(String id, String name, int color, String icon) {}
