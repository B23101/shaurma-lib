package dev.shaurmalib.anim.mc;

import com.mojang.blaze3d.platform.NativeImage;
import dev.shaurmalib.anim.ShaurmaAnim;
import dev.shaurmalib.anim.io.AnimPackScanner;
import dev.shaurmalib.anim.io.AnimResourceLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Перезавантажує бібліотеку кліпів (старт клієнта і F3+T). Ванільний інтерфейс —
 * лоадер лише реєструє екземпляр (Forge: RegisterClientReloadListenersEvent; Fabric: ResourceManagerHelper).
 *
 * <p>Два джерела, друге перекриває перше:
 * <ol>
 *   <li>ресурс-паки й моди ({@code assets/<ns>/shaurma_anim/...});</li>
 *   <li>папка гри {@code <гра>/shaurma_anim/}: {@code .zip} з Blockbench-плагіна (розпаковувати не треба)
 *       або теки — кліпи, моделі пропсів і їхні PNG (реєструються як текстури гри).</li>
 * </ol>
 */
public final class AnimReloader implements ResourceManagerReloadListener {

    private static final Logger LOG = LoggerFactory.getLogger("ShaurmaAnim");
    private static final String[] ROOTS = { "shaurma_anim", "player_animation", "player_animations", "animations" };
    /** Текстури з папки гри, зареєстровані минулого разу — звільняються перед новим читанням. */
    private final Set<ResourceLocation> folderTextures = new HashSet<>();

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

        AnimPackScanner.Result folder = null;
        try {
            Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve(AnimPackScanner.FOLDER);
            folder = AnimPackScanner.scan(dir, loader, w -> LOG.warn("[ShaurmaAnim] {}", w));
            registerTextures(folder.textures());
        } catch (RuntimeException ex) {
            LOG.warn("[ShaurmaAnim] папка {}: {}", AnimPackScanner.FOLDER, ex.toString());
        }

        ShaurmaAnim.clips().replaceAll(loader.clips(), loader.meshes());
        LOG.info("[ShaurmaAnim] завантажено {} кліпів і {} моделей пропсів з {} файлів{}",
            loader.clips().size(), loader.meshes().size(), loader.files(),
            folder == null || folder.sources().isEmpty() ? "" : "; з папки " + AnimPackScanner.FOLDER + ": " + folder.sources()
                + " (" + folder.textures().size() + " текстур)");
    }

    /** PNG з папки гри → текстури за тими самими id, що пише плагін ({@code ns:textures/shaurma_anim/x.png}). */
    private void registerTextures(Map<String, byte[]> textures) {
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation old : folderTextures) tm.release(old);
        folderTextures.clear();
        for (Map.Entry<String, byte[]> e : textures.entrySet()) {
            ResourceLocation rl = ResourceLocation.tryParse(e.getKey());
            if (rl == null) {
                LOG.warn("[ShaurmaAnim] погане ім'я текстури {} (лише a-z0-9_./-)", e.getKey());
                continue;
            }
            try {
                NativeImage img = NativeImage.read(new ByteArrayInputStream(e.getValue()));
                tm.register(rl, new DynamicTexture(img));
                folderTextures.add(rl);
            } catch (IOException | RuntimeException ex) {
                LOG.warn("[ShaurmaAnim] текстура {}: {}", rl, ex.toString());
            }
        }
    }
}
