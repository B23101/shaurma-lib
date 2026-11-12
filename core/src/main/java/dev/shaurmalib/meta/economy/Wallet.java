package dev.shaurmalib.meta.economy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Гаманець: {@code валюта → баланс}. Баланс ніколи не від'ємний; списання атомарне — або все, або нічого.
 * Сам гаманець не знає, ЗА ЩО гроші: це вирішує {@code MetaServer} (магазин, крафт, нагороди), і лише він
 * його змінює — клієнт отримує копію.
 */
public final class Wallet {

    private final Map<String, Long> balances = new LinkedHashMap<>();

    public long get(String currency) {
        Long v = balances.get(currency);
        return v == null ? 0L : v;
    }

    public void add(String currency, long amount) {
        if (amount <= 0) return;
        long next = get(currency) + amount;
        balances.put(currency, next < 0 ? Long.MAX_VALUE : next);   // захист від переповнення
    }

    public boolean canAfford(String currency, long amount) {
        return amount <= 0 || get(currency) >= amount;
    }

    /** @return false — коштів не вистачає, баланс не змінено */
    public boolean spend(String currency, long amount) {
        if (amount <= 0) return true;
        long have = get(currency);
        if (have < amount) return false;
        balances.put(currency, have - amount);
        return true;
    }

    public void set(String currency, long amount) { balances.put(currency, Math.max(0, amount)); }

    public Map<String, Long> view() { return Collections.unmodifiableMap(balances); }
}
