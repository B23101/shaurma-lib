package dev.shaurmalib.common.modesettings;

import java.util.List;
import java.util.Map;

/**
 * Міст між {@code ModeSettingsScreen} (lib-forge, план 3.32) і мережевим
 * шаром консюмера. Бібліотека НЕ постачає власних мережевих пакетів для
 * цього модуля (на відміну від чату/радіо) — на момент, коли екран
 * відкривається, дані налаштувань (список {@link SettingDefinition} з
 * поточними значеннями) вже мають прийти від сервера тим каналом, який
 * обере сам консюмер (в оригіналі — {@code OpenGameSettingsPacket}); той
 * самий принцип, що {@code MarkerVisibilityPolicy} (план 3.13) — лібу
 * надає рушій рендеру/UI, правила й транспорт лишаються на боці мода.
 * <p>
 * Один екземпляр створюється консюмером один раз і передається в
 * {@code ModeSettingsScreen}-конструктор.
 */
public interface SettingsSyncBridge {

    /** Список гравців для вкладки "Гравці" — {@code null}/порожній список ховає вкладку неявно (порожній стан). */
    List<PlayerModeRow> playersList();

    /**
     * Викликається під час {@code init()} екрана — запит свіжого списку
     * гравців з сервера (в оригіналі — {@code RequestPlayerListPacket}).
     */
    void requestPlayerList();

    /** Перемкнути гравця player/spectator (в оригіналі — {@code SetPlayerModePacket}). */
    void togglePlayerSpectator(String playerName, boolean makeSpectator);

    /**
     * Зберегти обрані значення налаштувань (в оригіналі —
     * {@code GameSettingsSavePacket(Map)}). Порожня мапа означає
     * "скинути до дефолтів" (той самий сигнал, що {@code resetToDefaults()}
     * оригіналу).
     */
    void saveSettings(Map<String, Integer> selectedValues);

    /** Запросити зміну активного режиму (в оригіналі — {@code SetGameModePacket(ordinal)}). */
    void requestModeChange(String modeId);
}
