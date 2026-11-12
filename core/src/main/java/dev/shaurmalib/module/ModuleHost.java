package dev.shaurmalib.module;

import dev.shaurmalib.platform.Dist;
import dev.shaurmalib.platform.event.EventBus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Збирає модулі, топологічно впорядковує за {@link ShaurmaModule#dependsOn()} і підключає.
 * Пропущена залежність або цикл — {@link IllegalStateException} з назвою модуля (а не тихий збій у рантаймі).
 */
public final class ModuleHost implements ModuleContext {
    private final EventBus bus;
    private final Dist dist;
    private final Map<String, ShaurmaModule> modules = new LinkedHashMap<>();
    private final List<ShaurmaModule> attached = new ArrayList<>();

    public ModuleHost(EventBus bus, Dist dist) {
        this.bus = Objects.requireNonNull(bus);
        this.dist = Objects.requireNonNull(dist);
    }

    public ModuleHost add(ShaurmaModule m) {
        if (modules.putIfAbsent(m.id(), m) != null) throw new IllegalStateException("Дублікат модуля: " + m.id());
        return this;
    }

    @Override public EventBus bus() { return bus; }
    @Override public Dist dist() { return dist; }

    public List<ShaurmaModule> attachAll() {
        List<ShaurmaModule> order = new ArrayList<>();
        Map<String, Integer> state = new LinkedHashMap<>(); // 1 = у стеку, 2 = готово
        for (ShaurmaModule m : modules.values()) visit(m, state, order, new ArrayList<>());
        for (ShaurmaModule m : order) { m.attach(this); attached.add(m); }
        return List.copyOf(order);
    }

    public void detachAll() {
        for (int i = attached.size() - 1; i >= 0; i--) attached.get(i).detach();
        attached.clear();
    }

    private void visit(ShaurmaModule m, Map<String, Integer> state, List<ShaurmaModule> out, List<String> path) {
        Integer st = state.get(m.id());
        if (st != null && st == 2) return;
        path.add(m.id());
        if (st != null) throw new IllegalStateException("Цикл залежностей модулів: " + String.join(" -> ", path));
        state.put(m.id(), 1);
        for (String dep : m.dependsOn()) {
            ShaurmaModule d = modules.get(dep);
            if (d == null) throw new IllegalStateException("Модуль '" + m.id() + "' потребує непідключеного '" + dep + "'");
            visit(d, state, out, path);
        }
        state.put(m.id(), 2);
        path.remove(path.size() - 1);
        out.add(m);
    }
}
