package dev.shaurmalib.forge.module;

import dev.shaurmalib.forge.markers.MarkerRenderRules;
import dev.shaurmalib.forge.markers.WorldBillboardPrimitives;

/**
 * Точка вбудовування рушія міток/білбордів (план, п. 3.13) —
 * {@link WorldBillboardPrimitives} (статичні примітиви:
 * {@code quad}/{@code border}/{@code texturedQuad}/{@code text}/
 * {@code diamond}) +
 * {@link dev.shaurmalib.common.markers.BillboardOrientation} (чиста
 * математика right/forward/up векторів) +
 * {@link dev.shaurmalib.common.markers.MarkerVisibilityPolicy}
 * (контракт "коли/кому показувати", який лишається на боці консюмера) +
 * {@link MarkerRenderRules} (оркеструючий цикл culling/fade/orientation/
 * pushPose навколо примітивів вище — узагальнення того, що
 * {@code LeaderboardWorldRenderer.onRenderLevel} і
 * {@code C4MarkerRenderer.onRenderLevelStage} раніше повторювали кожен
 * своїм окремим {@code for}-циклом).
 * <p>
 * Як і {@link LeaderboardModuleHook} і {@link MatchHistoryModuleHook},
 * НЕ підключається через {@code ShaurmaLib.Builder.withXxx(...)} —
 * {@link WorldBillboardPrimitives} і {@link MarkerRenderRules} обидва
 * статичні/безстанові (створюються консюмером один раз як constants),
 * консюмер просто викликає їх зі свого власного
 * {@code RenderLevelStageEvent}-хендлера (як в оригінальних
 * {@code LeaderboardWorldRenderer}/{@code C4MarkerRenderer}).
 * <p>
 * <b>Що лишається продуктовою специфікою консюмера, не переноситься</b>
 * (план, розділ 3.13): сам дизайн конкретної мітки (розмір карток
 * лідерборду, кольори по типу метрики, шрифт заголовків, розмір/колір
 * ромба C4) і які саме мітки/скільки їх зараз існує — рушій дає лише
 * culling/orientation-цикл і low-level примітиви рендеру, не готові
 * "картки". Консюмер компонує їх у власному
 * {@code drawBoard}/{@code drawCard}-подібному callback, як і раніше, але
 * без дублювання obscure z-bias/billboard-текст математики чи циклу
 * culling/orientation по кожному новому типу мітки.
 * <p>
 * Приклад для нового типу мітки в майбутньому режимі (maniac), тепер через
 * {@link MarkerRenderRules} замість ручного циклу:
 * <pre>{@code
 * private static final MarkerRenderRules<MyMarker> RULES = MarkerRenderRules.leaderboardDefaults();
 *
 * @SubscribeEvent
 * static void onRenderLevel(RenderLevelStageEvent event) {
 *     if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
 *     RULES.renderFixedYaw(event.getPoseStack(), event.getCamera(), myMarkers,
 *             myVisibilityPolicy, mc.player.getUUID(), MyMarker::appearAlpha,
 *             (ps, marker, axes, dist, alpha) -> {
 *                 WorldBillboardPrimitives.quad(ps, axes.right(), axes.forward(), 0f, -1f, 0f, 2f, 0.4f, 0xAA000000);
 *                 WorldBillboardPrimitives.text(ps, mc.font, axes.right(), axes.forward(), 0.01f, 0, 0.2f, marker.title(), 0xFFFFFFFF, true, 0.013f);
 *             });
 * }
 * }</pre>
 */
public interface MarkersModuleHook {
    void onAttach(FMLModuleContext ctx);
}
