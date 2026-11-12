package dev.shaurmalib.common.mixin;

import java.util.EnumMap;
import java.util.Map;

/**
 * Центральний реєстр "які toggle-миксини бібліотеки застосовувати"
 * (план, п. 3.17 — "деякі миксини одним викликом можна вмикати чи
 * вимикати"). Читається виключно {@code ShaurmaLibMixinPlugin} під час
 * mixin-трансформації класів; сам реєстр — простий статичний
 * {@code EnumMap}, без залежності на Forge/FML, щоб лишатись у
 * {@code lib-common} (Architecture Sniffer, п. 2.1).
 * <p>
 * <b>Критичний порядок викликів:</b> {@link #enable}/{@link #disable}
 * мають виконатись ДО того, як Mixin-транформер вперше торкнеться
 * відповідного цільового класу. На практиці це означає виклик з
 * {@code ShaurmaLib.Builder.withMixins(...)} у конструкторі
 * {@code @Mod}-класу консюмера — FML гарантовано виконує конструктор
 * мода раніше, ніж завантажує/трансформує ігрові класи, які миксини
 * патчать (рендер, моделі тощо завантажуються значно пізніше, при
 * вході у world/gui). Виклик {@code enable}/{@code disable} із
 * будь-якого коду, що виконується вже під час гри (наприклад команда
 * адміна "вимкнути миксин на льоту") НЕ матиме ефекту для миксинів,
 * чий цільовий клас уже трансформовано — Mixin не підтримує
 * рантайм-untransform.
 * <p>
 * Дефолт — усі миксини УВІМКНЕНІ, якщо консюмер explicit не викликав
 * {@link #disable}: бібліотека не повинна мовчки вимикати фічу, яку
 * ніхто не просив вимкнути (той самий принцип "нічого не активується
 * автоматично, але нічого explicit не запитане теж не гасне
 * автоматично" — тут навпаки, бо миксини вмикаються за замовчуванням,
 * а модулі {@code withXxx()} — навпаки, вимкнені, доки не запитані;
 * різниця свідома: миксин — це патч конкретної відомої проблеми, а не
 * нова функціональність, яку споживач мав явно захотіти).
 */
public final class MixinToggleRegistry {

    private static final Map<MixinId, Boolean> STATE = new EnumMap<>(MixinId.class);

    private MixinToggleRegistry() {}

    public static void enable(MixinId id) {
        STATE.put(id, Boolean.TRUE);
    }

    public static void disable(MixinId id) {
        STATE.put(id, Boolean.FALSE);
    }

    /** Дефолт {@code true}, якщо консюмер жодного разу не викликав {@link #disable} для цього id. */
    public static boolean isEnabled(MixinId id) {
        return STATE.getOrDefault(id, Boolean.TRUE);
    }

    /**
     * Пошук {@link MixinId} за іменем цільового {@code @Mixin}-класу
     * (без пакета), який {@code ShaurmaLibMixinPlugin} отримує від
     * Mixin-транформера. Повертає {@code null}, якщо клас не є
     * toggle-миксином бібліотеки (тобто завжди застосовується —
     * плагін у такому разі не викликає цей метод узагалі, дивись
     * {@code ShaurmaLibMixinPlugin}).
     */
    public static MixinId byClassName(String simpleClassName) {
        for (MixinId id : MixinId.values()) {
            if (id.defaultClassName().equals(simpleClassName)) {
                return id;
            }
        }
        return null;
    }

    /** Скидає всі перевизначення до дефолту (усі увімкнені) — використовується лише в тестах. */
    public static void resetForTests() {
        STATE.clear();
    }
}
