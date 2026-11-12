package dev.shaurmalib.client.cinematic;

/**
 * Одна подія кінематографічного таймлайну — узагальнення того, що в
 * {@code SCCinematicSequencer} було жорстко прошите в
 * {@code tickHold()/tickWhitenIn()/tickMissionOverlay()/...}: показ
 * оверлею, звук, зміна музики, вимкнення камери, мережевий ack,
 * очікування сигналу сервера. У снайперах кожен такий крок:
 *
 *  1. мав власний boolean-прапорець "чи вже виконано" (whitenInStarted,
 *     missionOverlayShown, kitScreenClosed, readySentToServer, ...),
 *  2. дублювався ще раз у {@code applyPingCompensation()} — коли
 *     клієнт приєднується/лагає і має "перестрибнути" вже пройдені
 *     кроки, довелось вручну повторити кожен if встановлення прапорця
 *     в кожній гілці ping-compensation (6 однакових копіпаст-блоків),
 *  3. і ще раз у {@code reset()} (скидання прапорця).
 *
 * {@link CineCue} прибирає всі три копії в одну: рушій
 * ({@link CineTimeline}) сам знає, чи кʼю вже виконане (за фактом
 * виклику {@link #fire}), сам обробляє "миттєве перемотування" при
 * пізньому приєднанні (виконує без візуального ефекту всі кʼю, чий
 * момент вже минув — див. {@link CineTimeline#begin(long)}), і сам
 * скидає стан при кожному новому {@link CineTimeline#begin()}.
 *
 * Мод пише лише "що робити" (тіло {@link #fire}), не "чи вже робив" —
 * той самий клас багів, що описаний у плані бібліотеки для
 * {@code GameModeRegistry.setActive} (забутий null-чек), тут
 * структурно неможливий: {@link CineTimeline} гарантує рівно один
 * виклик {@link #fire} на кʼю за сесію.
 */
@FunctionalInterface
public interface CineCue {

    /**
     * Викликається рушієм рівно один раз, коли настає момент кʼю.
     *
     * @param ctx           контекст сесії (modeId, timeline-параметри, replay-режим)
     * @param catchingUp    true, якщо цей виклик — частина "перемотування" при
     *                      пізньому приєднанні/ping-compensation, а НЕ природний
     *                      прохід таймлайну в реальному часі. Кʼю з видимим
     *                      ефектом (звук, screen-flash) зазвичай перевіряють цей
     *                      прапорець і мовчки пропускають власний рендер/звук,
     *                      залишаючи побічний ефект (напр. "камеру вимкнено")
     *                      — так само, як ping-compensation у SCCinematicSequencer
     *                      вручну не грав звуки для пропущених фаз, лише
     *                      виставляв прапорці.
     */
    void fire(CineContext ctx, boolean catchingUp);

    /**
     * Кʼю, що нічого не робить, крім побічного ефекту через lambda —
     * зручний short-hand для випадків, де catchingUp не важливий
     * (стан ідемпотентний незалежно від того, доганяємо ми чи ні).
     */
    static CineCue simple(Runnable action) {
        return (ctx, catchingUp) -> action.run();
    }
}
