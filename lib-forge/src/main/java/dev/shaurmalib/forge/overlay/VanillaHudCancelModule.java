package dev.shaurmalib.forge.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.function.BooleanSupplier;

/**
 * VanillaHudCancelModule (план, п. 3.17 —
 * {@link dev.shaurmalib.common.mixin.MixinId#VANILLA_HUD_CANCEL}) —
 * перенесення {@code org.example.snipers_shaurma.core.client.gui.overlay.hud.VanillaHudCancelHandler}.
 * <p>
 * <b>Важливе уточнення щодо назви toggle-миксину в плані</b>: у
 * snipers_shaurma існує ДВА артефакти з цим призначенням —
 * {@code MixinVanillaHudCancel} (клас, названий у плані як кандидат) і
 * {@code VanillaHudCancelHandler}. Перший НЕ зареєстрований у
 * {@code snipers_shaurma.mixins.json} і є покинутим, непрацюючим
 * артефактом попередньої спроби (миксин на {@code Gui#renderPlayerHealth}/
 * {@code renderHotbar}) — задокументований у сусідньому класі
 * {@code CustomHealthOverlay} як підхід, що вже ОДНОГО РАЗУ зламав ESC-меню
 * при трансформації всього класу {@code Gui}. Реальний, робочий і
 * реєстрований механізм — саме {@code VanillaHudCancelHandler}, що діє
 * через подієвий {@link RenderGuiOverlayEvent.Pre} на КОНКРЕТНИЙ оверлей,
 * без патчингу класу {@code Gui} взагалі. Тому в бібліотеці переноситься
 * саме цей, другий, підхід — 1:1 логіка, назва класу змінена на
 * {@code VanillaHudCancelModule}, щоб не породжувати мертвий клас з іменем
 * "Mixin", якого насправді немає.
 * <p>
 * Приховує рівно 6 ванільних HUD-елементів (хотбар, HP, ситість, досвід,
 * броня, чат), замінених кастомними оверлеями консюмера
 * ({@link OverlayEngine}/{@code HotbarPanelRenderer}/{@code CustomHealthOverlay}-
 * аналог) — жодних інших ванільних оверлеїв (F3-екран, ESC-меню, тощо) не
 * торкається, бо скасовується КОНКРЕТНИЙ {@code event.getOverlay()} по
 * {@code id()}, а не сам клас рендеру.
 * <p>
 * <b>Критичний spectator-guard (перенесений 1:1)</b>: приховування НЕ
 * застосовується, коли гравець зараз спостерігач — ванільний
 * {@code GameType.SPECTATOR}, АБО кастомний "обмежений spectator"
 * консюмера (вселення камери в тімейта/ціль після смерті, FreeCamera тощо
 * — той самий критерій {@code mc.getCameraEntity() != mc.player}, що вже
 * використовують {@code HotbarPanelRenderer}/кастомні HP-оверлеї
 * консюмера). Без цього guard'а спостерігач лишається взагалі без HUD:
 * кастомні оверлеї консюмера вже самі ховаються в spectator-режимі, і
 * якщо ванільні теж скасовані — гравець не бачить ні кастомного, ні
 * ванільного хотбару/HP.
 * <p>
 * <b>Виняток для чату</b>: {@code CHAT_PANEL} скасовується НЕЗАЛЕЖНО від
 * spectator-статусу, якщо консюмер передав {@code chatActive == true} —
 * бібліотечний {@code ChatModule}/{@code AlertNotificationSystem}
 * лишається єдиним видимим фідом повідомлень завжди (інакше кожне
 * повідомлення в spectator-режимі показувалось би двічі — і ванільним
 * чатом, і кастомним фідом). Якщо чат-модуль консюмера не підключено
 * (передано {@code () -> false}) — ванільний чат ніколи не ховається,
 * бо ховати єдиний наявний фід повідомлень нічим не замінюючи було б
 * втратою функціональності, а не перенесенням.
 * <p>
 * Реєструється вручну через {@link #attach()} (не
 * {@code @Mod.EventBusSubscriber}) — консюмер викликає це один раз при
 * підключенні модуля через {@code ShaurmaLib.Builder.withVanillaHudCancel(...)},
 * той самий підхід, що решта необов'язкових {@code attachXxx}-точок
 * бібліотеки (див. {@code ShaurmaLib#attachOverlayEngine}).
 */
public final class VanillaHudCancelModule {

    private VanillaHudCancelModule() {}

    private static volatile BooleanSupplier chatActive = () -> false;
    private static volatile boolean attached = false;

    /**
     * Підключає модуль до {@code MinecraftForge.EVENT_BUS}. Безпечно
     * викликати повторно — підписка виконується лише один раз.
     *
     * @param chatActive {@code true}, якщо бібліотечний чат-модуль
     *                   консюмера зараз є єдиним видимим фідом
     *                   повідомлень (типово {@code () -> true}, якщо
     *                   {@code ShaurmaLib.Builder.withChat(...)}
     *                   викликано; {@code () -> false} — не чіпати
     *                   ванільний чат взагалі).
     */
    @OnlyIn(Dist.CLIENT)
    public static synchronized void attach(BooleanSupplier chatActive) {
        VanillaHudCancelModule.chatActive = chatActive != null ? chatActive : () -> false;
        if (attached) return;
        MinecraftForge.EVENT_BUS.register(VanillaHudCancelModule.class);
        attached = true;
    }

    /**
     * {@code true}, якщо зараз слід ЛИШИТИ ванільний HUD як є (не
     * приховувати) — гравець у spectator-режимі (ванільному чи
     * кастомному "обмеженому spectator" консюмера) не повинен лишатись
     * зовсім без хотбару/HP/їжі/досвіду.
     */
    private static boolean isSpectating() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (mc.player.isSpectator()) return true;
        if (mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.SPECTATOR) return true;
        return mc.getCameraEntity() != null && mc.getCameraEntity() != mc.player;
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id())) {
            if (chatActive.getAsBoolean()) {
                event.setCanceled(true);
            }
            return;
        }

        if (isSpectating()) return;

        // Кожна перевірка незалежна — навмисно без early-return/else,
        // щоб скасування однієї не могло випадково "проковтнути" інші
        // при майбутніх правках цього методу.
        if (event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) {
            event.setCanceled(true);
        }
        if (event.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_HEALTH.id())) {
            event.setCanceled(true);
        }
        if (event.getOverlay().id().equals(VanillaGuiOverlay.FOOD_LEVEL.id())) {
            event.setCanceled(true);
        }
        if (event.getOverlay().id().equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())) {
            event.setCanceled(true);
        }
        if (event.getOverlay().id().equals(VanillaGuiOverlay.ARMOR_LEVEL.id())) {
            event.setCanceled(true);
        }
    }
}
