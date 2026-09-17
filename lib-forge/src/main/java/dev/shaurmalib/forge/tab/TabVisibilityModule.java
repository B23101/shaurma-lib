package dev.shaurmalib.forge.tab;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.resources.ResourceLocation;

/**
 * TabVisibilityModule — перенесення двох зв'язаних шматків
 * {@code CustomTabOverlay} snipers_shaurma, які досі НЕ були в бібліотеці
 * і тому мали повторюватись вручну в кожному консюмері:
 * <p>
 * 1) {@code cancelVanillaTab} — {@code @SubscribeEvent(priority = HIGHEST)}
 * на {@link RenderGuiOverlayEvent.Pre}, що скасовує рендер ванільного
 * {@code minecraft:player_list} оверлею (звичайний Tab-список гравців),
 * замінюваного кастомною таблицею консюмера. Перенесено 1:1 — той самий
 * {@code ResourceLocation}, той самий пріоритет.
 * <p>
 * 2) Стан "чи показана зараз таблиця" — в оригіналі це були локальні поля
 * {@code animProgress}/{@code forceVisible} і рядок
 * {@code tabDown = mc.options.keyPlayerList.isDown() || forceVisible}
 * на початку {@code render(...)}. Модуль тепер рахує це сам щокадру:
 * клавіша Tab (ванільний keybind {@code keyPlayerList}, тому автоматично
 * поважає ребайнд гравця) АБО {@link #setForceVisible(boolean)} тримають
 * ціль відкритою, з тим самим асиметричним згладжуванням, що в оригіналі
 * ({@code +0.12} на відкриття, {@code -0.15} на закриття).
 * <p>
 * <b>Що НЕ перенесено</b> (лишається продуктовою специфікою консюмера,
 * як і рушій {@link TabListStyle}): сам layout/контент таблиці (6 режимів
 * гри), рендер яких консюмер підключає через {@link #setRenderer}. Модуль
 * лише вирішує КОЛИ його викликати (з поточним {@code progress}) і ховає
 * ванільний оверлей — не малює нічого сам.
 * <p>
 * {@link #setForceVisible} лишається сумісним з існуючим контрактом
 * {@code ShaurmaLib.attachChatScreenIntercept(..., Consumer<Boolean>
 * tabOverlayForceVisible)} — консюмер передає туди {@code
 * TabVisibilityModule::setForceVisible} замість власного
 * {@code CustomTabOverlay.forceVisible = visible}, лишок API не міняється.
 * <p>
 * Реєструється вручну через {@link #attach(TabRenderer)} (не
 * {@code @Mod.EventBusSubscriber}) — той самий патерн, що
 * {@code VanillaHudCancelModule.attach}/{@code ShaurmaLib.Builder.withXxx}.
 * Безпечно викликати повторно — підписка на event bus виконується лише
 * один раз.
 */
public final class TabVisibilityModule {

    private static final ResourceLocation PLAYER_LIST =
            new ResourceLocation("minecraft", "player_list");

    /** Ключ, під яким модуль сам реєструє себе в {@link dev.shaurmalib.forge.overlay.OverlayEngine} усередині {@link #attach}. */
    public static final String OVERLAY_ID = "tab_list";

    private TabVisibilityModule() {}

    /** Контракт рендеру, який консюмер підключає через {@link #attach(TabRenderer)} — власний layout поверх {@link TabListStyle}. */
    @FunctionalInterface
    public interface TabRenderer {
        /**
         * @param progress поточна прозорість/прогрес появи таблиці (0..1,
         *                 те саме значення, що {@code anim} у методах
         *                 {@link TabListStyle}) — вже згладжене модулем,
         *                 консюмеру не треба рахувати {@code animProgress}
         *                 самому.
         */
        void render(net.minecraftforge.client.gui.overlay.ForgeGui gui,
                    net.minecraft.client.gui.GuiGraphics graphics,
                    float partialTick, int screenWidth, int screenHeight,
                    float progress);
    }

