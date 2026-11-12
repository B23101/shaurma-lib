package dev.shaurmalib.common.blocks;

/**
 * Назви GeckoLib-анімаційних кліпів (Blockbench {@code animation.*.json} імена),
 * що відповідають кожному {@link AnimatedBlockState} (план, п. 3.27). Чисті
 * рядкові дані — жодного {@code RawAnimation}/GeckoLib-типу тут навмисно
 * немає, це залишається виключно {@code lib-forge}-шаром (Architecture
 * Sniffer, план п. 2.1), тому {@code lib-common} лише описує "яка назва
 * кліпа для якого стану", не "як цей кліп програти".
 * <p>
 * У {@code CaseBlockEntity} snipers_shaurma ці чотири назви були
 * захардкоджені як {@code private static final RawAnimation} константи
 * ({@code ANIM_IDLE/ANIM_OPEN/ANIM_CLOSE/ANIM_REFILL}) з фіксованими
 * рядками {@code "idle"/"open"/"close"/"refill"}. Тут вони стають
 * параметром консюмера — інший блок (наприклад дверцята, що замикаються, а
 * не кейс, що рефіллиться) описує свій набір назв кліпів, а рушій лишається
 * тим самим.
 *
 * @param idle   назва циклічного кліпа для {@link AnimatedBlockState#IDLE}.
 * @param open   назва одноразового кліпа переходу в {@link AnimatedBlockState#OPEN}.
 * @param close  назва одноразового кліпа переходу назад у {@link AnimatedBlockState#IDLE}.
 * @param refill назва одноразового кліпа для {@link AnimatedBlockState#REFILLING}.
 *               Може бути {@code null}, якщо консюмерський блок не має
 *               фази рефілу — рушій тоді просто не запитує цей стан
 *               (див. клас-докстрінг {@code AnimatedBlockEntityBase}).
 */
public record AnimatedBlockClipSet(String idle, String open, String close, String refill) {

    /** Той самий набір назв, що {@code CaseBlockEntity}: idle/open/close/refill. */
    public static AnimatedBlockClipSet standard() {
        return new AnimatedBlockClipSet("idle", "open", "close", "refill");
    }

    /** Варіант без фази рефілу — для блоків, що лише відкриваються/закриваються. */
    public static AnimatedBlockClipSet withoutRefill(String idle, String open, String close) {
        return new AnimatedBlockClipSet(idle, open, close, null);
    }
}
