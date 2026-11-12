package dev.shaurmalib.render.overlay;

import dev.shaurmalib.render.GuiCanvas;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реєстр HUD-шарів (чиста заміна {@code OverlayEngine}). Клей лоадера підписується на
 * власну HUD-подію (Forge/NeoForge: RegisterGuiOverlays/RenderGuiEvent, Fabric: HudRenderCallback)
 * і викликає {@link #renderAll} з канвасом версії. Порядок — за id (стабільний).
 */
public final class OverlayRegistry {
    private static final Map<String, Overlay> OVERLAYS = new ConcurrentHashMap<>();

    private OverlayRegistry() {}

    public static void register(String id, Overlay o) { OVERLAYS.put(Objects.requireNonNull(id), Objects.requireNonNull(o)); }
    public static void unregister(String id) { OVERLAYS.remove(id); }

    public static void renderAll(GuiCanvas canvas, float partialTick) {
        OVERLAYS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> e.getValue().render(canvas, partialTick));
    }
}
