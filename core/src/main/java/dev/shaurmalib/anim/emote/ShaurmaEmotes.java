package dev.shaurmalib.anim.emote;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Точка входу системи емоцій. Ядро не знає гри: «гравець» тут — сирий об'єкт (на сервері
 * {@code ServerPlayer}, на клієнті {@code LocalPlayer}), мод приводить його сам.
 *
 * <h2>Що задає мод</h2>
 * <pre>
 * // спільний код: емоції (клієнт показує, сервер перевіряє id)
 * ShaurmaEmotes.register(Emote.builder("maniacmod:wave", "maniacmod:emote/wave").name("Помахати").build());
 *
 * // сервер: чи можна ЗАРАЗ (дія, лежить, на гаку — ні). Перевіряється і щотіка, поки емоція грає.
 * ShaurmaEmotes.setServerGate((player, emote) -&gt; canEmote((ServerPlayer) player));
 *
 * // клієнт: чи відкривати колесо (та сама логіка, щоб не показувати марне колесо)
 * ShaurmaEmotes.setClientGate(player -&gt; canEmoteClient((LocalPlayer) player));
 *
 * // режим: лише частина емоцій / колесо вимкнене зовсім
 * ShaurmaEmotes.setFilter(e -&gt; e.category().equals("social"));
 * ShaurmaEmotes.setWheelEnabled(false);
 * </pre>
 */
public final class ShaurmaEmotes {

    private ShaurmaEmotes() {}

    private static final EmoteRegistry REGISTRY = new EmoteRegistry();
    private static final EmoteWheel WHEEL = new EmoteWheel();
    private static final EmoteSessions SESSIONS = new EmoteSessions();

    private static volatile boolean wheelEnabled = true;
    private static volatile Predicate<Object> clientGate = p -> true;
    private static volatile BiPredicate<Object, Emote> serverGate = (p, e) -> true;

    public static EmoteRegistry registry() { return REGISTRY; }

    /** Колесо локального гравця (клієнт). */
    public static EmoteWheel wheel() { return WHEEL; }

    /** Активні емоції (сервер). */
    public static EmoteSessions sessions() { return SESSIONS; }

    public static Emote register(Emote e) {
        REGISTRY.register(e);
        return e;
    }

    public static Emote get(String id) { return REGISTRY.get(id); }

    public static List<Emote> available() { return REGISTRY.available(); }

    /** Обмежити список емоцій для режиму (null — усі). */
    public static void setFilter(Predicate<Emote> filter) { REGISTRY.setFilter(filter); }

    /** Вимкнути колесо повністю: клавіша нічого не відкриває, сервер відхиляє запити. */
    public static void setWheelEnabled(boolean enabled) { wheelEnabled = enabled; }

    public static boolean wheelEnabled() { return wheelEnabled; }

    public static void setClientGate(Predicate<Object> gate) { clientGate = gate == null ? p -> true : gate; }

    public static void setServerGate(BiPredicate<Object, Emote> gate) { serverGate = gate == null ? (p, e) -> true : gate; }

    /** Клієнт: чи можна відкрити колесо зараз. */
    public static boolean canOpenWheel(Object localPlayer) {
        if (!wheelEnabled || localPlayer == null || REGISTRY.available().isEmpty()) return false;
        try {
            return clientGate.test(localPlayer);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Сервер: чи дозволено цьому гравцю цю емоцію зараз (вимкнене колесо, фільтр режиму, хук мода). */
    public static boolean allowedOnServer(Object player, Emote e) {
        if (!wheelEnabled || e == null || REGISTRY.available(e.id()) == null) return false;
        try {
            return serverGate.test(player, e);
        } catch (RuntimeException ex) {
            return false; // кривий хук = заборона, а не виняток у мережевому потоці
        }
    }
}
