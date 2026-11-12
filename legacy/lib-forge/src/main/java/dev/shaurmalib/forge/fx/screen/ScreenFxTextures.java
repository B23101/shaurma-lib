package dev.shaurmalib.forge.fx.screen;

import net.minecraft.resources.ResourceLocation;

/** Вбудовані текстури екранних ефектів ({@code assets/shaurma_lib/textures/screenfx/...}). */
public final class ScreenFxTextures {
    private ScreenFxTextures() {}

    public static ResourceLocation[] of(String namespace, String folder, String... names) {
        ResourceLocation[] arr = new ResourceLocation[names.length];
        for (int i = 0; i < names.length; i++) {
            arr[i] = new ResourceLocation(namespace, "textures/screenfx/" + folder + "/" + names[i] + ".png");
        }
        return arr;
    }

    private static ResourceLocation[] lib(String folder, String... names) { return of("shaurma_lib", folder, names); }

    /** Порізи (гостра зброя, кігті). */
    public static final ResourceLocation[] SLASH = lib("blood", "slash0", "slash1", "slash2", "slash3", "slash4");
    /** Тупий удар. */
    public static final ResourceLocation[] IMPACT = lib("blood", "impact0", "impact1", "impact2", "impact3");
    /** Проколи (стріла, шипи, капкан). */
    public static final ResourceLocation[] PIERCE = lib("blood", "pierce0", "pierce1", "pierce2");
    /** Загальні бризки крові. */
    public static final ResourceLocation[] SPLATTER = lib("blood", "splatter0", "splatter1", "splatter2", "splatter3");
    public static final ResourceLocation[] FIRE = lib("fire", "fire0", "fire1");
    public static final ResourceLocation[] ICE = lib("ice", "ice0", "ice1", "ice2", "ice3", "ice4");
    /** Пил (вибух, дим). */
    public static final ResourceLocation[] DUST = lib("dust", "dust0", "dust1", "dust2");
    /** Повноекранна вінетка низького здоров'я (серцебиття). */
    public static final ResourceLocation LOWHEALTH = new ResourceLocation("shaurma_lib", "textures/screenfx/heartbeat/lowhealth0.png");
}
