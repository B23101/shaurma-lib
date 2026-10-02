package dev.shaurmalib.forge.module;

/**
 * Маркер підключення системи «офлайн-присутності» до
 * {@link dev.shaurmalib.forge.ShaurmaLib.Builder} (план, §5.13) — той самий
 * стиль, що {@link DamageModuleHook}/{@link SkinnableEntityModuleHook}.
 * <p>
 * Фактичну проводку робить {@code withOfflinePresence(config)}: у {@code build()}
 * створюється {@link dev.shaurmalib.forge.offline.OfflinePresenceModule}, який
 * <ul>
 *   <li>реєструє {@code OfflinePresenceHooks} на {@code MinecraftForge.EVENT_BUS}
 *       (лише коли модуль запитано: глобального {@code @Mod.EventBusSubscriber} немає);</li>
 *   <li>підписується на {@code GameModeRegistry.onDeactivation}, якщо підключено
 *       {@code withModeContract}: {@code closeScope(MODE_DEACTIVATED)};</li>
 *   <li>підписується на {@code MatchLifecycleBus}, якщо задано
 *       {@code bindScopeToLifecycle}.</li>
 * </ul>
 * Доступ після збірки: {@code handle.offlinePresenceModule()}. Виклик без
 * {@code withOfflinePresence(...)} кидає {@link IllegalStateException}.
 */
public interface OfflinePresenceModuleHook {
    void onAttach(FMLModuleContext ctx);
}
