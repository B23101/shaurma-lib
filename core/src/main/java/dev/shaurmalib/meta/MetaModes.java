package dev.shaurmalib.meta;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Реєстр режимів. На сервері режим, що веде гру, — {@link #server()}; на клієнті — той, про який сказав сервер
 * під час входу (пакет HELLO), інакше — перший зареєстрований.
 */
public final class MetaModes {

    private static final Map<String, MetaMode> MODES = new LinkedHashMap<>();
    private static volatile String clientMode;

    private MetaModes() {}

    public static synchronized MetaMode register(MetaMode mode) {
        MODES.put(mode.id, mode);
        return mode;
    }

    public static MetaMode get(String id) { return id == null ? null : MODES.get(id); }

    public static Collection<MetaMode> all() { return MODES.values(); }

    /** Режим, яким керує цей сервер: перший зареєстрований (у мода — завжди один). */
    public static MetaMode server() { return MODES.isEmpty() ? null : MODES.values().iterator().next(); }

    public static MetaMode client() {
        MetaMode m = get(clientMode);
        return m != null ? m : server();
    }

    public static void setClient(String id) { clientMode = id; }
}
