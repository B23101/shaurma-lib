package dev.shaurmalib.common.item;

import java.util.UUID;

/**
 * Мінімальна поверхня доступу до "гравець + інвентар", яку потребує
 * {@link UseSessionEngine}, щоб сам движок анти-дюп стейт-машини лишався
 * чистою common-логікою (0 Forge/Mojang-типів у сигнатурах), а не
 * тягнув {@code ServerPlayer}/{@code ItemStack} напряму.
 * <p>
 * lib-forge реалізує це один раз (обгортка над {@code ServerPlayer}) —
 * increment/decrement API руху сесії лишається ідентичним оригінальному
 * {@code AnimatedItemSystem}, лише розділеним на "що" (тут) і "як саме
 * дістати з Minecraft" (lib-forge).
 *
 * @param <S> тип "знімку стеку" — opaque-об'єкт, який рушій просто зберігає
 *            і повертає назад через {@link #stacksEqual}; на практиці це
 *            пара (клас предмета, копія NBT).
 */
public interface PlayerItemAccess<S> {

    UUID uuid();

    /** Поточний ігровий тік (для elapsed-розрахунків). */
    long currentGameTime();

    /** Індекс слоту, в якому фізично лежить предмет для даної руки в момент старту сесії. */
    int resolveSlot(HandSlot hand);

    /** Знімок стеку (клас + NBT) у вказаному слоті, або null якщо порожньо/не застосовно. */
    S snapshotSlot(int slot);

    /** Чи стек у {@code slot} все ще той самий предмет, що описаний у {@code snapshot}. */
    boolean slotMatchesSnapshot(int slot, S snapshot);

    /** Чи гравець зараз тримає активним саме той слот/руку, з якою почалась сесія. */
    boolean isHandActive(HandSlot hand, int slot);

    /** Сканує весь інвентар (включно з offhand) шукаючи предмет, що збігається зі знімком. -1 якщо не знайдено. */
    int findMatchingSlot(S snapshot, int preferredSlot);

    /** Видаляє 1 екземпляр предмета зі слоту (з очищенням GeckoLib-тегів анімації). */
    void consumeOne(int slot);
}
