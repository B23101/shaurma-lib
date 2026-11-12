package dev.shaurmalib.meta.economy;

import dev.shaurmalib.meta.cosmetics.Rarity;

/** Деталь для крафту косметики («нитки», «гарбузове насіння», «уламок черепа»). */
public record MaterialDef(String id, String name, String icon, Rarity rarity) {}
