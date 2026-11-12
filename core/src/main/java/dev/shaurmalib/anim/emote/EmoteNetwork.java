package dev.shaurmalib.anim.emote;

/**
 * Транспорт клієнт → сервер для емоцій — реалізує ЛОАДЕР (як {@code AnimNetwork} для зворотного напрямку).
 * Без реалізації (тести, клієнт без клею) запити тихо відкидаються.
 */
public interface EmoteNetwork {

    void sendToServer(EmoteMessage message);

    final class Holder {
        private static volatile EmoteNetwork instance = m -> { };

        private Holder() {}
    }

    static EmoteNetwork get() { return Holder.instance; }

    static void set(EmoteNetwork impl) { Holder.instance = impl == null ? m -> { } : impl; }
}
