package dev.shaurmalib.client.cinematic;

/**
 * Приклад побудови стартової кінематики матчу через
 * {@link CineTimeline} — декларативний відповідник
 * {@code SCCinematicSequencer}, включно з усіма трьома варіаціями
 * (SC/SR "класична" послідовність і SCN "без білого фону на GO").
 *
 * На відміну від оригіналу, різниця між SC/SR і SCN тут — НЕ
 * {@code if ("scn".equals(modeId))} усередині кожного кроку, а
 * різний набір аргументів {@link CineHooks}, переданий модом при
 * побудові. Сам {@link CineTimeline} не знає, що таке "scn" — це і є
 * гнучкість, якої просив мод: режим друга (maniac) описує власний
 * {@link CineHooks} з іншими overlay-класами/звуками, не займаючи
 * жодного if-розгалуження в бібліотеці.
 *
 * {@link CineHooks} — контракт "що показати/зіграти на кожному кроці"
 * (мод підключає власні overlay/звук виклики); {@link #classic}/
 * {@link #scn}/{@link #srMissionOnly} лише збирають з них
 * {@link CineTimeline} з правильними тривалостями й порядком кроків —
 * саме структуру, яку snipers мала захардкодженою.
 */
public final class CinePresets {
    private CinePresets() {}

    /** Тривалості кроків — винесені як параметри, а не константи класу, як HOLD_MS/WHITEN_IN_MS/... у снайперах. */
    public record Timings(
            long holdMs,               // HOLD_MS = 2000
            long whitenInMs,           // WHITEN_IN_MS = 1000
            long missionOverlayTimeoutMs, // MISSION_OVERLAY_TIMEOUT_MS = 6000
            long countdownMs,          // COUNTDOWN_MS = 3000
            long whitenOutMs,          // WHITEN_OUT_MS = 500
            long goShowMs              // GO_SHOW_MS = 800
    ) {
        /** Значення з реального SCCinematicSequencer — стартова точка для власного тюнінгу. */
        public static Timings defaults() {
            return new Timings(2000, 1000, 6000, 3000, 500, 800);
        }
    }

    /**
     * Контракт того, що мод показує/грає на кожному кроці — заміна
     * прямих викликів {@code ScreenFadeOverlay}/{@code MissionStartOverlay}/
     * {@code CinematicCountdownOverlay}/{@code KitSelectCameraManager}
     * усередині самого сиквенсера бібліотеки. Бібліотека керує ЧАСОМ,
     * мод керує РЕНДЕРОМ — той самий розподіл відповідальності, що
     * вже прийнятий у camera.core (EffectCue callback-патерн).
     */
    public interface CineHooks {
        void onHoldStart();
        void disableSelectionCamera();          // KitSelectCameraDisable()
        void startWhiteFadeIn(long durationMs);  // ScreenFadeOverlay.startWhiteFade(WHITEN_IN_MS, 0, 0)
        void playScreenFlashSound();
        void showMissionOverlay();               // MissionStartOverlay.show(modeId)
        void showLastKitAnnouncement();          // KitSelectedAnnouncementOverlay.showLastKit() — SC/SCN, не SR
        void playCinematicMusic();
        void closeSelectionScreen();             // KitSelectScreen.closeIfOpen()
        void startCountdown(int digits);         // CinematicCountdownOverlay.start(3, modeId)
        void playPlaneFlyawaySound();             // тільки SC/SR
        void startWhiteFadeHold(long fadeOutMs, long holdMs); // ScreenFadeOverlay.startWhiteFade(WHITEN_OUT_MS,0,0) варіант SC/SR
        void showGoOnWhiteBackground(long holdMs); // ScreenFadeOverlay.startFullOpacityFadeWithKey(...) — SC/SR
        void showGoCinematic();                    // CinematicCountdownOverlay.showGo(modeId) — SCN
        void playGoSound();
    }

