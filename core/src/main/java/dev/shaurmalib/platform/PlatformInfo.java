package dev.shaurmalib.platform;

import java.nio.file.Path;

/** Що ми запущені: лоадер, версія гри, сторона, директорія конфігів. */
public interface PlatformInfo {
    Loader loader();
    /** Рядок версії гри: "1.20.1", "1.21.1", "26.3". */
    String minecraftVersion();
    Dist dist();
    Path configDir();
    boolean isModLoaded(String modId);

    /** Зручна перевірка для рідкісних (!) версійних розгалужень у логіці. Віддавай перевагу бекендам. */
    default boolean isUnobfuscatedEra() { return minecraftVersion().matches("^\\d{2}\\..*"); }
}
