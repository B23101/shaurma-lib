package dev.shaurmalib.common.item;

/**
 * Перевизначення поведінки конкретної кістки GeckoLib-моделі предмета
 * відносно "реальної" руки гравця в слоті {@code handSlot}.
 * <p>
 * Це узагальнення ДВОХ окремих, НЕ синхронізованих механізмів оригіналу
 * (перевірено по реальному коду — вони незалежні одна від одної):
 * <ul>
 *   <li>{@code AnimatedGeoItemRenderer.renderRecursively} — кістки з
 *       іменами буквально "LeftArm"/"RightArm" ЗАВЖДИ (для всіх 8
 *       GeckoLib-предметів мода, без винятку) рендеряться як скін руки
 *       гравця замість геометрії предмета. Немає жодного whitelist на
 *       цьому рівні. {@code renderMode=SKIN_AS_ARM} — дефолт у рушію
 *       {@code AnimatedGeoItemRenderer} для будь-якої кістки з цими двома
 *       іменами, реєстрація {@link ArmOverride} не потрібна щоб УВІМКНУТИ
 *       цю поведінку — вона й так дефолтна.</li>
 *   <li>{@code ItemLeftArmHideHandler} + {@code LeftArmHideState} — ховає
 *       РЕАЛЬНУ ліву руку (тільки OFF_HAND) для жорсткого списку 4 класів
 *       предметів (Medkit/Tablet/LotteryTicket/RespawnCard), перевіряючи
 *       щокадру, чи на контролері "mainCtrl" зараз non-idle анімація.
 *       {@code LeftArmHideState.hideLeftArm} у реальному коді ніде не
 *       читається і не встановлюється — мертвий прапор; фактичне рішення
 *       приймається напряму в {@code onRenderHand}. Права рука (MAIN_HAND)
 *       у оригіналі такого приховування не мала ВЗАГАЛІ.</li>
 * </ul>
 * {@link ArmOverride} потрібен лише коли треба ВІДХИЛИТИСЬ від дефолту:
 * <ul>
 *   <li>інша назва кістки, ніж "LeftArm"/"RightArm" — {@link #of};</li>
 *   <li>кістка з іменем "LeftArm"/"RightArm", що в конкретній моделі є
 *       частиною геометрії предмета, а не рукою гравця (аналог
 *       оригінального {@code renderRecursivelyAsGeo}, яким жоден реальний
 *       предмет не скористався, але механізм лишається доступним) —
 *       {@link #disabled}.</li>
 * </ul>
 * {@code hideRealHand} стосується ВИКЛЮЧНО {@code ItemLeftArmHideEngine}
 * (друга вісь, окрема від рендеру скіна) — заміна жорсткого 4-класового
 * whitelist {@code ItemLeftArmHideHandler}, узагальнена на обидві руки
 * (в оригіналі MAIN_HAND такого механізму не мала взагалі).
 *
 * @param handSlot     яку реальну руку гравця це стосується
 * @param boneName     назва кістки в GeckoLib-моделі (Blockbench)
 * @param renderAsArm  якщо true (дефолт через {@link #of}) — рушій рендеру
 *                     підставляє скін гравця на цю кістку замість геометрії
 *                     предмета; якщо false ({@link #disabled}) — кістка
 *                     рендериться як звичайна частина гео-моделі, навіть
 *                     якщо її ім'я збігається з зарезервованим
 *                     "LeftArm"/"RightArm".
 * @param hideRealHand якщо true — {@code ItemLeftArmHideEngine} ховає
 *                     РЕАЛЬНУ руку гравця (RenderHandEvent/mixin) доки на
 *                     цій кістці/контролері активна non-idle анімація;
 *                     незалежно від {@code renderAsArm}.
 */
public record ArmOverride(HandSlot handSlot, String boneName, boolean renderAsArm, boolean hideRealHand) {

    /** Стандартний override: рендерити скін на кістці І ховати реальну руку під час анімації. */
    public static ArmOverride of(HandSlot handSlot, String boneName) {
        return new ArmOverride(handSlot, boneName, true, true);
    }

    /** Рендерити скін на кістці, але НЕ ховати реальну руку (окремі осі — див. клас-докстрінг). */
    public static ArmOverride skinOnly(HandSlot handSlot, String boneName) {
        return new ArmOverride(handSlot, boneName, true, false);
    }

    /**
     * Явно вимикає дефолтну "рука гравця" поведінку для кістки з
     * зарезервованим іменем "LeftArm"/"RightArm" — аналог оригінального
     * {@code renderRecursivelyAsGeo}: кістка лишається звичайною частиною
     * геометрії предмета, рушій рендеру її не чіпає, і arm-hide рушій
     * теж її ігнорує.
     */
    public static ArmOverride disabled(HandSlot handSlot, String boneName) {
        return new ArmOverride(handSlot, boneName, false, false);
    }
}