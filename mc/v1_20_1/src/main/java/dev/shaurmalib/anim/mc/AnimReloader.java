package dev.shaurmalib.anim.mc;

import dev.shaurmalib.anim.ShaurmaAnim;
import dev.shaurmalib.anim.io.AnimResourceLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.util.Map;

/**
 * Перезавантажує бібліотеку кліпів з ресурс-паків (старт клієнта і F3+T). Ванільний інтерфейс —
 * лоадер лише реєструє екземпляр (Forge: RegisterClientReloadListenersEvent; Fabric: ResourceManagerHelper).
 */
public final class AnimReloader implements ResourceManagerReloadListener {

    private static final Logger LOG = LoggerFactory.getLogger("ShaurmaAnim");
    private static final String[] ROOTS = { "shaurma_anim", "player_animation", "player_animations", "animations" };

    @Override
    public void onResourceManagerReload(ResourceManager rm) {
        AnimResourceLoader loader = new AnimResourceLoader(w -> LOG.warn("[ShaurmaAnim] {}", w));
        for (String root : ROOTS) {
            Map<ResourceLocation, Resource> found = rm.listResources(root, rl -> rl.getPath().endsWith(".json"));
            for (Map.Entry<ResourceLocation, Resource> e : found.entrySet()) {
                ResourceLocation rl = e.getKey();
                try (Reader r = e.getValue().openAsReader()) {
                    StringBuilder sb = new StringBuilder();
                    char[] buf = new char[8192];
                    int n;
                    while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
                    loader.accept(rl.getNamespace(), rl.getPath(), sb.toString());
                } catch (IOException ex) {
                    LOG.warn("[ShaurmaAnim] не прочитано {}: {}", rl, ex.toString());
                }
            }
        }
        ShaurmaAnim.clips().replaceAll(loader.clips(), loader.meshes());
        LOG.info("[ShaurmaAnim] завантажено {} кліпів і {} моделей пропсів з {} файлів",
            loader.clips().size(), loader.meshes().size(), loader.files());
    }
}
