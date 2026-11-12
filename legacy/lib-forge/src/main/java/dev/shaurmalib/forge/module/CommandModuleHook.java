package dev.shaurmalib.forge.module;

import dev.shaurmalib.common.license.LicenseGate;
import dev.shaurmalib.common.lifecycle.MatchLifecycleBus;
import dev.shaurmalib.forge.command.ShaurmaCommandRoot;
import dev.shaurmalib.forge.mode.GameModeRegistry;

/**
 * Точка вбудовування системи команд (план, п. 3.30) — заміна кореневої
 * структури {@code SGCommand} snipers_shaurma
 * {@link dev.shaurmalib.forge.command.ShaurmaCommandRoot}.
 * <p>
 * На відміну від решти {@code ModuleHook}, {@link ShaurmaCommandRoot} не
 * підключається через {@code ShaurmaLib.Builder.withXxx(...)}, бо команди
 * реєструються у власній точці Forge lifecycle
 * ({@code RegisterCommandsEvent}), не в конструкторі {@code @Mod}-класу —
 * той самий принцип, що {@code ShaurmaLib.attachOverlayEngine}. Консюмер
 * створює {@link ShaurmaCommandRoot} напряму у своєму
 * {@code @SubscribeEvent}-хендлері на {@code RegisterCommandsEvent}, маючи
 * вже готовий {@code ShaurmaLib.Handle} (для {@code licenseModule().gate()},
 * {@code lifecycleModule().lifecycleBus()}, {@code modeContractModule().registry()}).
 * <p>
 * Приклад:
 * <pre>{@code
 * @SubscribeEvent
 * static void onRegisterCommands(RegisterCommandsEvent event) {
 *     ShaurmaCommandRoot cmdRoot = CommandModuleHook.of(
 *         "sg", lib.licenseModule().gate(), lib.lifecycleModule().lifecycleBus(),
 *         lib.modeContractModule().registry(),
 *         (source, teamMode) -> GameStateManager.startGame(source.getServer()),
 *         source -> GameStateManager.stopGame(),
 *         (source, player) -> NetworkHandler.sendToPlayer(player, buildSettingsPacket())
 *     ).build(event.getDispatcher());
 * }
 * }</pre>
 */
public interface CommandModuleHook {
    void onAttach(FMLModuleContext ctx);

    /**
     * Зручна фабрика, що одразу створює {@link ShaurmaCommandRoot} з
     * готовими модулями {@code Handle} — консюмер лише додає власні
     * продуктово-специфічні гілки командного дерева до
     * {@link ShaurmaCommandRoot#build()} перед реєстрацією.
     */
    static ShaurmaCommandRoot rootOf(String rootName,
                                      LicenseGate licenseGate,
                                      MatchLifecycleBus lifecycleBus,
                                      GameModeRegistry modeRegistry,
                                      ShaurmaCommandRoot.StartAction startAction,
                                      ShaurmaCommandRoot.StopAction stopAction,
                                      ShaurmaCommandRoot.SettingsAction settingsAction) {
        return new ShaurmaCommandRoot(rootName, licenseGate, lifecycleBus, modeRegistry,
                startAction, stopAction, settingsAction);
    }
}
