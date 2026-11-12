package dev.shaurmalib.anim.clip;

import java.util.Map;

/**
 * Подія на таймлайні кліпа: звук, частинки або власна мітка мода.
 *
 * @param type  {@code sound} | {@code particle} | {@code custom} (Blockbench: sound / particle / timeline)
 * @param value id звуку/частинки або ім'я мітки ({@code "maniacmod:medkit_open"}, {@code "bandage_done"})
 * @param params додаткове ({@code volume}, {@code pitch}, {@code locator}...)
 */
public record ClipEvent(float time, String type, String value, Map<String, Object> params) {

    public double param(String key, double def) {
        Object v = params == null ? null : params.get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }
}