    /**
     * Класична послідовність SC/SR: HOLD → WHITEN_IN → MISSION_OVERLAY
     * (чекає сервер) → COUNTDOWN → WHITEN_OUT (з plane_flyaway, екран
     * лишається білим) → GO_SHOW (на білому фоні). Камера вимикається
     * в кінці WHITEN_IN (одразу після showMissionOverlay), як у
     * реальному {@code tickWhitenIn()} — щоб перехід був непомітний.
     */
    public static CineTimeline classic(CineContext ctx, Timings t, CineHooks hooks) {
        return CineTimeline.builder(ctx)
                .step(CineStep.timed("hold", t.holdMs())
                        .onEnter(hooks::onHoldStart))
                .step(CineStep.timed("whiten_in", t.whitenInMs())
                        .onEnter((c, catchingUp) -> {
                            hooks.startWhiteFadeIn(t.whitenInMs());
                            if (!catchingUp) hooks.playScreenFlashSound();
                            hooks.showMissionOverlay();
                            hooks.showLastKitAnnouncement();
                            hooks.playCinematicMusic();
                            hooks.disableSelectionCamera();
                            hooks.closeSelectionScreen();
                        }))
                .step(CineStep.awaitingSignal("mission_overlay", t.missionOverlayTimeoutMs())
                        .onEnter(hooks::showMissionOverlay))
                .step(CineStep.timed("countdown", t.countdownMs())
                        .onEnter((c, catchingUp) -> hooks.startCountdown(3)))
                .step(CineStep.timed("whiten_out", t.whitenOutMs())
                        .onEnter((c, catchingUp) -> {
                            if (!catchingUp) hooks.playPlaneFlyawaySound();
                            hooks.startWhiteFadeHold(t.whitenOutMs(), t.goShowMs());
                        }))
                .step(CineStep.timed("go_show", t.goShowMs())
                        .onEnter((c, catchingUp) -> {
                            hooks.showGoOnWhiteBackground(t.goShowMs());
                            if (!catchingUp) hooks.playGoSound();
                        }))
                .build();
    }

    /**
     * SCN-варіант: та сама структура кроків, різниця лише в тому, ЩО
     * робить hooks на whiten_out/go_show (без plane_flyaway, GO! без
     * білого фону) і в тому, що камера вимикається раніше — на вході
     * whiten_in, а не на виході (у снайперах: "SCN: FreeCamera
     * вимикається тут, при показі місія-розпочато"). Це окремий
     * builder-метод, а не прапорець усередині {@link #classic}, тому
     * що порядок кроків, а не лише вміст, відрізняється.
     */
    public static CineTimeline scn(CineContext ctx, Timings t, CineHooks hooks) {
        return CineTimeline.builder(ctx)
                .step(CineStep.timed("hold", t.holdMs())
                        .onEnter(hooks::onHoldStart))
                .step(CineStep.timed("whiten_in", t.whitenInMs())
                        .onEnter((c, catchingUp) -> {
                            hooks.startWhiteFadeIn(t.whitenInMs());
                            if (!catchingUp) hooks.playScreenFlashSound();
                            hooks.showMissionOverlay();
                            hooks.showLastKitAnnouncement();
                            hooks.playCinematicMusic();
                            hooks.disableSelectionCamera(); // SCN: тут, не в кінці кроку
                        }))
                .step(CineStep.awaitingSignal("mission_overlay", t.missionOverlayTimeoutMs())
                        .onEnter(hooks::showMissionOverlay))
                .step(CineStep.timed("countdown", t.countdownMs())
                        .onEnter((c, catchingUp) -> hooks.startCountdown(3)))
                .step(CineStep.timed("whiten_out", t.whitenOutMs())) // SCN: без plane_flyaway, без fade
                .step(CineStep.timed("go_show", t.goShowMs())
                        .onEnter((c, catchingUp) -> {
                            hooks.showGoCinematic();
                            if (!catchingUp) hooks.playGoSound();
                        }))
                .build();
    }

    /**
     * SR-drop: спрощений одноступеневий таймлайн — {@code showMissionForSRDrop()}
     * у снайперах не мав жодної фази окрім самого overlay. Тут це
     * один awaitingSignal-крок з timeout=тривалість overlay, а не
     * окремий {@code SR_MISSION_ONLY} enum-стан з власним tick-методом.
     */
    public static CineTimeline srMissionOnly(CineContext ctx, long overlayDurationMs, Runnable showOverlay) {
        return CineTimeline.builder(ctx)
                .step(CineStep.timed("sr_mission_only", overlayDurationMs)
                        .onEnter(showOverlay))
                .build();
    }
}
