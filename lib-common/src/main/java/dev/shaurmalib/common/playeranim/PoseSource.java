package dev.shaurmalib.common.playeranim;

/**
 * Джерело анімації для одного {@link PoseLayerId}-шару (план, п. 3.24).
 * Чисті дані — не залежить від {@code dev.kosmx.playerAnim.*} напряму,
 * щоб {@code lib-common} лишався бібліотеко-агностичним (той самий
 * принцип, що {@link dev.shaurmalib.common.item.ItemCameraTrack}: common
 * описує намір, lib-forge інтерпретує його через конкретне API).
 * <p>
 * Цей перший переніс покриває лише json-керовану гілку (те, що реально
 * використовує оригінальний {@code PlayerAnimDropBridge} —
 * {@code PlayerAnimationRegistry.getAnimation(...)} з ресурс-пака).
 * Процедурний each-tick pose provider (частина плану п. 3.24, для
 * майбутніх поз без готового json-запису) — окреме розширення поверх
 * {@link PlayerPoseController}, не додане в цьому переносі, щоб не
 * вигадувати API, якого немає прецеденту в оригінальному коді.
 *
 * @param resourceId  ідентифікатор {@code KeyframeAnimation}, зареєстрованої
 *                    PlayerAnimationLib через ресурс-пак
 *                    ({@code assets/<ns>/player_animations/<name>.json}).
 * @param namespace   простір імен ресурсу (типово namespace консюмера).
 * @param looping     чи анімація має циклічно повторюватись, поки шар
 *                    активний (drop-fall, zipline-ride) — {@code false}
 *                    для одноразових поз, що самі завершуються. Наразі
 *                    документує намір консюмера; сам {@code
 *                    KeyframeAnimationPlayer} у {@link PlayerPoseController}
 *                    керує циклічністю через властивості завантаженого
 *                    {@code KeyframeAnimation} (як в оригіналі) — це поле
 *                    підготовлене для явного override у наступному етапі.
 */
public record PoseSource(String namespace, String resourceId, boolean looping) {

    public static PoseSource jsonAnimation(String namespace, String resourceId, boolean looping) {
        return new PoseSource(namespace, resourceId, looping);
    }
}
