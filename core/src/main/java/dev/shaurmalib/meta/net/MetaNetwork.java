package dev.shaurmalib.meta.net;

import java.util.UUID;

/**
 * Розетка мережі: реалізує клей лоадера (Forge SimpleChannel / Fabric ServerPlayNetworking).
 * Без реалізації — повідомлення мовчки губляться (так ядро працює в тестах і на «голому» сервері).
 */
public interface MetaNetwork {

    void toPlayer(UUID player, MetaMessage m);

    void toAll(MetaMessage m);

    /** Гравцям у радіусі від точки (у світі, де стоїть {@code near}). */
    void toNear(UUID near, double x, double y, double z, double radius, MetaMessage m);

    void toServer(MetaMessage m);

    MetaNetwork NONE = new MetaNetwork() {
        @Override public void toPlayer(UUID player, MetaMessage m) { }
        @Override public void toAll(MetaMessage m) { }
        @Override public void toNear(UUID near, double x, double y, double z, double radius, MetaMessage m) { }
        @Override public void toServer(MetaMessage m) { }
    };

    final class Holder {
        private static volatile MetaNetwork instance = NONE;
        private Holder() {}
    }

    static MetaNetwork get() { return Holder.instance; }

    static void set(MetaNetwork n) { Holder.instance = n == null ? NONE : n; }
}
