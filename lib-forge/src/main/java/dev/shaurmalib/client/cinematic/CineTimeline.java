package dev.shaurmalib.client.cinematic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Дата-driven заміна {@code SCCinematicSequencer}: замість
 * захардкодженого {@code enum State} + {@code switch} + окремого
 * {@code applyPingCompensation()}, що вручну повторював кожен крок
 * ще раз (6 копіпаст-блоків з однаковими "виставити прапорці, що вже
 * зроблено"), тут — один список {@link CineStep}, і рушій сам вміє:
 *
 *  1. тікати кроки по черзі (природний прохід у реальному часі),
 *  2. "доганяти" (catch-up) — коли сесія стартує з ненульовим
 *     offsetMs (ping-compensation: гравець доєднався пізніше/лагнув),
 *     рушій сам проходить усі кроки, чий час вже минув, викликаючи їм
 *     {@code onEnter}-кʼю з {@code catchingUp=true} — ОДИН прохід
 *     коду замість окремої копії на кожен крок,
 *  3. записувати послідовність подій у {@link CineRecording}, якщо
 *     {@link Builder#recordable} увімкнено — той самий timeline, що
 *     керує кінематикою наживо, може бути "програний" пізніше з
 *     диска в replay-режимі (див. {@link CineContext#isReplay()}) —
 *     саме та властивість, яка знадобиться для реплею матчу/карти в
 *     інших модах: точки камери, звуки й overlay-и записуються як
 *     дані, а не як черговий хардкод-клас.
 *
 * Кроки типу {@link CineStep.Kind#AWAITING_SIGNAL} чекають виклику
 * {@link #signal(String)} з відповідним id — аналог
 * {@code onServerStartCountdown()}, узагальнений на будь-який крок,
 * а не лише MISSION_OVERLAY → COUNTDOWN.
 */
public final class CineTimeline {

    private final List<CineStep> steps;
    private final CineContext context;
    private final CineRecording recording; // nullable

    private int currentIndex = -1;
    private long stepStartMs;
    private boolean finished = false;
    private final Set<String> pendingSignals = new HashSet<>();
    private Consumer<CineTimeline> onDone;

    private CineTimeline(List<CineStep> steps, CineContext context, CineRecording recording) {
        this.steps = steps;
        this.context = context;
        this.recording = recording;
    }

    public static Builder builder(CineContext context) {
        return new Builder(context);
    }

    /** Стартує таймлайн з нуля (звичайний хост-старт, серверна сторона, час=0). */
    public void begin() {
        begin(0L);
    }

    /**
     * Стартує таймлайн, одразу перемотавши на offsetMs — заміна
     * {@code applyPingCompensation(alreadyElapsed)}. offsetMs=0
     * еквівалентно {@link #begin()}.
     */
    public void begin(long offsetMs) {
        currentIndex = -1;
        finished = false;
        pendingSignals.clear();
        boolean catchingUp = offsetMs > 0;
        advanceTo(0, catchingUp);
        stepStartMs = now() - offsetMs;
        if (catchingUp) catchUpIfNeeded();
    }

    /** Головний тік — викликати щоклієнтський тік, як {@code SCCinematicSequencer.onClientTick()}. */
    public void tick() {
        if (finished || currentIndex < 0) return;

        CineStep step = steps.get(currentIndex);
        long elapsed = now() - stepStartMs;

        boolean stepFinished = switch (step.kind()) {
            case TIMED -> elapsed >= step.durationMs();
            case AWAITING_SIGNAL -> pendingSignals.remove(step.id()) || elapsed >= step.durationMs();
        };

        if (stepFinished) {
            advance();
        }
    }

    /**
     * Зовнішній сигнал (напр. {@code CinematicStartCountdownPacket} з
     * сервера) для кроку {@code AWAITING_SIGNAL} з відповідним id.
     * Якщо таймлайн ще не дійшов до цього кроку — сигнал запам'ятовується
     * і спрацює одразу, як тільки крок настане (той самий випадок, що
     * {@code serverCountdownSignalReceived} прапорець у снайперах —
     * сервер може надіслати старт до того, як клієнт дійшов до
     * MISSION_OVERLAY через повільний WHITEN_IN).
     */
    public void signal(String stepId) {
        pendingSignals.add(stepId);
        if (currentIndex >= 0 && !finished) {
            CineStep step = steps.get(currentIndex);
            if (step.id().equals(stepId) && step.kind() == CineStep.Kind.AWAITING_SIGNAL) {
                pendingSignals.remove(stepId);
                advance();
            }
        }
    }

    public boolean isActive() { return !finished && currentIndex >= 0; }
    public boolean isFinished() { return finished; }
    public String currentStepId() { return finished || currentIndex < 0 ? null : steps.get(currentIndex).id(); }
    public CineContext context() { return context; }

    /** Аварійне скасування (напр. гравець від'єднався під час кінематики). */
    public void cancel() {
        finished = true;
        currentIndex = -1;
    }

    // ── internal ─────────────────────────────────────────────────────────

    private void advance() {
        advanceTo(currentIndex + 1, false);
        stepStartMs = now();
    }

    private void advanceTo(int index, boolean catchingUp) {
        if (index >= steps.size()) {
            finished = true;
            currentIndex = -1;
            if (onDone != null) onDone.accept(this);
            return;
        }
        currentIndex = index;
        CineStep step = steps.get(index);
        runEnterCues(step, catchingUp);
    }

    /**
     * Пропускає (без видимого ефекту, catchingUp=true для onEnter
     * кожного пройденого і поточного кроку) усі кроки, чий час вже
     * минув відносно offsetMs — один цикл замість шести ручних копій
     * applyPingCompensation() у снайперах. onEnter поточного (не до
     * кінця пройденого) кроку теж викликається з catchingUp=true —
     * гравець застає цей крок серед його тривалості, а не з початку,
     * тому кʼю мають знати, що це не "натуральний" вхід.
     */
    private void catchUpIfNeeded() {
        while (currentIndex >= 0 && !finished) {
            CineStep step = steps.get(currentIndex);
            long elapsed = now() - stepStartMs;
            boolean stepAlreadyPassed = elapsed >= step.durationMs();

            if (!stepAlreadyPassed) {
                // Поточний крок ще не минув — його onEnter вже виконано
                // (з catchingUp=true) у advanceTo() вище, більше нічого
                // не робимо, tick() продовжить природним чином.
                return;
            }
            long overshoot = elapsed - step.durationMs();
            advanceTo(currentIndex + 1, true);
            stepStartMs = now() - overshoot;
        }
    }

    private void runEnterCues(CineStep step, boolean catchingUp) {
        for (CineCue cue : step.onEnterCues()) {
            cue.fire(context, catchingUp);
        }
        if (recording != null && !catchingUp) {
            recording.recordStepEnter(step.id(), now());
        }
    }

    private long now() { return System.currentTimeMillis(); }

    // ── Builder ──────────────────────────────────────────────────────────

    public static final class Builder {
        private final CineContext context;
        private final List<CineStep> steps = new ArrayList<>();
        private CineRecording recording;
        private Consumer<CineTimeline> onDone;

        private Builder(CineContext context) {
            this.context = context;
        }

        public Builder step(CineStep step) {
            steps.add(step);
            return this;
        }

        /** Вмикає запис проходження таймлайну у {@link CineRecording} — основа для реплею. */
        public Builder recordable(CineRecording recording) {
            this.recording = recording;
            return this;
        }

        public Builder onDone(Consumer<CineTimeline> callback) {
            this.onDone = callback;
            return this;
        }

        public CineTimeline build() {
            if (steps.isEmpty()) {
                throw new IllegalStateException("CineTimeline requires at least one step");
            }
            CineTimeline timeline = new CineTimeline(steps, context, recording);
            timeline.onDone = onDone;
            return timeline;
        }
    }
}
