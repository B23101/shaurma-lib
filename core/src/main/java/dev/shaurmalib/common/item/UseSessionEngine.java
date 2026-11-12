package dev.shaurmalib.common.item;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Узагальнення стейт-машини {@code AnimatedItemSystem} з оригіналу
 * snipers_shaurma (392 рядки) — та ж anti-dupe логіка, той же порядок
 * перевірок, ті самі три "баги", явно виправлені коментарями в
 * оригінальному коді і збережені тут як інваріанти:
 * <p>
 * 1. Ефект застосовується ЛИШЕ якщо гравець тримає предмет у тій самій
 *    руці/слоті в момент activateTick. Якщо прибрав до activateTick —
 *    сесія скасовується без ефекту і без видалення предмета.
 * 2. Якщо ефект вже застосовано (activateTriggered=true) і гравець
 *    прибрав предмет — сесія продовжується до consumeTick і видаляє
 *    предмет зі ЗБЕРЕЖЕНОГО слоту (не з поточної руки).
 * 3. consume() завжди видаляє зі збереженого слоту (з fallback-пошуком
 *    по всьому інвентарю, якщо предмет перемістили — F-swap) — ніколи
 *    з "поточної руки", щоб не зачепити інший предмет після перемикання.
 * <p>
 * Це — ЧИСТА логіка: нуль Minecraft/Forge типів. Increment/decrement
 * подій (сервер-тік, ItemTossEvent) відбувається в lib-forge, який лише
 * викликає {@link #tick} і {@link #onItemTossed} у потрібний момент.
 *
 * @param <P> тип "гравець"-ідентифікатора для колбеків (на практиці ServerPlayer)
 * @param <H> тип "рука" для колбеків (на практиці InteractionHand)
 * @param <S> тип знімку стеку (opaque, див. {@link PlayerItemAccess})
 */
public final class UseSessionEngine<P, H, S> {

    /** Callback, що просить рушій скинути клієнтську анімацію на idle (мережа — форг-специфічна, тому SPI). */
    public interface AnimationResetter<P, H> {
        void resetToIdle(P player, H hand);
    }

    private record UseSession<H, S>(
            long startTick,
            HandSlot handSlot,
            H handValue,
            int slot,
            ItemDefinition<?, ?> def,
            boolean activateTriggered,
            S snapshot,
            Class<?> itemClass
    ) {
        UseSession<H, S> withTriggered() {
            return new UseSession<>(startTick, handSlot, handValue, slot, def, true, snapshot, itemClass);
        }
    }

    private final Map<UUID, UseSession<H, S>> active = new ConcurrentHashMap<>();
    /** Гравець викинув активний предмет (Q) — сутність чекає видалення на consumeTick. */
    private final Map<UUID, Runnable> pendingDroppedDiscard = new ConcurrentHashMap<>();

    private final PhantomSlotBridge<P> phantomSlotBridge; // nullable — опційна інтеграція
    /** Виняток з останнього onActivate, якщо був — форг-сторона читає й логує його після step(). */
    private volatile RuntimeException lastActivationError;

    public RuntimeException consumeLastActivationError() {
        RuntimeException e = lastActivationError;
        lastActivationError = null;
        return e;
    }

    public UseSessionEngine() {
        this(null);
    }

    public UseSessionEngine(PhantomSlotBridge<P> phantomSlotBridge) {
        this.phantomSlotBridge = phantomSlotBridge;
    }

    public boolean isPending(UUID uuid) {
        return active.containsKey(uuid);
    }

    /**
     * Починає сесію використання. Повертає false, якщо в гравця вже є
     * активна сесія (той самий інваріант, що й оригінал: одна сесія на
     * гравця одночасно, незалежно від предмета).
     */
    public boolean startUse(UUID uuid, HandSlot handSlot, H handValue, Class<?> itemClass,
                             ItemDefinition<?, ?> def, PlayerItemAccess<S> access) {
        if (active.containsKey(uuid)) return false;

        int slot = access.resolveSlot(handSlot);
        S snapshot = access.snapshotSlot(slot);

        active.put(uuid, new UseSession<>(access.currentGameTime(), handSlot, handValue, slot,
                def, false, snapshot, itemClass));
        return true;
    }

    /**
     * Баг 3 з оригіналу: примусово скасовує сесію (напр. при смерті гравця),
     * без застосування ефекту і без видалення предмета.
     */
    public void cancelSession(UUID uuid) {
        active.remove(uuid);
        pendingDroppedDiscard.remove(uuid);
    }

    /** Гравець викинув (Q) предмет, що зараз активний у сесії — позначаємо для видалення на consumeTick. */
    public void onItemTossed(UUID uuid, Class<?> tossedItemClass, Runnable discardDroppedEntity) {
        UseSession<H, S> session = active.get(uuid);
        if (session == null || !session.activateTriggered()) return;
        if (!session.itemClass().equals(tossedItemClass)) return;
        pendingDroppedDiscard.put(uuid, discardDroppedEntity);
    }

    /**
     * Один прохід стейт-машини для одного гравця. Викликається з
     * lib-forge на кожному сервер-тіку для кожного гравця з активною
     * сесією (форг-специфічний перебір {@code ACTIVE.entrySet()} лишається
     * на боці викликача — тут лише логіка одного кроку).
     *
     * @return true якщо сесію було завершено чи скасовано цим викликом
     * (викликач може прибрати запис зі свого зовнішнього індексу гравців,
     * якщо такий підтримується окремо).
     */
    @SuppressWarnings("unchecked")
    public StepResult step(UUID uuid, PlayerItemAccess<S> access,
                            AnimationResetter<P, H> animationResetter,
                            P playerForCallback) {
        UseSession<H, S> session = active.get(uuid);
        if (session == null) return StepResult.NONE;

        long elapsed = access.currentGameTime() - session.startTick();
        boolean holdingInHand = isHoldingInHand(access, session);

        if (!holdingInHand && !session.activateTriggered()) {
            // Ефект ще не застосовувався — скасовуємо повністю без наслідків.
            active.remove(uuid);
            pendingDroppedDiscard.remove(uuid);
            return StepResult.CANCELLED_BEFORE_ACTIVATION;
        }

        ItemDefinition<P, H> def = (ItemDefinition<P, H>) session.def();

        // ── activateTick ─────────────────────────────────────────────
        if (!session.activateTriggered() && def.activateTick >= 0 && elapsed >= def.activateTick) {
            if (!holdingInHand) {
                active.remove(uuid);
                return StepResult.CANCELLED_AT_ACTIVATION;
            }
            RuntimeException activationError = null;
            try {
                def.onActivate.onActivate(playerForCallback, session.handValue());
            } catch (RuntimeException e) {
                // Оригінал (AnimatedItemSystem.onServerTick) ковтає виняток і лише логує,
                // щоб один зіпсований onActivate не заморожував тик-цикл для інших гравців.
                // Сесія все одно переходить у activateTriggered=true — ефект вважається
                // "спробуваним", предмет далі буде витрачено на consumeTick як завжди.
                activationError = e;
            }
            active.put(uuid, session.withTriggered());
            lastActivationError = activationError;
            return StepResult.ACTIVATED;
        }

        // ── consumeTick ──────────────────────────────────────────────
        if (elapsed >= def.consumeTick) {
            active.remove(uuid);
            animationResetter.resetToIdle(playerForCallback, session.handValue());

            Runnable droppedDiscard = pendingDroppedDiscard.remove(uuid);
            if (droppedDiscard != null) {
                droppedDiscard.run();
                return StepResult.CONSUMED_FROM_DROPPED_ENTITY;
            }

            if (phantomSlotBridge != null
                    && phantomSlotBridge.isFrozenInPhantomSlot(playerForCallback, session.itemClass(), session.snapshot())) {
                phantomSlotBridge.shrinkFrozenItem(playerForCallback);
                return StepResult.CONSUMED_FROM_PHANTOM_SLOT;
            }

            int realSlot = access.findMatchingSlot(session.snapshot(), session.slot());
            if (realSlot >= 0) {
                access.consumeOne(realSlot);
                return StepResult.CONSUMED_FROM_SLOT;
            }
            return StepResult.CONSUME_TARGET_NOT_FOUND;
        }

        return StepResult.PENDING;
    }

    private boolean isHoldingInHand(PlayerItemAccess<S> access, UseSession<H, S> session) {
        if (!access.slotMatchesSnapshot(session.slot(), session.snapshot())) return false;
        return access.isHandActive(session.handSlot(), session.slot());
    }

    public Optional<HandSlot> activeHandSlot(UUID uuid) {
        UseSession<H, S> s = active.get(uuid);
        return s == null ? Optional.empty() : Optional.of(s.handSlot());
    }

    /**
     * Знімок UUID гравців з активною сесією на момент викову — навмисно
     * КОПІЯ (не live view), щоб lib-forge міг вільно викликати
     * {@link #cancelSession}/{@link #step} у циклі без ризику
     * concurrent-модифікації внутрішньої мапи під час ітерації
     * (ConcurrentHashMap це технічно дозволяє, але явна копія прибирає
     * будь-яку залежність від цієї деталі реалізації).
     */
    public java.util.List<UUID> activePlayersSnapshot() {
        return new java.util.ArrayList<>(active.keySet());
    }

    public enum StepResult {
        NONE, PENDING, ACTIVATED,
        CANCELLED_BEFORE_ACTIVATION, CANCELLED_AT_ACTIVATION,
        CONSUMED_FROM_SLOT, CONSUMED_FROM_DROPPED_ENTITY, CONSUMED_FROM_PHANTOM_SLOT,
        CONSUME_TARGET_NOT_FOUND
    }
}
