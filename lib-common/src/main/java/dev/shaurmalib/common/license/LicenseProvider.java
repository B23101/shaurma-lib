package dev.shaurmalib.common.license;

import java.nio.file.Path;

/**
 * Контракт ліцензійної перевірки (план, п. 3.31 і розділ 4, пункт 2).
 * <p>
 * <b>Важливо:</b> бібліотека НІКОЛИ не бачить і не зберігає сам секрет чи
 * алгоритм перевірки. У snipers_shaurma це зараз
 * {@code core.license.LicenseValidator} — клас із власною XOR-обфускованою
 * HMAC-сіллю (32-байтний {@code _SALT_A}/{@code _SALT_B}) і власним форматом
 * ключа (Base58, HmacSHA256) — це комерційна специфіка ІНШОГО продукту
 * (snipers), тому цей клас <u>не переноситься в lib</u> і не повинен туди
 * потрапити навіть частково.
 * <p>
 * Замість цього кожен споживач (snipers_shaurma, maniac-mode) реалізує
 * {@link LicenseProvider} сам — своїм власним алгоритмом/ключем/сховищем
 * (або взагалі без ліцензування, якщо режиму це не потрібно — тоді просто
 * не викликає {@code ShaurmaLib.Builder.withLicense(...)}). {@link LicenseGate}
 * лише викликає ці два методи в потрібний момент lifecycle і кеширує
 * булевий результат {@link #isActivated()} — каркас, а не секрет.
 */
public interface LicenseProvider {

    /**
     * Викликається один раз при старті сервера, до будь-якої іншої логіки
     * мода (так само, як зараз {@code LicenseValidator.init(...)}
     * викликається в {@code SnipersMod.onServerStarted} першим, перед
     * {@code GameModeRegistry.initPersistence(...)}).
     *
     * @param licenseDir тека для зберігання ліцензійних файлів
     *                   (типово {@code worldRoot/.license} чи подібне —
     *                   визначає сам споживач при виклику {@code withLicense(...)}).
     */
    void init(Path licenseDir);

    /** Чи активована ліцензія станом на останній {@link #init} / {@link #activate}. */
    boolean isActivated();

    /**
     * Спроба активації новим ключем (наприклад з команди
     * {@code /sg license activate <key>}). Повертає {@code true}, якщо
     * ключ валідний і збережений.
     */
    boolean activate(Path licenseDir, String key);

    /**
     * Опційний людський ідентифікатор інстансу для відображення в чаті/
     * логах (у snipers це {@code LicenseValidator.getMaskedId()} —
     * замаскований hex). Може повернути {@code null}, якщо провайдер
     * не підтримує такий ідентифікатор.
     */
    default String displayId() {
        return null;
    }
}
