package dev.shaurmalib.forge.module;

import dev.shaurmalib.client.cinematic.CinePresets;
import dev.shaurmalib.client.cinematic.CineRecording;
import dev.shaurmalib.client.cinematic.CineTimeline;

/**
 * Cinematic Timeline Engine — доповнення до плану, якого не було в
 * оригінальних 34 пунктах розділу 3 (дивись
 * {@code dev.shaurmalib.client.cinematic} package README.md для повного
 * обґрунтування): заміна {@code SCCinematicSequencer}
 * (533 рядки, захардкоджений {@code enum State} + {@code switch} +
 * окрема функція {@code applyPingCompensation()}, що вручну дублювала
 * кожен крок стейт-машини ще раз) на дата-driven {@link CineTimeline}.
 * <p>
 * Як і {@link StyleModuleHook}/{@link LeaderboardModuleHook}/
 * {@link MarkersModuleHook}, НЕ підключається через
 * {@code ShaurmaLib.Builder.withXxx(...)} — {@link CineTimeline} не
 * підписується на event bus сам, не тримає жодного глобального стану:
 * консюмер створює конкретний екземпляр через
 * {@link CineTimeline#builder} (або готові {@link CinePresets#classic}/
 * {@link CinePresets#scn}/{@link CinePresets#srMissionOnly}) у момент
 * старту кінематики (типово — вхід у {@code CountdownPhase}), тримає
 * посилання на нього доти, доки сесія активна, і сам викликає
 * {@code timeline.tick()} зі свого клієнтського тик-хука.
 * <p>
 * Мінімальний приклад підключення (SC/SR-режим):
 * <pre>{@code
 * CineContext ctx = new CineContext(modeId, false);
 * CineTimeline timeline = CinePresets.classic(ctx, CinePresets.Timings.defaults(),
 *     new CinePresets.CineHooks() {
 *         public void onHoldStart() { FreeCamera.disableMovement(); }
 *         public void disableSelectionCamera() { KitSelectCameraManager.disable(); }
 *         public void startWhiteFadeIn(long ms) { ScreenFadeOverlay.startWhiteFade(ms, 0, 0); }
 *         // ... решта методів CineHooks — прямі виклики вже наявних
 *         // оверлеїв/звуків мода, як і в оригінальному сиквенсері.
 *     });
 * timeline.begin(pingOffsetMs); // 0, якщо гравець вже був на сервері
 * // кожен клієнтський тік, поки timeline.isActive():
 * timeline.tick();
 * }</pre>
 * <p>
 * {@link CineRecording} (запис проходження таймлайну для подальшого
 * реплею) — окрема, повністю опційна функція: консюмер вмикає її лише
 * якщо явно передав {@code CineTimeline.builder(ctx).recordable(recording)}.
 * Без цього виклику жодні дані не записуються і не зберігаються —
 * той самий принцип "нічого не активується, поки не запитано явно", що
 * й решта бібліотеки, лише виражений через сам API рушія (Builder-flag),
 * а не через {@code ShaurmaLib.Builder}, бо рішення "записувати чи ні"
 * приймається на рівні КОНКРЕТНОЇ сесії кінематики, а не на рівні всього
 * мода одразу.
 */
public interface CinematicModuleHook {
    void onAttach(FMLModuleContext ctx);
}
