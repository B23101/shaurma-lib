package dev.shaurmalib.common.playeranim;

import static dev.shaurmalib.common.playeranim.PoseLifecycle.Decision.*;
import static dev.shaurmalib.common.playeranim.PoseLifecycle.State.*;

import dev.shaurmalib.harness.T;

/** Усі переходи таблиці з клас-докстрінгу {@link PoseLifecycle}. Чиста Java. */
public final class PoseLifecycleTest {

    public static void run() {
        T.section("PoseLifecycle — таблиця переходів");

        PoseLifecycle l = new PoseLifecycle();
        T.eq("початковий стан IDLE", l.state(), IDLE);
        T.eq("IDLE tick → NONE", l.tick(true, 5), NONE);
        T.eq("IDLE requestStop → NONE", l.requestStop(), NONE);
        T.eq("IDLE лишається IDLE", l.state(), IDLE);

        // HOLD
        l.begin(true, 4);
        T.eq("begin → PLAYING", l.state(), PLAYING);
        for (int i = 0; i < 500; i++) l.tick(true, PoseLifecycle.UNBOUNDED);
        T.eq("HOLD 500 тіків → усе ще PLAYING", l.state(), PLAYING);
        T.eq("HOLD requestStop fadeOut>0 → START_FADE_OUT", l.requestStop(), START_FADE_OUT);
        T.eq("→ FADING_OUT", l.state(), FADING_OUT);
        T.eq("довжина fade = fadeOutTicks", l.currentFadeLength(), 4);
        T.eq("FADING_OUT requestStop → NONE", l.requestStop(), NONE);
        T.eq("fade tick 1 → NONE", l.tick(true, 0), NONE);
        T.eq("fade tick 2 → NONE", l.tick(true, 0), NONE);
        T.eq("fade tick 3 → NONE", l.tick(true, 0), NONE);
        T.eq("fade tick 4 → FINISH", l.tick(true, 0), FINISH);
        T.eq("після FINISH → IDLE", l.state(), IDLE);

        // HOLD, fadeOut == 0
        l.begin(true, 0);
        T.eq("HOLD fadeOut=0 requestStop → FINISH", l.requestStop(), FINISH);
        T.eq("→ IDLE", l.state(), IDLE);

        // HOLD, кліп раптом неактивний (хтось зупинив ззовні)
        l.begin(true, 4);
        T.eq("HOLD, кліп неактивний → FINISH (fade вже неможливий)", l.tick(false, 0), FINISH);

        // ONE_SHOT
        l.begin(false, 3);
        T.eq("ONE_SHOT далеко від кінця → NONE", l.tick(true, 10), NONE);
        T.eq("ONE_SHOT залишок 4 > fadeOut 3 → NONE", l.tick(true, 4), NONE);
        T.eq("ONE_SHOT залишок 3 ≤ fadeOut → START_FADE_OUT", l.tick(true, 3), START_FADE_OUT);
        T.eq("довжина = залишок (3)", l.currentFadeLength(), 3);
        T.eq("→ FADING_OUT", l.state(), FADING_OUT);

        l.begin(false, 10);
        T.eq("ONE_SHOT fadeOut 10, залишок 2 → START_FADE_OUT", l.tick(true, 2), START_FADE_OUT);
        T.eq("fade скорочено до залишку (2), а не 10", l.currentFadeLength(), 2);

        l.begin(false, 5);
        T.eq("ONE_SHOT залишок 0 → довжина щонайменше 1", l.tick(true, 0), START_FADE_OUT);
        T.eq("мінімум 1 тік", l.currentFadeLength(), 1);

        l.begin(false, 0);
        T.eq("ONE_SHOT fadeOut=0, кліп грає → NONE", l.tick(true, 1), NONE);
        T.eq("ONE_SHOT fadeOut=0, кліп зупинився → FINISH", l.tick(false, 0), FINISH);

        l.begin(false, 3);
        T.eq("ONE_SHOT, кліп уже неактивний → FINISH (не START_FADE_OUT)", l.tick(false, 0), FINISH);

        // requestStop з усіма варіантами
        l.begin(false, 6);
        T.eq("requestStop ONE_SHOT: залишок 2 < fadeOut 6", l.requestStop(true, 2), START_FADE_OUT);
        T.eq("довжина = min(6, 2) = 2", l.currentFadeLength(), 2);

        l.begin(false, 3);
        T.eq("requestStop, кліп неактивний → FINISH", l.requestStop(false, 0), FINISH);

        // begin посеред fade
        l.begin(true, 5);
        l.requestStop();
        T.eq("перед begin: FADING_OUT", l.state(), FADING_OUT);
        l.begin(true, 5);
        T.eq("begin посеред fade → PLAYING", l.state(), PLAYING);
        T.eq("лічильник fade скинуто", l.fadeRemaining(), 0);

        // fadeRemaining
        l.begin(true, 4);
        l.requestStop();
        T.eq("fadeRemaining одразу = 4", l.fadeRemaining(), 4);
        l.tick(true, 0);
        T.eq("після тіку = 3", l.fadeRemaining(), 3);

        // reset
        l.reset();
        T.eq("reset → IDLE", l.state(), IDLE);
        T.eq("reset → fadeRemaining 0", l.fadeRemaining(), 0);

        // негативний fadeOut нормалізується
        l.begin(true, -5);
        T.eq("від'ємний fadeOut → як 0", l.requestStop(), FINISH);
    }
}
