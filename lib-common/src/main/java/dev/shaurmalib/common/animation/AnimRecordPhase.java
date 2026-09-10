package dev.shaurmalib.common.animation;

/**
 * Фаза сесії запису (план, п. 3.16) — спільна для всіх трьох оригінальних
 * менеджерів snipers_shaurma. {@code AnimRecordManager} (SC) не мав фази
 * {@code COUNTDOWN} узагалі (запис стартує одразу після телепорту), тому
 * для нього рушій просто пропускає цю фазу — {@link RecordSession} трактує
 * {@code countdownTicks == 0} як "почати одразу в RECORDING".
 */
public enum AnimRecordPhase {
    COUNTDOWN,
    RECORDING
}
