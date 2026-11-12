package dev.shaurmalib.loader.forge;

import dev.shaurmalib.mc.McLootDropper;
import dev.shaurmalib.mc.McTeleportBackend;
import dev.shaurmalib.mc.McText;
import dev.shaurmalib.mc.PlayerOpsImpl;
import dev.shaurmalib.mc.RefFactoryImpl;
import dev.shaurmalib.platform.Dist;
import dev.shaurmalib.platform.Loader;
import dev.shaurmalib.platform.Platform;
import dev.shaurmalib.platform.PlatformInfo;
import dev.shaurmalib.platform.service.PlayerOps;
import dev.shaurmalib.platform.service.PlayerPersistence;
import dev.shaurmalib.platform.service.RefFactory;
import net.minecraft.SharedConstants;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

/**
 * Встановлює сервіси {@link Platform} для Forge 1.20.1. Викликається один раз з конструктора мода бібліотеки,
 * до будь-якого модуля. Після цього модулі core (телепорт, офлайн-аватари, стаміна, чат…) працюють
 * через SPI і нічого не знають про Forge чи версію гри.
 *
 * <p>Нова пара «лоадер × версія» = такий самий клас, що реєструє реалізації з відповідного {@code mc/<версія>}.
 */
public final class ForgePlatform {
    private static boolean installed;

    private ForgePlatform() {}

    public static synchronized void install() {
        if (installed) return;
        installed = true;
        PlatformInfo info = new PlatformInfo() {
            @Override public Loader loader() { return Loader.FORGE; }
            @Override public String minecraftVersion() { return SharedConstants.getCurrentVersion().getName(); }
            @Override public Dist dist() { return FMLEnvironment.dist.isClient() ? Dist.CLIENT : Dist.DEDICATED_SERVER; }
            @Override public Path configDir() { return FMLPaths.CONFIGDIR.get(); }
            @Override public boolean isModLoaded(String modId) { return ModList.get().isLoaded(modId); }
        };
        Platform.install(info, new McTeleportBackend(), new McLootDropper(), new McText());
        Platform.register(RefFactory.class, new RefFactoryImpl());
        Platform.register(PlayerOps.class, new PlayerOpsImpl());
        Platform.register(PlayerPersistence.class, new ForgePlayerPersistence());
    }
}
