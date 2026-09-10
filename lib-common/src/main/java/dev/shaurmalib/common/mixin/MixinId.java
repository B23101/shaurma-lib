package dev.shaurmalib.common.mixin;

/**
 * Ідентифікатори миксинів бібліотеки, які можна вмикати/вимикати
 * незалежно один від одного (план, п. 3.17). Кожен елемент відповідає
 * рівно одному {@code @Mixin}-класу в {@code lib-forge} і має бути
 * зареєстрований у {@code shaurma_lib.mixins.json} — сам факт
 * перебування класу в json ще не означає, що він застосується: це
 * вирішує {@link MixinToggleRegistry} через
 * {@code ShaurmaLibMixinPlugin.shouldApplyMixin(...)} під час
 * mixin-трансформації, ДО того, як FML переходить до основного
 * lifecycle мода.
 * <p>
 * Елементи нижче — це вже задекларовані в оригінальному
 * {@code snipers_shaurma.mixins.json} фіча-специфічні кандидати
 * (план, розділ 3.17, список "безпечних на вимикання"). Кожен новий
 * toggle-миксин лібу (у майбутніх модулях — zipline, playeranim,
 * TACZ/SuperbWarfare-міст тощо) отримує тут власну константу ПЕРЕД
 * тим, як його клас з'являється в {@code lib-forge}, а не навпаки —
 * так {@link MixinToggleRegistry} завжди має відому множину
 * ідентифікаторів, навіть якщо конкретний клас-миксин ще не написаний.
 * <p>
 * Миксини, завжди активні незалежно від {@code withXxx()}-підключення
 * консюмера (наприклад {@code MixinChatComponent}, {@code
 * MixinItemInHandRenderer}, {@code SoundEngineAccessor}/{@code
 * SoundManagerAccessor} — вони самі по собі неактивні, доки консюмер
 * не викликав відповідний {@code withChat()}/{@code withAnimatedItems()},
 * бо перевіряють прапорець підключеного модуля зсередини, а не через
 * mixin-plugin) — тут НЕ перелічуються: toggle на рівні
 * mixin-transformer потрібен лише для миксинів, які самі по собі не
 * мають дешевого internal-guard (наприклад патчать final-поле рендеру
 * кожен тік і internal if() коштував би продуктивності), або які
 * консюмер хоче прибрати з classpath повністю (наприклад щоб уникнути
 * конфлікту з іншим модом, що патчить той самий клас).
 */
public enum MixinId {
    /** Патч GameRenderer для рендеру зіплайн-мотузки/руки під час катання (план, п. 3.16/3.24). */
    ZIPLINE_GAME_RENDERER,
    /** Патч HumanoidModel для пози тіла гравця під час зіплайну. */
    ZIPLINE_HUMANOID_MODEL,
    /** Патч ItemInHandRenderer для приховання предмета в руці під час зіплайну. */
    ZIPLINE_ITEM_IN_HAND_RENDERER,
    /** Приховування руки в режимі spectator-прив'язки до цілі (план, п. 3.34). */
    SPECTATOR_HAND_VISIBILITY,
    /** Приглушення SoundEngine на старті відтворення певних категорій (план, п. 3.5/3.33 ducking). */
    SOUND_ENGINE_DUCK_ON_START,
    /**
     * Повне скасування рендеру ванільного HUD-елемента (використовується
     * кастомним HUD бібліотеки).
     * <p>
     * <b>Уточнення після звірки з оригіналом:</b> ця константа названа за
     * планом п. 3.17 як "toggle-миксин", але фактичний перенесений
     * механізм — {@code dev.shaurmalib.forge.overlay.VanillaHudCancelModule}
     * (lib-forge), подієвий ({@code RenderGuiOverlayEvent.Pre}), НЕ
     * {@code @Mixin}-клас, і тому НЕ бере участі в
     * {@code ShaurmaLibMixinPlugin}/{@link MixinToggleRegistry}-механізмі
     * — вмикається/вимикається
     * лише самим фактом виклику (чи невиклику)
     * {@code ShaurmaLib.attachVanillaHudCancel(...)} консюмером, а не
     * через {@code withMixins(MixinId.VANILLA_HUD_CANCEL, ...)}.
     * Оригінальний {@code MixinVanillaHudCancel} (справжній
     * {@code @Mixin(Gui.class)}-клас з такою ж метою) у snipers_shaurma
     * НЕ зареєстрований у {@code mixins.json} і є покинутим артефактом:
     * задокументований у сусідньому коді як підхід, що вже одного разу
     * зламав ESC-меню при трансформації класу {@code Gui}. Константа
     * лишена в цьому enum з історичних причин найменування плану — не
     * викликайте для неї {@link MixinToggleRegistry#disable}/{@code enable},
     * це не матиме жодного ефекту.
     */
    VANILLA_HUD_CANCEL;

    /**
     * Ім'я {@code @Mixin}-класу (без пакета) у {@code lib-forge}, яке
     * {@code ShaurmaLibMixinPlugin} звіряє з targetClassName, який йому
     * передає Mixin-транформер. За замовчуванням — {@code "Mixin" +
     * PascalCase(name())}; консюмер плагіна не повинен покладатись на
     * це правило напряму, лише на {@link MixinToggleRegistry#isEnabled}.
     */
    public String defaultClassName() {
        StringBuilder sb = new StringBuilder("Mixin");
        boolean upperNext = true;
        for (char c : name().toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            if (c == '_') {
                upperNext = true;
                continue;
            }
            sb.append(upperNext ? Character.toUpperCase(c) : c);
            upperNext = false;
        }
        return sb.toString();
    }
}
