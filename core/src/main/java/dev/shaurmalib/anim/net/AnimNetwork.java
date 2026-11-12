package dev.shaurmalib.anim.net;

/**
 * Мережевий транспорт рушія — реалізує ЛОАДЕР (Forge SimpleChannel, Fabric payload, NeoForge payload).
 * Ядро й шар версії лише кличуть {@link #get()}; без реалізації (сервер без лоадер-клею, тести)
 * повідомлення тихо відкидаються.
 */
public interface AnimNetwork {

    /**
     * @param entity      серверна сутність (сирий об'єкт гри)
     * @param includeSelf чи слати і самому гравцю-сутності
     */
    void sendToTracking(Object entity, AnimMessage message, boolean includeSelf);

    final class Holder {
        private static volatile AnimNetwork instance = (e, m, s) -> { };

        private Holder() {}
    }

    static AnimNetwork get() { return Holder.instance; }

    static void set(AnimNetwork impl) { Holder.instance = impl == null ? (e, m, s) -> { } : impl; }
}
