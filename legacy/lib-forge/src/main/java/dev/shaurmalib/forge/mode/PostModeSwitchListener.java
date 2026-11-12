package dev.shaurmalib.forge.mode;

/**
 * Хук, що викликається {@link GameModeRegistry} ПІСЛЯ того, як конфіг
 * нового активного режиму вже перезавантажено — заміна прямого виклику
 * {@code ConfigLoader.reloadAirdropConfig()}, який зараз захардкоджений
 * усередині оригінального {@code GameModeRegistry.setActive(...)}
 * (snipers airdrop.yml — режим-специфічний конфіг, що лежить ПОЗА деревом
 * {@code ModeConfigDescriptor} конкретного режиму, тож не підхоплюється
 * автоматично звичайним reload циклом).
 * <p>
 * Використовується рідше, ніж {@link ModeDeactivationListener} — лише
 * коли споживачу треба щось перечитати саме після зміни режиму, а не
 * після кожного {@code ConfigReloadBus.fireReload(...)}.
 */
@FunctionalInterface
public interface PostModeSwitchListener {
    void onModeSwitched(String newModeId);
}