    private static volatile TabRenderer renderer = null;
    private static volatile boolean forceVisible = false;
    private static volatile boolean attached = false;
    private static float progress = 0f;

    /**
     * Підключає модуль: скасовує ванільний {@code player_list} і сам
     * реєструє свій {@link #render} в {@link dev.shaurmalib.forge.overlay.OverlayEngine}
     * під ключем {@value #OVERLAY_ID} — на відміну від
     * {@code VanillaHudCancelModule} (де "сховати" і "чим замінити" —
     * дві окремі відповідальності різних модулів), тут приховання
     * ванільного і показ кастомного НЕРОЗРИВНІ, як в оригінальному
     * {@code CustomTabOverlay.render(...)}, тому розділяти їх на два
     * ручні виклики консюмера (легко забути другий і лишитись без
     * жодного tab-списку) немає сенсу. Викликати один раз, типово
     * одразу після {@code ShaurmaLib.Builder.build()} — консюмер більше
     * не пише ні {@code cancelVanillaTab}, ні окремий
     * {@code OverlayEngine.register("tab_list", ...)} в жодному зі своїх
     * модів.
     *
     * @param tabRenderer консюмерський рендер кастомної таблиці — див.
     *                    {@link TabRenderer}. Може бути {@code null}, якщо
     *                    на цьому кроці підключення рендер ще не готовий;
     *                    підставити пізніше через {@link #setRenderer}.
     */
    @OnlyIn(Dist.CLIENT)
    public static synchronized void attach(TabRenderer tabRenderer) {
        renderer = tabRenderer;
        if (attached) return;
        MinecraftForge.EVENT_BUS.register(TabVisibilityModule.class);
        dev.shaurmalib.forge.overlay.OverlayEngine.register(OVERLAY_ID, TabVisibilityModule::render);
        attached = true;
    }

    /** Підміняє рендер кастомної таблиці без повторної підписки на event bus. */
    public static void setRenderer(TabRenderer tabRenderer) {
        renderer = tabRenderer;
    }

    /**
     * Примусово тримає таблицю видимою незалежно від фактичного стану
     * клавіші Tab — те саме призначення, що {@code CustomTabOverlay.forceVisible}
     * в оригіналі (використовувалось {@code ChatHistoryScreen} (T-меню),
     * що показує копію списку зліва без дублювання коду рендеру). Передати
     * як метод-референс у
     * {@code ShaurmaLib.attachChatScreenIntercept(..., TabVisibilityModule::setForceVisible)}.
     */
    public static void setForceVisible(boolean visible) {
        forceVisible = visible;
    }

    /** Поточний прогрес появи (0..1) — для консюмерів, яким потрібне значення поза {@link TabRenderer#render}, напр. для синхронізації іншого UI. */
    public static float getProgress() {
        return progress;
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void cancelVanillaTab(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay().id().equals(PLAYER_LIST)) {
            event.setCanceled(true);
        }
    }

    /**
     * Просуває {@code progress} і викликає консюмерський {@link #renderer},
     * якщо він підключений і {@code progress > 0.01f} (той самий поріг
     * early-return, що в оригіналі — уникає зайвих викликів рендеру на
     * повністю прихованій таблиці). Реєструється автоматично всередині
     * {@link #attach} — консюмеру НЕ треба викликати
     * {@code OverlayEngine.register(...)} самому.
     */
    @OnlyIn(Dist.CLIENT)
    public static void render(net.minecraftforge.client.gui.overlay.ForgeGui gui,
                               net.minecraft.client.gui.GuiGraphics graphics,
                               float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        boolean tabDown = mc.options.keyPlayerList.isDown() || forceVisible;
        progress = tabDown
                ? Math.min(1f, progress + 0.12f)
                : Math.max(0f, progress - 0.15f);
        if (progress <= 0.01f) return;
        if (renderer == null) return;
        renderer.render(gui, graphics, partialTick, screenWidth, screenHeight, progress);
    }
}
