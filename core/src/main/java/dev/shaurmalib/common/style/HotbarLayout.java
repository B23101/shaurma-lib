package dev.shaurmalib.common.style;

/**
 * Позиція кастомної hotbar-панелі відносно ширини екрана (план, п. 3.3,
 * {@code HotbarPanelRenderer}) — точно ті 3 варіанти, які вже задає
 * гравцям snipers_shaurma ("як у снайперів", формулювання із запиту).
 * Сам розрахунок фінальних X/Y-координат — на боці {@code
 * lib-forge.client.style.HotbarPanelRenderer}, бо потребує
 * {@code guiScaledWidth}/{@code guiScaledHeight}; тут лише декларація
 * наміру, що читається з {@code style.yml}.
 */
public enum HotbarLayout {
    LEFT,
    CENTER,
    RIGHT;

    public static HotbarLayout fromConfigString(String raw, HotbarLayout def) {
        if (raw == null) return def;
        try {
            return HotbarLayout.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
