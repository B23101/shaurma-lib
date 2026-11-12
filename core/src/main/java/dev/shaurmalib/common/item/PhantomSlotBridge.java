package dev.shaurmalib.common.item;

/**
 * Опційний SPI-хук для anti-dupe перевірки в {@code UseSessionEngine}.
 * <p>
 * Джерело реальної проблеми: {@code AnimatedItemSystem.onServerTick} в
 * оригіналі напряму звертається до {@code PhantomSlotData.get(player)} —
 * capability, яка існує ЛИШЕ тому, що snipers_shaurma має фічі
 * "параглайдер по F" / "зіплайн по G", що тимчасово підміняють предмет у
 * слоті (immer PhantomMode.F/G). Якщо гравець розпочав use-анімацію
 * (protein_bar/medkit/...), ефект вже застосувався, а потім натиснув F/G —
 * реальний предмет "заморожується" у {@code PhantomSlotData.savedItem}
 * замість фізичного слоту. Без спеціальної перевірки на цей випадок
 * {@code consumeSlot()} нічого не знаходить і предмет НЕ витрачається,
 * хоча ефект уже отримано — прямий дюп.
 * <p>
 * Ця фіча (параглайдер/зіплайн-по-клавіші) сама по собі НЕ переноситься
 * в бібліотеку — вона snipers-специфічна. Але anti-dupe ГАЧОК має бути
 * в бібліотечному рушії, інакше режим-друга (maniac), якщо він колись
 * зробить щось подібне (підміна предмета в слоті по гарячій клавіші),
 * успадкує ту саму дірку без способу її закрити.
 * <p>
 * Якщо мод не реєструє {@link PhantomSlotBridge} — рушій просто не робить
 * цю додаткову перевірку (як і зараз для режимів/предметів, де такої
 * фічі немає).
 *
 * @param <P> тип гравця (на практиці net.minecraft.server.level.ServerPlayer)
 */
public interface PhantomSlotBridge<P> {

    /**
     * @return true якщо в гравця активний "фантомний" слот і саме в ньому
     * заморожений предмет класу {@code itemClass}, що збігається з
     * {@code itemNbtSnapshot} (порівняння NBT — деталі формату лишає собі
     * реалізація моста, рушій передає лише opaque-об'єкт зі свого боку).
     */
    boolean isFrozenInPhantomSlot(P player, Class<?> itemClass, Object itemNbtSnapshot);

    /** Зменшує заморожений предмет на 1 (аналог {@code shrinkSavedItem()} в оригіналі). */
    void shrinkFrozenItem(P player);
}
