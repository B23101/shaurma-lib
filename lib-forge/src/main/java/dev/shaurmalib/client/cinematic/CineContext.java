package dev.shaurmalib.client.cinematic;

import java.util.HashMap;
import java.util.Map;

/**
 * Контекст однієї активної {@link CineTimeline}-сесії — заміна
 * захардкодженого {@code private static String modeId} у
 * {@code SCCinematicSequencer}, розкиданого потім по десятку
 * {@code if ("scn".equals(modeId))}/{@code if (!"sr".equals(modeId))}
 * перевірок у кожній фазі.
 *
 * Замість перевірки конкретного рядка modeId усередині кʼю, мод сам
 * вирішує ЯКІ кʼю додати в таймлайн для якого режиму (SC/SR/SCN
 * будують по-різному складений {@link CineTimeline} через
 * {@link CineTimeline.Builder}) — тому саме тіло кʼю більше не
 * повинно "знати" про існування інших режимів. {@code modeId} тут
 * лишається — але як довідкова інформація (напр. для вибору звукового
 * банку), а не як розгалуження "чи взагалі виконувати цей кʼю".
 *
 * {@code data} — довільні typed-параметри для кʼю, що потребують
 * значення, відомі лише в момент запуску сесії (наприклад ключ
 * останнього обраного кіта для {@code KitSelectedAnnouncementOverlay}
 * — у снайперах читався напряму зі статичного поля іншого класу;
 * тут — явний параметр, переданий у {@link CineTimeline#begin}).
 */
public final class CineContext {

    private final String modeId;
    private final boolean replay;
    private final Map<String, Object> data = new HashMap<>();

    public CineContext(String modeId, boolean replay) {
        this.modeId = modeId;
        this.replay = replay;
    }

    public String modeId() { return modeId; }

    /**
     * true, якщо ця сесія програється з записаного {@code CineRecording}
     * (реплей карти/матчу), а не наживо із серверного пакету. Кʼю, що
     * надсилають мережеві ack-и (див. {@link CineCue} readySentToServer
     * аналог), мають пропускати мережевий виклик у replay-режимі — сесія
     * відтворюється локально, сервера, який чекає ack, може взагалі не
     * існувати (перегляд збереженого запису офлайн).
     */
    public boolean isReplay() { return replay; }

    public CineContext put(String key, Object value) {
        data.put(key, value);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    public <T> T get(String key, T defaultValue) {
        Object v = data.get(key);
        return v == null ? defaultValue : (T) v;
    }
}
