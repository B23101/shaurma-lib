package dev.shaurmalib.forge.camera;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Центральний реєстр "хто зараз тримає клієнтську камеру" (п. 3.15
 * плану). Замінює патерн, який був у snipers_shaurma: кожен новий
 * camera-менеджер (KitSelectCameraManager, EndGameCameraManager,
 * CameraSpectateHandler, DropAnimationHandler, SDRoundCameraManager)
 * писав власний {@code isActive()} і ручний перелік перевірок ВСІХ
 * ІНШИХ менеджерів перед тим, як повернути камеру на гравця:
 * <pre>
 * // оригінал, CameraSpectateHandler.stopSpectating():
 * if (!KitSelectCameraManager.isActive()
 *         && !DropAnimationHandler.isActive()
 *         && !EndGameCameraManager.isActive()
 *         && ...)
 *     mc.setCameraEntity(mc.player);
 * </pre>
 * Кожен новий менеджер камери означав редагування списку перевірок у
 * ВСІХ вже існуючих менеджерах — джерело задокументованих у
 * оригінальному коді race conditions (KitSelectCameraManager.disable()
 * міг обірвати EndGame-кінематику, якщо порядок подій не збігався з
 * очікуваним).
 * <p>
 * Тут — стек власників (не набір прапорців): останній, хто захопив
 * камеру ({@link #acquire}), — активний власник; звільнення
 * ({@link #release}) знімає з вершини стека лише якщо токен збігається
 * (щоб застарілий/дублікатний release() не зняв чужого власника), і
 * камера автоматично переходить до наступного власника в стеку — або
 * до гравця, якщо стек порожній. Жодному camera-менеджеру не потрібно
 * знати про існування інших: кожен просто acquire()/release() свій
 * власний {@link CameraOwner}, реєстр сам вирішує, хто зараз повинен
 * керувати {@code mc.setCameraEntity(...)}.
 */
@OnlyIn(Dist.CLIENT)
public final class CameraOwnershipRegistry {

    /** Токен володіння — повертається з {@link #acquire}, потрібен для коректного {@link #release}. */
    public static final class OwnershipToken {
        private final CameraOwner owner;
        private OwnershipToken(CameraOwner owner) { this.owner = owner; }
    }

    private static final Deque<CameraOwner> stack = new ArrayDeque<>();

    private CameraOwnershipRegistry() {}

    /**
     * Захоплює камеру для {@code owner}, який стає новим активним
     * власником (поверх усіх попередніх, якщо такі є). Не викликає
     * {@code mc.setCameraEntity(...)} сам — {@code owner} відповідає за
     * встановлення власної camera entity одразу після виклику
     * {@code acquire}; реєстр лише відстежує порядок володіння для
     * коректного {@link #release}.
     */
    public static OwnershipToken acquire(CameraOwner owner) {
        stack.push(owner);
        return new OwnershipToken(owner);
    }

    /**
     * "Тихо" знімає {@code owner} зі стека БЕЗ виклику {@code resumeOwnership()}
     * наступного власника і без падіння на гравця — на відміну від
     * {@link #release}. Призначений для консюмерів, які деспаунять свою
     * стару camera entity лише для того, щоб одразу (в тому самому
     * виклику, без проміжного кадру) поставити нову і викликати
     * {@link #acquire} повторно — типовий випадок "enable() викликано
     * вдруге поки попередня сесія цього ж контролера ще активна" (джерело:
     * {@code SDRoundCameraManager.startIdle()} робить це навмисно з
     * {@code returnToPlayer=false} саме щоб уникнути видимого стрибка
     * камери на гравця між старою і новою сесією). Якщо викликати замість
     * цього {@link #release}, і {@code owner} на момент виклику є вершиною
     * стека, гравець встиг би побачити один кадр на попередньому
     * власнику/гравці між зняттям старої камери і встановленням нової.
     */
    public static void discardSilently(OwnershipToken token) {
        if (token == null) return;
        stack.remove(token.owner);
    }

    /**
     * Звільняє камеру. Якщо {@code token} відповідає поточному
     * власнику на вершині стека — знімає його і автоматично передає
     * камеру наступному власнику в стеку ({@link CameraOwner#resumeOwnership()}),
     * або гравцю, якщо стек порожній. Якщо {@code token} НЕ відповідає
     * вершині (застарілий/дублікатний виклик release() від власника, що
     * вже був витіснений) — видаляє його зі стека без побічних ефектів
     * на поточного активного власника (щоб пізній release() однієї
     * системи не перервав уже активну кінематику іншої — саме той клас
     * бага, що був задокументований в оригінальному
     * {@code KitSelectCameraManager.disableInternal}).
     */
    public static void release(OwnershipToken token) {
        if (token == null) return;
        if (!stack.isEmpty() && stack.peek() == token.owner) {
            stack.pop();
            resumeTopOrPlayer();
        } else {
            stack.remove(token.owner);
        }
    }

    private static void resumeTopOrPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (!stack.isEmpty()) {
            stack.peek().resumeOwnership();
            return;
        }
        if (mc.player != null && mc.getCameraEntity() != mc.player) {
            mc.setCameraEntity(mc.player);
        }
    }

    /** true, якщо {@code owner} наразі є активним (найвищим) власником камери. */
    public static boolean isActiveOwner(CameraOwner owner) {
        return stack.peek() == owner;
    }

    /** true, якщо будь-який власник наразі тримає камеру (тобто камера не на гравці). */
    public static boolean anyActive() {
        return !stack.isEmpty();
    }
}
